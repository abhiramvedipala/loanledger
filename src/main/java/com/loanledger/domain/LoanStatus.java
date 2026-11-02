package com.loanledger.domain;

import java.util.Set;

/** Loss-mitigation state machine: CURRENT -> DELINQUENT -> (FORBEARANCE | MODIFICATION) -> RESOLVED. */
public enum LoanStatus {
    CURRENT, DELINQUENT, FORBEARANCE, MODIFICATION, RESOLVED;

    public Set<LoanStatus> next() {
        return switch (this) {
            case CURRENT -> Set.of(DELINQUENT);
            case DELINQUENT -> Set.of(FORBEARANCE, MODIFICATION);
            case FORBEARANCE, MODIFICATION -> Set.of(RESOLVED);
            case RESOLVED -> Set.of();
        };
    }

    public boolean canMoveTo(LoanStatus target) {
        return next().contains(target);
    }
}
