package com.titanium.claim.valueobject;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public class PolicyId {
    private final String value;

    private PolicyId(String value) {
        this.value = value;
    }

    public static PolicyId of(String value) {
        return new PolicyId(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PolicyId policyId = (PolicyId) o;
        return value.equals(policyId.value);
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
