package com.titanium.claim.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.claim.common.enums.ClaimStatus;
import com.titanium.claim.query.mapper.ClaimQueryResultMapper;
import com.titanium.claim.query.repository.ClaimViewRepository;

class ClaimQueryServiceImplTest {

    @Test
    void shouldRestrictPolicyQueryToTenant() {
        ClaimViewRepository repository = mock(ClaimViewRepository.class);
        ClaimQueryServiceImpl service = new ClaimQueryServiceImpl(repository, mock(ClaimQueryResultMapper.class));
        when(repository.findByPolicyIdAndTenantId("policy-1", "tenant-b")).thenReturn(List.of());

        assertTrue(service.getClaimSummariesByPolicyId("policy-1", "tenant-b").isEmpty());

        verify(repository).findByPolicyIdAndTenantId("policy-1", "tenant-b");
    }

    @Test
    void shouldRestrictAllClaimsQueryToTenant() {
        ClaimViewRepository repository = mock(ClaimViewRepository.class);
        ClaimQueryServiceImpl service = new ClaimQueryServiceImpl(repository, mock(ClaimQueryResultMapper.class));
        when(repository.findByTenantIdOrderByCreateTimeDescClaimIdDesc("tenant-a")).thenReturn(List.of());

        assertTrue(service.getAllClaimSummaries("tenant-a").isEmpty());

        verify(repository).findByTenantIdOrderByCreateTimeDescClaimIdDesc("tenant-a");
    }

    /** NCD 反算取数（G11/g11-03）：VIN 与标的ID同时提供时以 VIN 优先，不碰标的ID分支。 */
    @Test
    void shouldPreferVinOverSubjectIdWhenResolvingClaimHistory() {
        ClaimViewRepository repository = mock(ClaimViewRepository.class);
        ClaimQueryServiceImpl service = new ClaimQueryServiceImpl(repository, mock(ClaimQueryResultMapper.class));
        LocalDateTime from = LocalDateTime.of(2025, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 1, 0, 0);
        when(repository.findByTenantIdAndVinAndIncidentDateGreaterThanEqualAndIncidentDateLessThanAndStatusNot(
                "tenant-a", "VIN-1", from, to, ClaimStatus.REJECTED)).thenReturn(List.of());

        assertTrue(service.getClaimSummariesBySubject("VIN-1", "SUBJ-1", from, to, "tenant-a").isEmpty());

        verify(repository).findByTenantIdAndVinAndIncidentDateGreaterThanEqualAndIncidentDateLessThanAndStatusNot(
                "tenant-a", "VIN-1", from, to, ClaimStatus.REJECTED);
        verify(repository, never())
                .findByTenantIdAndSubjectIdAndIncidentDateGreaterThanEqualAndIncidentDateLessThanAndStatusNot(
                        any(), any(), any(), any(), any());
    }

    /** NCD 反算取数（G11/g11-03）：VIN 缺失时退化到保单内标的 subjectId 兜底。 */
    @Test
    void shouldFallBackToSubjectIdWhenVinAbsent() {
        ClaimViewRepository repository = mock(ClaimViewRepository.class);
        ClaimQueryServiceImpl service = new ClaimQueryServiceImpl(repository, mock(ClaimQueryResultMapper.class));
        LocalDateTime from = LocalDateTime.of(2025, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 1, 0, 0);
        when(repository.findByTenantIdAndSubjectIdAndIncidentDateGreaterThanEqualAndIncidentDateLessThanAndStatusNot(
                "tenant-a", "SUBJ-1", from, to, ClaimStatus.REJECTED)).thenReturn(List.of());

        assertTrue(service.getClaimSummariesBySubject(null, "SUBJ-1", from, to, "tenant-a").isEmpty());

        verify(repository).findByTenantIdAndSubjectIdAndIncidentDateGreaterThanEqualAndIncidentDateLessThanAndStatusNot(
                "tenant-a", "SUBJ-1", from, to, ClaimStatus.REJECTED);
    }

    /**
     * NCD 反算取数（G11/g11-03）：VIN 与标的ID皆空属调用方错误，必须显式抛异常。
     * <p>
     * 判据要旨：缺键不得静默返回空列表——空列表是「查无赔案」的合法事实，两者混同会让
     * NCD 反算把「查不到」误当「无赔款」。
     * </p>
     */
    @Test
    void shouldRejectHistoryQueryWithoutAnySubjectIdentifier() {
        ClaimViewRepository repository = mock(ClaimViewRepository.class);
        ClaimQueryServiceImpl service = new ClaimQueryServiceImpl(repository, mock(ClaimQueryResultMapper.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.getClaimSummariesBySubject(" ", null, LocalDateTime.now(), LocalDateTime.now(), "tenant-a"));
        assertThrows(IllegalArgumentException.class,
                () -> service.getClaimSummariesBySubject(null, "", LocalDateTime.now(), LocalDateTime.now(), "tenant-a"));
    }
}
