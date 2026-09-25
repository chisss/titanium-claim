package com.titanium.claim.infrastructure.adapter.customer;

import org.springframework.stereotype.Component;

import com.titanium.claim.port.customer.CustomerServicePort;
import com.titanium.customer.api.CustomerApi;
import com.titanium.customer.api.response.CustomerResponse;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户服务 Adapter（对端域：customer）
 * <p>
 * 实现 {@link CustomerServicePort}：调用客户域 {@link CustomerApi} Feign，将下游契约
 * {@link CustomerResponse} 翻译为领域摘要 {@link CustomerInfo}（防腐：领域不依赖对端 api 类型）。
 * 供身故/全残给付的受益人身份核验（CLAIM-4）取证——受益人在客户主数据中不存在或状态非有效即阻断给付。
 * </p>
 * <p>
 * 🔴 <b>空值语义</b>：对端契约「客户不存在时 {@code data} 为 {@code null}」，故此处<b>不抛异常</b>而返回 {@code null}，
 * 由调用方判定（不存在与状态无效是两种不同的拒绝理由，须能分别报错）；对端调用失败（Feign 异常、
 * 或 2xx + 错误信封）则抛错，不通融为「查不到」——核验取不到证据时给付必须中断，不得静默放行。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceAdapter implements CustomerServicePort {

    private final CustomerApi customerApi;

    @Override
    public CustomerInfo getCustomer(String customerId, String tenantId) {
        log.info("获取客户信息, customerId={}, tenantId={}", customerId, tenantId);
        ApiResponse<CustomerResponse> response = customerApi.getCustomer(customerId);
        if (response == null) {
            // 兜底：防「Feign 解码异常却未抛错」的下游变更，按对端契约语义判为不存在
            return null;
        }
        if (!response.isSuccess()) {
            // 🔴 不得把错误信封当作「客户不存在」——核验失败必须中断给付，而非给出错误的拒绝理由
            log.error("获取客户信息失败, customerId={}, error={}", customerId, response.getMessage());
            throw new DomainException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                    "客户服务调用失败: customerId=" + customerId + ", errorCode=" + response.getCode());
        }
        CustomerResponse customer = response.getData();
        if (customer == null) {
            // 对端契约语义：不存在时 data 为 null（非错误），由调用方判定并给出精确拒绝理由
            return null;
        }
        return new CustomerInfo(
                customer.getCustomerId() == null ? customerId : customer.getCustomerId(),
                customer.getFullName(),
                customer.getStatus() == null ? null : customer.getStatus().getCode());
    }
}
