package com.titanium.claim.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * 理赔域险种列注释修正变更集测试（G10/g10-02）。
 * <p>
 * 该变更集修正 4 张配置表 {@code insurance_line} 的列注释（原注释引用不存在的枚举）。
 * 4 条正向与 4 条 rollback 语句均为「单行多语句分号分隔」，且都携带 {@code NOT NULL}——
 * 重写列定义时漏写 NOT NULL 会把列悄悄改成可空、漏写 COMMENT 则注释修正落空，
 * 故在 H2 上验证「注释确实被改、NOT NULL 保持、回滚可完整还原」。
 * </p>
 */
class ClaimConfigInsuranceCommentLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/claim_config_insurance_comment_202610091849_weisun_ddl.sql";

    /** {表名, 变更前原注释}——原注释文案各表不一（仅 flow_template 带枚举出处），回滚须逐表还原各自原文案 */
    private static final String[][] TABLES = {
            {"t_claim_flow_template", "险种线 code(metadata ClaimEnum.InsuranceLine)"},
            {"t_claim_payout_rule", "险种线 code"},
            {"t_claim_document_template", "险种线 code"},
            {"t_claim_time_limit_rule", "险种线 code"}};

    @Test
    void shouldFixColumnCommentKeepingNotNullAndRollback() throws Exception {
        String databaseName = "claim_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                for (String[] entry : TABLES) {
                    // 变更前形态替身：NOT NULL + 原文案注释
                    statement.execute("CREATE TABLE " + entry[0] + " ("
                            + "id VARCHAR(64) PRIMARY KEY,"
                            + "insurance_line VARCHAR(32) NOT NULL COMMENT '" + entry[1] + "')");
                }
            }
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());
            for (String[] entry : TABLES) {
                assertEquals(32, columnWidth(connection, entry[0]), entry[0] + " 列宽不得变化");
                assertEquals("NO", isNullable(connection, entry[0]), entry[0] + " 须保持 NOT NULL");
                assertTrue(remarks(connection, entry[0]).contains("权威三级码"),
                        entry[0] + " 注释应被修正为权威三级码文案");
            }

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            for (String[] entry : TABLES) {
                assertEquals(32, columnWidth(connection, entry[0]), entry[0] + " 回滚后列宽不变");
                assertEquals("NO", isNullable(connection, entry[0]), entry[0] + " 回滚后仍须 NOT NULL");
                assertEquals(entry[1], remarks(connection, entry[0]),
                        entry[0] + " 回滚应还原原文案");
            }
        }
    }

    private int columnWidth(Connection connection, String tableName) throws Exception {
        return Integer.parseInt(columnMeta(connection, tableName, "CHARACTER_MAXIMUM_LENGTH"));
    }

    private String isNullable(Connection connection, String tableName) throws Exception {
        return columnMeta(connection, tableName, "IS_NULLABLE");
    }

    private String remarks(Connection connection, String tableName) throws Exception {
        String value = columnMeta(connection, tableName, "REMARKS");
        return value == null ? "" : value;
    }

    private String columnMeta(Connection connection, String tableName, String field) throws Exception {
        String sql = "SELECT " + field + " FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = 'insurance_line'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }
}
