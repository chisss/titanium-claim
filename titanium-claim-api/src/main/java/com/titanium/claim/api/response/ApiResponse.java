package com.titanium.claim.api.response;

import java.io.Serializable;

/**
 * API 响应统一格式（理赔域对外契约）
 * <p>
 * 供 {@code ClaimApi} Feign 契约承载远程调用结果，{@code code=200} 表示成功，
 * 资源不存在等业务失败以 {@code code} 区分（如 404），避免 HTTP 异常穿透。
 * </p>
 *
 * @param <T> 业务数据类型
 */
public class ApiResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int    code;
    private String message;
    private T      data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "success", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    /**
     * 是否成功响应（code==200）
     */
    public boolean isSuccess() {
        return this.code == 200;
    }
}
