package com.loanledger.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Fixed-rate amortization. Rates are annual percentages: 6.5 means 6.5%. */
public final class Amortization {

    private static final BigDecimal MONTHS_TIMES_PERCENT = BigDecimal.valueOf(1200);

    public record Row(int number, LocalDate dueDate, BigDecimal payment, BigDecimal principal,
                      BigDecimal interest, BigDecimal balance) {
    }

    private Amortization() {
    }

    /** Level monthly principal-and-interest payment, P * r / (1 - (1 + r)^-n), to the cent. */
    public static BigDecimal monthlyPayment(BigDecimal principal, BigDecimal annualRate, int termMonths) {
        if (annualRate.signum() == 0) {
            return money(principal.divide(BigDecimal.valueOf(termMonths), MathContext.DECIMAL128));
        }
        BigDecimal r = annualRate.divide(MONTHS_TIMES_PERCENT, MathContext.DECIMAL128);
        BigDecimal growth = BigDecimal.ONE.add(r).pow(termMonths, MathContext.DECIMAL128);
        BigDecimal factor = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(growth, MathContext.DECIMAL128));
        return money(principal.multiply(r).divide(factor, MathContext.DECIMAL128));
    }

    /** One month's interest on a balance, to the cent. */
    public static BigDecimal monthlyInterest(BigDecimal balance, BigDecimal annualRate) {
        return money(balance.multiply(annualRate).divide(MONTHS_TIMES_PERCENT, MathContext.DECIMAL128));
    }

    /** Monthly escrow collection that covers a year of tax and insurance. */
    public static BigDecimal monthlyEscrow(BigDecimal annualTax, BigDecimal annualInsurance) {
        return money(annualTax.add(annualInsurance).divide(BigDecimal.valueOf(12), MathContext.DECIMAL128));
    }

    /** Contractual schedule from origination terms. The last payment absorbs accumulated rounding. */
    public static List<Row> schedule(BigDecimal principal, BigDecimal annualRate, int termMonths, LocalDate originationDate) {
        BigDecimal level = monthlyPayment(principal, annualRate, termMonths);
        BigDecimal balance = money(principal);
        List<Row> rows = new ArrayList<>(termMonths);
        for (int n = 1; n <= termMonths; n++) {
            BigDecimal interest = monthlyInterest(balance, annualRate);
            BigDecimal paydown = n == termMonths ? balance : level.subtract(interest).min(balance);
            balance = balance.subtract(paydown);
            rows.add(new Row(n, originationDate.plusMonths(n), paydown.add(interest), paydown, interest, balance));
        }
        return rows;
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
