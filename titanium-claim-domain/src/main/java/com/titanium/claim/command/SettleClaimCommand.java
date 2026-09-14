package com.titanium.claim.command;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 核赔结算命令（领域层）
 * <p>
 * 在理赔 APPROVED 状态下提交核赔结论，记录核定赔付金额/给付方式/收款方， 触发理赔聚合流转至 PAID 并发布
 * {@code ClaimSettledEvent}（应用层据此触发支付域 CLAIM_PAYOUT）。
 * </p>
 * <p>
 * 🔴 <b>核定赔付金额的来源规则</b>：金额存在唯一权威来源，调用方不得透传改数——
 * <ul>
 *   <li><b>已定损案件</b>（聚合持有 {@code LossAssessment}）：核定赔付金额取定损核定额
 *       {@code LossAssessment.payableAmount()}（=（定损总金额−残值）×责任比例），
 *       {@code settledAmount} 可传 {@code null}；若传入则必须与该核定额**相等**，否则拒绝。</li>
 *   <li><b>未定损案件</b>：{@code settledAmount} 必填，为 {@code null} 即拒绝。</li>
 * </ul>
 * 该规则与身故给付「禁止透传金额、由基本保额精算」同源（CLAIM-2），保证赔付金额始终有可追溯的精算依据。
 * </p>
 *
 * @param claimId       理赔案件ID
 * @param settledAmount 核定赔付金额（已定损案件可传 null，由定损核定额派生；未定损案件必填）
 * @param payoutMethod  给付方式（BANK_TRANSFER/CASH/CHECK/OFFSET_PREMIUM）
 * @param payeeAccount  收款账户
 * @param conclusion    核赔结论
 */
public record SettleClaimCommand(
        @TargetAggregateIdentifier ClaimId claimId,
        BigDecimal settledAmount,
        ClaimEnum.PayoutMethod payoutMethod,
        String payeeAccount,
        String conclusion
) {
}
