package com.titanium.claim.web.controller.config;

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
import com.titanium.claim.application.command.config.HospitalNetworkConfigService;
import com.titanium.claim.application.query.config.BlacklistConfigQueryService;
import com.titanium.claim.application.query.config.HospitalNetworkConfigQueryService;
import com.titanium.claim.web.dto.config.ClaimBlacklistConfigDTO;
import com.titanium.claim.web.dto.config.ClaimHospitalNetworkConfigDTO;
import com.titanium.claim.web.mapper.ClaimConfigWebMapper;
import com.titanium.claim.web.response.config.ClaimBlacklistConfigVO;
import com.titanium.claim.web.response.config.ClaimHospitalNetworkConfigVO;
import com.titanium.metadata.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 理赔医院网络与黑名单配置控制器（管理后台 HTTP 入口）
 * <p>
 * 路径 {@code /web/v1/config}（与拆分前逐字一致）。覆盖医院网络（含暂停／恢复／终止的协议状态流转）
 * 与黑名单（含撤销）两个配置主题。Controller 零业务逻辑。
 * </p>
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/web/v1/config")
public class ClaimNetworkConfigController {

    private final HospitalNetworkConfigService     hospitalNetworkConfigService;
    private final BlacklistConfigService           blacklistConfigService;
    private final HospitalNetworkConfigQueryService hospitalNetworkConfigQueryService;
    private final BlacklistConfigQueryService      blacklistConfigQueryService;
    private final ClaimConfigWebMapper             claimConfigWebMapper;

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
