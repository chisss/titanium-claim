package com.titanium.claim.valueobject;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.UUID;

@Getter
@Accessors(fluent = true)
public class ClaimId {
    private final String value;

    private ClaimId(String value) {
        this.value = value;
    }

    public static ClaimId generate() {
        return new ClaimId(UUID.randomUUID().toString());
    }

    public static ClaimId of(String value) {
        return new ClaimId(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClaimId claimId = (ClaimId) o;
        return value.equals(claimId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}