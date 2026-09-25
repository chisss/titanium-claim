package com.titanium.claim.web.handler;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.titanium.claim.common.exception.ClaimNotFoundException;
import com.titanium.claim.common.exception.CustomerNotFoundException;
import com.titanium.claim.common.exception.PolicyNotFoundException;
import com.titanium.claim.web.response.error.ClaimErrorVO;
import com.titanium.common.boundary.ErrorCategoryHttpStatus;
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
 * 「业务规则拒绝」与「系统故障」。
 * </p>
 * <p>
 * <b>本类为何不用 {@code ExceptionResponseFactory}</b>：本域的错误信封是 {@link ClaimErrorVO}
 * （{@code {code, message}}），与全仓 {@code ApiResponse} 形态不同，换信封属破坏性契约变更。
 * 故这里<b>只</b>复用其状态映射——{@link ErrorCategoryHttpStatus} 是全系统唯一的
 * 「错误码类别 → HTTP 状态」表，信封形态由各域自决。
 * </p>
 */
@RestControllerAdvice(basePackages = "com.titanium.claim.web")
@Slf4j
public class ClaimExceptionHandler {

    /**
     * 业务异常 → 状态取错误码的语义类别。
     * <p>
     * 改造前此处恒为 400：领域侧虽早已按语义定义错误码的类别，传输层却不读它，
     * 于是「资源不存在」「状态冲突」「服务端故障」全被报成 400。
     * </p>
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ClaimErrorVO> handleDomainException(DomainException exception) {
        log.warn("[理赔-领域规则] 请求被拒, errorCode={}, message={}", exception.getErrorCodeValue(),
                exception.getMessage());
        return ResponseEntity.status(ErrorCategoryHttpStatus.of(exception.getErrorCode()))
                .body(new ClaimErrorVO(exception.getErrorCodeValue(), exception.getMessage()));
    }

    /**
     * 资源不存在 → 404。
     * <p>
     * HTTP 状态码属传输层职责，领域侧不持有 {@code HttpStatus} 字段：原先由 claim 域自建的
     * {@code BusinessException} 携带 {@code status} 供本处理器读取，代价是 domain / application
     * 层反向依赖 {@code org.springframework.http}。m30-03 删除该字段与那个异常根后，404 语义改由
     * 本处理器<b>按异常类型显式映射</b>——与原先的 {@code HttpStatus.NOT_FOUND} 声明等价，
     * 且 domain / application 层回到零 Spring HTTP 依赖。
     * </p>
     */
    @ExceptionHandler({
            ClaimNotFoundException.class,
            CustomerNotFoundException.class,
            PolicyNotFoundException.class
    })
    public ResponseEntity<ClaimErrorVO> handleResourceNotFoundException(DomainException exception) {
        log.warn("[理赔-资源不存在] errorCode={}, message={}", exception.getErrorCodeValue(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ClaimErrorVO(exception.getErrorCodeValue(), exception.getMessage()));
    }

    /**
     * 方法级校验失败（{@code @Validated} 的形参约束）→ 400 通用参数错误码。
     * <p>
     * 🔴 <b>目标类型只剩 {@link ConstraintViolationException}</b>：改造前此处并列声明了 7 个类型，
     * 其中 6 个（{@code MethodArgumentNotValidException}、{@code HandlerMethodValidationException}、
     * {@code HttpMessageNotReadableException}、{@code MethodArgumentTypeMismatchException}、
     * {@code MissingRequestHeaderException}、{@code MissingServletRequestParameterException}）
     * 全部可由 {@code titanium-common} 的<b>边界处理器</b>按可赋值性接管，而边界 advice 序在前
     * （{@code HIGHEST_PRECEDENCE + 20} vs 本类无 {@code @Order}），
     * 故那 6 支<b>永不派发</b>——是死码。收窄后语义不变（这 6 类仍得 400），死码消失。
     * </p>
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ClaimErrorVO> handleInvalidRequest(ConstraintViolationException exception) {
        String detail = extractValidationDetail(exception);
        log.warn("[理赔-参数校验] 请求参数不合法: {}", detail);
        return ResponseEntity.badRequest()
                .body(new ClaimErrorVO(SystemErrorCode.PARAM_INVALID.getCode(),
                        detail != null ? detail : "请求参数格式或约束校验失败"));
    }

    /**
     * 从约束违例中提取用户可读的字段级错误明细（如「客户ID不能为空」），
     * 兜底返回 {@code null}（由调用方使用通用文案）。
     */
    private String extractValidationDetail(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .toList();
        return details.isEmpty() ? null : String.join("；", details);
    }

    /** 兜底：未知异常 → 500 通用系统错误码（不泄漏堆栈细节） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ClaimErrorVO> handleUnexpectedException(Exception exception) {
        log.error("[理赔-系统异常] Web 请求处理失败", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ClaimErrorVO(SystemErrorCode.SYSTEM_ERROR.getCode(), "系统处理失败，请稍后重试"));
    }
}
