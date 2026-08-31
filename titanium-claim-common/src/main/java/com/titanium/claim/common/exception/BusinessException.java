package com.titanium.claim.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.metadata.errorcode.ClaimErrorCode;

import lombok.Getter;

/**
 * 理赔域业务异常基类
 * <p>
 * 统一携带 {@link ClaimErrorCode} 业务错误码（8 位数字，30 段），供全局异常兜底渲染与日志检索；
 * {@code status} 为传输层 HTTP 状态建议（业务错误码与 HTTP 状态码分离，后者仅经
 * {@code @ControllerAdvice}/传输层表达）。
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    /**
     * 构造业务异常（携带 {@link ClaimErrorCode}，消息取枚举中文消息，推荐）
     *
     * @param errorCode 理赔错误码枚举
     */
    public BusinessException(ClaimErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * 构造业务异常（携带 {@link ClaimErrorCode} + 自定义消息，推荐）
     *
     * @param errorCode 理赔错误码枚举
     * @param message   错误消息（业务上下文细化，可覆盖枚举默认消息）
     */
    public BusinessException(ClaimErrorCode errorCode, String message) {
        this(errorCode, message, HttpStatus.BAD_REQUEST);
    }

    /**
     * 构造业务异常（携带 {@link ClaimErrorCode} + 根因，推荐）
     *
     * @param errorCode 理赔错误码枚举
     * @param cause     根因异常
     */
    public BusinessException(ClaimErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode.getMessage(), HttpStatus.BAD_REQUEST, cause);
    }

    /**
     * 构造业务异常（携带 {@link ClaimErrorCode} + 传输层状态建议）
     * <p>
     * 供资源不存在类异常（如 {@code HttpStatus.NOT_FOUND}）等需要传输层状态提示的场景使用。
     * </p>
     *
     * @param errorCode 理赔错误码枚举
     * @param status    HTTP 状态建议
     */
    public BusinessException(ClaimErrorCode errorCode, HttpStatus status) {
        this(errorCode, errorCode.getMessage(), status);
    }

    /**
     * 构造业务异常（携带 {@link ClaimErrorCode} + 自定义消息 + 传输层状态建议）
     *
     * @param errorCode 理赔错误码枚举
     * @param message   错误消息
     * @param status    HTTP 状态建议
     */
    public BusinessException(ClaimErrorCode errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode.getCode();
        this.status = status;
    }

    private BusinessException(ClaimErrorCode errorCode, String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode.getCode();
        this.status = status;
    }

    /**
     * 构造业务异常
     *
     * @deprecated 请改用 {@link #BusinessException(ClaimErrorCode)}，禁止裸串错误码
     */
    @Deprecated
    public BusinessException(String message) {
        this(message, "BUSINESS_ERROR", HttpStatus.BAD_REQUEST);
    }

    /**
     * 构造业务异常
     *
     * @deprecated 请改用 {@link #BusinessException(ClaimErrorCode, String)}，禁止裸串错误码
     */
    @Deprecated
    public BusinessException(String message, String errorCode) {
        this(message, errorCode, HttpStatus.BAD_REQUEST);
    }

    /**
     * 构造业务异常
     *
     * @deprecated 请改用 {@link #BusinessException(ClaimErrorCode, HttpStatus)}，禁止裸串错误码
     */
    @Deprecated
    public BusinessException(String message, HttpStatus status) {
        this(message, "BUSINESS_ERROR", status);
    }

    /**
     * 构造业务异常
     *
     * @deprecated 请改用 {@link #BusinessException(ClaimErrorCode, String, HttpStatus)}，禁止裸串错误码
     */
    @Deprecated
    public BusinessException(String message, String errorCode, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
