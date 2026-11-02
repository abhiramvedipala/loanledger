package com.loanledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The money state of a loan and the rules for applying a payment to it. Pure, so the API,
 * the simulate endpoint and the seed all post payments the same way.
 *
 * <p>A payment goes to interest due, then principal due, then escrow due. Once the installment
 * is fully paid the due date advances, and anything left is extra principal. A partial payment
 * stops wherever it runs out and leaves the rest due.
 */
public record LoanState(BigDecimal balance, LocalDate nextDueDate, BigDecimal interestDue,
                        BigDecimal principalDue, BigDecimal escrowDue) {

    public record Terms(BigDecimal annualRate, BigDecimal monthlyPayment, BigDecimal monthlyEscrow,
                        LocalDate maturityDate) {
    }

    /** {@code extraPrincipal} is included in {@code principal}; {@code shortfall} is what stays due. */
    public record Allocation(BigDecimal interest, BigDecimal principal, BigDecimal escrow,
                             BigDecimal extraPrincipal, BigDecimal shortfall) {
    }

    public record Posting(Allocation allocation, LoanState after) {
    }

    /** State of a new loan: first installment due one month after origination. */
    public static LoanState opening(BigDecimal principal, LocalDate originationDate, Terms terms) {
        return installment(Amortization.money(principal), originationDate.plusMonths(1), terms);
    }

    private static LoanState installment(BigDecimal balance, LocalDate dueDate, Terms terms) {
        if (balance.signum() == 0) {
            return new LoanState(balance, dueDate, zero(), zero(), zero());
        }
        BigDecimal interest = Amortization.monthlyInterest(balance, terms.annualRate());
        BigDecimal principal = dueDate.isBefore(terms.maturityDate())
                ? terms.monthlyPayment().subtract(interest).max(zero()).min(balance)
                : balance; // the last installment absorbs rounding, same as the schedule
        return new LoanState(balance, dueDate, interest, principal, terms.monthlyEscrow());
    }

    /** The most this loan can accept: everything due plus the rest of the principal. */
    public BigDecimal payoffAmount() {
        return balance.add(interestDue).add(escrowDue);
    }

    public boolean paidOff() {
        return balance.signum() == 0 && escrowDue.signum() == 0;
    }

    public Posting apply(BigDecimal payment, Terms terms) {
        BigDecimal amount = Amortization.money(payment);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment must be positive");
        }
        if (amount.compareTo(payoffAmount()) > 0) {
            throw new IllegalArgumentException("Payment exceeds payoff amount " + payoffAmount());
        }
        BigDecimal left = amount;
        BigDecimal interest = left.min(interestDue);
        left = left.subtract(interest);
        BigDecimal principal = left.min(principalDue);
        left = left.subtract(principal);
        BigDecimal escrow = left.min(escrowDue);
        BigDecimal extra = left.subtract(escrow); // the payoff check guarantees this fits in the balance

        BigDecimal newBalance = balance.subtract(principal).subtract(extra);
        BigDecimal interestLeft = interestDue.subtract(interest);
        BigDecimal principalLeft = principalDue.subtract(principal);
        BigDecimal escrowLeft = escrowDue.subtract(escrow);
        BigDecimal shortfall = interestLeft.add(principalLeft).add(escrowLeft);

        LoanState after = shortfall.signum() == 0
                ? installment(newBalance, nextDueDate.plusMonths(1), terms)
                : new LoanState(newBalance, nextDueDate, interestLeft, principalLeft, escrowLeft);
        return new Posting(new Allocation(interest, principal.add(extra), escrow, extra, shortfall), after);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
