package com.jack.jianxuan.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册参数的校验测试。
 *
 * <p>为什么值得单独写：昵称超长原来不会返回 400，而是直接把
 * {@code DataIntegrityViolationException}（Data too long）抛到全局异常处理，
 * 最后变成用户眼里的「系统繁忙」。这类问题的根因不在数据库，而在于
 * <b>校验注解没跟上表结构</b>——列是 VARCHAR(50)，DTO 却没有 @Size(50)。
 *
 * <p>放在 DTO 层做纯单元测试（不起 Spring 容器），是因为这里要验证的就是
 * 注解本身有没有写对：跑一次一毫秒，而且改注解忘了改测试会立刻红。
 */
class RegisterRequestValidationTest {

    private static final String NICKNAME_MAX = "昵".repeat(50);

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("昵称 51 个字符：必须在校验层被拦下（400），而不是交给数据库报错（500）")
    void nicknameTooLong_isRejected() {
        RegisterRequest req = validRequest();
        req.setNickname("昵".repeat(51));

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("nickname")),
                "昵称超过 VARCHAR(50) 必须在这里被拒绝，实际违规项: " + violations);
    }

    @Test
    @DisplayName("昵称不传或刚好 50 个字符：都算合法（昵称是可选的）")
    void nicknameOptionalAndBoundary_areAccepted() {
        RegisterRequest omitted = validRequest();
        omitted.setNickname(null);
        assertTrue(validator.validate(omitted).isEmpty(),
                "不传昵称时应由 Service 回退成用户名，不该报校验错误");

        RegisterRequest boundary = validRequest();
        boundary.setNickname(NICKNAME_MAX);
        assertTrue(validator.validate(boundary).isEmpty(),
                "刚好 50 个字符是可存入的，不能误判为超长");
    }

    private RegisterRequest validRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("jack");
        req.setPassword("123456");
        return req;
    }
}
