package com.titanium.claim.valueobject;

import com.titanium.claim.common.exception.InvalidClaimAmountException;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Getter
@Accessors(fluent = true)
public class ClaimAmount {
    private final BigDecimal value;

    private ClaimAmount(BigDecimal value) {
        this.value = value;
    }

    public static ClaimAmount of(BigDecimal value) {
        validate(value);
        return new ClaimAmount(value);
    }

    public static ClaimAmount of(String value) {
        return of(new BigDecimal(value));
    }

    public static ClaimAmount of(double value) {
        return of(new BigDecimal(value));
    }

    private static void validate(BigDecimal value) {
        if (value == null) {
            throw new InvalidClaimAmountException();
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidClaimAmountException();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClaimAmount that = (ClaimAmount) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}