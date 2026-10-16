package com.titanium.claim.fieldcatalog;

import java.time.LocalDate;
import java.util.regex.Pattern;

import com.titanium.metadata.enums.claim.fieldcatalog.ClaimFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.errorcode.BaseErrorCode;
import com.titanium.metadata.errorcode.ClaimErrorCode;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;

/**
 * Claim 对外发布的稳定字段描述。
 *
 * <p>十一个组件与 {@code t_claim_field_catalog} 表一一对应（
 * {@code capability} 展开为该表的六个能力列、{@code analysis} 展开为四个分析位列），
 * 是「先建表、再由 starter YAML 导入」的落点。</p>
 *
 * <p>🔴 {@code objectType} 用本域专属枚举 {@link ClaimFieldObjectType}；其余三个类型枚举
 * （值类型 / 敏感等级 / 掩码策略）复用 metadata 中既有的<b>跨域共享</b>枚举——它们已被 30 余个
 * 文件跨域 import，重建一份会造成语义分裂。</p>
 */
public record ClaimFieldDescriptor(
        String fieldCode,
        ClaimFieldObjectType objectType,
        PolicyFieldValueType valueType,
        String labelKey,
        boolean collection,
        String objectIdentityField,
        ClaimFieldCapability capability,
        PolicyFieldSensitivityLevel sensitivity,
        PolicyFieldMaskingPolicy maskingPolicy,
        LocalDate deprecatedAt,
        FieldAnalysisCapability analysis) {

    private static final Pattern FIELD_CODE_PATTERN =
            Pattern.compile("[a-z][a-zA-Z0-9]*(\\.[a-z][a-zA-Z0-9]*)+");

    public ClaimFieldDescriptor {
        fieldCode = requireText(fieldCode, "字段编码");
        labelKey = requireText(labelKey, "标签键");
        objectIdentityField = normalize(objectIdentityField);
        if (!FIELD_CODE_PATTERN.matcher(fieldCode).matches()) {
            throw new ClaimFieldCatalogValidationException("字段编码格式非法: " + fieldCode);
        }
        if (objectType == null || valueType == null || capability == null
                || sensitivity == null || maskingPolicy == null || analysis == null) {
            throw new ClaimFieldCatalogValidationException("字段类型、能力、分析位和敏感策略不能为空");
        }
        if (collection && objectIdentityField == null) {
            throw new ClaimFieldCatalogValidationException("集合字段必须配置稳定业务对象标识");
        }
        if (!collection && objectIdentityField != null) {
            throw new ClaimFieldCatalogValidationException("非集合字段不能配置业务对象标识");
        }
        if (capability.requiresObjectId() && !collection) {
            throw new ClaimFieldCatalogValidationException("仅集合字段可以要求业务对象标识");
        }
        if (sensitivity.requiresMasking() && maskingPolicy == PolicyFieldMaskingPolicy.NONE) {
            throw new ClaimFieldCatalogValidationException("敏感字段必须配置掩码策略");
        }
        BaseErrorCode analysisError = validateAnalysis(analysis, valueType, collection);
        if (analysisError != null) {
            throw new ClaimFieldCatalogValidationException(
                    analysisError, analysisError.getMessage() + ": " + fieldCode);
        }
    }

    /**
     * 校验「可分析」能力位的四条不变量（G07/g07-03），返回违反的错误码而非直接抛出。
     *
     * <p>四条规则（rule → 错误码）：</p>
     * <ol>
     *   <li>{@code analyzable=false} ⇒ 其余三位必须全 false —— 总开关关掉却声称能聚合，自相矛盾。</li>
     *   <li>{@code valueType ∈ {TEXT, OBJECT, ARRAY}} ⇒ {@code aggregatable} 不得为 true
     *       —— 文本/对象/数组没有可求和的度量语义。</li>
     *   <li>{@code collection=true} ⇒ {@code groupable=true}
     *       —— 否则聚合结果无法按业务主体归并（归并键就是 {@code objectIdentityField}）。</li>
     *   <li>{@code groupable=true} ⇒ {@code filterable=true} —— 能分组必能过滤。</li>
     * </ol>
     *
     * <p>🔴 <b>为何四条都落在这里、而不像需求文档 §g07-03 第 3 点所说「内聚为
     * {@code FieldAnalysisCapability} 的紧凑构造器不变量」</b>：规则 2 需要 {@code valueType}、
     * 规则 3 需要 {@code collection}，metadata 侧的 {@code FieldAnalysisCapability} 看不到这两个事实；
     * 错误码枚举亦定义在 metadata，它反向依赖业务域同样会破坏「metadata 是纯契约 jar」的定位。
     * 规则只能内聚在<b>唯一能同时看到三个事实的地方</b>——即本类。已同批勘误文档措辞。</p>
     *
     * <p>🔴 <b>为何返回 {@link BaseErrorCode} 而非复用 {@code RuleDecision}</b>：
     * 🔧 订正（2026-10-15，DSN-08/m25-10）：原记「本域不存在 {@code RuleDecision} 类型、它与
     * policy 域同名类型形态不兼容（后者 {@code passed()/errorCode()} 三态裁决、前者
     * {@code approved()/manualReview()}）」已随三副本收敛失效——现全域唯一版在 metadata
     * （{@code com.titanium.metadata.valueobject.RuleDecision}），本域可引。本方法仍返回
     * {@link BaseErrorCode} 是<b>轻量契约选择</b>：通过返回 {@code null}、拒绝返回违反码，
     * 调用方（紧凑构造器）取码拼字段定位、拒绝态零决策对象分配；本方法只有「通过/拒绝」两态，
     * 不涉统一版的「需人工判定」第三态（未来若需第三态，须改返 {@code RuleDecision} 而非用
     * null 语义承载）。</p>
     *
     * @param analysis   分析能力位；调用方须先行判空（紧凑构造器已在统一判空处拦截）
     * @param valueType  字段值类型
     * @param collection 是否集合对象
     * @return 通过返回 {@code null}；违规返回对应的 {@link ClaimErrorCode}
     */
    public static BaseErrorCode validateAnalysis(
            FieldAnalysisCapability analysis,
            PolicyFieldValueType valueType,
            boolean collection) {
        if (!analysis.analyzable()
                && (analysis.aggregatable() || analysis.groupable() || analysis.filterable())) {
            return ClaimErrorCode.CLAIM_FIELD_ANALYSIS_INCONSISTENT;
        }
        if (isNonAggregatableType(valueType) && analysis.aggregatable()) {
            return ClaimErrorCode.CLAIM_FIELD_ANALYSIS_TYPE_UNSUPPORTED;
        }
        if (collection && !analysis.groupable()) {
            return ClaimErrorCode.CLAIM_FIELD_ANALYSIS_COLLECTION_NOT_GROUPABLE;
        }
        if (analysis.groupable() && !analysis.filterable()) {
            return ClaimErrorCode.CLAIM_FIELD_ANALYSIS_GROUPABLE_NOT_FILTERABLE;
        }
        return null;
    }

    /**
     * 是否为「不具备聚合语义」的值类型。
     *
     * <p>switch <b>不写 default</b>：{@code PolicyFieldValueType} 新增取值时本方法编译失败，
     * 把「新类型能不能聚合」变成构建期强制项（与 {@code ClaimErrorCode.category()} 同一手法）。
     * 否则新类型会静默落进 {@code default} 分支，得到一份无人复核的聚合口径。</p>
     */
    private static boolean isNonAggregatableType(PolicyFieldValueType valueType) {
        return switch (valueType) {
            case TEXT, OBJECT, ARRAY -> true;
            case INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, ENUM -> false;
        };
    }

    /** 创建标量字段描述（无业务对象标识、无集合语义）。 */
    public static ClaimFieldDescriptor scalar(
            String fieldCode,
            ClaimFieldObjectType objectType,
            PolicyFieldValueType valueType,
            String labelKey,
            ClaimFieldCapability capability,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy,
            FieldAnalysisCapability analysis) {
        return new ClaimFieldDescriptor(fieldCode, objectType, valueType, labelKey, false, null,
                capability, sensitivity, maskingPolicy, null, analysis);
    }

    /** 创建集合字段描述（须携带稳定业务对象标识，供按主体归并）。 */
    public static ClaimFieldDescriptor collectionField(
            String fieldCode,
            ClaimFieldObjectType objectType,
            PolicyFieldValueType valueType,
            String labelKey,
            String objectIdentityField,
            ClaimFieldCapability capability,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy,
            FieldAnalysisCapability analysis) {
        return new ClaimFieldDescriptor(fieldCode, objectType, valueType, labelKey, true,
                objectIdentityField, capability, sensitivity, maskingPolicy, null, analysis);
    }

    /** 供目录计算内容哈希的规范形式（包内可见，不对外暴露）。 */
    String canonicalForm() {
        return String.join("\u001f",
                fieldCode,
                objectType.getCode(),
                valueType.getCode(),
                labelKey,
                Boolean.toString(collection),
                nullToEmpty(objectIdentityField),
                Boolean.toString(capability.readable()),
                Boolean.toString(capability.proposable()),
                Boolean.toString(capability.clearable()),
                Boolean.toString(capability.executionSupported()),
                Boolean.toString(capability.requiresObjectId()),
                nullToEmpty(capability.changeTypeCode()),
                sensitivity.getCode(),
                maskingPolicy.getCode(),
                deprecatedAt == null ? "" : deprecatedAt.toString(),
                Boolean.toString(analysis.analyzable()),
                Boolean.toString(analysis.aggregatable()),
                Boolean.toString(analysis.groupable()),
                Boolean.toString(analysis.filterable()));
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ClaimFieldCatalogValidationException(label + "不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
