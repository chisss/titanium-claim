package com.titanium.claim.fieldcatalog;

import com.titanium.metadata.errorcode.BaseErrorCode;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * Claim 字段目录定义或查询条件非法。
 * <p>
 * 单参构造器的错误码固定为 {@link SystemErrorCode#PARAM_INVALID}——与 policy 域同款异常
 * {@code PolicyFieldCatalogValidationException} 取同一码，保持五域目录校验失败对外码一致。
 * 当调用方已从 {@code ClaimFieldDescriptor.validateAnalysis} 拿到具体错误码（四条分析位不变量）
 * 时走第二构造器，使「违反的是哪条规则」在错误码层面可辨。
 * </p>
 */
public class ClaimFieldCatalogValidationException extends DomainException {

    public ClaimFieldCatalogValidationException(String message) {
        super(SystemErrorCode.PARAM_INVALID, message);
    }

    /** 携带具体业务错误码的构造器；{@code errorCode} 为空时回退 {@link SystemErrorCode#PARAM_INVALID}。 */
    public ClaimFieldCatalogValidationException(BaseErrorCode errorCode, String message) {
        super(errorCode == null ? SystemErrorCode.PARAM_INVALID : errorCode, message);
    }
}
