package com.loanledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id")
    private Long loanId;

    private LocalDate paymentDate;
    private BigDecimal amount;
    private BigDecimal principalAmount;
    private BigDecimal interestAmount;
    private BigDecimal escrowAmount;

    protected Payment() {
    }

    public Payment(Long loanId, LocalDate paymentDate, LoanState.Allocation allocation) {
        this.loanId = loanId;
        this.paymentDate = paymentDate;
        this.principalAmount = allocation.principal();
        this.interestAmount = allocation.interest();
        this.escrowAmount = allocation.escrow();
        this.amount = principalAmount.add(interestAmount).add(escrowAmount);
    }

    public Long getId() {
        return id;
    }

    public Long getLoanId() {
        return loanId;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public BigDecimal getInterestAmount() {
        return interestAmount;
    }

    public BigDecimal getEscrowAmount() {
        return escrowAmount;
    }
}
