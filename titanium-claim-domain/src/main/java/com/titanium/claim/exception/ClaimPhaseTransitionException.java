package com.titanium.claim.exception;

import com.titanium.claim.valueobject.ClaimId;
import com.titanium.metadata.enums.claim.ClaimPhase;
import com.titanium.metadata.exception.IllegalStateTransitionException;

/**
 * 理赔阶段流转非法异常
 * <p>
 * 当理赔案件的处理阶段（ClaimPhase）流转违反规则时抛出，如未查勘直接定损、未定损直接核赔等。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class ClaimPhaseTransitionException extends IllegalStateTransitionException {

    public ClaimPhaseTransitionException(ClaimId claimId, ClaimPhase from, ClaimPhase to) {
        super("理赔案件阶段", claimId.value(), from.name(), to.name());
    }
}
