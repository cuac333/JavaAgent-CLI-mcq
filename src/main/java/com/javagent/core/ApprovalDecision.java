package com.javagent.core;

public enum ApprovalDecision {
    APPROVED,
    DENIED,
    CANCELLED;

    public boolean isApproved() {
        return this == APPROVED;
    }
}
