package com.jack.jianxuan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jack.jianxuan.common.BusinessException;
import com.jack.jianxuan.common.CacheHealth;
import com.jack.jianxuan.dto.PageResult;
import com.jack.jianxuan.dto.ProductQuery;
import com.jack.jianxuan.dto.ProductSaveRequest;
import com.jack.jianxuan.dto.ProductVO;
import com.jack.jianxuan.entity.Category;
import com.jack.jianxuan.entity.Product;
import com.jack.jianxuan.mapper.CategoryMapper;
import com.jack.jianxuan.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    /** 新增商品没填封面时的占位图，放在前端 public/covers 下 */
    private static final String DEFAULT_COVER = "/covers/0.svg";

    /** 分类列表缓存 key（全量一份，没有参数组合） */
    private static final String CATEGORY_CACHE_KEY = "cache:category:list";

    /** 分类缓存 TTL：1 小时。分类是「几乎不变、每次列表页都要读」的典型场景。 */
    private static final long CATEGORY_TTL_SECONDS = 3600;

    /** 随机偏移上限（秒）：避免同一时刻写入的 key 同时过期 */
    private static final int CATEGORY_TTL_JITTER_SECONDS = 600;

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductCache productCache;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheHealth cacheHealth;

    /**
     * 分类列表，按 sort 升序。
     *
     * <p>这是「读极多、写几乎为零」的典型：每个商品列表页都要把 categoryId 翻成名字，
     * 但分类一年也改不了几次。缓存 1 小时，命中率接近 100%。
     *
     * <p>注意：现在没有分类管理接口，所以这里没有任何 evict 调用。一旦以后加了分类的增删改，
     * 必须在写操作后删掉 {@link #CATEGORY_CACHE_KEY}，否则改名后前端会一直看到旧名字。
     */
    public List<Category> listCategories() {
        // 熔断中（Redis 挂了）直接查库，不去等两次超时
        if (!cacheHealth.isOpen()) {
            String cached = getCategoryCache();
            if (cached != null) {
                List<Category> list = parseCategories(cached);
                if (list != null) {
                    return list;
                }
                log.warn("分类缓存反序列化失败，按未命中处理");
            }
        }

        List<Category> list = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort)
        );
        putCategoryCache(list);
        return list;
    }

    /**
     * 用户端商品分页：只返回上架商品，支持关键字、分类、排序。
     */
    public PageResult<ProductVO> page(ProductQuery query) {
        return queryPage(query, true);
    }

    /**
     * 管理端商品分页：能看到下架商品，所以不能复用用户端那个写死 status = 1 的条件。
     */
    public PageResult<ProductVO> adminPage(ProductQuery query) {
        return queryPage(query, false);
    }

    /**
     * 用户端和管理端共用的分页逻辑，差异只在两处：
     * 1. 是否强制只看上架商品；
     * 2. 返回的 VO 里要不要带 status。
     */
    private PageResult<ProductVO> queryPage(ProductQuery query, boolean onlyOnSale) {
        normalize(query);

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (onlyOnSale) {
            // 用户端：只查上架。逻辑删除由 @TableLogic 自动追加 deleted = 0，这里不用写
            wrapper.eq(Product::getStatus, 1);
        } else if (query.getStatus() != null) {
            // 管理端：传了 status 就按状态筛，不传则上下架都要
            wrapper.eq(Product::getStatus, query.getStatus());
        }

        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            // 必须用 and(...) 包一层括号，生成的 SQL 才是
            //   status = 1 AND (name LIKE ? OR subtitle LIKE ?)
            // 如果直接 .like().or().like()，会变成
            //   status = 1 AND name LIKE ? OR subtitle LIKE ?
            // 由于 AND 优先级高于 OR，等于 (status=1 AND name LIKE ?) OR (subtitle LIKE ?)，
            // 下架商品会从副标题这条缝里漏出来。这是最经典的 SQL 拼接坑。
            wrapper.and(w -> w.like(Product::getName, keyword)
                    .or()
                    .like(Product::getSubtitle, keyword));
        }

        if (query.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, query.getCategoryId());
        }

        applySort(wrapper, query.getSort());

        Page<Product> page = productMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        if (page.getRecords().isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), page.getCurrent(), page.getSize());
        }

        // 分类只有几条，一次查全量在内存里拼名字，比每行 join 一次更省事也更快
        Map<Long, String> categoryNames = categoryNameMap();
        List<ProductVO> list = page.getRecords().stream()
                .map(p -> {
                    ProductVO vo = ProductVO.from(p);
                    vo.setCategoryName(categoryNames.getOrDefault(p.getCategoryId(), ""));
                    if (!onlyOnSale) {
                        // 管理端需要看到上下架状态，用户端不返回
                        vo.setStatus(p.getStatus());
                    }
                    return vo;
                })
                .toList();

        return PageResult.of(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 用户端商品详情：不存在或已下架，一律按 404 处理，不暴露「存在但下架」这种中间态。
     *
     * <p>走 Cache-Aside：先查 Redis，未命中再回源并回填。穿透 / 击穿 / 雪崩的处理都在
     * {@link ProductCache} 里，这里只负责「缓存没有时怎么查库」。
     */
    public ProductVO detail(Long id) {
        return productCache.get(id, () -> loadDetail(id));
    }

    /**
     * 真正查库并组装详情。缓存未命中时由 {@link ProductCache} 回调。
     *
     * <p>这里查了两次库（商品 + 分类），缓存命中时这两次都省掉了——这就是缓存的收益来源。
     */
    private ProductVO loadDetail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw new BusinessException(404, "商品不存在或已下架");
        }
        ProductVO vo = ProductVO.from(product);
        Category category = categoryMapper.selectById(product.getCategoryId());
        vo.setCategoryName(category == null ? "" : category.getName());
        return vo;
    }

    /**
     * 让某个商品的详情缓存失效。
     *
     * <p>除了本类里的写操作，{@link OrderService} 在下单扣库存、取消回补库存之后也要调——
     * 商品详情里带着 stock / sales，那两项每笔订单都会变。
     */
    public void evictDetail(Long id) {
        productCache.evict(id);
    }

    /* ==================== 管理端：增删改 ==================== */

    /**
     * 新增商品。@Valid 在 Controller 层已经把 name / price / categoryId 校验过了，
     * 这里只需处理「不传就用默认值」的字段。
     */
    public ProductVO create(ProductSaveRequest req) {
        checkCategory(req.getCategoryId());

        Product product = new Product();
        product.setName(req.getName().trim());
        product.setSubtitle(req.getSubtitle() == null ? "" : req.getSubtitle().trim());
        product.setPrice(req.getPrice());
        // 划线价不传就等于售价，避免前端算折扣时拿到 null
        product.setOriginalPrice(req.getOriginalPrice() == null ? req.getPrice() : req.getOriginalPrice());
        product.setStock(req.getStock() == null ? 0 : req.getStock());
        product.setSales(0);
        product.setCategoryId(req.getCategoryId());
        product.setStatus(req.getStatus() == null ? 1 : normalizeStatus(req.getStatus()));
        product.setCover(StringUtils.hasText(req.getCover()) ? req.getCover().trim() : DEFAULT_COVER);
        product.setDescription(req.getDesc() == null ? "" : req.getDesc());

        productMapper.insert(product);
        // 新建一般不需要删缓存（这个 id 之前不存在），但万一有请求提前查过这个 id、
        // 把「不存在」的空值缓存写进去了，这里删一次能避免新商品上线后最长 60 秒还是 404。
        productCache.evict(product.getId());
        return toVO(product, true);
    }

    /**
     * 编辑商品。字段全部可选，只更新传了值的项，符合 api.md 里的接口约定。
     */
    public ProductVO update(Long id, ProductSaveRequest req) {
        Product product = getExisting(id);

        if (req.getName() != null) {
            if (req.getName().isBlank()) {
                throw new BusinessException(400, "商品名称不能为空");
            }
            product.setName(req.getName().trim());
        }
        if (req.getSubtitle() != null) {
            product.setSubtitle(req.getSubtitle().trim());
        }
        if (req.getPrice() != null) {
            if (req.getPrice().signum() <= 0) {
                throw new BusinessException(400, "售价必须大于 0");
            }
            product.setPrice(req.getPrice());
        }
        if (req.getOriginalPrice() != null) {
            product.setOriginalPrice(req.getOriginalPrice());
        }
        if (req.getStock() != null) {
            if (req.getStock() < 0) {
                throw new BusinessException(400, "库存不能为负数");
            }
            product.setStock(req.getStock());
        }
        if (req.getCategoryId() != null) {
            checkCategory(req.getCategoryId());
            product.setCategoryId(req.getCategoryId());
        }
        if (req.getStatus() != null) {
            product.setStatus(normalizeStatus(req.getStatus()));
        }
        if (StringUtils.hasText(req.getCover())) {
            product.setCover(req.getCover().trim());
        }
        if (req.getDesc() != null) {
            product.setDescription(req.getDesc());
        }

        // 必须手动刷新 updated_at：updateById 会把查出来的旧值一起写回，
        // 显式赋了值，数据库的 ON UPDATE CURRENT_TIMESTAMP 就不再自动生效
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        // 先更库、再删缓存。顺序反了会留下「删完缓存、更库生效前，读请求把旧值写回」的脏数据。
        productCache.evict(id);
        return toVO(product, true);
    }

    /**
     * 逻辑删除。加了 @TableLogic 后，deleteById 实际执行的是
     * UPDATE product SET deleted = 1 WHERE id = ? AND deleted = 0
     */
    public void delete(Long id) {
        getExisting(id);
        productMapper.deleteById(id);
        productCache.evict(id);
    }

    public ProductVO updateStatus(Long id, Integer status) {
        Product product = getExisting(id);
        product.setStatus(normalizeStatus(status));
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        // 下架必须删缓存：否则缓存里还是「在售」状态，用户点进去看到详情却能下单，下单时才报错
        productCache.evict(id);
        return toVO(product, true);
    }

    private Product getExisting(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        return product;
    }

    private void checkCategory(Long categoryId) {
        if (categoryId == null || categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException(400, "商品分类不存在");
        }
    }

    /** 只允许 0 / 1，其他值一律当上架，避免脏数据 */
    private Integer normalizeStatus(Integer status) {
        return Integer.valueOf(0).equals(status) ? 0 : 1;
    }

    private ProductVO toVO(Product product, boolean withStatus) {
        ProductVO vo = ProductVO.from(product);
        Category category = categoryMapper.selectById(product.getCategoryId());
        vo.setCategoryName(category == null ? "" : category.getName());
        if (withStatus) {
            vo.setStatus(product.getStatus());
        }
        return vo;
    }

    private Map<Long, String> categoryNameMap() {
        return listCategories().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }

    /* ==================== 分类缓存读写（全部失败降级，不影响主流程） ==================== */

    private String getCategoryCache() {
        try {
            return redisTemplate.opsForValue().get(CATEGORY_CACHE_KEY);
        } catch (Exception e) {
            cacheHealth.recordFailure();
            log.warn("读分类缓存失败，降级查库", e);
            return null;
        }
    }

    private void putCategoryCache(List<Category> list) {
        try {
            String json = objectMapper.writeValueAsString(list);
            long ttl = CATEGORY_TTL_SECONDS + ThreadLocalRandom.current().nextInt(CATEGORY_TTL_JITTER_SECONDS);
            redisTemplate.opsForValue().set(CATEGORY_CACHE_KEY, json, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            cacheHealth.recordFailure();
            log.warn("写分类缓存失败", e);
        }
    }

    private List<Category> parseCategories(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Category>>() { });
        } catch (Exception e) {
            return null;
        }
    }

    private void applySort(LambdaQueryWrapper<Product> wrapper, String sort) {
        if ("priceAsc".equals(sort)) {
            wrapper.orderByAsc(Product::getPrice);
        } else if ("priceDesc".equals(sort)) {
            wrapper.orderByDesc(Product::getPrice);
        } else if ("salesDesc".equals(sort)) {
            wrapper.orderByDesc(Product::getSales);
        } else {
            wrapper.orderByDesc(Product::getId);
        }
    }

    /**
     * 参数兜底。分页参数是外部直接传进来的，不能信任：
     * page 传 0 或负数会让 offset 变成负数；size 传 100000 等于让人拖库。
     */
    private void normalize(ProductQuery query) {
        if (query.getPage() < 1) {
            query.setPage(1);
        }
        if (query.getSize() < 1) {
            query.setSize(12);
        }
        if (query.getSize() > 100) {
            query.setSize(100);
        }
    }
}
