package com.jack.jianxuan.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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

    /**
     * HTTP 方法用错（比如取消订单写成 PUT，实际接口只收 POST）。
     *
     * 这类异常以前会一路掉进下面的 Exception 兜底变成 500「系统繁忙」，
     * 把「客户端调错了」伪装成「服务端炸了」——排查时得翻日志才知道真相。
     * 405 是协议层面的正确答案，前端看到就能立刻定位。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: method={}, 支持的方法={}", e.getMethod(), e.getSupportedHttpMethods());
        return build(405, "请求方法 " + e.getMethod() + " 不受支持，该接口支持 " + e.getSupportedHttpMethods());
    }

    /**
     * 请求体缺失或 JSON 格式错误（少括号、字段类型对不上、body 为空）。
     * 同样属于调用方问题，应该是 400 而不是 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return build(400, "请求体格式错误，请检查 JSON 结构与字段类型");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        // 走到这里说明不是已知的调用方错误，而是真的没预料到的问题。
        // 加上异常类型，日志里一眼能分辨是新 bug 还是被漏处理的框架异常。
        log.error("未捕获异常: {}", e.getClass().getName(), e);
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
