package com.titanium.claim.application.orchestration.payment;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.claim.command.CompletePaymentCommand;
import com.titanium.claim.valueobject.ClaimId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付回写编排器（application/orchestration/payment）
 * <p>
 * 「支付出账成功 → 回写赔案赔付完成」的命令翻译编排：消费侧防腐监听器（infrastructure）解析
 * 支付域消息后，以基本参数委托本编排器派发 {@link CompletePaymentCommand}，赔案流转至 PAID。
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
}
