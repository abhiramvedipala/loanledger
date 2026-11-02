package com.loanledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "loss_mitigation_cases")
public class LossMitigationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id")
    private Long loanId;

    @Enumerated(EnumType.STRING)
    private LoanStatus status;

    private String notes;
    private Instant openedAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    protected LossMitigationCase() {
    }

    public LossMitigationCase(Long loanId, LoanStatus status, String notes) {
        this.loanId = loanId;
        this.status = status;
        this.notes = notes;
        this.openedAt = Instant.now();
        this.updatedAt = openedAt;
    }

    public void moveTo(LoanStatus target) {
        status = target;
        updatedAt = Instant.now();
        if (target == LoanStatus.RESOLVED) {
            resolvedAt = updatedAt;
        }
    }

    public void setNotes(String notes) {
        this.notes = notes;
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getLoanId() {
        return loanId;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
