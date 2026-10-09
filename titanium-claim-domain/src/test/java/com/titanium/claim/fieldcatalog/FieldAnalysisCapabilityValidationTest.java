package com.titanium.claim.fieldcatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.claim.fieldcatalog.ClaimFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.errorcode.BaseErrorCode;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

/**
 * 「可分析」能力位四条不变量（G07/g07-03/AC-03）的单元测试。
 *
 * <p>🔴 <b>每条规则的用例都只用「仅违反该规则、其它规则一律不触发」的组合</b>——
 * 若用它例顺带覆盖，四条规则里有一条失效时无从分辨，判据就退了。
 * 逐条的依据（值类型 / collection / analyzable 三个维度的取值都据此挑）：</p>
 * <ul>
 *   <li>规则 1 用例取 INTEGER 标量：值类型可聚合（不触发规则 2）、非集合（不触发规则 3）、
 *       {@code groupable=false}（不触发规则 4）。</li>
 *   <li>规则 2 用例取 TEXT 标量 + {@code analyzable=true}（不触发规则 1）、非集合（不触发规则 3）。</li>
 *   <li>规则 3 用例取 TEXT 集合 + {@code aggregatable=false}（不触发规则 2）、
 *       {@code groupable=false}（不触发规则 4）。</li>
 *   <li>规则 4 用例取 INTEGER 标量 + {@code aggregatable=true}（INTEGER 可聚合，不触发规则 2）、
 *       非集合（不触发规则 3）。</li>
 * </ul>
 *
 * <p>另有两条横向判据：<b>短路顺序</b>（多违规时报第一条而非任意一条）与
 * <b>两条调用路径一致</b>（静态裁决方法与紧凑构造器必须给出同一个错误码——
 * 它们是「先问能不能」与「能不能都由它说了算」的两种用法，规则漂移即此处破裂）。</p>
 *
 * <p>本域静态裁决以 {@code null} 表示通过、以错误码表示违规（本域不存在 policy 域的
 * {@code RuleDecision} 类型，理由见 {@code ClaimFieldDescriptor.validateAnalysis} javadoc）。</p>
 */
class FieldAnalysisCapabilityValidationTest {

    /** 取本域任一对象类型：用例不依赖具体枚举值（值域由字段对象类型枚举各自锁定）。 */
    private static final ClaimFieldObjectType OBJECT_TYPE = ClaimFieldObjectType.values()[0];

    // ---------------------------------------------------------------- 规则 1

    @Test
    void shouldRejectInconsistentBitsWhenAnalyzableIsOff() {
        // 只违反规则 1：INTEGER 可聚合、非集合、groupable=false，故规则 2/3/4 均不适用。
        BaseErrorCode decision = ClaimFieldDescriptor.validateAnalysis(
                FieldAnalysisCapability.of(false, true, false, false),
                PolicyFieldValueType.INTEGER,
                false);

        assertEquals(ClaimErrorCode.CLAIM_FIELD_ANALYSIS_INCONSISTENT, decision,
                "总开关关掉却声称可聚合，必须拒绝");

        // 其余两位各自单独为 true 时同样拒绝（三个合取项各验一次，避免只覆盖 aggregatable）。
        for (FieldAnalysisCapability inconsistent : new FieldAnalysisCapability[] {
                FieldAnalysisCapability.of(false, false, true, false),
                FieldAnalysisCapability.of(false, false, false, true)}) {
            assertEquals(ClaimErrorCode.CLAIM_FIELD_ANALYSIS_INCONSISTENT,
                    ClaimFieldDescriptor.validateAnalysis(inconsistent, PolicyFieldValueType.INTEGER, false),
                    "analyzable=false 时任一其它位为 true 都应拒绝: " + inconsistent);
        }

        // 边界正例：全 false 是「不参与分析」的合法表达（与 FieldAnalysisCapability.none() 一致）。
        assertNull(ClaimFieldDescriptor.validateAnalysis(
                FieldAnalysisCapability.none(), PolicyFieldValueType.INTEGER, false),
                "analyzable=false 且其余全 false 必须放行");
    }

    // ---------------------------------------------------------------- 规则 2

    @Test
    void shouldRejectAggregatableOnNonMeasurableTypes() {
        // 只违反规则 2：TEXT/OBJECT/ARRAY 标量，analyzable=true 故规则 1 不适用、非集合故规则 3 不适用。
        for (PolicyFieldValueType valueType : new PolicyFieldValueType[] {
                PolicyFieldValueType.TEXT, PolicyFieldValueType.OBJECT, PolicyFieldValueType.ARRAY}) {
            BaseErrorCode decision = ClaimFieldDescriptor.validateAnalysis(
                    FieldAnalysisCapability.of(true, true, false, false),
                    valueType,
                    false);

            assertEquals(ClaimErrorCode.CLAIM_FIELD_ANALYSIS_TYPE_UNSUPPORTED, decision,
                    "不可度量类型不得声明可聚合: " + valueType);
        }

        // 边界正例：其余六种类型均可聚合（COUNT/SUM 口径下有意义）。
        for (PolicyFieldValueType valueType : new PolicyFieldValueType[] {
                PolicyFieldValueType.INTEGER,
                PolicyFieldValueType.DECIMAL,
                PolicyFieldValueType.BOOLEAN,
                PolicyFieldValueType.DATE,
                PolicyFieldValueType.DATETIME,
                PolicyFieldValueType.ENUM}) {
            assertNull(ClaimFieldDescriptor.validateAnalysis(
                    FieldAnalysisCapability.of(true, true, false, false), valueType, false),
                    "可度量类型应放行: " + valueType);
        }
    }

    // ---------------------------------------------------------------- 规则 3

    @Test
    void shouldRejectCollectionFieldThatIsNotGroupable() {
        // 只违反规则 3：TEXT 集合 + aggregatable=false（不触发规则 2）、groupable=false（不触发规则 4）。
        BaseErrorCode decision = ClaimFieldDescriptor.validateAnalysis(
                FieldAnalysisCapability.of(true, false, false, false),
                PolicyFieldValueType.TEXT,
                true);

        assertEquals(ClaimErrorCode.CLAIM_FIELD_ANALYSIS_COLLECTION_NOT_GROUPABLE, decision,
                "集合字段必须可分组，否则聚合结果无法按业务主体归并");

        // 边界正例：集合 + groupable=true（filterable 必须同时为 true 才不算违反规则 4）。
        assertNull(ClaimFieldDescriptor.validateAnalysis(
                FieldAnalysisCapability.of(true, false, true, true), PolicyFieldValueType.TEXT, true),
                "集合字段可分组即放行");
    }

    // ---------------------------------------------------------------- 规则 4

    @Test
    void shouldRejectGroupableFieldThatIsNotFilterable() {
        // 只违反规则 4：INTEGER 标量可聚合（不触发规则 2）、非集合（不触发规则 3）。
        BaseErrorCode decision = ClaimFieldDescriptor.validateAnalysis(
                FieldAnalysisCapability.of(true, true, true, false),
                PolicyFieldValueType.INTEGER,
                false);

        assertEquals(ClaimErrorCode.CLAIM_FIELD_ANALYSIS_GROUPABLE_NOT_FILTERABLE, decision,
                "能分组必能过滤");
    }

    // ---------------------------------------------------------------- 短路顺序

    @Test
    void shouldReportFirstViolationInRuleOrder() {
        // 同时违反规则 2 与规则 3：TEXT 集合 + aggregatable=true + groupable=false。
        // 必须报规则 2（前者），证明四条是**有序短路**而非任取一条。
        BaseErrorCode decision = ClaimFieldDescriptor.validateAnalysis(
                FieldAnalysisCapability.of(true, true, false, false),
                PolicyFieldValueType.TEXT,
                true);

        assertEquals(ClaimErrorCode.CLAIM_FIELD_ANALYSIS_TYPE_UNSUPPORTED, decision,
                "多违规时必须报规则序中靠前的那条");
    }

    // ---------------------------------------------------------------- 构造器与静态裁决一致

    @Test
    void shouldMakeConstructorAndStaticCheckAgreeOnErrorCode() {
        // 同一份违规输入走两条路径：静态裁决返回错误码，紧凑构造器抛出携带同一错误码的异常。
        // 二者不一致即说明规则在两处各写了一份（本类存在的意义就是钉死这一点）。
        FieldAnalysisCapability invalid = FieldAnalysisCapability.of(true, true, true, false);
        PolicyFieldValueType valueType = PolicyFieldValueType.INTEGER;

        BaseErrorCode expected = ClaimFieldDescriptor
                .validateAnalysis(invalid, valueType, false);

        ClaimFieldCatalogValidationException thrown = assertThrows(
                ClaimFieldCatalogValidationException.class,
                () -> ClaimFieldDescriptor.scalar(
                        "claim.test.amount",
                        OBJECT_TYPE,
                        valueType,
                        "claim.field.test.amount",
                        ClaimFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.INTERNAL,
                        PolicyFieldMaskingPolicy.NONE,
                        invalid));

        assertEquals(expected, thrown.getErrorCode(), "构造器与静态裁决必须给出同一个错误码");
        assertEquals(ClaimErrorCode.CLAIM_FIELD_ANALYSIS_GROUPABLE_NOT_FILTERABLE, expected,
                "本用例注入的是规则 4 违规，前置断言防用例自身漂移");
        assertTrue(thrown.getMessage().contains("claim.test.amount"),
                "异常消息须定位到字段（错误码本身不含参数，字段码由构造器拼接）");
    }

    // ---------------------------------------------------------------- 判空

    @Test
    void shouldRejectNullAnalysis() {
        ClaimFieldCatalogValidationException thrown = assertThrows(
                ClaimFieldCatalogValidationException.class,
                () -> ClaimFieldDescriptor.scalar(
                        "claim.test.amount",
                        OBJECT_TYPE,
                        PolicyFieldValueType.INTEGER,
                        "claim.field.test.amount",
                        ClaimFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.INTERNAL,
                        PolicyFieldMaskingPolicy.NONE,
                        null));

        assertTrue(thrown.getMessage().contains("分析位"), "判空消息须点明是分析位缺失");
    }
}
