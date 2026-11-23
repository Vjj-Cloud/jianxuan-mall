package com.jack.jianxuan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jack.jianxuan.common.BusinessException;
import com.jack.jianxuan.dto.CartAddRequest;
import com.jack.jianxuan.dto.CartItemVO;
import com.jack.jianxuan.dto.ProductVO;
import com.jack.jianxuan.entity.CartItem;
import com.jack.jianxuan.entity.Category;
import com.jack.jianxuan.entity.Product;
import com.jack.jianxuan.mapper.CartItemMapper;
import com.jack.jianxuan.mapper.CategoryMapper;
import com.jack.jianxuan.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车业务。
 *
 * 这个模块有两个容易写错的地方，都在下面标了：
 * 1. 加入购物车不能「先查再插」，要靠唯一键 + DuplicateKeyException；
 * 2. 所有按 itemId 的写操作必须带 userId 条件，否则会被人越权改别人的购物车。
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;

    public List<CartItemVO> list(Long userId) {
        List<CartItem> items = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .orderByDesc(CartItem::getId));
        return toVOList(items);
    }

    /**
     * 加入购物车：已存在则累加。
     *
     * 不能用「先 selectOne 判断存在，再 insert 或 update」——判断和写入之间有缝隙，
     * 并发下两个请求都会看到「不存在」，于是各插一行（或者被唯一键打成 500）。
     * 正确做法是把判断交给数据库：直接 insert，撞唯一键说明已存在，改成累加。
     */
    public List<CartItemVO> add(Long userId, CartAddRequest request) {
        Product product = productMapper.selectById(request.getProductId());
        if (product == null || product.getStatus() != 1) {
            throw new BusinessException(400, "商品不存在或已下架");
        }
        int delta = request.getQuantity() == null ? 1 : request.getQuantity();
        if (product.getStock() <= 0) {
            throw new BusinessException(400, "商品库存不足");
        }
        if (delta > product.getStock()) {
            throw new BusinessException(400, "超出库存数量");
        }

        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(product.getId());
        item.setQuantity(delta);
        try {
            cartItemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // 撞到 uk_user_product，说明这条已经在购物车里了 → 改成原子累加
            int rows = cartItemMapper.increaseQuantity(userId, product.getId(), delta, product.getStock());
            if (rows == 0) {
                throw new BusinessException(400, "购物车中该商品数量已达库存上限");
            }
        }
        return list(userId);
    }

    public List<CartItemVO> updateQuantity(Long userId, Long itemId, Integer quantity) {
        int rows = cartItemMapper.updateQuantity(userId, itemId, quantity);
        if (rows == 0) {
            // 0 行有两种可能，多查一次把消息说清楚，别让用户猜
            CartItem exist = cartItemMapper.selectOne(
                    new LambdaQueryWrapper<CartItem>()
                            .eq(CartItem::getId, itemId)
                            .eq(CartItem::getUserId, userId));
            if (exist == null) {
                throw new BusinessException(404, "购物车条目不存在");
            }
            throw new BusinessException(400, "超出库存数量，或商品已下架");
        }
        return list(userId);
    }

    public List<CartItemVO> remove(Long userId, Long itemId) {
        int rows = cartItemMapper.deleteByIdAndUser(itemId, userId);
        if (rows == 0) {
            throw new BusinessException(404, "购物车条目不存在");
        }
        return list(userId);
    }

    public List<CartItemVO> clear(Long userId) {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
        return List.of();
    }

    /**
     * 批量转 VO。
     *
     * 商品和分类都用 IN 一次性查回来，不要 for 循环里逐条 selectById——
     * 那是典型的 N+1 查询：购物车 20 条就是 1 + 20 次数据库往返。
     */
    private List<CartItemVO> toVOList(List<CartItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = items.stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());

        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, String> categoryNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        List<CartItemVO> result = new ArrayList<>(items.size());
        for (CartItem item : items) {
            Product product = productMap.get(item.getProductId());
            // 商品被删除或被下架，条目就不返回了（与 api.md 约定一致）
            if (product == null || product.getStatus() != 1) {
                continue;
            }
            ProductVO productVO = ProductVO.from(product);
            productVO.setCategoryName(categoryNames.getOrDefault(product.getCategoryId(), ""));
            result.add(CartItemVO.from(item, productVO));
        }
        return result;
    }
}
