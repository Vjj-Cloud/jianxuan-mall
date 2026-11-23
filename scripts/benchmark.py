#!/usr/bin/env python3
"""
简选商城压测脚本（纯标准库，无需安装任何依赖）

用法：
  python scripts/benchmark.py                     # 全部场景（读 + 写 + 超卖），32 并发
  python scripts/benchmark.py -c 64 -n 1000       # 64 并发，每场景 1000 次
  python scripts/benchmark.py --scenario read     # 只压读接口
  python scripts/benchmark.py --scenario oversell # 只验证超卖（默认 10 件库存）
  python scripts/benchmark.py --scenario oversell --stock 50 -c 200

三个场景目的不同，别混为一谈：
  read     测读接口吞吐上限
  create   测下单写入吞吐（库存 100 万，永不会缺货，失败只可能是系统瓶颈）
  oversell 测正确性 —— 小库存 + 高并发，验证「不会把同一件商品卖给两个人」

它会自动完成：登录 → 建压测专用商品 → 压测 → 删掉商品（超卖场景查完库存才删）。

关于数字怎么读（很重要）：
  这是「本机 → 本机」的极限测试，客户端和服务端抢同一台机器的 CPU，
  网络延迟接近 0，所以绝对 QPS 只用于**横向对比**（改配置前 vs 改配置后），
  不能当作线上容量。线上真实 QPS 通常远低于这个数字。

清理压测数据：
  压测会往 orders / order_item 写入大量订单。跑完执行
      mysql -umall -p jianxuan_mall < scripts/cleanup_bench.sql
  即可清掉压测订单。
"""
import argparse
import http.client
import json
import statistics
import sys
import threading
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


class Client:
    """每个线程一个长连接，避免把 TCP 握手算进延迟。"""

    def __init__(self, host, port):
        self.conn = http.client.HTTPConnection(host, port, timeout=30)

    def request(self, method, path, body=None, token=None):
        headers = {}
        payload = None
        if body is not None:
            payload = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = "Bearer " + token
        self.conn.request(method, path, body=payload, headers=headers)
        resp = self.conn.getresponse()
        raw = resp.read()
        try:
            data = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            data = None
        return resp.status, data

    def close(self):
        self.conn.close()


def percentile(values, p):
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = min(int(len(ordered) * p / 100), len(ordered) - 1)
    return ordered[idx] * 1000  # 毫秒


def run_scenario(name, host, port, concurrency, total, make_call):
    """开 total 个请求，分布在 concurrency 个线程里，用 Barrier 让它们同时起跑。"""
    per_thread = total // concurrency
    latencies = []
    errors = []
    lock = threading.Lock()
    barrier = threading.Barrier(concurrency)

    def worker():
        client = Client(host, port)
        local_lat = []
        local_err = []
        try:
            barrier.wait()
            for _ in range(per_thread):
                t0 = time.perf_counter()
                try:
                    status, data = make_call(client)
                    ok = status == 200
                except Exception as exc:  # 连接被重置、超时等
                    status, data, ok = 0, None, False
                cost = time.perf_counter() - t0
                local_lat.append(cost)
                if not ok:
                    msg = data.get("message") if isinstance(data, dict) else str(data)
                    local_err.append(f"HTTP {status} {msg}")
        finally:
            client.close()
            with lock:
                latencies.extend(local_lat)
                errors.extend(local_err)

    threads = [threading.Thread(target=worker) for _ in range(concurrency)]
    started = time.perf_counter()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.perf_counter() - started

    success = len(latencies) - len(errors)
    qps = len(latencies) / elapsed if elapsed > 0 else 0
    avg = (sum(latencies) / len(latencies) * 1000) if latencies else 0

    print(f"\n{name}")
    print(f"  并发 {concurrency} × 每线程 {per_thread} 次 = {len(latencies)} 请求，耗时 {elapsed:.2f}s")
    print(f"  成功 {success} / 失败 {len(errors)}   吞吐 {qps:.0f} req/s")
    print(f"  延迟 avg {avg:.1f}ms | P50 {percentile(latencies, 50):.1f}ms "
          f"| P90 {percentile(latencies, 90):.1f}ms | P99 {percentile(latencies, 99):.1f}ms")
    if errors:
        from collections import Counter
        for msg, count in Counter(errors).most_common(3):
            print(f"  失败样例 ×{count}: {msg}")
    return {"qps": qps, "avg": avg, "p50": percentile(latencies, 50),
            "p90": percentile(latencies, 90), "p99": percentile(latencies, 99),
            "success": success, "failed": len(errors), "errors": errors}


def create_bench_product(admin, token, name, stock):
    """建一个压测专用商品，返回它的 id。"""
    status, data = admin.request("POST", "/api/admin/products", {
        "name": name,
        "subtitle": "benchmark only",
        "price": 1.00,
        "stock": stock,
        "categoryId": 1,
        "desc": "压测脚本自动创建，跑完会删除",
    }, token)
    if status != 200:
        print(f"创建压测商品失败: {status} {data}")
        sys.exit(1)
    return data["data"]["id"]


def run_oversell_scenario(host, port, admin, admin_token, user_token, stock, concurrency):
    """小库存 + 高并发：验证「原子扣减」到底挡不挡得住超卖。

    前面的 read / create 场景测的是吞吐（库存 100 万，永远买不完），
    只有这里测的是**正确性** —— 会不会把同一件商品卖给两个人。
    这是整个项目里唯一能用自动化证明的业务规则，比任何 QPS 数字都值钱。
    """
    print("\n" + "=" * 64)
    print(f"场景 C｜超卖验证：{stock} 件库存，{concurrency} 个并发同时下单")
    print("=" * 64)

    product_id = create_bench_product(admin, admin_token, "【压测】超卖验证商品", stock)
    print(f"  测试商品 id={product_id}，库存 {stock}，并发 {concurrency}")

    body = {
        "items": [{"productId": product_id, "quantity": 1}],
        "receiverName": "压测",
        "receiverPhone": "13800138000",
        "receiverAddress": "压测地址",
    }

    try:
        result = run_scenario(
            "  同时下单（每线程 1 单，Barrier 让它们同时起跑）",
            host, port, concurrency, concurrency,
            lambda c: c.request("POST", "/api/orders", body, user_token))

        status, data = admin.request("GET", f"/api/products/{product_id}")
        final_stock = data["data"]["stock"] if status == 200 else None

        # 失败的必须全是「业务拒绝」。如果混进超时或连接异常，
        # 前两条断言就可能碰巧成立 —— 比如库存 10 只成功 8 个，
        # 看着像防御生效，其实是请求根本没打进来。所以这条断言不能省。
        biz_reject = sum(1 for e in result["errors"] if "库存不足" in e)
        other_fail = len(result["errors"]) - biz_reject

        expect_ok = stock
        expect_reject = concurrency - stock
        passed = (result["success"] == expect_ok
                  and biz_reject == expect_reject
                  and other_fail == 0
                  and final_stock == 0)

        print(f"\n  核对结果")
        print(f"    成功下单       {result['success']:>4}   期望 {expect_ok}")
        print(f"    库存不足拒绝   {biz_reject:>4}   期望 {expect_reject}")
        print(f"    其他失败       {other_fail:>4}   期望 0")
        print(f"    剩余库存       {str(final_stock):>4}   期望 0")

        if passed:
            print("\n  通过：没有超卖，扣减数量准确")
        else:
            print("\n  未通过，请先看下面的「其他失败」样例")
            if other_fail:
                from collections import Counter
                bad = Counter(e for e in result["errors"] if "库存不足" not in e)
                for msg, count in bad.most_common(3):
                    print(f"    其他失败样例 x{count}: {msg}")
        return passed
    finally:
        admin.request("DELETE", f"/api/admin/products/{product_id}", token=admin_token)
        print("  测试商品已删除")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="localhost")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("-c", "--concurrency", type=int, default=32)
    ap.add_argument("-n", "--requests", type=int, default=500)
    ap.add_argument("--scenario", choices=["read", "create", "oversell", "all"], default="all")
    ap.add_argument("--stock", type=int, default=10,
                    help="超卖场景的库存数（默认 10，并发会自动抬到至少 2 倍库存）")
    args = ap.parse_args()

    host, port = args.host, args.port
    admin = Client(host, port)

    status, data = admin.request("POST", "/api/auth/login",
                                 {"username": "admin", "password": "admin123"})
    if status != 200:
        print("管理员登录失败，后端是否已启动？", status, data)
        sys.exit(1)
    admin_token = data["data"]["token"]

    status, data = admin.request("POST", "/api/auth/login",
                                 {"username": "user", "password": "user123"})
    if status != 200:
        print("普通用户登录失败", status, data)
        sys.exit(1)
    user_token = data["data"]["token"]

    results = {}
    oversell_passed = None

    # 读/写场景共用一个「库存 100 万」的商品：保证压测期间失败原因只会是系统瓶颈，
    # 而不是库存不足。超卖场景必须另建小库存商品 —— 两者目的相反，不能共用。
    if args.scenario in ("read", "create", "all"):
        bench_product_id = create_bench_product(
            admin, admin_token, "【压测】基准测试商品", 1000000)
        print(f"压测商品已创建 id={bench_product_id}（库存 1000000）")

        order_body = {
            "items": [{"productId": bench_product_id, "quantity": 1}],
            "receiverName": "压测",
            "receiverPhone": "13800138000",
            "receiverAddress": "压测地址",
        }

        try:
            if args.scenario in ("read", "all"):
                results["read"] = run_scenario(
                    "场景 A｜商品列表 GET /api/products（读，走分页 + 分类聚合）",
                    host, port, args.concurrency, args.requests,
                    lambda c: c.request("GET", "/api/products?page=1&size=10"))

            if args.scenario in ("create", "all"):
                results["create"] = run_scenario(
                    "场景 B｜下单 POST /api/orders（写，事务 + 原子扣库存 + 3 表写入）",
                    host, port, max(1, args.concurrency // 2), max(2, args.requests // 2),
                    lambda c: c.request("POST", "/api/orders", order_body, user_token))
        finally:
            admin.request("DELETE", f"/api/admin/products/{bench_product_id}", token=admin_token)
            print("\n压测商品已删除（订单里仍留有快照数据）")

    if args.scenario in ("oversell", "all"):
        stock = args.stock
        # 并发必须明显多于库存，否则「没超卖」可能只是因为请求不够多，测了等于没测
        oversell_concurrency = max(args.concurrency, stock * 2)
        oversell_passed = run_oversell_scenario(
            host, port, admin, admin_token, user_token, stock, oversell_concurrency)

    admin.close()

    if len(results) == 2:
        print("\n读/写差距")
        print(f"  读 {results['read']['qps']:.0f} req/s vs 写 {results['create']['qps']:.0f} req/s"
              f"（写约为读的 {results['create']['qps'] / results['read']['qps'] * 100:.0f}%）")
        print("  写入慢在预期内：每个订单要 5 次 DB 往返（查商品 / 扣库存 / 建单 / 建明细 / 清购物车），"
              "而且整段在事务里持有行锁。")

    if oversell_passed is not None:
        print(f"\n超卖验证：{'通过' if oversell_passed else '未通过'}")
        if oversell_passed:
            print("  这条比任何 QPS 数字都值钱 —— 它证明的是「不会卖超」，不是「跑得快」。")


if __name__ == "__main__":
    main()
