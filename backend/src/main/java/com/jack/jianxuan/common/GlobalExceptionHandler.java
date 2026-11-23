package com.jack.jianxuan.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常 code={}, message={}", e.getCode(), e.getMessage());
        return build(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidException(MethodArgumentNotValidException e) {
        String message = "参数校验失败";
        if (e.getBindingResult().getFieldError() != null) {
            message = e.getBindingResult().getFieldError().getDefaultMessage();
        }
        log.warn("参数校验失败: {}", message);
        return build(400, message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFoundException(NoResourceFoundException e) {
        log.warn("接口不存在: {}", e.getResourcePath());
        return build(404, "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("未捕获异常", e);
        return build(500, "系统繁忙，请稍后重试");
    }

    /**
     * 统一构造响应：HTTP 状态码表达协议语义，body 里的 code 表达业务语义。
     *
     * 业务码若本身是标准 HTTP 状态码（400/401/403/404/500），HTTP 状态码沿用它，
     * 前端才能靠 axios 的 error 分支识别「登录失效」并跳转登录页；
     * 如果是自定义业务码（如 1001），HTTP 层统一按 400 返回。
     */
    private ResponseEntity<Result<Void>> build(int code, String message) {
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null
                || status.is1xxInformational()
                || status.is2xxSuccessful()
                || status.is3xxRedirection()) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(Result.fail(code, message));
    }
}
