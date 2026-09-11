package com.titanium.claim.application.orchestration.payment;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.claim.command.CompletePaymentCommand;
import com.titanium.claim.command.RecordPaymentFailureCommand;
import com.titanium.claim.valueobject.ClaimId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付回写编排器（application/orchestration/payment）
 * <p>
 * 「支付出账结果 → 回写赔案」的命令翻译编排，覆盖两种结果：出账成功派发 {@link CompletePaymentCommand}
 * 使赔案流转至 PAID；出账未成功（渠道确认失败 / 人工取消）派发 {@link RecordPaymentFailureCommand}
 * 标记赔付失败、案件保持 APPROVED 待人工重派。
 * </p>
 * <p>
 * 消费侧防腐监听器（infrastructure）解析支付域消息后，以基本参数委托本编排器发命令——
 * 发命令属 application 编排职责，infrastructure 不得持有 CommandGateway（ArchUnit 固化）。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletionOrchestrator {

    private final CommandGateway commandGateway;

    /**
     * 回写赔案赔付完成（支付域出账成功回写）。
     *
     * @param claimId   赔案ID（支付单业务号 businessId）
     * @param paymentNo 支付单号
     */
    public void completePayment(String claimId, String paymentNo) {
        commandGateway.sendAndWait(new CompletePaymentCommand(ClaimId.of(claimId), paymentNo));
        log.info("[支付回写编排] 赔付完成命令已发送, claimId={}, paymentNo={}", claimId, paymentNo);
    }

    /**
     * 回写赔案赔付失败（支付域出账未成功回写：渠道确认失败 / 人工取消）。
     * <p>
     * 与 {@link #completePayment} 同属「支付出账结果回写」一类职责，故同居本编排器：
     * 二者的幂等与乱序容忍策略必须成对演进（成功与未成功分属两个主题，跨主题无保序保证），
     * 拆到两个类反而会让这套策略失去内聚。
     * </p>
     *
     * @param claimId       赔案ID（支付单业务号 businessId）
     * @param paymentNo     支付单号
     * @param failureType   失败类型（对端 resultType code）
     * @param failureReason 出款未成功原因
     */
    public void recordPaymentFailure(String claimId, String paymentNo, String failureType, String failureReason) {
        commandGateway.sendAndWait(new RecordPaymentFailureCommand(ClaimId.of(claimId), paymentNo, failureType,
                failureReason));
        log.info("[支付回写编排] 赔付失败命令已发送, claimId={}, paymentNo={}, failureType={}", claimId, paymentNo,
                failureType);
    }
}
