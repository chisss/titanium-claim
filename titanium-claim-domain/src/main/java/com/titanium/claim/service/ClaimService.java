package com.titanium.claim.service;

import com.titanium.claim.common.exception.InvalidClaimAmountException;
import com.titanium.claim.common.exception.InvalidClaimStatusException;
import com.titanium.claim.enums.ClaimStatus;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.metadata.enums.claim.ClaimEnum;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class ClaimService {

    /**
     * 验证理赔金额是否有效
     * @param amount 理赔金额
     */
    public void validateClaimAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidClaimAmountException();
        }
    }

    /**
     * 验证理赔状态是否有效
     * @param status 理赔状态
     */
    public void validateClaimStatus(String status) {
        try {
            ClaimStatus.fromCode(status);
        } catch (Exception e) {
            throw new InvalidClaimStatusException();
        }
    }

    /**
     * 生成理赔编号
     * @return 理赔编号
     */
    public String generateClaimNumber() {
        LocalDateTime now = LocalDateTime.now();
        return String.format("CLAIM-%04d%02d%02d-%06d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                (int) (Math.random() * 1000000));
    }

    /**
     * 检查理赔是否在保险范围内
     * @param claimType 理赔类型
     * @param claimAmount 理赔金额
     * @param policyCoverage 保单覆盖范围
     * @param policyLimit 保单限额
     * @return 是否在保险范围内
     */
    public boolean isClaimInCoverage(ClaimEnum.ClaimType claimType, ClaimAmount claimAmount,
                                   String policyCoverage, BigDecimal policyLimit) {
        // 这里可以添加更复杂的业务逻辑
        // 示例：检查理赔类型是否在保单覆盖范围内，且金额不超过保单限额
        if (policyCoverage != null && claimType != null && policyCoverage.contains(claimType.getCode())) {
            return claimAmount.value().compareTo(policyLimit) <= 0;
        }
        return false;
    }
}