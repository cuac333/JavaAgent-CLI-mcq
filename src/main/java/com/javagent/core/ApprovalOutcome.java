package com.javagent.core;

public record ApprovalOutcome(
        boolean approved,
        boolean fromCache,
        String reason
) {
    public static ApprovalOutcome approved(String reason) {
        return new ApprovalOutcome(true, false, reason);
    }

    public static ApprovalOutcome cachedApproved(String reason) {
        return new ApprovalOutcome(true, true, reason);
    }

    public static ApprovalOutcome denied(String reason) {
        return new ApprovalOutcome(false, false, reason);
    }

    public static ApprovalOutcome cachedDenied(String reason) {
        return new ApprovalOutcome(false, true, reason);
    }
}
