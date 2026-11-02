package com.loanledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "escrow_accounts")
public class EscrowAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id")
    private Long loanId;

    private BigDecimal balance;
    private BigDecimal monthlyAmount;
    private BigDecimal annualTax;
    private BigDecimal annualInsurance;

    protected EscrowAccount() {
    }

    public EscrowAccount(Long loanId, BigDecimal annualTax, BigDecimal annualInsurance) {
        this.loanId = loanId;
        this.balance = BigDecimal.ZERO.setScale(2);
        this.monthlyAmount = Amortization.monthlyEscrow(annualTax, annualInsurance);
        this.annualTax = annualTax;
        this.annualInsurance = annualInsurance;
    }

    public void deposit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }

    public BigDecimal getAnnualTax() {
        return annualTax;
    }

    public BigDecimal getAnnualInsurance() {
        return annualInsurance;
    }
}
