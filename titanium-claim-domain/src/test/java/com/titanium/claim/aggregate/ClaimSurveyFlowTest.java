package com.titanium.claim.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.command.SubmitLossAssessmentCommand;
import com.titanium.claim.command.SubmitSurveyCommand;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.event.ClaimLossAssessedEvent;
import com.titanium.claim.event.ClaimSurveySubmittedEvent;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.LossAssessment;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.claim.valueobject.Survey;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔查勘/定损接线测试
 * <p>
 * 验证 {@code SubmitSurveyCommand}/{@code SubmitLossAssessmentCommand} 补 {@code @TargetAggregateIdentifier}
 * 后能正确路由到聚合并推进阶段；事件含 {@code now()} 故用 expectSuccessfulHandlerExecution 断言执行成功，
 * 不做精确时间比对。
 * </p>
 */
class ClaimSurveyFlowTest {

    private FixtureConfiguration<Claim> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Claim.class);
    }

    private ClaimCreatedEvent createdEvent() {
        return new ClaimCreatedEvent(ClaimId.of("CLAIM-1"), CustomerId.of("C-1"), PolicyId.of("P-1"), "CLM-001",
                ClaimEnum.ClaimType.PROPERTY, LocalDateTime.now().minusDays(1), "车辆碰撞", ClaimAmount.of("10000"),
                LocalDateTime.now().minusDays(1), "T-1");
    }

    @Test
    @DisplayName("提交查勘命令正确路由到聚合并推进阶段至 SURVEY")
    void shouldRouteSubmitSurveyCommand() {
        Survey survey = new Survey("S-1", "现场勘查报告", List.of("photo1.jpg"), "属实", LocalDateTime.now());
        fixture.given(createdEvent())
                .when(new SubmitSurveyCommand(ClaimId.of("CLAIM-1"), survey))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers
                        .payloadsMatching(org.axonframework.test.matchers.Matchers
                                .exactSequenceOf(org.hamcrest.CoreMatchers.instanceOf(ClaimSurveySubmittedEvent.class))));
    }

    @Test
    @DisplayName("查勘后可提交定损，阶段推进至 LOSS_ASSESS")
    void shouldSubmitLossAssessmentAfterSurvey() {
        LossAssessment loss = new LossAssessment(new BigDecimal("8000"),
                List.of(new LossAssessment.LossItem("保险杠", new BigDecimal("8000"))), null,
                new BigDecimal("1.0"), "A-1");
        fixture.given(createdEvent(),
                        new ClaimSurveySubmittedEvent(ClaimId.of("CLAIM-1"),
                                new Survey("S-1", "报告", List.of(), "属实", LocalDateTime.now().minusHours(1)),
                                com.titanium.metadata.enums.claim.ClaimPhase.SURVEY, LocalDateTime.now().minusHours(1)))
                .when(new SubmitLossAssessmentCommand(ClaimId.of("CLAIM-1"), loss))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(org.axonframework.test.matchers.Matchers
                        .payloadsMatching(org.axonframework.test.matchers.Matchers
                                .exactSequenceOf(org.hamcrest.CoreMatchers.instanceOf(ClaimLossAssessedEvent.class))));
    }
}
