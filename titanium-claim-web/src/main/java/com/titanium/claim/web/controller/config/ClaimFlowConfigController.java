package com.titanium.claim.web.controller.config;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.claim.application.command.config.DocumentTemplateConfigService;
import com.titanium.claim.application.command.config.FlowTemplateConfigService;
import com.titanium.claim.application.query.config.DocumentTemplateConfigQueryService;
import com.titanium.claim.application.query.config.FlowTemplateConfigQueryService;
import com.titanium.claim.web.dto.config.ClaimDocumentTemplateConfigDTO;
import com.titanium.claim.web.dto.config.ClaimFlowTemplateConfigDTO;
import com.titanium.claim.web.mapper.ClaimConfigWebMapper;
import com.titanium.claim.web.response.config.ClaimDocumentTemplateConfigVO;
import com.titanium.claim.web.response.config.ClaimFlowTemplateConfigVO;
import com.titanium.metadata.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 理赔流程与单证模板配置控制器（管理后台 HTTP 入口）
 * <p>
 * 路径 {@code /web/v1/config}（与拆分前逐字一致）。覆盖流程模板、单证模板两个配置主题；入参 web 层
 * {@code XxxDTO}、出参 {@code XxxVO}，经 {@link ClaimConfigWebMapper} 与应用层读写服务交互。
 * Controller 零业务逻辑。
 * </p>
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/web/v1/config")
public class ClaimFlowConfigController {

    private final FlowTemplateConfigService        flowTemplateConfigService;
    private final DocumentTemplateConfigService    documentTemplateConfigService;
    private final FlowTemplateConfigQueryService   flowTemplateConfigQueryService;
    private final DocumentTemplateConfigQueryService documentTemplateConfigQueryService;
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
}
