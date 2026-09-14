package com.titanium.claim.port.payment;

import java.math.BigDecimal;
import java.util.List;

import com.titanium.claim.valueobject.BenefitCalculation;

/**
 * 支付服务出口 Port（对端域：payment）
 * <p>
 * 领域需要的支付能力契约：理赔赔付支付单派发（赔付 Saga 使用）。
 * 实现为 infrastructure 层 {@code adapter/payment/PaymentServiceAdapter}（经 Kafka
 * {@code payment-order-created} 主题发消息，payment 域防腐消费）。
 * </p>
 */
public interface PaymentServicePort {

    /**
     * 派发理赔赔付支付单。
     * <p>
     * 🔴 <b>失败会抛出</b>（broker 不可达、确认超时、主题无权限）→ 抛 {@code KafkaPublishException}，
     * 这是「失败可见 → 入 DLQ → 定时重投」链路的触发点；调用方所在处理组须为 tracking + DLQ
     * （{@code claim-settlement-group}）。实现见 {@code PaymentServiceAdapter}（m6-913）。
     * </p>
     * <p>
     * 本端口为 <b>at-least-once</b>：Kafka 确认超时后重投可能造成重复投递，重复由 payment 域
     * 双层幂等兜底（在途查重 + {@code CP-} 确定性聚合 ID），不会重复出款。
     * </p>
     *
     * @param instruction 赔付指令（金额/给付方式/收款账户/受益人分账明细）
     * @return 派发标识，Kafka 场景下即入参 {@code claimId}（**不是**消息偏移，也**不是**支付单号；
     *         支付单号由 payment 域按 {@code CP- + claimId} 确定性派生）
     */
    String createClaimPayout(ClaimPayoutInstruction instruction);

    /**
     * 理赔赔付指令（PaymentServicePort 入参，领域出站契约 record）
     *
     * @param claimId          理赔案件ID
     * @param policyId         关联保单ID
     * @param amount           赔付金额（身故给付为分账总额）
     * @param payoutMethodCode 给付方式编码（ClaimEnum.PayoutMethod code）
     * @param payeeAccount     收款账户（按受益人分账给付时为空）
     * @param beneficiaryShares 受益人分账明细（身故给付按份额分账；普通赔付为空）
     */
    record ClaimPayoutInstruction(
            String claimId,
            String policyId,
            BigDecimal amount,
            String payoutMethodCode,
            String payeeAccount,
            List<BenefitCalculation.BeneficiaryShare> beneficiaryShares,
            String tenantId
    ) {
    }
}
