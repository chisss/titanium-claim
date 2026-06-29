package com.titanium.claim.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.claim.api.dto.ClaimRequestDTO;
import com.titanium.claim.api.dto.ClaimResponseDTO;

import jakarta.validation.Valid;

@FeignClient(name = "titanium-claim", path = "/api/v1/claims")
public interface ClaimClient {

    @PostMapping
    String createClaim(@RequestBody @Valid ClaimRequestDTO requestDTO);

    @GetMapping("/{claimId}")
    ClaimResponseDTO getClaim(@PathVariable String claimId);

    @PutMapping("/{claimId}/status")
    void updateClaimStatus(@PathVariable String claimId, @RequestParam String status);

    @GetMapping("/customer/{customerId}")
    ClaimResponseDTO[] getClaimsByCustomerId(@PathVariable String customerId);
}
