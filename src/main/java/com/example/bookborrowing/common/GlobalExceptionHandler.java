package com.example.bookborrowing.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一将异常转换为 Result 响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常：返回具体业务提示。
     *
     * @param e 业务异常
     * @return 失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getMessage());
    }

    /**
     * 参数校验异常。
     *
     * @param e 校验异常
     * @return 失败响应
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidException(Exception e) {
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException ex
                && ex.getBindingResult().getFieldError() != null) {
            message = ex.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof BindException ex && ex.getBindingResult().getFieldError() != null) {
            message = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Result.error(message);
    }

    /**
     * 请求方法不支持：本系统只允许 GET 与 POST（写操作用 POST，删除用 POST + 逻辑删除）。
     *
     * @param e 方法不支持异常
     * @return 失败响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法：{}", e.getMethod());
        return Result.error(405, "不支持的请求方法：" + e.getMethod() + "，本系统仅允许 GET 与 POST");
    }

    /**
     * 系统异常：打印完整堆栈到控制台，并返回异常类型便于定位。
     * 注意：Mapper 接口方法未声明 throws SQLException，JDK 动态代理会把受检异常包装为
     * UndeclaredThrowableException（其消息为 null），此处需要解包出真实原因。
     *
     * @param e 系统异常
     * @return 失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        Throwable actual = e;
        if (e instanceof java.lang.reflect.UndeclaredThrowableException && e.getCause() != null) {
            actual = e.getCause();
        }
        log.error("系统异常：{}", actual.getClass().getName(), actual);
        String detail = actual.getClass().getSimpleName()
                + (actual.getMessage() == null ? "" : "：" + actual.getMessage());
        return Result.error(500, "系统异常：" + detail);
    }
}
