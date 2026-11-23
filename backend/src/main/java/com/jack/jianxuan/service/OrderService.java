package com.jack.jianxuan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jack.jianxuan.common.BusinessException;
import com.jack.jianxuan.dto.OrderCreateRequest;
import com.jack.jianxuan.dto.OrderItemRequest;
import com.jack.jianxuan.dto.OrderQuery;
import com.jack.jianxuan.dto.OrderVO;
import com.jack.jianxuan.dto.PageResult;
import com.jack.jianxuan.entity.CartItem;
import com.jack.jianxuan.entity.Order;
import com.jack.jianxuan.entity.OrderItem;
import com.jack.jianxuan.entity.Product;
import com.jack.jianxuan.mapper.CartItemMapper;
import com.jack.jianxuan.mapper.OrderItemMapper;
import com.jack.jianxuan.mapper.OrderMapper;
import com.jack.jianxuan.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    /** 订单状态。用 String 常量而不是枚举，是为了和数据库里的 VARCHAR 一一对应、不做类型转换。 */
    private static final String PENDING = "PENDING";
    private static final String SHIPPED = "SHIPPED";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final CartItemMapper cartItemMapper;

    /**
     * 创建订单。
     *
     * 事务范围覆盖「扣库存 → 建订单 → 建明细 → 清购物车」四步，
     * 任何一步失败整个事务回滚，不会出现「库存扣了但订单没建」这种脏状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(Long userId, OrderCreateRequest request) {
        // 1. 合并同一商品的多行。前端理论上不会重复传，但重复传会导致同一商品被扣两次。
        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        for (OrderItemRequest item : request.getItems()) {
            quantityByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        // 2. 按 productId 升序处理。多商品下单时，如果 A 用户先锁商品1再锁商品2、
        //    B 用户先锁商品2再锁商品1，就会互相等待形成死锁。
        //    统一按主键升序加锁，所有事务的加锁顺序一致，死锁就不会发生。
        List<Long> productIds = quantityByProduct.keySet().stream().sorted().toList();

        // 3. 一次性查回所有商品，做「友善校验」：库存不足、已下架时给出明确提示。
        //    这一步只为了错误消息友好，真正的并发保护在下一步的原子扣减。
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>(productIds.size());
        for (Long productId : productIds) {
            Product product = productMap.get(productId);
            int quantity = quantityByProduct.get(productId);
            if (product == null || product.getStatus() != 1) {
                throw new BusinessException(400, "部分商品已下架，请刷新后重试");
            }
            if (product.getStock() < quantity) {
                throw new BusinessException(400, "「" + product.getName() + "」库存不足");
            }

            // 金额由服务端按当前商品价格重算，前端传来的任何金额都不采信
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));

            // 商品名/封面/价格在这里定格成快照，之后再改商品也不影响这张订单
            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setCover(product.getCover());
            orderItem.setPrice(product.getPrice());
            orderItem.setQuantity(quantity);
            orderItems.add(orderItem);
        }

        // 4. 原子扣库存。这是防超卖的真正防线：
        //    上面的校验可能已经不成立了（并发请求在这期间把库存买走了），
        //    只有 UPDATE ... WHERE stock >= ? 还能给出最终答案。
        //    返回 0 行 → 抛异常 → 整个事务回滚，前面扣成功的商品也会一起撤销。
        for (Long productId : productIds) {
            int rows = productMapper.deductStock(productId, quantityByProduct.get(productId));
            if (rows == 0) {
                throw new BusinessException(400, "库存不足，请刷新后重试");
            }
        }

        // 5. 写订单主表
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(PENDING);
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());
        order.setReceiverAddress(request.getReceiverAddress());
        // 时间戳必须手动赋值。这两列在数据库里有 DEFAULT CURRENT_TIMESTAMP，
        // 但那只是「INSERT 没带这列时数据库自己填」，MyBatis 插入后并不会把
        // 数据库生成的值读回 Java 对象。不手动 set 的话，紧接着 return 给前端的
        // createdAt 就是 null（列表和详情是重新查库，所以那边看着正常，只有下单
        // 这一次返回会缺时间——这种「只有某个接口缺字段」的 bug 特别难查）。
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        // 6. 写明细（order.getId() 由 MyBatis 从自增主键回填）
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrderId(order.getId());
            orderItemMapper.insert(orderItem);
        }

        // 7. 把已下单的商品从购物车摘掉。in 条件用 productIds，用户可能只结算了购物车里的一部分。
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getProductId, productIds));

        return OrderVO.from(order, orderItems);
    }

    /** 用户端：只看自己的订单 */
    public PageResult<OrderVO> page(Long userId, OrderQuery query) {
        return queryPage(userId, query);
    }

    /** 管理端：查全部用户的订单，userId 传 null 表示不限制归属 */
    public PageResult<OrderVO> adminPage(OrderQuery query) {
        return queryPage(null, query);
    }

    private PageResult<OrderVO> queryPage(Long userId, OrderQuery query) {
        long pageNo = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        long pageSize = query.getSize() == null || query.getSize() < 1 ? 10 : Math.min(query.getSize(), 50);

        // 必须先算出来：condition 为 false 只能让 MP 跳过拼接 SQL，
        // 拦不住 Java 提前求值 query.getKeyword().trim()——搜索框为空时这里就是 NPE。
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                // userId 为 null 时整段条件不拼进 SQL，管理员据此看到所有订单；
                // 用户端 userId 恒非空，所以「只查自己」这条防线依然生效。
                .eq(userId != null, Order::getUserId, userId)
                .eq(StringUtils.hasText(query.getStatus()), Order::getStatus, query.getStatus())
                .like(keyword != null, Order::getOrderNo, keyword)
                .orderByDesc(Order::getId);

        Page<Order> page = orderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<Order> records = page.getRecords();
        Map<Long, List<OrderItem>> itemsByOrder = loadItems(
                records.stream().map(Order::getId).toList());

        // 管理端才带 userId：用户看自己的订单，这个字段没有意义
        boolean withUserId = userId == null;
        List<OrderVO> list = records.stream()
                .map(o -> {
                    List<OrderItem> items = itemsByOrder.getOrDefault(o.getId(), List.of());
                    return withUserId
                            ? OrderVO.fromAdmin(o, items)
                            : OrderVO.from(o, items);
                })
                .toList();
        return PageResult.of(list, page.getTotal(), pageNo, pageSize);
    }

    public OrderVO detail(Long userId, Long orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId));
        if (order == null) {
            // 别人的订单一律按「不存在」处理，不告诉对方「这个订单存在但不属于你」
            throw new BusinessException(404, "订单不存在");
        }
        return OrderVO.from(order, loadItems(List.of(orderId)).getOrDefault(orderId, List.of()));
    }

    /**
     * 取消订单：回补库存、回退销量。
     *
     * 状态变更用「带条件的 UPDATE」而不是「先查状态再 update」：
     * 两个请求同时点取消时，只有第一个能把 PENDING 改成 CANCELLED（影响 1 行），
     * 第二个命中 0 行，直接报错返回，不会把库存加两遍。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancel(Long userId, Long orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId));
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!PENDING.equals(order.getStatus())) {
            throw new BusinessException(400, "当前状态不可取消");
        }

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, PENDING)
                .set(Order::getStatus, CANCELLED)
                .set(Order::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(400, "订单状态已变更，请刷新后重试");
        }

        List<OrderItem> items = loadItems(List.of(orderId)).getOrDefault(orderId, List.of());
        for (OrderItem item : items) {
            productMapper.restoreStock(item.getProductId(), item.getQuantity());
        }
        return detail(userId, orderId);
    }

    /** 确认收货：仅 SHIPPED → COMPLETED，同样用条件更新防重复提交。 */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO confirm(Long userId, Long orderId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId));
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!SHIPPED.equals(order.getStatus())) {
            throw new BusinessException(400, "只有已发货的订单可以确认收货");
        }

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, SHIPPED)
                .set(Order::getStatus, COMPLETED)
                .set(Order::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(400, "订单状态已变更，请刷新后重试");
        }
        return detail(userId, orderId);
    }

    /**
     * 发货：仅 PENDING → SHIPPED。
     *
     * 跟 cancel / confirm 一样用「带条件的 UPDATE」：
     * 两个管理员同时点发货，只有第一个能命中 PENDING（影响 1 行），
     * 第二个命中 0 行直接报错，不会出现重复发货或 shipped_at 被覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO ship(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!PENDING.equals(order.getStatus())) {
            throw new BusinessException(400, "只有待发货的订单可以发货");
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, PENDING)
                .set(Order::getStatus, SHIPPED)
                // 这一列最容易漏。只改 status 不改 shipped_at，前端「已发货」永远没有时间，
                // 而且数据库那边也没有 ON UPDATE 能帮你补——它只自动维护 updated_at。
                .set(Order::getShippedAt, now)
                .set(Order::getUpdatedAt, now));
        if (rows == 0) {
            throw new BusinessException(400, "订单状态已变更，请刷新后重试");
        }

        // 重新查一次拿最新状态。直接改内存里的 order 对象也行，但那等于在 Java 里
        // 手写一份「数据库刚刚做了什么」的副本，字段一多就容易漏，不如查库。
        Order updated = orderMapper.selectById(orderId);
        return OrderVO.fromAdmin(updated,
                loadItems(List.of(orderId)).getOrDefault(orderId, List.of()));
    }

    /** 一次 IN 查完所有订单的明细，避免「每条订单查一次」的 N+1。 */
    private Map<Long, List<OrderItem>> loadItems(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }

    /**
     * 订单号：SO + 年月日时分秒(14) + 4 位随机数，共 20 位。
     *
     * 为什么不用自增主键当订单号？自增 id 会暴露业务量（一天卖了多少单），
     * 而且多库分表时无法保证全局唯一。业务单号与主键分离是常规做法。
     * 同一秒内 4 位随机仍有极小概率重复，由 uk_order_no 唯一索引兜底。
     */
    private String generateOrderNo() {
        return "SO"
                + LocalDateTime.now().format(ORDER_NO_TIME)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
