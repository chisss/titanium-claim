package com.titanium.claim.analytical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.titanium.analytical.config.AnalyticalEntityProperties;
import com.titanium.analytical.config.AnalyticalFieldDefinition;
import com.titanium.analytical.seed.AnalyticalFieldCatalogSeeder;
import com.titanium.claim.application.query.ClaimFieldCatalogApplicationService;
import com.titanium.claim.bootstrap.ClaimApplication;
import com.titanium.claim.fieldcatalog.ClaimFieldCatalogCriteria;
import com.titanium.clause.api.ClauseApi;
import com.titanium.customer.api.CustomerApi;
import com.titanium.policy.api.InsuranceApi;
import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.PolicyCashValueApi;
import com.titanium.policy.api.PolicyFieldCatalogApi;
import com.titanium.policy.api.PolicyIssuanceApi;
import com.titanium.policy.api.ProposalApi;
import com.titanium.ruleengine.api.RuleEngineApi;

/**
 * 理赔域字段目录种子导入的判据（G07/AC-09，g07-02e）：真实 YAML 声明 → 启动期一次导入 → 此后<b>表为权威</b>。
 *
 * <h3>与 starter 自身测试的分工</h3>
 * <p>{@code AnalyticalFieldCatalogSeederTest}（starter 模块）验的是<b>导入器机制</b>（手工构造声明 +
 * 手工建表，覆盖三条结构性拒绝与 INIT-ONLY 语义）；本类验的是<b>本域落地</b>——真实 {@code application.yml}
 * 绑定出的声明、真实目录表（由 JPA 建出，与生产 DDL 同列集）、真实 {@code ApplicationReadyEvent} 链路。
 * 机制层全绿而落地层漏字段（如某个字段没写 catalog 段）是两类不同缺陷。</p>
 *
 * <h3>🔴 为什么 webEnvironment 必须是 MOCK 而不是 NONE</h3>
 * <p>starter 的装配条件是 {@code @ConditionalOnWebApplication(SERVLET)}：在 {@code NONE} 下
 * starter 整体不装配，seeder Bean 根本不存在——若照抄本域 DLQ 测试的 {@code NONE}，本类会因
 * 「找不到 Bean」而失败（还算好的）或改成手工构造 seeder 后<b>对自动装配这条最关键的链路全盲</b>。
 * 必须让真实 Starter + 真实事件链路在场。</p>
 *
 * <h3>🔴 三个用例的顺序是判据的一部分，不是随意排列</h3>
 * <p>用例 3 会<b>故意修改目录表</b>（模拟运营在库侧修订），若它先跑，用例 1/2 读到的就不是启动时的原始导入结果。
 * 故用 {@link MethodOrderer.OrderAnnotation} 固定：导入 → 幂等 → 反向。</p>
 *
 * <h3>🔴 反向用例（用例 3）是唯一能证明「表为权威」的判据</h3>
 * <p>用例 1/2（正向：导入 + 幂等零写入）在「YAML 与表双向同步」的<b>错误实现</b>下同样全绿——
 * 双向同步的实现第二次跑也会「没有新东西要写」。只有「手工改表后重跑，改动<b>不得</b>被 YAML 覆盖回去」
 * 能把 INIT-ONLY 与双向同步区分开（见 G07 方案文档 86 行）。</p>
 */
@SpringBootTest(classes = ClaimApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:claim-g07-seed;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.liquibase.enabled=false",
                "spring.task.scheduling.enabled=false",
                "axon.axonserver.enabled=false"
        })
// 与 ClaimDeadLetterQueueContextTest 同集合：本地无 spring-cloud-starter-loadbalancer，
// 未被 mock 的 Feign 客户端会因惰性解析失败而拖垮上下文。**新增 Feign 契约时必须同步补入本表**。
@MockitoBean(types = {
        ClauseApi.class,
        CustomerApi.class,
        InsuranceApi.class,
        PolicyApi.class,
        PolicyCashValueApi.class,
        PolicyFieldCatalogApi.class,
        PolicyIssuanceApi.class,
        ProposalApi.class,
        RuleEngineApi.class
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FieldCatalogSeedIdempotencyTest {

    /** 实体码：与 t_entity_registry 登记行、field_code 前缀三者一致（YAML 键）。 */
    private static final String ENTITY_CODE = "claim";

    /** 目录表（YAML 的 catalog-table）。 */
    private static final String CATALOG_TABLE = "t_claim_field_catalog";

    /** 目录写入的租户（跨租户共享的平台元数据，取 starter 默认值）。 */
    private static final String CATALOG_TENANT = "1";

    /**
     * 期望字段数（独立口径：按本域 application.yml 中与 fields 同级的 {@code - code:} 行手数，20 项，2026-10-08）。
     *
     * <p>数法：{@code grep -c -e '- code: ' titanium-claim-bootstrap/src/main/resources/application.yml}。
     * 与运行时 {@code properties} 现取的声明数互为对账——若有人改了 YAML 而未同步本值，两侧不等即报红，
     * 红的方向指向「声明变了」，确认变更后同步本值即可（这正是「手写数字旁必须留数法」的落点）。</p>
     */
    private static final long EXPECTED_FIELDS = 20L;

    /** 抽样字段：ENUM 类型（裁示口径「ENUM 可分组」的落地断言）。 */
    private static final String SAMPLE_ENUM_FIELD = "claim.status";

    /** 抽样字段：DATETIME 类型（裁示口径「DATETIME 不可分组」的落地断言）。 */
    private static final String SAMPLE_DATETIME_FIELD = "claim.incidentDate";

    @Autowired
    private AnalyticalFieldCatalogSeeder seeder;

    @Autowired
    private AnalyticalEntityProperties properties;

    @Autowired
    private ClaimFieldCatalogApplicationService catalogService;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpJdbc() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    @Order(1)
    @DisplayName("启动即导入：ApplicationReadyEvent 把 YAML 目录声明一次性写进目录表，逐字段集合相等")
    void seedsCatalogOnStartup() {
        // 独立口径对账：期望值（手数自 YAML）与运行时绑定结果必须一致——只断行数无法发现「YAML 少写一个字段」
        Set<String> declaredCodes = catalogBearingFields().stream()
                .map(field -> ENTITY_CODE + "." + field.code())
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(EXPECTED_FIELDS, (long) declaredCodes.size(),
                "YAML 中带目录声明的字段数应等于手数期望值（不等说明声明变了而未同步期望，或声明被误删）");

        Set<String> actualCodes = new TreeSet<>(
                jdbc.queryForList("SELECT field_code FROM " + CATALOG_TABLE, String.class));
        assertEquals(declaredCodes, actualCodes,
                "目录行集应逐字段等于声明集：缺一即漏导，多一即越权写入（应用日志可见 seeder 启动记录）");

        // 抽样：ENUM 可分组（2026-10-01 裁示口径：groupable 含 ENUM/DATE）
        Map<String, Object> enumRow = rowOf(SAMPLE_ENUM_FIELD);
        assertEquals("ENUM", enumRow.get("value_type"), SAMPLE_ENUM_FIELD + " 的目录值类型");
        assertTrue(asFlag(enumRow.get("groupable")),
                SAMPLE_ENUM_FIELD + " 是 ENUM，裁示口径下可分组（与目录 DML 的 CASE 谓词同源）");
        assertEquals("CLAIM", enumRow.get("object_type"), "对象类型取自域专属枚举的 code");
        assertEquals(CATALOG_TENANT, enumRow.get("tenant_id"), "目录是跨租户共享的平台元数据");

        // 抽样：DATETIME 不可分组（2026-10-01 裁示口径：groupable 只含 ENUM/DATE）。
        // 🔴 断言不假设物理类型：H2（JPA create-drop）该列落 BOOLEAN、真机 MySQL 落 TINYINT——
        //    直接强转 (Number) 会在 H2 上 ClassCast，症状伪装成「列值没写进去」，真因是两库类型差异
        Object groupable = rowOf(SAMPLE_DATETIME_FIELD).get("groupable");
        assertFalse(asFlag(groupable),
                SAMPLE_DATETIME_FIELD + " 是 DATETIME，裁示口径下不可分组（与目录 DML 的 CASE 谓词同源）");

        // 全表敏感度口径：本域无任何掩码设施与掩码用法，目录声明必须与域内实况一致（不得凭空发明敏感级别）
        Long deviating = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + CATALOG_TABLE
                        + " WHERE sensitivity <> 'INTERNAL' OR masking_policy <> 'NONE'",
                Long.class);
        assertEquals(0L, deviating == null ? -1L : deviating.longValue(),
                "全表应为 INTERNAL/NONE：任何非该口径的行都意味着目录声明凭空发明了本域不存在的敏感级别");
    }

    @Test
    @Order(2)
    @DisplayName("幂等：再次执行 seed-mode=init 零写入、零更新，内容哈希不变（表非空即整体跳过）")
    void reimportWritesNothing() {
        List<Map<String, Object>> before = snapshot();
        String hashBefore = currentContentHash();

        // 直接驱动导入器：等价于「下一次启动又走到 ApplicationReadyEvent」，
        // 不必为第二次启动再起一个上下文；形参事件未被 seeder 读取，传 null 与其 javadoc 一致
        seeder.onApplicationEvent(null);

        assertEquals(before, snapshot(), "第二次 init 必须逐行全等（含零更新）——任何写入/覆盖都会改变快照");
        assertEquals(hashBefore, currentContentHash(),
                "内容哈希不变：读侧据此判定目录未变（变了却无版本头，整个目录会被判损坏）");
    }

    @Test
    @Order(3)
    @DisplayName("反向——表为权威：手工修订目录行后重跑 init，改动不得被 YAML 覆盖回去")
    void manualCatalogEditIsNeverOverwritten() {
        String yamlLabel = catalogBearingFields().stream()
                .filter(field -> SAMPLE_ENUM_FIELD.equals(ENTITY_CODE + "." + field.code()))
                .findFirst().orElseThrow()
                .catalog().labelKey();
        String tampered = "离线修订：运营在库侧改过的标签";

        // 夹具区分力自检：改动值必须与 YAML 原值不同。若相同，「覆盖回去」与「没覆盖」在数据上同形，
        // 本用例会恒绿而零承重（这正是「夹具无区分力」的形态）
        assertNotEquals(yamlLabel, tampered, "前提：改动值须区别于 YAML 原值，否则本用例无区分力");

        jdbc.update("UPDATE " + CATALOG_TABLE + " SET label_key = ? WHERE field_code = ?",
                tampered, SAMPLE_ENUM_FIELD);
        assertEquals(tampered, rowOf(SAMPLE_ENUM_FIELD).get("label_key"), "前提：手工修订已生效");

        long rowsBefore = countOf(CATALOG_TABLE);
        seeder.onApplicationEvent(null);

        assertEquals(rowsBefore, countOf(CATALOG_TABLE), "重跑不得追加行");
        assertEquals(tampered, rowOf(SAMPLE_ENUM_FIELD).get("label_key"),
                "手工修订不得被 YAML 覆盖回去——本断言是「表为权威（INIT-ONLY）」的唯一判据："
                        + "若实现被改成双向同步，本用例变红而正向用例照样全绿");
    }

    // ------------------------------------------------------------------ 装置

    /** 本域 YAML 中带目录声明的字段（绑定结果现取，不复制 YAML 内容）。 */
    private List<AnalyticalFieldDefinition> catalogBearingFields() {
        return properties.entities().get(ENTITY_CODE).fields().stream()
                .filter(AnalyticalFieldDefinition::hasCatalogDeclaration)
                .toList();
    }

    /** 全表逐行快照（含所有列），用于「零写入」的深比较；行序按主键固定。 */
    private List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("SELECT * FROM " + CATALOG_TABLE + " ORDER BY field_code");
    }

    /** 经读用例入口取当前目录的内容哈希（与生产读取路径同链：View → Assembler → Domain record）。 */
    private String currentContentHash() {
        return catalogService
                .getCurrentCatalog(new ClaimFieldCatalogCriteria(CATALOG_TENANT, LocalDate.now()))
                .contentHash();
    }

    /** 读一行并统一小写列名（未加引号的标识符在 H2 折大写，直接取键会得到 null）。 */
    private Map<String, Object> rowOf(String fieldCode) {
        Map<String, Object> raw = jdbc.queryForMap(
                "SELECT * FROM " + CATALOG_TABLE + " WHERE field_code = ?", fieldCode);
        // 🔴 不用 Collectors.toMap：它不接受 null 值，而 object_identity_field/change_type_code/
        //    deprecated_at 三列按声明本就可空（seeder 写入 null）——用 toMap 会在第一行就 NPE，
        //    症状伪装成「测试失败」，真因在测试装置的收集方式
        Map<String, Object> normalized = new LinkedHashMap<>();
        raw.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        assertTrue(normalized.containsKey("label_key"), "列名归一化后应含 label_key");
        return normalized;
    }

    private long countOf(String table) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0L : count;
    }

    /** 把布尔位列的值归一为布尔语义：H2 返回 {@link Boolean}，MySQL 的 TINYINT 返回 {@link Number}。 */
    private static boolean asFlag(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value instanceof Number number && number.intValue() != 0;
    }
}
