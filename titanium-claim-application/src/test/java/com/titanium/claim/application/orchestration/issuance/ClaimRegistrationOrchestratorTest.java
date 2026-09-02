package com.titanium.claim.application.orchestration.issuance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.application.model.issuance.CreateClaimRequest;
import com.titanium.claim.application.orchestration.issuance.validator.ClaimAmountValidator;
import com.titanium.claim.application.orchestration.issuance.validator.PolicyActiveValidator;
import com.titanium.claim.command.CreateClaimCommand;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.InvalidClaimAmountException;
import com.titanium.claim.common.exception.PolicyNotActiveException;
import com.titanium.claim.port.policy.PolicyInfo;
import com.titanium.claim.port.policy.PolicyServicePort;
import com.titanium.claim.service.ClaimService;

/**
 * 报案校验链编排器测试
 * <p>
 * 验证 {@link ClaimRegistrationOrchestrator}：校验链（金额 → 保单）顺序执行、任一失败中断且不发命令、
 * 校验全过后生成理赔 ID/编号并装配发送 {@link CreateClaimCommand}。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ClaimRegistrationOrchestratorTest {

    @Mock
    private ClaimService      claimService;
    @Mock
    private PolicyServicePort policyServicePort;
    @Mock
    private TenantContext     tenantContext;
    @Mock
    private CommandGateway    commandGateway;

    private ClaimRegistrationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimRegistrationOrchestrator(
                List.of(new ClaimAmountValidator(claimService), new PolicyActiveValidator(policyServicePort,
                        tenantContext)),
                claimService, commandGateway);
        lenient().when(tenantContext.getCurrentTenantId()).thenReturn("default-tenant");
    }

    private CreateClaimRequest request() {
        CreateClaimRequest request = new CreateClaimRequest();
        request.setCustomerId("CUST-1");
        request.setPolicyId("POL-1");
        request.setClaimType("MEDICAL");
        request.setIncidentDate(LocalDateTime.now());
        request.setIncidentDescription("门诊就医");
        request.setClaimAmount(new BigDecimal("8000"));
        return request;
    }

    @Test
    @DisplayName("校验链全过 → 生成编号并发送创建命令，返回理赔 ID")
    void shouldRegisterClaimWhenValidationPasses() {
        when(policyServicePort.getPolicy("POL-1", "default-tenant")).thenReturn(new PolicyInfo("POL-1", "ACTIVE", new BigDecimal("100000"), LocalDateTime.now().minusDays(30)));
        when(claimService.generateClaimNumber()).thenReturn("CLAIM-20260902-000001");

        String claimId = orchestrator.registerClaim(request());

        ArgumentCaptor<CreateClaimCommand> captor = ArgumentCaptor.forClass(CreateClaimCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        CreateClaimCommand command = captor.getValue();
        assertEquals(claimId, command.claimId().value());
        assertEquals("CLAIM-20260902-000001", command.claimNumber());
        assertEquals("CUST-1", command.customerId().value());
        assertEquals("POL-1", command.policyId().value());
        assertEquals("MEDICAL", command.claimType().getCode());
        assertEquals(new BigDecimal("8000"), command.claimAmount().value());
    }

    @Test
    @DisplayName("保单不存在 → 抛 PolicyNotActiveException 且不发命令")
    void shouldRejectWhenPolicyMissing() {
        when(policyServicePort.getPolicy("POL-1", "default-tenant")).thenReturn(null);

        assertThrows(PolicyNotActiveException.class, () -> orchestrator.registerClaim(request()));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("保单非 ACTIVE → 抛 PolicyNotActiveException 且不发命令")
    void shouldRejectWhenPolicyInactive() {
        when(policyServicePort.getPolicy("POL-1", "default-tenant")).thenReturn(new PolicyInfo("POL-1", "EXPIRED", new BigDecimal("100000"), null));

        assertThrows(PolicyNotActiveException.class, () -> orchestrator.registerClaim(request()));
        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    @DisplayName("金额非法（领域服务判定失败）→ 异常传播且不触达保单校验")
    void shouldRejectWhenAmountInvalid() {
        CreateClaimRequest request = request();
        request.setClaimAmount(BigDecimal.ZERO);
        doThrow(new InvalidClaimAmountException()).when(claimService).validateClaimAmount(BigDecimal.ZERO);

        assertThrows(InvalidClaimAmountException.class, () -> orchestrator.registerClaim(request));
        verify(policyServicePort, never()).getPolicy(any(), any());
        verify(commandGateway, never()).sendAndWait(any());
    }
}
