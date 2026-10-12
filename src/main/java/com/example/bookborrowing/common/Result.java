package com.example.bookborrowing.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一响应结构。
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    /** 成功码 */
    public static final int CODE_SUCCESS = 200;

    private Integer code;

    private String message;

    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应（无数据）。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success() {
        return new Result<>(CODE_SUCCESS, "success", null);
    }

    /**
     * 成功响应（带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, "success", data);
    }

    /**
     * 失败响应。
     *
     * @param message 错误信息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(400, message, null);
    }

    /**
     * 失败响应（自定义错误码）。
     *
     * @param code    错误码
     * @param message 错误信息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
