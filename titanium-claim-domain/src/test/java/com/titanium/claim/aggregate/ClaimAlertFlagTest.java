package com.titanium.claim.aggregate;

import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.axonframework.test.matchers.Matchers.payloadsMatching;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.command.FlagClaimAlertCommand;
import com.titanium.claim.common.constant.ClaimConstants;
import com.titanium.claim.common.enums.AlertType;
import com.titanium.claim.event.ClaimAlertFlaggedEvent;
import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.valueobject.AlertFlag;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔警示打标测试（反欺诈警示 + 统计口径标记，dev-012b）
 * <p>
 * 验证 {@code FlagClaimAlertCommand}：打标发布 {@link ClaimAlertFlaggedEvent} 投影读模型；按类型合并去重，
 * 重复同类型打标幂等（不发布事件）；新类型追加；空列表静默返回。事件含 {@code now()}，
 * 故用 expectSuccessfulHandlerExecution/expectEventsMatching 断言执行成功，不做精确时间比对。
 * </p>
 */
class ClaimAlertFlagTest {

    private FixtureConfiguration<Claim> fixture;

    private static final String CLAIM_ID = "CLAIM-ALERT-1";
    private static final String POLICY_ID = "POL-ALERT-1";

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Claim.class);
    }

    /** 理赔创建事件（DEATH 类型，警示打标与理赔类型无关） */
    private ClaimCreatedEvent claimCreated() {
        return new ClaimCreatedEvent(ClaimId.of(CLAIM_ID), CustomerId.of("C-1"), PolicyId.of(POLICY_ID), "CLM-ALERT-001",
                ClaimEnum.ClaimType.DEATH, LocalDateTime.now().minusDays(3), "被保险人身故",
                ClaimAmount.of("500000"), LocalDateTime.now().minusDays(3), "T-1");
    }

    private ClaimAlertFlaggedEvent flagged(AlertType type) {
        return new ClaimAlertFlaggedEvent(ClaimId.of(CLAIM_ID),
                List.of(new AlertFlag(type, ClaimConstants.AlertRule.RULE_LATE_REPORT)),
                LocalDateTime.now().minusDays(2));
    }

    @Test
    @DisplayName("打标发布事件：首次打标携带全部标记")
    void shouldPublishEventOnFirstFlagging() {
        fixture.given(claimCreated())
                .when(new FlagClaimAlertCommand(ClaimId.of(CLAIM_ID),
                        List.of(new AlertFlag(AlertType.LATE_REPORT, ClaimConstants.AlertRule.RULE_LATE_REPORT))))
                .expectEventsMatching(payloadsMatching(exactSequenceOf(instanceOf(ClaimAlertFlaggedEvent.class))))
                .expectState(state -> {
                    assertEquals(1, state.getAlertFlags().size());
                    assertEquals(AlertType.LATE_REPORT, state.getAlertFlags().get(0).type());
                    assertEquals(ClaimConstants.AlertRule.RULE_LATE_REPORT, state.getAlertFlags().get(0).ruleCode());
                });
    }

    @Test
    @DisplayName("多标记一次打标：两个类型合并发布单条事件")
    void shouldMergeMultipleFlagsInOneEvent() {
        fixture.given(claimCreated())
                .when(new FlagClaimAlertCommand(ClaimId.of(CLAIM_ID), List.of(
                        new AlertFlag(AlertType.LATE_REPORT, ClaimConstants.AlertRule.RULE_LATE_REPORT),
                        new AlertFlag(AlertType.MULTIPLE_REPORTS, ClaimConstants.AlertRule.RULE_MULTIPLE_REPORTS))))
                .expectEventsMatching(payloadsMatching(exactSequenceOf(instanceOf(ClaimAlertFlaggedEvent.class))))
                .expectState(state -> assertEquals(2, state.getAlertFlags().size()));
    }

    @Test
    @DisplayName("幂等：重复同类型打标不发布事件")
    void shouldIgnoreDuplicateType() {
        fixture.given(claimCreated(), flagged(AlertType.LATE_REPORT))
                .when(new FlagClaimAlertCommand(ClaimId.of(CLAIM_ID),
                        List.of(new AlertFlag(AlertType.LATE_REPORT, ClaimConstants.AlertRule.RULE_LATE_REPORT))))
                .expectNoEvents();
    }

    @Test
    @DisplayName("追加新类型：既有标记保留，新类型并入")
    void shouldAppendNewType() {
        fixture.given(claimCreated(), flagged(AlertType.LATE_REPORT))
                .when(new FlagClaimAlertCommand(ClaimId.of(CLAIM_ID),
                        List.of(new AlertFlag(AlertType.MULTIPLE_REPORTS, ClaimConstants.AlertRule.RULE_MULTIPLE_REPORTS))))
                .expectEventsMatching(payloadsMatching(exactSequenceOf(instanceOf(ClaimAlertFlaggedEvent.class))))
                .expectState(state -> {
                    assertEquals(2, state.getAlertFlags().size());
                    assertEquals(AlertType.LATE_REPORT, state.getAlertFlags().get(0).type());
                    assertEquals(AlertType.MULTIPLE_REPORTS, state.getAlertFlags().get(1).type());
                });
    }

    @Test
    @DisplayName("空列表幂等：无标记时不发布事件")
    void shouldIgnoreEmptyFlags() {
        fixture.given(claimCreated())
                .when(new FlagClaimAlertCommand(ClaimId.of(CLAIM_ID), List.of()))
                .expectNoEvents();
    }
}
