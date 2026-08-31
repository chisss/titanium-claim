package com.titanium.claim.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

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
        when(repository.findByTenantId("tenant-a")).thenReturn(List.of());

        assertTrue(service.getAllClaimSummaries("tenant-a").isEmpty());

        verify(repository).findByTenantId("tenant-a");
    }
}
