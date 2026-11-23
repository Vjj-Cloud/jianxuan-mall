package com.jack.jianxuan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在 3-20 位之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    /**
     * 昵称可选，不传则用用户名（见 UserService#register）。
     *
     * <p>@Size 对 null 是放行的，正好符合「可选」语义；但一旦传了就必须卡住长度——
     * 数据库列是 VARCHAR(50)，超长会抛 DataIntegrityViolationException，
     * 落到全局异常处理里就是 500。这类「用户输入越界」必须是 400，
     * 让前端能提示「昵称太长」，而不是让用户看到「系统繁忙」。
     */
    @Size(max = 50, message = "昵称最长 50 个字符")
    private String nickname;
}
