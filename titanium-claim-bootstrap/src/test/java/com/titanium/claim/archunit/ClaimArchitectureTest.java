package com.titanium.claim.archunit;

import org.junit.jupiter.api.Test;

import com.titanium.buildtools.archunit.AbstractArchitectureGuardTest;

/**
 * 理赔域架构守护测试：继承共享基类，仅提供本域根包。
 * 全部 DDD 分层/命名/依赖注入规则由 {@link AbstractArchitectureGuardTest} 提供，
 * 规则一处维护、各域复用，杜绝测试代码复制粘贴漂移。
 */
class ClaimArchitectureTest extends AbstractArchitectureGuardTest {

    @Override
    protected String basePackage() {
        return "com.titanium.claim";
    }

    /**
     * 启用「application 层不得依赖 api 的 DTO」。
     * <p>
     * 理赔域 api/web 已按《API层与Web层职责边界及协作规范》整改：DTO→应用层入参的翻译在 web 完成，
     * {@code ClaimCommandService} 与 {@code ClaimAppQueryService} 只依赖本层入参 DTO 与读侧结果，不依赖 {@code claim.api} 契约细节。
     * </p>
     */
    @Test
    @Override
    protected void applicationMustNotDependOnApiDto() {
        super.applicationMustNotDependOnApiDto();
    }

    /**
     * 启用「api 层使用 Request/Response 而非 DTO」（2026-07-19 命名新规）。
     * <p>
     * 理赔域 api 层已弃用 DTO：写入参 {@code ClaimRequest}/{@code SettleClaimRequest} 等落 {@code claim.api.request}，
     * 读出参 {@code ClaimResponse}/{@code ClaimStatisticsResponse} 落 {@code claim.api.response}，api 层无 DTO 后缀类型。
     * </p>
     */
    @Test
    @Override
    protected void apiLayerUsesRequestResponseNotDto() {
        super.apiLayerUsesRequestResponseNotDto();
    }

    /**
     * 启用「web 层使用 DTO/VO 而非 Request/Response」（2026-07-19 命名新规）。
     * <p>
     * 理赔域 web 层前端入参已改名 {@code CreateClaimDTO}/{@code SettleClaimDTO} 等落 {@code claim.web.dto}，
     * 出参 {@code ClaimResponseVO} 等用 VO，web 层无 Request/Response 后缀类型。
     * </p>
     */
    @Test
    @Override
    protected void webLayerUsesDtoVoNotRequest() {
        super.webLayerUsesDtoVoNotRequest();
    }

    /**
     * 启用「API 契约实现（Provider）须位于 web.provider 且以 Provider 结尾」。
     * <p>
     * 理赔域契约实现为 {@code ClaimApiProvider}，统一落在 web/provider。
     * </p>
     */
    @Test
    @Override
    protected void apiContractImplMustResideInProviderPackage() {
        super.apiContractImplMustResideInProviderPackage();
    }

    /**
     * 启用「Controller 不得实现 api 契约接口」。
     * <p>
     * {@code ClaimController} 已去掉 {@code implements ClaimApi}，契约实现下沉 web/provider 的 Provider。
     * </p>
     */
    @Test
    @Override
    protected void controllerMustNotImplementApi() {
        super.controllerMustNotImplementApi();
    }

    /**
     * 启用「api 层 Feign 契约接口须以 Api 结尾（命名主键为聚合根）」。
     * <p>
     * 理赔域契约统一为 {@code ClaimApi}，原 {@code ClaimClient}（Client 后缀）冗余已删除。
     * </p>
     */
    @Test
    @Override
    protected void apiInterfacesMustBeNamedByAggregate() {
        super.apiInterfacesMustBeNamedByAggregate();
    }

    /**
     * 启用「web.dto 按业务主题拆子包、顶层清零」（分包规则·批次 2）。
     * <p>
     * 理赔域 web.dto 顶层 10 类已按业务主题归位至既有子包：{@code dto.issuance}（报案立案）、
     * {@code dto.maintenance}（案件变更 / 状态 / 拒赔 / 警示标记）、{@code dto.settlement}
     * （核赔结算 + 身故 / 全残给付）、{@code dto.assessment}（查勘 / 定损 / 报销理算），
     * 与既有的 {@code dto.config}（7 类配置）合计 18 类，顶层零类。
     * </p>
     */
    @Test
    @Override
    protected void webDtoShouldNotContainFlatClasses() {
        super.webDtoShouldNotContainFlatClasses();
    }

    /**
     * 启用「api.request 按业务主题拆子包、顶层清零」（分包规则·批次 2）。
     * <p>
     * 理赔域 api.request 已按与 web.dto / application.model 同构的四主题拆分：
     * {@code request.issuance}（理赔案件创建与更新契约）、{@code request.maintenance}
     * （警示标记 / 拒赔）、{@code request.settlement}（核赔结算 + 身故 / 全残给付）、
     * {@code request.assessment}（查勘 / 定损），顶层零类。
     * </p>
     * <p>
     * 全仓普查确认本包<b>零跨域引用方</b>——8 个 request 类仅本域 {@code ClaimApi} /
     * {@code ClaimController} / {@code ClaimWebMapper} / {@code ClaimApiProvider} 使用，
     * 故拆分仅涉本域 4 文件 import 改写。
     * </p>
     */
    @Test
    @Override
    protected void apiRequestShouldNotContainFlatClasses() {
        super.apiRequestShouldNotContainFlatClasses();
    }

    /**
     * 启用「application.model 按业务主题拆子包、顶层清零」（分包规则·批次 2）。
     * <p>
     * 理赔域 application.model 早已按业务主题五分（{@code config} 7 / {@code assessment} 4 /
     * {@code maintenance} 3 / {@code settlement} 3 / {@code issuance} 1），顶层长期为空，
     * 但对应断言此前从未启用（ArchUnit 按字节码包名判定，规则形同虚设）。本次复核确认
     * 目录与 {@code package} 声明零偏差后启用固化。
     * </p>
     */
    @Test
    @Override
    protected void applicationModelShouldNotContainFlatClasses() {
        super.applicationModelShouldNotContainFlatClasses();
    }

    // 注：域内其余契约/门面包（web.controller 2、web.response 1 + assessment/config/error 三子包、
    // api.response 2、application.command 1 + config 子包）顶层均 ≤2 类，
    // 按《包结构分包规范与执行方案-2026-09》§一判据表豁免，不启用对应断言。

    // 注：不启用严格隔离断言 webShouldNotDependOnDomainCommandsOrAggregates。
    // 现行 api/web 规范允许 web 依赖 command/query（但不碰 aggregate），故回退为基类默认 @Disabled。
}
