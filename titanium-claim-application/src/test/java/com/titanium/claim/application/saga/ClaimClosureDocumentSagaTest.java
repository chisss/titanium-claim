package com.titanium.claim.application.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.claim.application.saga.assembler.ClaimClosureDocumentAssembler;
import com.titanium.claim.common.enums.BenefitSource;
import com.titanium.claim.event.ClaimRejectedEvent;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.DeathBenefitSettledEvent;
import com.titanium.claim.event.DisabilityBenefitSettledEvent;
import com.titanium.claim.port.document.DocumentServicePort;
import com.titanium.claim.port.document.DocumentServicePort.ClaimDocument;
import com.titanium.claim.valueobject.BenefitCalculation;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.ClaimSettlement;
import com.titanium.claim.valueobject.DeathClaimEvidence;
import com.titanium.claim.valueobject.DisabilityClaimEvidence;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.enums.claim.RejectReason;

/**
 * 理赔结案单证集成编排测试
 * <p>
 * 锁死四条路径（普通赔付 / 身故给付 / 全残给付 / 拒赔）<b>均</b>经 {@link DocumentServicePort} 派发结案
 * 单证要素：此前该端口零实现零调用，理赔结案不产生任何单证。断言内容落在「要素装配是否正确」而非
 * 「是否调用」——金额、给付方式编码、结案说明、结案时间与租户须逐字段透传，拒赔路径的赔付要素须为空
 * （无赔付事实，不得臆造金额）。
 * </p>
 * <p>
 * 另锁死处理组复用：与 {@link ClaimSettlementPaymentSaga} 同组 {@code claim-settlement-group}
 * （tracking + DLQ + 首启位点登记三处 yml 配置已就位），组名漂移会使 DLQ 与首启位点同时落空且不报错。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ClaimClosureDocumentSagaTest {

    /** 租户ID：验证跨域单证载荷的租户贯穿 */
    private static final String          TENANT_ID = "T-1";
    /** 结案时间 */
    private static final LocalDateTime   CLOSED_AT = LocalDateTime.of(2026, 9, 14, 10, 0);

    @Mock
    private DocumentServicePort documentServicePort;

    private ClaimClosureDocumentSaga saga;

    @BeforeEach
    void setUp() {
        saga = new ClaimClosureDocumentSaga(documentServicePort, new ClaimClosureDocumentAssembler());
    }

    @Test
    @DisplayName("普通赔付核赔结算 → 派发结案单证（金额/给付方式/核赔意见逐字段透传）")
    void shouldDispatchDocumentOnClaimSettled() {
        ClaimSettlement settlement = ClaimSettlement.of(new BigDecimal("8000"), ClaimEnum.PayoutMethod.BANK_TRANSFER,
                "ACCT-1", "核赔通过");
        saga.on(new ClaimSettledEvent(ClaimId.of("CLAIM-1"), "POL-1", settlement, CLOSED_AT, TENANT_ID));

        ClaimDocument document = capturedDocument();
        assertEquals("CLAIM-1", document.claimId());
        assertEquals("POL-1", document.policyId());
        assertEquals(new BigDecimal("8000"), document.settledAmount());
        assertEquals(ClaimEnum.PayoutMethod.BANK_TRANSFER.getCode(), document.payoutMethodCode());
        assertEquals("核赔通过", document.conclusion());
        assertEquals(CLOSED_AT, document.closedAt());
        assertEquals(TENANT_ID, document.tenantId(), "跨域单证载荷须携带租户，否则单证域无法判定归属租户");
    }

    @Test
    @DisplayName("身故给付结算 → 派发结案单证（与普通赔付同构，给付额取核算总额）")
    void shouldDispatchDocumentOnDeathBenefitSettled() {
        saga.on(new DeathBenefitSettledEvent(ClaimId.of("CLAIM-2"), "POL-2",
                new DeathClaimEvidence("DC-1", CLOSED_AT, "疾病", true, "BP-1", CLOSED_AT),
                new BenefitCalculation(new BigDecimal("500000"),
                        List.of(new BenefitCalculation.BeneficiaryShare("B-1", "本人", BigDecimal.ONE,
                                new BigDecimal("500000"))),
                        BenefitSource.BASIC_SUM_INSURED),
                ClaimSettlement.of(new BigDecimal("500000"), ClaimEnum.PayoutMethod.BANK_TRANSFER, null, "身故给付核准"),
                CLOSED_AT, TENANT_ID));

        ClaimDocument document = capturedDocument();
        assertEquals("CLAIM-2", document.claimId());
        assertEquals("POL-2", document.policyId());
        assertEquals(new BigDecimal("500000"), document.settledAmount());
        assertEquals("身故给付核准", document.conclusion());
        assertEquals(TENANT_ID, document.tenantId());
    }

    @Test
    @DisplayName("全残给付结算 → 派发结案单证；载荷 settlement 为空时仍派发（要素留空，不因空值吞掉派发）")
    void shouldDispatchDocumentOnDisabilityBenefitSettledEvenWhenSettlementMissing() {
        saga.on(new DisabilityBenefitSettledEvent(ClaimId.of("CLAIM-3"), "POL-3",
                new DisabilityClaimEvidence("DB-1", "一级", CLOSED_AT, "市劳动能力鉴定中心", "BP-1", CLOSED_AT),
                null, null, CLOSED_AT, TENANT_ID));

        ClaimDocument document = capturedDocument();
        assertEquals("CLAIM-3", document.claimId());
        assertEquals("POL-3", document.policyId());
        assertNull(document.settledAmount(), "结算载荷为空时金额留空，单证正文按占位符渲染");
        assertNull(document.payoutMethodCode());
        assertNull(document.conclusion());
        assertEquals(CLOSED_AT, document.closedAt(), "结案时间取自事件本身，不随结算载荷缺失而丢失");
    }

    @Test
    @DisplayName("拒赔结案 → 派发结案单证：无赔付事实（金额/给付方式为空），结案说明为「原因码/批注」")
    void shouldDispatchDocumentOnClaimRejected() {
        saga.on(new ClaimRejectedEvent(ClaimId.of("CLAIM-4"), "POL-4", "CUST-1", RejectReason.NOT_IN_COVERAGE,
                "出险事由不在保障责任范围", CLOSED_AT, TENANT_ID));

        ClaimDocument document = capturedDocument();
        assertEquals("CLAIM-4", document.claimId());
        assertEquals("POL-4", document.policyId());
        assertNull(document.settledAmount(), "拒赔无赔付事实，不得臆造赔付金额");
        assertNull(document.payoutMethodCode(), "拒赔无给付方式");
        assertEquals(RejectReason.NOT_IN_COVERAGE.getCode() + "/出险事由不在保障责任范围", document.conclusion(),
                "结案说明由结构化原因码与业务批注拼成，不含写死的中文文案常量");
        assertEquals(CLOSED_AT, document.closedAt());
        assertEquals(TENANT_ID, document.tenantId());
    }

    @Test
    @DisplayName("拒赔结案：无批注时结案说明退化为原因码（不产生悬空分隔符）")
    void shouldFallbackToReasonCodeWhenCommentMissing() {
        saga.on(new ClaimRejectedEvent(ClaimId.of("CLAIM-5"), "POL-5", "CUST-2", RejectReason.WAITING_PERIOD, "  ",
                CLOSED_AT, TENANT_ID));

        assertEquals(RejectReason.WAITING_PERIOD.getCode(), capturedDocument().conclusion());
    }

    @Test
    @DisplayName("结案单证派发组与 @ProcessingGroup 一致（复用赔付派发组，其 DLQ 与首启位点配置已就位）")
    void shouldReuseSettlementProcessingGroup() {
        ProcessingGroup annotation = ClaimClosureDocumentSaga.class.getAnnotation(ProcessingGroup.class);

        assertNotNull(annotation, "结案单证派发编排必须声明 @ProcessingGroup");
        assertEquals("claim-settlement-group", annotation.value(),
                "组名须与 application.yml 的 axon.eventhandling.processors 及 "
                        + "titanium.axon.outbound-relay.groups 两处配置键一致，漂移会使 DLQ 与首启位点同时落空且不报错");
        assertEquals(ClaimSettlementPaymentSaga.class.getAnnotation(ProcessingGroup.class).value(), annotation.value(),
                "两个结算后派发编排须同组：可靠性要求完全一致，分组只会多出一处必须对齐的配置键");
    }

    /** 捕获派发到文档域的结案单证要素 */
    private ClaimDocument capturedDocument() {
        ArgumentCaptor<ClaimDocument> captor = ArgumentCaptor.forClass(ClaimDocument.class);
        verify(documentServicePort).archiveClaimDocument(captor.capture());
        return captor.getValue();
    }
}
