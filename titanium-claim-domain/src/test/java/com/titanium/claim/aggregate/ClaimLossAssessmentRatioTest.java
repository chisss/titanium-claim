package com.titanium.claim.aggregate;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.claim.command.SettleClaimCommand;
import com.titanium.claim.command.SubmitLossAssessmentCommand;
import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimLossAssessedEvent;
import com.titanium.claim.event.ClaimSettledEvent;
import com.titanium.claim.event.ClaimStatusChangedEvent;
import com.titanium.claim.event.ClaimSurveySubmittedEvent;
import com.titanium.claim.exception.ClaimLiabilityRatioException;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.claim.valueobject.Survey;
import com.titanium.metadata.enums.claim.ClaimEnum;
import com.titanium.metadata.enums.claim.ClaimPhase;

/**
 * 定损责任比例量纲不变量测试（🔴 D-501-49）
 * <p>
 * 缺陷现场：前端按百分数录入（`80` = 80%）、后端契约是 0-1 小数，跨层无一处转换
 * ⇒ {@code 10000 × 80 = 800000}，放大 100 倍，且该值是结算金额的唯一权威、只校验 {@code >0} 无上界。
 * </p>
 * <p>
 * 🔴 <b>本类锁死两条判据，且第二条比第一条更重要</b>：
 * </p>
 * <ol>
 *   <li><b>命令入口拒绝越界</b>：新增的定损不能再写进 `80`/`100` 这类量纲错值；</li>
 *   <li><b>重放必须容忍历史越界值</b>：校验<i>不得</i>下沉到 {@link LossAssessment} 紧凑构造器 ——
 *       该值对象是 {@link ClaimLossAssessedEvent} 的载荷，事件溯源重放历史事件时 Jackson 走的正是
 *       那个规范构造器。一旦构造即校验，事件库中<b>修复前已写入</b>的越界值会让聚合<b>永远无法加载</b>
 *       （且报错是序列化层的晦涩异常而非业务错误）。故校验置于
 *       {@link Claim#handle(SubmitLossAssessmentCommand)}，本类以「越界载荷可反序列化」固化该边界。</li>
 * </ol>
 */
class ClaimLossAssessmentRatioTest {

    /** 「赔付比例无效」错误码 + 异常类型的联合判据 */
    private static final Matcher<Object> RATIO_INVALID = allOf(
            instanceOf(ClaimLiabilityRatioException.class),
            hasProperty("errorCode", equalTo("30001021")));

    private FixtureConfiguration<Claim> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Claim.class);
    }

    private ClaimCreatedEvent createdEvent() {
        return new ClaimCreatedEvent(ClaimId.of("CLAIM-1"), CustomerId.of("C-1"), PolicyId.of("P-1"), "CLM-001",
                ClaimEnum.ClaimType.ACCIDENT, LocalDateTime.now().minusDays(1), "车辆碰撞",
                ClaimAmount.of("10000"), LocalDateTime.now().minusDays(1), "T-1");
    }

    private ClaimSurveySubmittedEvent surveyEvent() {
        return new ClaimSurveySubmittedEvent(ClaimId.of("CLAIM-1"),
                new Survey("S-1", "报告", List.of(), "属实", LocalDateTime.now().minusHours(1)),
                ClaimPhase.SURVEY, LocalDateTime.now().minusHours(1));
    }

    private LossAssessment assessment(String ratio) {
        return new LossAssessment(new BigDecimal("10000"), List.of(), null,
                ratio == null ? null : new BigDecimal(ratio), "A-1");
    }

    @Test
    @DisplayName("责任比例按百分数传入（80）：命令入口显式拒绝，不发定损事件")
    void shouldRejectPercentScaleRatio() {
        fixture.given(createdEvent(), surveyEvent())
                .when(new SubmitLossAssessmentCommand(ClaimId.of("CLAIM-1"), assessment("80")))
                .expectException(RATIO_INVALID);
    }

    @Test
    @DisplayName("责任比例越界（1.5）：命令入口拒绝")
    void shouldRejectRatioAboveOne() {
        fixture.given(createdEvent(), surveyEvent())
                .when(new SubmitLossAssessmentCommand(ClaimId.of("CLAIM-1"), assessment("1.5")))
                .expectException(RATIO_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "0.5", "0.8", "1" })
    @DisplayName("责任比例在 0-1 内：放行并发出定损事件")
    void shouldAcceptDecimalScaleRatio(String ratio) {
        fixture.given(createdEvent(), surveyEvent())
                .when(new SubmitLossAssessmentCommand(ClaimId.of("CLAIM-1"), assessment(ratio)))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers
                        .payloadsMatching(org.axonframework.test.matchers.Matchers
                                .exactSequenceOf(org.hamcrest.CoreMatchers
                                        .instanceOf(ClaimLossAssessedEvent.class))));
    }

    @Test
    @DisplayName("责任比例为空：保持原有容忍（不参与赔付计算，由结算金额校验兜底）")
    void shouldTolerateNullRatio() {
        fixture.given(createdEvent(), surveyEvent())
                .when(new SubmitLossAssessmentCommand(ClaimId.of("CLAIM-1"), assessment(null)))
                .expectSuccessfulHandlerExecution();
    }

    @Test
    @DisplayName("🔴 历史越界载荷必须可重放：反序列化 `liabilityRatio:80` 不得抛异常（校验不得下沉到构造器）")
    void shouldReplayLegacyOutOfRangePayload() {
        // 载荷取自事件库实况 axon_domain_event_entry（案件 225548316912386048 的 ClaimLossAssessedEvent），
        // 字段名与 LossAssessment 记录组件一致 —— Jackson 正是走规范构造器构造本值对象。
        String legacyPayload = """
                {"assessedAmount":10000,"items":[],"salvageValue":null,"liabilityRatio":80,\
                "assessorId":"ASSESSOR-A22-001"}""";

        LossAssessment replayed = assertDoesNotThrow(
                () -> new ObjectMapper().readValue(legacyPayload, LossAssessment.class),
                "构造器一旦校验，事件库中该聚合将永远无法加载");

        // 量纲仍是越界的（历史事实），但可加载；识别能力由静态判据提供
        assertEquals(80, replayed.liabilityRatio().intValue());
        assertEquals(true, LossAssessment.isOutOfDecimalScale(replayed.liabilityRatio()));
    }

    @Test
    @DisplayName("🔴 修复前已落库的越界定损：结算闸口拒绝出账，不放行不可信的核定金额")
    void shouldRejectSettlementOnLegacyOutOfRangeAssessment() {
        // 现场复刻：事件库中 ratio=80 的 ClaimLossAssessedEvent 已存在且案件已推进至 APPROVED，
        // 此时前端预填会取到被放大 100 倍的 assessedPayableAmount（= 定损核定额），
        // 与聚合自证式相等于 put 进去的金额 —— 唯一能挡住这笔出账的就是结算闸口。
        fixture.given(createdEvent(), surveyEvent(),
                        new ClaimLossAssessedEvent(ClaimId.of("CLAIM-1"), assessment("80"),
                                ClaimPhase.LOSS_ASSESS, LocalDateTime.now().minusMinutes(30)),
                        new ClaimStatusChangedEvent(ClaimId.of("CLAIM-1"), ClaimStatus.PENDING,
                                ClaimStatus.PROCESSING, "状态更新", LocalDateTime.now().minusMinutes(20), "T-1"),
                        new ClaimStatusChangedEvent(ClaimId.of("CLAIM-1"), ClaimStatus.PROCESSING,
                                ClaimStatus.APPROVED, "状态更新", LocalDateTime.now().minusMinutes(10), "T-1"))
                .when(new SettleClaimCommand(ClaimId.of("CLAIM-1"), null,
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "6222-0000", "核赔通过"))
                .expectException(RATIO_INVALID);
    }

    @Test
    @DisplayName("定损量纲合法时结算照常放行（闸口不误伤正常案件）")
    void shouldSettleWhenRatioInScale() {
        fixture.given(createdEvent(), surveyEvent(),
                        new ClaimLossAssessedEvent(ClaimId.of("CLAIM-1"), assessment("0.8"),
                                ClaimPhase.LOSS_ASSESS, LocalDateTime.now().minusMinutes(30)),
                        new ClaimStatusChangedEvent(ClaimId.of("CLAIM-1"), ClaimStatus.PENDING,
                                ClaimStatus.PROCESSING, "状态更新", LocalDateTime.now().minusMinutes(20), "T-1"),
                        new ClaimStatusChangedEvent(ClaimId.of("CLAIM-1"), ClaimStatus.PROCESSING,
                                ClaimStatus.APPROVED, "状态更新", LocalDateTime.now().minusMinutes(10), "T-1"))
                .when(new SettleClaimCommand(ClaimId.of("CLAIM-1"), new BigDecimal("8000.00"),
                        ClaimEnum.PayoutMethod.BANK_TRANSFER, "6222-0000", "核赔通过"))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers
                        .payloadsMatching(org.axonframework.test.matchers.Matchers
                                .exactSequenceOf(org.hamcrest.CoreMatchers
                                        .instanceOf(ClaimSettledEvent.class))));
    }
}
