package com.titanium.claim.application.saga.assembler;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.titanium.claim.port.document.DocumentServicePort.ClaimDocument;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.metadata.enums.claim.RejectReason;

/**
 * 理赔结案单证装配器（application/saga/assembler）
 * <p>
 * 收敛「结案事件要素 → 文档域出站契约 {@link ClaimDocument}」的装配细节（7 字段），使
 * {@code ClaimClosureDocumentSaga} 只保留派发语义、不散落字段搬运代码（红线 21：&gt;5 字段的对象组装
 * 须由独立装配器承担）。赔付结算与拒赔结案两条路径共用本类，避免两份字段映射各自漂移。
 * </p>
 */
@Component
public class ClaimClosureDocumentAssembler {

    /** 拒赔结案说明中「原因码 / 批注」的分隔符：拼接的是结构化枚举码与业务自由文本，非业务文案常量 */
    private static final String REASON_COMMENT_SEPARATOR = "/";

    /**
     * 由赔付结算（普通赔付 / 身故给付 / 全残给付）要素装配结案单证。
     *
     * @param claimId    理赔案件ID
     * @param policyId   关联保单ID
     * @param settlement 核赔结论（可为空——结算事件载荷空安全，此时金额与给付方式留空）
     * @param closedAt   结案时间
     * @param tenantId   租户ID
     */
    public ClaimDocument fromSettlement(String claimId, String policyId, ClaimSettlement settlement,
            LocalDateTime closedAt, String tenantId) {
        if (settlement == null) {
            return new ClaimDocument(claimId, policyId, null, null, null, closedAt, tenantId);
        }
        return new ClaimDocument(claimId, policyId, settlement.settledAmount(),
                settlement.payoutMethod() == null ? null : settlement.payoutMethod().getCode(),
                settlement.conclusion(), closedAt, tenantId);
    }

    /**
     * 由拒赔结案要素装配结案单证。
     * <p>
     * 拒赔无赔付事实，故金额与给付方式恒为空；结案说明由「拒赔原因码 + 批注」拼成
     * （原因码是结构化枚举码，批注为业务自由文本，二者均不落中文文案字面量）。
     * </p>
     *
     * @param claimId  理赔案件ID
     * @param policyId 关联保单ID
     * @param reason   拒赔原因（可为空）
     * @param comment  拒赔批注（可为空）
     * @param closedAt 结案时间
     * @param tenantId 租户ID
     */
    public ClaimDocument fromRejection(String claimId, String policyId, RejectReason reason, String comment,
            LocalDateTime closedAt, String tenantId) {
        return new ClaimDocument(claimId, policyId, null, null, composeRejectionConclusion(reason, comment), closedAt,
                tenantId);
    }

    /** 结案说明拼装：有批注则「原因码/批注」，无批注退化为原因码，两者皆空则为空 */
    private static String composeRejectionConclusion(RejectReason reason, String comment) {
        String reasonCode = reason == null ? null : reason.getCode();
        boolean hasComment = comment != null && !comment.isBlank();
        if (reasonCode == null) {
            return hasComment ? comment : null;
        }
        return hasComment ? reasonCode + REASON_COMMENT_SEPARATOR + comment : reasonCode;
    }
}
