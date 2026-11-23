package com.jack.jianxuan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理端新增 / 编辑商品的请求体。
 *
 * <p>为什么不直接用 Product 实体接收？
 * 1. 实体里有 id、sales、deleted、createdAt 这些「服务端说了算」的字段，
 *    让前端传等于把主键和删除标记交给外部控制；
 * 2. 前端字段叫 desc，实体叫 description，需要一层翻译；
 * 3. 校验注解必须贴在「请求」上，而不是「数据表映射」上。
 *
 * <p><b>为什么用校验分组，而不是简单地「加个 @Valid」：</b>
 * 新增和编辑的必填规则天然不同——新增必须有名称/售价/分类，编辑是「传了才改」。
 * 如果只在字段上标 {@code @NotBlank}，编辑接口要加 {@code @Valid} 就必须让前端全量提交；
 * 而不加 {@code @Valid}，连「名称最多 100 字」这种纯格式校验也一起丢了，
 * 超长字符串会一路撞到数据库列宽，最后变成一个 500「系统繁忙」。
 * 分组让两件事同时成立：新增校验必填、编辑校验格式。
 *
 * <p>注意：一旦在参数上用了 {@code @Validated(Create.class)}，**没有写进任何分组**的约束
 * 都不会被执行（Bean Validation 的组是「白名单」语义，不会隐式包含 Default 组）。
 * 所以下面每个约束都显式写了 groups。
 */
@Data
public class ProductSaveRequest {

    /** 新增时的校验分组：必填项要校验。 */
    public interface Create {
    }

    /** 编辑时的校验分组：字段都可选，只校验传了值的那部分格式是否合法。 */
    public interface Update {
    }

    @NotBlank(groups = Create.class, message = "商品名称不能为空")
    @Size(max = 100, groups = {Create.class, Update.class}, message = "商品名称最多 100 个字符")
    private String name;

    @Size(max = 200, groups = {Create.class, Update.class}, message = "副标题最多 200 个字符")
    private String subtitle;

    /**
     * 售价。
     *
     * <p><b>为什么是 @Digits(integer = 6) 而不是照着列宽写 8：</b>
     * 列是 {@code DECIMAL(10,2)}（最多 8 位整数），照抄会允许 99,999,999.99。
     * 而订单总额 = 单价 × 数量 × 商品数，quantity 上限 999、items 上限 50，
     * 8 位整数能把总额推到 4.99e12，远超订单表的容量——那就只是把 500 从商品接口
     * 挪到了下单接口。
     *
     * <p>所以这里取「业务上限」6 位整数（最大 999,999.99）：
     * 50 × 999 × 999,999.99 ≈ 4.99e10，能安稳落在扩到 {@code DECIMAL(14,2)} 的
     * {@code orders.total_amount} 里。**精度约束要按整条链路的容量倒推，不能只看单表。**
     *
     * <p>报错也从「系统繁忙」变成字段级提示：原来的 {@code DataIntegrityViolationException}
     * （Out of range value）会落到全局异常兜底分支，用户看到的是 500。
     */
    @NotNull(groups = Create.class, message = "售价不能为空")
    @DecimalMin(value = "0.01", groups = {Create.class, Update.class}, message = "售价必须大于 0")
    @Digits(integer = 6, fraction = 2, groups = {Create.class, Update.class},
            message = "售价最多 6 位整数、2 位小数（最大 999999.99）")
    private BigDecimal price;

    /** 划线价：和售价同一套精度规则，否则「划线价越界」只是把 500 换了个字段。 */
    @DecimalMin(value = "0.00", groups = {Create.class, Update.class}, message = "划线价不能为负数")
    @Digits(integer = 6, fraction = 2, groups = {Create.class, Update.class},
            message = "划线价最多 6 位整数、2 位小数（最大 999999.99）")
    private BigDecimal originalPrice;

    @Min(value = 0, groups = {Create.class, Update.class}, message = "库存不能为负数")
    private Integer stock;

    @NotNull(groups = Create.class, message = "请选择商品分类")
    private Long categoryId;

    @Size(max = 500, groups = {Create.class, Update.class}, message = "封面地址最多 500 个字符")
    private String cover;

    /** 前端字段叫 desc，数据库列叫 description（TEXT 类型，但仍限制长度防止恶意灌大文本） */
    @Size(max = 5000, groups = {Create.class, Update.class}, message = "商品描述最多 5000 个字符")
    private String desc;

    /** 1 上架 / 0 下架，不传默认上架 */
    private Integer status;
}
