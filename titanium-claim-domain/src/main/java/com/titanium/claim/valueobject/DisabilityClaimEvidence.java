package com.titanium.claim.valueobject;

import java.time.LocalDateTime;

/**
 * 全残理赔证据值对象（寿险/意外险全残给付材料集）
 * <p>
 * 全残理赔（{@code ClaimType.DISABILITY}）核赔的材料依据：残疾鉴定证明、伤残等级鉴定、受益人关系证明等。
 * 与身故给付（{@link DeathClaimEvidence}）并列，是寿险全残场景的专属证据模型；
 * 全残给付后保单责任终止（同身故，CLAIM-6）。
 * </p>
 * <p>
 * 充血不可变值对象：{@link #isComplete()} 内聚「材料是否齐备」的领域规则——残疾鉴定证明编号、
 * 残疾等级与受益人关系证明为全残给付的必备要件，三者齐备方可进入核赔给付。
 * </p>
 *
 * @param disabilityCertificateNo 残疾鉴定证明编号（司法鉴定机构/医疗鉴定机构出具）
 * @param disabilityGrade         残疾等级（如「一级伤残」，供给付比例条款判定）
 * @param assessmentDate          鉴定日期
 * @param assessmentAgency        鉴定机构名称
 * @param beneficiaryProofNo      受益人关系证明编号（受益人身份与关系凭证）
 * @param submittedAt             材料提交时间
 */
public record DisabilityClaimEvidence(
        String disabilityCertificateNo,
        String disabilityGrade,
        LocalDateTime assessmentDate,
        String assessmentAgency,
        String beneficiaryProofNo,
        LocalDateTime submittedAt) {

    /**
     * 全残给付材料是否齐备。
     * <p>
     * 残疾鉴定证明编号、残疾等级与受益人关系证明为全残给付的必备要件，齐备方可核赔给付。
     * </p>
     *
     * @return 材料齐备返回 {@code true}
     */
    public boolean isComplete() {
        return disabilityCertificateNo != null && !disabilityCertificateNo.isBlank()
                && disabilityGrade != null && !disabilityGrade.isBlank()
                && beneficiaryProofNo != null && !beneficiaryProofNo.isBlank();
    }
}
