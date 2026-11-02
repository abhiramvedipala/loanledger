package com.loanledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LoanStateTest {

    private static final LocalDate ORIGINATION = LocalDate.of(2025, 1, 1);
    private static final LocalDate FIRST_DUE = LocalDate.of(2025, 2, 1);
    private static final LoanState.Terms TERMS = new LoanState.Terms(new BigDecimal("6.5"), new BigDecimal("1896.20"),
            new BigDecimal("300.00"), ORIGINATION.plusMonths(360));

    private final LoanState opening = LoanState.opening(new BigDecimal("300000"), ORIGINATION, TERMS);

    @Test
    void exactPaymentCoversInstallmentAndAdvancesDueDate() {
        LoanState.Posting p = opening.apply(new BigDecimal("2196.20"), TERMS);
        assertAllocation(p.allocation(), "1625.00", "271.20", "300.00", "0.00", "0.00");
        assertThat(p.after().balance()).isEqualByComparingTo("299728.80");
        assertThat(p.after().nextDueDate()).isEqualTo(FIRST_DUE.plusMonths(1));
        assertThat(p.after().interestDue()).isEqualByComparingTo("1623.53");
    }

    @Test
    void partialStoppingInInterestLeavesBalanceAndDueDate() {
        LoanState.Posting p = opening.apply(new BigDecimal("1000.00"), TERMS);
        assertAllocation(p.allocation(), "1000.00", "0.00", "0.00", "0.00", "1196.20");
        assertThat(p.after().balance()).isEqualByComparingTo("300000.00");
        assertThat(p.after().nextDueDate()).isEqualTo(FIRST_DUE);
        assertThat(p.after().interestDue()).isEqualByComparingTo("625.00");
    }

    @Test
    void partialStoppingInPrincipalPaysNoEscrow() {
        LoanState.Posting p = opening.apply(new BigDecimal("1700.00"), TERMS);
        assertAllocation(p.allocation(), "1625.00", "75.00", "0.00", "0.00", "496.20");
        assertThat(p.after().balance()).isEqualByComparingTo("299925.00");
    }

    @Test
    void partialStoppingInEscrowLeavesEscrowDue() {
        LoanState.Posting p = opening.apply(new BigDecimal("2000.00"), TERMS);
        assertAllocation(p.allocation(), "1625.00", "271.20", "103.80", "0.00", "196.20");
        assertThat(p.after().escrowDue()).isEqualByComparingTo("196.20");
        assertThat(p.after().nextDueDate()).isEqualTo(FIRST_DUE);
    }

    @Test
    void twoPartialsCompleteTheInstallment() {
        LoanState afterFirst = opening.apply(new BigDecimal("1000.00"), TERMS).after();
        LoanState.Posting second = afterFirst.apply(new BigDecimal("1196.20"), TERMS);
        assertThat(second.allocation().shortfall()).isEqualByComparingTo("0.00");
        assertThat(second.after().balance()).isEqualByComparingTo("299728.80");
        assertThat(second.after().nextDueDate()).isEqualTo(FIRST_DUE.plusMonths(1));
    }

    @Test
    void overpaymentGoesToPrincipalAndLowersNextInterest() {
        LoanState.Posting p = opening.apply(new BigDecimal("2696.20"), TERMS);
        assertAllocation(p.allocation(), "1625.00", "771.20", "300.00", "500.00", "0.00");
        assertThat(p.after().balance()).isEqualByComparingTo("299228.80");
        assertThat(p.after().interestDue()).isEqualByComparingTo("1620.82");
    }

    @Test
    void payoffAmountClearsTheLoan() {
        LoanState.Posting p = opening.apply(opening.payoffAmount(), TERMS);
        assertThat(p.after().balance()).isEqualByComparingTo("0.00");
        assertThat(p.after().paidOff()).isTrue();
    }

    @Test
    void paymentAbovePayoffIsRejected() {
        BigDecimal tooMuch = opening.payoffAmount().add(new BigDecimal("0.01"));
        assertThatThrownBy(() -> opening.apply(tooMuch, TERMS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositivePaymentIsRejected() {
        assertThatThrownBy(() -> opening.apply(BigDecimal.ZERO, TERMS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payingEveryInstallmentEndsExactlyAtMaturityLikeTheSchedule() {
        LoanState state = opening;
        BigDecimal interest = BigDecimal.ZERO;
        for (int n = 0; n < 360; n++) {
            BigDecimal due = state.interestDue().add(state.principalDue()).add(state.escrowDue());
            LoanState.Posting p = state.apply(due, TERMS);
            interest = interest.add(p.allocation().interest());
            state = p.after();
        }
        BigDecimal scheduled = Amortization.schedule(new BigDecimal("300000"), new BigDecimal("6.5"), 360, ORIGINATION)
                .stream().map(Amortization.Row::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(state.paidOff()).isTrue();
        assertThat(interest).isEqualByComparingTo(scheduled);
    }

    private static void assertAllocation(LoanState.Allocation a, String interest, String principal, String escrow,
                                         String extra, String shortfall) {
        assertThat(a.interest()).isEqualByComparingTo(interest);
        assertThat(a.principal()).isEqualByComparingTo(principal);
        assertThat(a.escrow()).isEqualByComparingTo(escrow);
        assertThat(a.extraPrincipal()).isEqualByComparingTo(extra);
        assertThat(a.shortfall()).isEqualByComparingTo(shortfall);
    }
}
