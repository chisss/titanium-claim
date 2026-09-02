package com.titanium.claim.application.orchestration.issuance.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.application.model.issuance.CreateClaimRequest;
import com.titanium.claim.common.context.TenantContext;
import com.titanium.claim.common.exception.ClaimOutOfCoverageException;
import com.titanium.claim.port.clause.ClauseServicePort;
import com.titanium.claim.port.clause.ClauseServicePort.CoverageInfo;
import com.titanium.claim.port.policy.ClauseRef;
import com.titanium.claim.port.policy.PolicyInfo;
import com.titanium.claim.port.policy.PolicyServicePort;
import com.titanium.claim.service.ClaimService;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 责任校验器测试（CLAIM-4 报案侧责任校验链第 3 环）
 * <p>
 * 验证 {@link CoverageValidator}：Port 取数（保单→条款→责任）→ 领域服务纯规则判定 →
 * 结论分支（责任除外中断报案、需人工判定放行转人工、责任成立放行）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class CoverageValidatorTest {

    @Mock
    private PolicyServicePort policyServicePort;
    @Mock
    private ClauseServicePort clauseServicePort;

    private CoverageValidator validator;
    private TenantContext    tenantContext;

    @BeforeEach
    void setUp() {
        tenantContext = new TenantContext();
        tenantContext.setCurrentTenantId("T-1");
        validator = new CoverageValidator(policyServicePort, clauseServicePort, new ClaimService(), tenantContext);
    }

    private CreateClaimRequest request() {
        CreateClaimRequest request = new CreateClaimRequest();
        request.setPolicyId("POL-1");
        request.setClaimType(ClaimEnum.ClaimType.DEATH.getCode());
        request.setIncidentDate(LocalDateTime.of(2026, 6, 1, 0, 0));
        request.setClaimAmount(new BigDecimal("10000"));
        return request;
    }

    private PolicyInfo policy(LocalDateTime effectiveDate) {
        return new PolicyInfo("POL-1", "ACTIVE", new BigDecimal("500000"), effectiveDate);
    }

    @Test
    @DisplayName("命中责任且已过等待期 → 责任成立，校验放行")
    void shouldPassWhenInCoverage() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(policy(LocalDateTime.of(2026, 1, 1, 0, 0)));
        when(policyServicePort.fetchClauses("POL-1", "T-1"))
                .thenReturn(List.of(new ClauseRef("CL-1", "CLAUSE-DEATH-01", "身故条款", true)));
        when(clauseServicePort.fetchCoverages("CL-1", "T-1"))
                .thenReturn(List.of(new CoverageInfo("COV-1", "COV-DEATH-01", "身故保险金", 90)));

        assertDoesNotThrow(() -> validator.validate(request()));
    }

    @Test
    @DisplayName("条款无责任数据 → 责任除外，抛异常中断报案")
    void shouldRejectWhenNoCoverage() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(policy(LocalDateTime.of(2026, 1, 1, 0, 0)));
        when(policyServicePort.fetchClauses("POL-1", "T-1"))
                .thenReturn(List.of(new ClauseRef("CL-1", "CLAUSE-DEATH-01", "身故条款", true)));
        when(clauseServicePort.fetchCoverages("CL-1", "T-1")).thenReturn(List.of());

        assertThrows(ClaimOutOfCoverageException.class, () -> validator.validate(request()));
    }

    @Test
    @DisplayName("出险在等待期内非意外 → 需人工判定，放行转人工核赔")
    void shouldPassManualReviewWithinWaitingPeriod() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(policy(LocalDateTime.of(2026, 5, 1, 0, 0)));
        when(policyServicePort.fetchClauses("POL-1", "T-1"))
                .thenReturn(List.of(new ClauseRef("CL-1", "CLAUSE-DEATH-01", "身故条款", true)));
        when(clauseServicePort.fetchCoverages("CL-1", "T-1"))
                .thenReturn(List.of(new CoverageInfo("COV-1", "COV-DEATH-01", "身故保险金", 90)));

        assertDoesNotThrow(() -> validator.validate(request()));
    }

    @Test
    @DisplayName("保单无条款快照 → 无法自动定责，跳过校验转人工")
    void shouldSkipWhenNoClause() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(policy(LocalDateTime.of(2026, 1, 1, 0, 0)));
        when(policyServicePort.fetchClauses("POL-1", "T-1")).thenReturn(List.of());

        assertDoesNotThrow(() -> validator.validate(request()));
    }

    @Test
    @DisplayName("保单或生效日期缺失 → 跳过责任校验")
    void shouldSkipWhenPolicyMissing() {
        when(policyServicePort.getPolicy("POL-1", "T-1")).thenReturn(null);

        assertDoesNotThrow(() -> validator.validate(request()));
    }

    @Test
    @DisplayName("多条款保单：主条款优先取责任")
    void shouldPickMainClauseFirst() {
        when(policyServicePort.getPolicy("POL-1", "T-1"))
                .thenReturn(policy(LocalDateTime.of(2026, 1, 1, 0, 0)));
        when(policyServicePort.fetchClauses("POL-1", "T-1")).thenReturn(List.of(
                new ClauseRef("CL-2", "CLAUSE-ACC-01", "意外附加条款", false),
                new ClauseRef("CL-1", "CLAUSE-DEATH-01", "身故主条款", true)));
        when(clauseServicePort.fetchCoverages("CL-1", "T-1"))
                .thenReturn(List.of(new CoverageInfo("COV-1", "COV-DEATH-01", "身故保险金", 90)));

        assertDoesNotThrow(() -> validator.validate(request()));
    }
}
