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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 理赔配置中心控制器（管理后台 HTTP 入口）
 * <p>
 * 面向管理后台，路径 {@code /web/v1/config}，覆盖理赔配置子域六个配置主题：流程模板、赔付规则、单证模板、
 * 时限规则、医院网络（宠物医院直赔网络）与黑名单。入参 web 层 {@code XxxDTO}（web/dto/config）、出参
 * {@code XxxVO}（web/response/config），经 {@link ClaimConfigWebMapper} 与六组应用层读写服务交互。
 * 配置子域为纯 JPA CRUD（不走 Axon 命令网关），租户由 {@code TenantInterceptor → TenantContext} 贯穿。
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
    public ResponseEntity<String> saveFlowTemplate(@RequestBody @Valid ClaimFlowTemplateConfigDTO dto) {
        return new ResponseEntity<>(flowTemplateConfigService.saveFlowTemplate(
                claimConfigWebMapper.toRequest(dto)), HttpStatus.CREATED);
    }

    /** 流程模板列表（当前租户） */
    @GetMapping("/flow-templates")
    public ResponseEntity<List<ClaimFlowTemplateConfigVO>> listFlowTemplates() {
        return ResponseEntity.ok(flowTemplateConfigQueryService.listFlowTemplates()
                .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 流程模板详情 */
    @GetMapping("/flow-templates/{templateId}")
    public ResponseEntity<ClaimFlowTemplateConfigVO> getFlowTemplate(@PathVariable String templateId) {
        return ResponseEntity.ok(claimConfigWebMapper.toVO(
                flowTemplateConfigQueryService.getFlowTemplate(templateId)));
    }

    /** 删除流程模板（逻辑删除） */
    @DeleteMapping("/flow-templates/{templateId}")
    public ResponseEntity<Void> deleteFlowTemplate(@PathVariable String templateId) {
        flowTemplateConfigService.deleteFlowTemplate(templateId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 赔付规则配置 ====================

    /** 新增/更新赔付规则（ruleId 空=新增） */
    @PostMapping("/payout-rules")
    public ResponseEntity<String> savePayoutRule(@RequestBody @Valid ClaimPayoutRuleConfigDTO dto) {
        return new ResponseEntity<>(payoutRuleConfigService.savePayoutRule(
                claimConfigWebMapper.toRequest(dto)), HttpStatus.CREATED);
    }

    /** 赔付规则列表（当前租户） */
    @GetMapping("/payout-rules")
    public ResponseEntity<List<ClaimPayoutRuleConfigVO>> listPayoutRules() {
        return ResponseEntity.ok(payoutRuleConfigQueryService.listPayoutRules()
                .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 赔付规则详情 */
    @GetMapping("/payout-rules/{ruleId}")
    public ResponseEntity<ClaimPayoutRuleConfigVO> getPayoutRule(@PathVariable String ruleId) {
        return ResponseEntity.ok(claimConfigWebMapper.toVO(
                payoutRuleConfigQueryService.getPayoutRule(ruleId)));
    }

    /** 删除赔付规则（逻辑删除） */
    @DeleteMapping("/payout-rules/{ruleId}")
    public ResponseEntity<Void> deletePayoutRule(@PathVariable String ruleId) {
        payoutRuleConfigService.deletePayoutRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 快赔规则配置 ====================

    /** 新增/更新快赔规则（ruleId 空=新增） */
    @PostMapping("/quick-pay-rules")
    public ResponseEntity<String> saveQuickPayRule(@RequestBody @Valid ClaimQuickPayRuleConfigDTO dto) {
        return new ResponseEntity<>(quickPayRuleConfigService.saveQuickPayRule(
                claimConfigWebMapper.toRequest(dto)), HttpStatus.CREATED);
    }

    /** 快赔规则列表（当前租户） */
    @GetMapping("/quick-pay-rules")
    public ResponseEntity<List<ClaimQuickPayRuleConfigVO>> listQuickPayRules() {
        return ResponseEntity.ok(quickPayRuleConfigQueryService.listQuickPayRules()
                .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 快赔规则详情 */
    @GetMapping("/quick-pay-rules/{ruleId}")
    public ResponseEntity<ClaimQuickPayRuleConfigVO> getQuickPayRule(@PathVariable String ruleId) {
        return ResponseEntity.ok(claimConfigWebMapper.toVO(
                quickPayRuleConfigQueryService.getQuickPayRule(ruleId)));
    }

    /** 删除快赔规则（逻辑删除） */
    @DeleteMapping("/quick-pay-rules/{ruleId}")
    public ResponseEntity<Void> deleteQuickPayRule(@PathVariable String ruleId) {
        quickPayRuleConfigService.deleteQuickPayRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 单证模板配置 ====================

    /** 新增/更新单证模板（templateId 空=新增） */
    @PostMapping("/document-templates")
    public ResponseEntity<String> saveDocumentTemplate(@RequestBody @Valid ClaimDocumentTemplateConfigDTO dto) {
        return new ResponseEntity<>(documentTemplateConfigService.saveDocumentTemplate(
                claimConfigWebMapper.toRequest(dto)), HttpStatus.CREATED);
    }

    /** 单证模板列表（当前租户） */
    @GetMapping("/document-templates")
    public ResponseEntity<List<ClaimDocumentTemplateConfigVO>> listDocumentTemplates() {
        return ResponseEntity.ok(documentTemplateConfigQueryService.listDocumentTemplates()
                .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 单证模板详情 */
    @GetMapping("/document-templates/{templateId}")
    public ResponseEntity<ClaimDocumentTemplateConfigVO> getDocumentTemplate(@PathVariable String templateId) {
        return ResponseEntity.ok(claimConfigWebMapper.toVO(
                documentTemplateConfigQueryService.getDocumentTemplate(templateId)));
    }

    /** 删除单证模板（逻辑删除） */
    @DeleteMapping("/document-templates/{templateId}")
    public ResponseEntity<Void> deleteDocumentTemplate(@PathVariable String templateId) {
        documentTemplateConfigService.deleteDocumentTemplate(templateId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 时限规则配置 ====================

    /** 新增/更新时限规则（ruleId 空=新增） */
    @PostMapping("/time-limit-rules")
    public ResponseEntity<String> saveTimeLimitRule(@RequestBody @Valid ClaimTimeLimitRuleConfigDTO dto) {
        return new ResponseEntity<>(timeLimitRuleConfigService.saveTimeLimitRule(
                claimConfigWebMapper.toRequest(dto)), HttpStatus.CREATED);
    }

    /** 时限规则列表（当前租户） */
    @GetMapping("/time-limit-rules")
    public ResponseEntity<List<ClaimTimeLimitRuleConfigVO>> listTimeLimitRules() {
        return ResponseEntity.ok(timeLimitRuleConfigQueryService.listTimeLimitRules()
                .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 时限规则详情 */
    @GetMapping("/time-limit-rules/{ruleId}")
    public ResponseEntity<ClaimTimeLimitRuleConfigVO> getTimeLimitRule(@PathVariable String ruleId) {
        return ResponseEntity.ok(claimConfigWebMapper.toVO(
                timeLimitRuleConfigQueryService.getTimeLimitRule(ruleId)));
    }

    /** 删除时限规则（逻辑删除） */
    @DeleteMapping("/time-limit-rules/{ruleId}")
    public ResponseEntity<Void> deleteTimeLimitRule(@PathVariable String ruleId) {
        timeLimitRuleConfigService.deleteTimeLimitRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 医院网络配置 ====================

    /** 新增/更新医院网络（hospitalId 空=新增） */
    @PostMapping("/hospital-networks")
    public ResponseEntity<String> saveHospitalNetwork(@RequestBody @Valid ClaimHospitalNetworkConfigDTO dto) {
        return new ResponseEntity<>(hospitalNetworkConfigService.saveHospitalNetwork(
                claimConfigWebMapper.toRequest(dto)), HttpStatus.CREATED);
    }

    /** 医院网络列表（当前租户） */
    @GetMapping("/hospital-networks")
    public ResponseEntity<List<ClaimHospitalNetworkConfigVO>> listHospitalNetworks() {
        return ResponseEntity.ok(hospitalNetworkConfigQueryService.listHospitals()
                .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 医院网络详情 */
    @GetMapping("/hospital-networks/{hospitalId}")
    public ResponseEntity<ClaimHospitalNetworkConfigVO> getHospitalNetwork(@PathVariable String hospitalId) {
        return ResponseEntity.ok(claimConfigWebMapper.toVO(
                hospitalNetworkConfigQueryService.getHospital(hospitalId)));
    }

    /** 暂停医院协议（ACTIVE → SUSPENDED，暂停期间不参与直赔/赔付比例计算） */
    @PutMapping("/hospital-networks/{hospitalId}/suspend")
    public ResponseEntity<Void> suspendHospital(@PathVariable String hospitalId) {
        hospitalNetworkConfigService.suspendHospital(hospitalId);
        return ResponseEntity.noContent().build();
    }

    /** 恢复医院协议（SUSPENDED → ACTIVE） */
    @PutMapping("/hospital-networks/{hospitalId}/activate")
    public ResponseEntity<Void> activateHospital(@PathVariable String hospitalId) {
        hospitalNetworkConfigService.activateHospital(hospitalId);
        return ResponseEntity.noContent().build();
    }

    /** 终止医院协议（ACTIVE/SUSPENDED → TERMINATED） */
    @PutMapping("/hospital-networks/{hospitalId}/terminate")
    public ResponseEntity<Void> terminateHospital(@PathVariable String hospitalId) {
        hospitalNetworkConfigService.terminateHospital(hospitalId);
        return ResponseEntity.noContent().build();
    }

    /** 删除医院网络（逻辑删除） */
    @DeleteMapping("/hospital-networks/{hospitalId}")
    public ResponseEntity<Void> deleteHospitalNetwork(@PathVariable String hospitalId) {
        hospitalNetworkConfigService.deleteHospitalNetwork(hospitalId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 黑名单配置 ====================

    /** 新增/更新黑名单（blacklistId 空=新增，更新保持生效状态不变） */
    @PostMapping("/blacklists")
    public ResponseEntity<String> saveBlacklist(@RequestBody @Valid ClaimBlacklistConfigDTO dto) {
        return new ResponseEntity<>(blacklistConfigService.saveBlacklist(
                claimConfigWebMapper.toRequest(dto)), HttpStatus.CREATED);
    }

    /** 黑名单列表（当前租户） */
    @GetMapping("/blacklists")
    public ResponseEntity<List<ClaimBlacklistConfigVO>> listBlacklists() {
        return ResponseEntity.ok(blacklistConfigQueryService.listBlacklists()
                .stream().map(claimConfigWebMapper::toVO).toList());
    }

    /** 黑名单详情 */
    @GetMapping("/blacklists/{blacklistId}")
    public ResponseEntity<ClaimBlacklistConfigVO> getBlacklist(@PathVariable String blacklistId) {
        return ResponseEntity.ok(claimConfigWebMapper.toVO(
                blacklistConfigQueryService.getBlacklist(blacklistId)));
    }

    /** 撤销黑名单（ACTIVE → REVOKED） */
    @PostMapping("/blacklists/{blacklistId}/revoke")
    public ResponseEntity<Void> revokeBlacklist(@PathVariable String blacklistId) {
        blacklistConfigService.revokeBlacklist(blacklistId);
        return ResponseEntity.noContent().build();
    }

    /** 删除黑名单（逻辑删除） */
    @DeleteMapping("/blacklists/{blacklistId}")
    public ResponseEntity<Void> deleteBlacklist(@PathVariable String blacklistId) {
        blacklistConfigService.deleteBlacklist(blacklistId);
        return ResponseEntity.noContent().build();
    }
}
