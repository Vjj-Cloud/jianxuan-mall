package com.jack.jianxuan.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 商品详情缓存的失效器，专门解决「删缓存」的时机与补偿。
 *
 * <p><b>为什么不能在事务里直接删（本项目踩过的真实 bug）：</b>
 * 取消订单时库存回补是一条 UPDATE，它在事务提交前对别的连接是不可见的。
 * 如果这时先删了缓存：
 * <pre>
 *  T1（取消订单）           T2（读商品详情）
 *  ├─ UPDATE stock+1         │
 *  ├─ DEL cache              │
 *  │                     ├─ 缓存未命中，回源查库
 *  │                     │  （读到的是没提交的旧库存）
 *  │                     └─ 把旧库存写回缓存，TTL 30 分钟
 *  └─ COMMIT
 * </pre>
 * 结果：库存已经回补成功，但缓存里是旧值，而且 30 分钟内没有任何动作会纠正它。
 * 用户看到「库存 3」，点了下单却提示库存不足——这正是 M8 里说的那种最难查的脏缓存。
 *
 * <p>「删除是幂等的，所以什么时候删都安全」这句话只对了一半：删除本身不会写脏数据，
 * 但它会打开一个「读请求把旧值回填」的窗口。窗口的另一端是「数据库什么时候可见新值」，
 * 所以删缓存必须排在提交之后。
 *
 * <p><b>为什么还要延迟再删一次：</b>
 * afterCommit 之后立刻删，仍然有一个极窄的窗口——T2 在提交前发起查询、在删除之后才写回缓存。
 * 这种偏差没法靠加锁彻底消除（那要把缓存读写放进数据库事务里，得不偿失），
 * 工程上的标准做法是「延迟双删」：提交后删一次，隔一小段时间再删一次，
 * 把那个慢半拍的读请求造成的回填也清掉。代价是多一次 DEL，可以忽略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCacheEvictor {

    /**
     * 补偿删除的延迟。
     *
     * <p>取 1 秒，是因为要覆盖的是「一次查询 + 一次写缓存」的耗时（毫秒级到百毫秒级），
     * 同时又远小于缓存 TTL（30 分钟）。不需要更精确：再长也只是让脏数据多活一会儿，
     * 再短则可能和慢查询撞在一起。
     */
    private static final long COMPENSATE_DELAY_MILLIS = 1000L;

    private final ProductService productService;

    /**
     * 单线程守护调度器，只用来跑补偿删除。
     *
     * <p>用守护线程：它不持有任何业务状态，不应该阻止 JVM 退出。
     * 单线程足够——补偿删除是「每秒几条 DEL」的量级，多线程只会增加连接池压力。
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "product-cache-evict");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 在「当前事务提交之后」失效缓存。事务回滚时什么都不做——那时库存没变，缓存本来就没脏。
     *
     * <p>没有活动事务时（比如管理端那些单条 UPDATE 的自动提交方法）直接执行，
     * 不会白白注册一个永远不会被回调的同步器。
     */
    public void evictAfterCommit(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(productIds);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictNow(ids);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictNow(ids);
            }
        });
    }

    /**
     * 立即失效，并安排一次延迟补偿删除。
     *
     * <p>注意不要在 afterCommit 回调里调用 {@link #evictAfterCommit}：
     * 那时同步器仍在「激活」状态，新注册的那个不会再被回调，缓存就白等了。
     * 这也是这里拆成两个方法的原因。
     */
    public void evictNow(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(productIds);
        ids.forEach(this::deleteQuietly);
        try {
            scheduler.schedule(() -> ids.forEach(this::deleteQuietly),
                    COMPENSATE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // 调度器已关闭（应用正在停机）时会抛 RejectedExecutionException。
            // 这时第一次删除已经生效，不补删也只是回到单删的可靠性水平，不影响正确性。
            log.warn("安排缓存补偿删除失败 ids={}", ids, e);
        }
    }

    /**
     * 删除单个商品的缓存，异常一律吞掉。
     *
     * <p>走到这里时订单事务已经提交，业务已经成功。如果因为 Redis 报错把异常抛出去，
     * 调用方看到的就是「接口 500 但订单其实创建成功了」——这比缓存脏 30 分钟严重得多。
     */
    private void deleteQuietly(Long productId) {
        try {
            productService.evictDetail(productId);
        } catch (Exception e) {
            log.warn("删除商品缓存失败，等 TTL 自然过期 id={}", productId, e);
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
