package com.titanium.claim.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.claim.application.command.config.BlacklistConfigService;
import com.titanium.claim.application.command.config.DocumentTemplateConfigService;
import com.titanium.claim.application.command.config.FlowTemplateConfigService;
import com.titanium.claim.application.command.config.HospitalNetworkConfigService;
import com.titanium.claim.application.command.config.PayoutRuleConfigService;
import com.titanium.claim.application.command.config.QuickPayRuleConfigService;
import com.titanium.claim.application.command.config.TimeLimitRuleConfigService;
import com.titanium.claim.application.query.config.BlacklistConfigQueryService;
import com.titanium.claim.application.query.config.DocumentTemplateConfigQueryService;
import com.titanium.claim.application.query.config.FlowTemplateConfigQueryService;
import com.titanium.claim.application.query.config.HospitalNetworkConfigQueryService;
import com.titanium.claim.application.query.config.PayoutRuleConfigQueryService;
import com.titanium.claim.application.query.config.QuickPayRuleConfigQueryService;
import com.titanium.claim.application.query.config.TimeLimitRuleConfigQueryService;
import com.titanium.claim.web.dto.config.ClaimBlacklistConfigDTO;
import com.titanium.claim.web.dto.config.ClaimDocumentTemplateConfigDTO;
import com.titanium.claim.web.dto.config.ClaimFlowTemplateConfigDTO;
import com.titanium.claim.web.dto.config.ClaimHospitalNetworkConfigDTO;
import com.titanium.claim.web.dto.config.ClaimPayoutRuleConfigDTO;
import com.titanium.claim.web.dto.config.ClaimQuickPayRuleConfigDTO;
import com.titanium.claim.web.dto.config.ClaimTimeLimitRuleConfigDTO;
import com.titanium.claim.web.mapper.ClaimConfigWebMapper;
import com.titanium.claim.web.response.config.ClaimBlacklistConfigVO;
import com.titanium.claim.web.response.config.ClaimDocumentTemplateConfigVO;
import com.titanium.claim.web.response.config.ClaimFlowTemplateConfigVO;
import com.titanium.claim.web.response.config.ClaimHospitalNetworkConfigVO;
import com.titanium.claim.web.response.config.ClaimPayoutRuleConfigVO;
import com.titanium.claim.web.response.config.ClaimQuickPayRuleConfigVO;
import com.titanium.claim.web.response.config.ClaimTimeLimitRuleConfigVO;
import com.titanium.metadata.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 理赔配置中心控制器（管理后台 HTTP 入口）
 * <p>
 * 面向管理后台，路径 {@code /web/v1/config}，覆盖理赔配置子域六个配置主题：流程模板、赔付规则、单证模板、
 * 时限规则、医院网络（宠物医院直赔网络）与黑名单。入参 web 层 {@code XxxDTO}（web/dto/config）、出参
 * {@code XxxVO}（web/response/config），经 {@link ClaimConfigWebMapper} 与六组应用层读写服务交互。
 * 配置子域为纯 JPA CRUD（不走 Axon 命令网关），租户由 common 的 {@code RequestContextInterceptor → RequestContextHolder} 贯穿。
 * Controller 零业务逻辑。
 * </p>
 */
@RestController
@RequestMapping("/web/v1/config")
@RequiredArgsConstructor
public class ClaimConfigController {

    private final FlowTemplateConfigService        flowTemplateConfigService;
    private final PayoutRuleConfigService          payoutRuleConfigService;
    private final QuickPayRuleConfigService         quickPayRuleConfigService;
    private final DocumentTemplateConfigService    documentTemplateConfigService;
    private final TimeLimitRuleConfigService       timeLimitRuleConfigService;
    private final HospitalNetworkConfigService     hospitalNetworkConfigService;
    private final BlacklistConfigService           blacklistConfigService;
    private final FlowTemplateConfigQueryService   flowTemplateConfigQueryService;
    private final PayoutRuleConfigQueryService     payoutRuleConfigQueryService;
    private final QuickPayRuleConfigQueryService    quickPayRuleConfigQueryService;
    private final DocumentTemplateConfigQueryService documentTemplateConfigQueryService;
    private final TimeLimitRuleConfigQueryService  timeLimitRuleConfigQueryService;
    private final HospitalNetworkConfigQueryService hospitalNetworkConfigQueryService;
    private final BlacklistConfigQueryService      blacklistConfigQueryService;
    private final ClaimConfigWebMapper             claimConfigWebMapper;

    // ==================== 流程模板配置 ====================

    /** 新增/更新理赔流程模板（templateId 空=新增） */
    @PostMapping("/flow-templates")
    public ResponseEntity<ApiResponse<String>> saveFlowTemplate(@RequestBody @Valid ClaimFlowTemplateConfigDTO dto) {
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(flowTemplateConfigService.saveFlowTemplate(
                claimConfigWebMapper.toRequest(dto))));
    }

    /** 流程模板列表（当前租户） */
    @GetMapping("/flow-templates")
    public ApiResponse<List<ClaimFlowTemplateConfigVO>> listFlowTemplates() {
        return ApiResponse.success(flowTemplateConfigQueryService.listFlowTemplates()
             .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 流程模板详情 */
    @GetMapping("/flow-templates/{templateId}")
    public ApiResponse<ClaimFlowTemplateConfigVO> getFlowTemplate(@PathVariable String templateId) {
        return ApiResponse.success(claimConfigWebMapper.toVO(
             flowTemplateConfigQueryService.getFlowTemplate(templateId)));
    }

    /** 删除流程模板（逻辑删除） */
    @DeleteMapping("/flow-templates/{templateId}")
    public ApiResponse<Void> deleteFlowTemplate(@PathVariable String templateId) {
        flowTemplateConfigService.deleteFlowTemplate(templateId);
        return ApiResponse.success();
    }

    // ==================== 赔付规则配置 ====================

    /** 新增/更新赔付规则（ruleId 空=新增） */
    @PostMapping("/payout-rules")
    public ResponseEntity<ApiResponse<String>> savePayoutRule(@RequestBody @Valid ClaimPayoutRuleConfigDTO dto) {
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(payoutRuleConfigService.savePayoutRule(
                claimConfigWebMapper.toRequest(dto))));
    }

    /** 赔付规则列表（当前租户） */
    @GetMapping("/payout-rules")
    public ApiResponse<List<ClaimPayoutRuleConfigVO>> listPayoutRules() {
        return ApiResponse.success(payoutRuleConfigQueryService.listPayoutRules()
             .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 赔付规则详情 */
    @GetMapping("/payout-rules/{ruleId}")
    public ApiResponse<ClaimPayoutRuleConfigVO> getPayoutRule(@PathVariable String ruleId) {
        return ApiResponse.success(claimConfigWebMapper.toVO(
             payoutRuleConfigQueryService.getPayoutRule(ruleId)));
    }

    /** 删除赔付规则（逻辑删除） */
    @DeleteMapping("/payout-rules/{ruleId}")
    public ApiResponse<Void> deletePayoutRule(@PathVariable String ruleId) {
        payoutRuleConfigService.deletePayoutRule(ruleId);
        return ApiResponse.success();
    }

    // ==================== 快赔规则配置 ====================

    /** 新增/更新快赔规则（ruleId 空=新增） */
    @PostMapping("/quick-pay-rules")
    public ResponseEntity<ApiResponse<String>> saveQuickPayRule(@RequestBody @Valid ClaimQuickPayRuleConfigDTO dto) {
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(quickPayRuleConfigService.saveQuickPayRule(
                claimConfigWebMapper.toRequest(dto))));
    }

    /** 快赔规则列表（当前租户） */
    @GetMapping("/quick-pay-rules")
    public ApiResponse<List<ClaimQuickPayRuleConfigVO>> listQuickPayRules() {
        return ApiResponse.success(quickPayRuleConfigQueryService.listQuickPayRules()
             .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 快赔规则详情 */
    @GetMapping("/quick-pay-rules/{ruleId}")
    public ApiResponse<ClaimQuickPayRuleConfigVO> getQuickPayRule(@PathVariable String ruleId) {
        return ApiResponse.success(claimConfigWebMapper.toVO(
             quickPayRuleConfigQueryService.getQuickPayRule(ruleId)));
    }

    /** 删除快赔规则（逻辑删除） */
    @DeleteMapping("/quick-pay-rules/{ruleId}")
    public ApiResponse<Void> deleteQuickPayRule(@PathVariable String ruleId) {
        quickPayRuleConfigService.deleteQuickPayRule(ruleId);
        return ApiResponse.success();
    }

    // ==================== 单证模板配置 ====================

    /** 新增/更新单证模板（templateId 空=新增） */
    @PostMapping("/document-templates")
    public ResponseEntity<ApiResponse<String>> saveDocumentTemplate(@RequestBody @Valid ClaimDocumentTemplateConfigDTO dto) {
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(documentTemplateConfigService.saveDocumentTemplate(
                claimConfigWebMapper.toRequest(dto))));
    }

    /** 单证模板列表（当前租户） */
    @GetMapping("/document-templates")
    public ApiResponse<List<ClaimDocumentTemplateConfigVO>> listDocumentTemplates() {
        return ApiResponse.success(documentTemplateConfigQueryService.listDocumentTemplates()
             .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 单证模板详情 */
    @GetMapping("/document-templates/{templateId}")
    public ApiResponse<ClaimDocumentTemplateConfigVO> getDocumentTemplate(@PathVariable String templateId) {
        return ApiResponse.success(claimConfigWebMapper.toVO(
             documentTemplateConfigQueryService.getDocumentTemplate(templateId)));
    }

    /** 删除单证模板（逻辑删除） */
    @DeleteMapping("/document-templates/{templateId}")
    public ApiResponse<Void> deleteDocumentTemplate(@PathVariable String templateId) {
        documentTemplateConfigService.deleteDocumentTemplate(templateId);
        return ApiResponse.success();
    }

    // ==================== 时限规则配置 ====================

    /** 新增/更新时限规则（ruleId 空=新增） */
    @PostMapping("/time-limit-rules")
    public ResponseEntity<ApiResponse<String>> saveTimeLimitRule(@RequestBody @Valid ClaimTimeLimitRuleConfigDTO dto) {
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(timeLimitRuleConfigService.saveTimeLimitRule(
                claimConfigWebMapper.toRequest(dto))));
    }

    /** 时限规则列表（当前租户） */
    @GetMapping("/time-limit-rules")
    public ApiResponse<List<ClaimTimeLimitRuleConfigVO>> listTimeLimitRules() {
        return ApiResponse.success(timeLimitRuleConfigQueryService.listTimeLimitRules()
             .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 时限规则详情 */
    @GetMapping("/time-limit-rules/{ruleId}")
    public ApiResponse<ClaimTimeLimitRuleConfigVO> getTimeLimitRule(@PathVariable String ruleId) {
        return ApiResponse.success(claimConfigWebMapper.toVO(
             timeLimitRuleConfigQueryService.getTimeLimitRule(ruleId)));
    }

    /** 删除时限规则（逻辑删除） */
    @DeleteMapping("/time-limit-rules/{ruleId}")
    public ApiResponse<Void> deleteTimeLimitRule(@PathVariable String ruleId) {
        timeLimitRuleConfigService.deleteTimeLimitRule(ruleId);
        return ApiResponse.success();
    }

    // ==================== 医院网络配置 ====================

    /** 新增/更新医院网络（hospitalId 空=新增） */
    @PostMapping("/hospital-networks")
    public ResponseEntity<ApiResponse<String>> saveHospitalNetwork(@RequestBody @Valid ClaimHospitalNetworkConfigDTO dto) {
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(hospitalNetworkConfigService.saveHospitalNetwork(
                claimConfigWebMapper.toRequest(dto))));
    }

    /** 医院网络列表（当前租户） */
    @GetMapping("/hospital-networks")
    public ApiResponse<List<ClaimHospitalNetworkConfigVO>> listHospitalNetworks() {
        return ApiResponse.success(hospitalNetworkConfigQueryService.listHospitals()
             .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 医院网络详情 */
    @GetMapping("/hospital-networks/{hospitalId}")
    public ApiResponse<ClaimHospitalNetworkConfigVO> getHospitalNetwork(@PathVariable String hospitalId) {
        return ApiResponse.success(claimConfigWebMapper.toVO(
             hospitalNetworkConfigQueryService.getHospital(hospitalId)));
    }

    /** 暂停医院协议（ACTIVE → SUSPENDED，暂停期间不参与直赔/赔付比例计算） */
    @PutMapping("/hospital-networks/{hospitalId}/suspend")
    public ApiResponse<Void> suspendHospital(@PathVariable String hospitalId) {
        hospitalNetworkConfigService.suspendHospital(hospitalId);
        return ApiResponse.success();
    }

    /** 恢复医院协议（SUSPENDED → ACTIVE） */
    @PutMapping("/hospital-networks/{hospitalId}/activate")
    public ApiResponse<Void> activateHospital(@PathVariable String hospitalId) {
        hospitalNetworkConfigService.activateHospital(hospitalId);
        return ApiResponse.success();
    }

    /** 终止医院协议（ACTIVE/SUSPENDED → TERMINATED） */
    @PutMapping("/hospital-networks/{hospitalId}/terminate")
    public ApiResponse<Void> terminateHospital(@PathVariable String hospitalId) {
        hospitalNetworkConfigService.terminateHospital(hospitalId);
        return ApiResponse.success();
    }

    /** 删除医院网络（逻辑删除） */
    @DeleteMapping("/hospital-networks/{hospitalId}")
    public ApiResponse<Void> deleteHospitalNetwork(@PathVariable String hospitalId) {
        hospitalNetworkConfigService.deleteHospitalNetwork(hospitalId);
        return ApiResponse.success();
    }

    // ==================== 黑名单配置 ====================

    /** 新增/更新黑名单（blacklistId 空=新增，更新保持生效状态不变） */
    @PostMapping("/blacklists")
    public ResponseEntity<ApiResponse<String>> saveBlacklist(@RequestBody @Valid ClaimBlacklistConfigDTO dto) {
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(blacklistConfigService.saveBlacklist(
                claimConfigWebMapper.toRequest(dto))));
    }

    /** 黑名单列表（当前租户） */
    @GetMapping("/blacklists")
    public ApiResponse<List<ClaimBlacklistConfigVO>> listBlacklists() {
        return ApiResponse.success(blacklistConfigQueryService.listBlacklists()
             .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 黑名单详情 */
    @GetMapping("/blacklists/{blacklistId}")
    public ApiResponse<ClaimBlacklistConfigVO> getBlacklist(@PathVariable String blacklistId) {
        return ApiResponse.success(claimConfigWebMapper.toVO(
             blacklistConfigQueryService.getBlacklist(blacklistId)));
    }

    /** 撤销黑名单（ACTIVE → REVOKED） */
    @PostMapping("/blacklists/{blacklistId}/revoke")
    public ApiResponse<Void> revokeBlacklist(@PathVariable String blacklistId) {
        blacklistConfigService.revokeBlacklist(blacklistId);
        return ApiResponse.success();
    }

    /** 删除黑名单（逻辑删除） */
    @DeleteMapping("/blacklists/{blacklistId}")
    public ApiResponse<Void> deleteBlacklist(@PathVariable String blacklistId) {
        blacklistConfigService.deleteBlacklist(blacklistId);
        return ApiResponse.success();
    }
}
