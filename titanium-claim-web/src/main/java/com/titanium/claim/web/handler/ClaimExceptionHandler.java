package com.titanium.claim.web.handler;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.titanium.claim.common.exception.BusinessException;
import com.titanium.claim.web.response.error.ClaimErrorVO;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * 理赔 Web 全局异常处理器
 * <p>
 * 将理赔业务异常映射为稳定的 HTTP 状态与业务错误码（红线 8.2：业务错误码 ≠ HTTP 状态码，
 * HTTP 状态码由本处理器在传输层表达）。此前理赔域无全局兜底，聚合根抛出的
 * {@link DomainException}（如非法状态流转 30000007）以 500 呈现，管理后台无法区分
 * 「业务规则拒绝」与「系统故障」；现统一为 400 + 业务错误码。
 * </p>
 */
@RestControllerAdvice(basePackages = "com.titanium.claim.web")
@Slf4j
public class ClaimExceptionHandler {

    /** 领域规则违规（状态流转/阶段流转/前置条件）→ 400 业务错误码 */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ClaimErrorVO> handleDomainException(DomainException exception) {
        log.warn("[理赔-领域规则] 请求被拒, errorCode={}, message={}", exception.getErrorCode(),
                exception.getMessage());
        return ResponseEntity.badRequest()
                .body(new ClaimErrorVO(exception.getErrorCode(), exception.getMessage()));
    }

    /** 业务异常按异常自带的传输层状态建议映射（默认 400，资源不存在类 404） */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ClaimErrorVO> handleBusinessException(BusinessException exception) {
        HttpStatus status = exception.getStatus() == null ? HttpStatus.BAD_REQUEST : exception.getStatus();
        log.warn("[理赔-业务异常] errorCode={}, status={}, message={}", exception.getErrorCode(),
                status.value(), exception.getMessage());
        return ResponseEntity.status(status)
                .body(new ClaimErrorVO(exception.getErrorCode(), exception.getMessage()));
    }

    /** 请求参数/约束校验失败 → 400 通用参数错误码（提取具体字段校验消息，便于用户定位） */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ClaimErrorVO> handleInvalidRequest(Exception exception) {
        String detail = extractValidationDetail(exception);
        log.warn("[理赔-参数校验] 请求参数不合法: {}", detail);
        return ResponseEntity.badRequest()
                .body(new ClaimErrorVO(SystemErrorCode.PARAM_INVALID.getCode(),
                        detail != null ? detail : "请求参数格式或约束校验失败"));
    }

    /**
     * 从各类校验异常中提取用户可读的字段级错误明细（如「客户ID不能为空」），
     * 兜底返回 {@code null}（由调用方使用通用文案）。
     */
    private String extractValidationDetail(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validException) {
            List<String> details = validException.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + " " + error.getDefaultMessage())
                    .toList();
            return details.isEmpty() ? null : String.join("；", details);
        }
        if (exception instanceof HandlerMethodValidationException handlerException) {
            List<String> details = handlerException.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage()
                            : error.toString())
                    .toList();
            return details.isEmpty() ? null : String.join("；", details);
        }
        if (exception instanceof ConstraintViolationException violationException) {
            List<String> details = violationException.getConstraintViolations().stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .toList();
            return details.isEmpty() ? null : String.join("；", details);
        }
        if (exception instanceof MissingRequestHeaderException headerException) {
            return "缺少请求头 " + headerException.getHeaderName();
        }
        if (exception instanceof MissingServletRequestParameterException paramException) {
            return "缺少请求参数 " + paramException.getParameterName();
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatchException) {
            return "参数 " + mismatchException.getName() + " 类型不匹配";
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return "请求体格式错误，无法解析";
        }
        return null;
    }

    /** 兜底：未知异常 → 500 通用系统错误码（不泄漏堆栈细节） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ClaimErrorVO> handleUnexpectedException(Exception exception) {
        log.error("[理赔-系统异常] Web 请求处理失败", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ClaimErrorVO(SystemErrorCode.SYSTEM_ERROR.getCode(), "系统处理失败，请稍后重试"));
    }
}
