package com.jack.jianxuan.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商品金额参数的精度测试。
 *
 * <p>要防的是同一个坑的两个面：
 * <ul>
 *   <li>管理员填 999999999999（12 位整数）→ 撞 {@code DECIMAL(10,2)} 的容量，
 *       抛 Out of range，最后变成 500「系统繁忙」；</li>
 *   <li>填 1234.567（3 位小数）→ MySQL 会<b>静默四舍五入</b>成 1234.57，
 *       接口返回成功但金额和提交的不一样。这种不报错的偏差比报错更难发现。</li>
 * </ul>
 *
 * <p>这里用 Create / Update 两个分组各测一次：这个 DTO 的约束全部显式写了 groups，
 * 用错分组会让约束静默失效（Bean Validation 的组是白名单语义），
 * 所以「编辑接口也校验精度」必须单独验证，不能靠新增接口的用例推断。
 */
class ProductSaveRequestValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("新增：12 位整数金额被拒（否则数据库 Out of range → 500）")
    void createHugePrice_isRejected() {
        ProductSaveRequest req = validCreateRequest();
        req.setPrice(new BigDecimal("999999999999"));
        req.setOriginalPrice(new BigDecimal("999999999999"));

        Set<ConstraintViolation<ProductSaveRequest>> violations =
                validator.validate(req, ProductSaveRequest.Create.class);

        assertTrue(hasViolation(violations, "price"), "售价超长必须在这里被拒绝: " + violations);
        assertTrue(hasViolation(violations, "originalPrice"), "划线价同样要拦: " + violations);
    }

    @Test
    @DisplayName("新增：上限边界 999999.99 通过，多 1 分就不行")
    void createBoundaryPrice_isAccepted() {
        ProductSaveRequest atLimit = validCreateRequest();
        atLimit.setPrice(new BigDecimal("999999.99"));
        atLimit.setOriginalPrice(new BigDecimal("999999.99"));
        assertTrue(validator.validate(atLimit, ProductSaveRequest.Create.class).isEmpty(),
                "999999.99 是允许的最高单价，不能误判");

        ProductSaveRequest overLimit = validCreateRequest();
        overLimit.setPrice(new BigDecimal("1000000.00"));
        assertTrue(hasViolation(
                validator.validate(overLimit, ProductSaveRequest.Create.class), "price"),
                "7 位整数（100 万）必须被拒绝——上限是 6 位整数，不是列宽的 8 位");
    }

    @Test
    @DisplayName("编辑：只传价格时，精度校验同样生效")
    void updateGroup_alsoChecksPrecision() {
        ProductSaveRequest req = new ProductSaveRequest();
        req.setPrice(new BigDecimal("1234.567"));

        Set<ConstraintViolation<ProductSaveRequest>> violations =
                validator.validate(req, ProductSaveRequest.Update.class);

        assertTrue(hasViolation(violations, "price"),
                "3 位小数会被 MySQL 静默四舍五入，必须在接口层拒绝: " + violations);
    }

    private boolean hasViolation(Set<ConstraintViolation<ProductSaveRequest>> violations, String field) {
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    private ProductSaveRequest validCreateRequest() {
        ProductSaveRequest req = new ProductSaveRequest();
        req.setName("精度测试商品");
        req.setCategoryId(1L);
        req.setPrice(new BigDecimal("9.90"));
        return req;
    }
}
