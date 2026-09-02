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
     *
     * @param instruction 赔付指令（金额/给付方式/收款账户/受益人分账明细）
     * @return 消息派发结果标识（Kafka 场景返回主题分区偏移或 null，不作为业务单号）
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
            List<BenefitCalculation.BeneficiaryShare> beneficiaryShares
    ) {
    }
}
