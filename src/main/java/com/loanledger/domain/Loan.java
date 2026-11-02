package com.loanledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Generated
    @Column(insertable = false, updatable = false)
    private String loanNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrower_id")
    private Borrower borrower;

    private BigDecimal principal;
    private BigDecimal annualRate;
    private int termMonths;
    private LocalDate originationDate;

    @Enumerated(EnumType.STRING)
    private LoanStatus status;

    private BigDecimal monthlyPayment;
    private BigDecimal currentBalance;
    private LocalDate nextDueDate;
    private BigDecimal interestDue;
    private BigDecimal principalDue;
    private BigDecimal escrowDue;

    /** Serialises money changes: of two concurrent payments on a loan, the second gets a 409. */
    @Version
    private long version;

    protected Loan() {
    }

    public Loan(Borrower borrower, BigDecimal principal, BigDecimal annualRate, int termMonths,
                LocalDate originationDate, BigDecimal monthlyEscrow) {
        this.borrower = borrower;
        this.principal = principal;
        this.annualRate = annualRate;
        this.termMonths = termMonths;
        this.originationDate = originationDate;
        this.status = LoanStatus.CURRENT;
        this.monthlyPayment = Amortization.monthlyPayment(principal, annualRate, termMonths);
        setState(LoanState.opening(principal, originationDate, terms(monthlyEscrow)));
    }

    public LoanState.Terms terms(BigDecimal monthlyEscrow) {
        return new LoanState.Terms(annualRate, monthlyPayment, monthlyEscrow, originationDate.plusMonths(termMonths));
    }

    public LoanState state() {
        return new LoanState(currentBalance, nextDueDate, interestDue, principalDue, escrowDue);
    }

    public void setState(LoanState state) {
        currentBalance = state.balance();
        nextDueDate = state.nextDueDate();
        interestDue = state.interestDue();
        principalDue = state.principalDue();
        escrowDue = state.escrowDue();
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getLoanNumber() {
        return loanNumber;
    }

    public Borrower getBorrower() {
        return borrower;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public LocalDate getOriginationDate() {
        return originationDate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public BigDecimal getMonthlyPayment() {
        return monthlyPayment;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public BigDecimal getInterestDue() {
        return interestDue;
    }

    public BigDecimal getPrincipalDue() {
        return principalDue;
    }

    public BigDecimal getEscrowDue() {
        return escrowDue;
    }
}
