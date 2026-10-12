package com.example.bookborrowing.common;

/**
 * 业务异常，用于可预期的业务规则校验失败场景。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
