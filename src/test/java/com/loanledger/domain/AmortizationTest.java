package com.loanledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AmortizationTest {

    private static final BigDecimal PRINCIPAL = new BigDecimal("300000");
    private static final BigDecimal RATE = new BigDecimal("6.5");
    private static final LocalDate ORIGINATION = LocalDate.of(2025, 1, 1);

    private final List<Amortization.Row> schedule = Amortization.schedule(PRINCIPAL, RATE, 360, ORIGINATION);

    @Test
    void knownMonthlyPayment() {
        assertThat(Amortization.monthlyPayment(PRINCIPAL, RATE, 360)).isEqualByComparingTo("1896.20");
    }

    @Test
    void knownFirstTwoRows() {
        assertRow(schedule.get(0), "1625.00", "271.20", "299728.80");
        assertRow(schedule.get(1), "1623.53", "272.67", "299456.13");
        assertThat(schedule.get(0).dueDate()).isEqualTo(LocalDate.of(2025, 2, 1));
    }

    @Test
    void scheduleFullyRepaysAndTotalInterestMatchesKnownValue() {
        assertThat(schedule).hasSize(360);
        assertThat(schedule.get(359).balance()).isEqualByComparingTo("0.00");
        BigDecimal principal = schedule.stream().map(Amortization.Row::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interest = schedule.stream().map(Amortization.Row::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(principal).isEqualByComparingTo("300000.00");
        // Unrounded the total is 382,633.47. Paying 1,896.20 instead of 1,896.2041 leaves a little more
        // balance each month, so cent rounding adds 3.24 and the last payment absorbs the residual.
        assertThat(interest).isEqualByComparingTo("382636.71");
        assertThat(schedule.get(359).payment()).isEqualByComparingTo("1900.91");
    }

    @Test
    void zeroRateSplitsPrincipalEvenly() {
        assertThat(Amortization.monthlyPayment(new BigDecimal("12000"), BigDecimal.ZERO, 12)).isEqualByComparingTo("1000.00");
    }

    private static void assertRow(Amortization.Row row, String interest, String principal, String balance) {
        assertThat(row.interest()).isEqualByComparingTo(interest);
        assertThat(row.principal()).isEqualByComparingTo(principal);
        assertThat(row.balance()).isEqualByComparingTo(balance);
    }
}
