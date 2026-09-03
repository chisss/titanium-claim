package com.titanium.claim.valueobject;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 身故理赔证据值对象（寿险身故给付材料集）
 * <p>
 * 身故理赔（{@code ClaimType.DEATH}）核赔的材料依据：死亡证明、户籍注销证明、受益人关系证明等。
 * 定期寿险/终身寿险以「被保险人身故」为给付条件，须核验身故事实与受益人身份后方可给付并终止保单。
 * 与车险查勘（{@link Survey}）/定损（{@link LossAssessment}）并列，是寿险身故场景的专属证据模型。
 * </p>
 * <p>
 * 充血不可变值对象：{@link #isComplete()} 内聚「材料是否齐备」的领域规则——死亡证明与受益人关系证明
 * 为身故给付的必备要件，二者齐备方可进入核赔给付。
 * </p>
 * <p>
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}：历史事件序列化含派生属性 {@code complete}
 * （{@code isComplete()} 被 Jackson 视为 getter），事件重放须忽略该未知字段，否则反序列化失败阻塞投影。
 * </p>
 *
 * @param deathCertificateNo    死亡证明编号（医学死亡证明/司法死亡宣告）
 * @param deathDate             身故日期
 * @param deathCause            身故原因（疾病/意外等，供责任判定与除外核查）
 * @param householdCancelled    是否已办理户籍注销
 * @param beneficiaryProofNo    受益人关系证明编号（受益人身份与关系凭证）
 * @param submittedAt           材料提交时间
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeathClaimEvidence(
        String deathCertificateNo,
        LocalDateTime deathDate,
        String deathCause,
        boolean householdCancelled,
        String beneficiaryProofNo,
        LocalDateTime submittedAt) {

    /**
     * 身故给付材料是否齐备。
     * <p>
     * 死亡证明编号、身故日期与受益人关系证明为身故给付的必备要件，齐备方可核赔给付。
     * </p>
     *
     * @return 材料齐备返回 {@code true}
     */
    @JsonIgnore
    public boolean isComplete() {
        return deathCertificateNo != null && !deathCertificateNo.isBlank()
                && deathDate != null
                && beneficiaryProofNo != null && !beneficiaryProofNo.isBlank();
    }
}
