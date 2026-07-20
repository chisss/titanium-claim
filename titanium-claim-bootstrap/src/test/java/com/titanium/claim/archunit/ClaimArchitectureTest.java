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
     * {@code ClaimApplicationService} 只依赖本层入参 DTO 与读侧结果，不依赖 {@code claim.api} 契约细节。
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

    // 注：不启用严格隔离断言 webShouldNotDependOnDomainCommandsOrAggregates。
    // 现行 api/web 规范允许 web 依赖 command/query（但不碰 aggregate），故回退为基类默认 @Disabled。
}
