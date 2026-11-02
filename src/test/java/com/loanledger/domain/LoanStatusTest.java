package com.loanledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoanStatusTest {

    @ParameterizedTest
    @CsvSource({"CURRENT,DELINQUENT", "DELINQUENT,FORBEARANCE", "DELINQUENT,MODIFICATION",
            "FORBEARANCE,RESOLVED", "MODIFICATION,RESOLVED"})
    void legalTransitions(LoanStatus from, LoanStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"CURRENT,FORBEARANCE", "CURRENT,RESOLVED", "DELINQUENT,CURRENT", "DELINQUENT,RESOLVED",
            "FORBEARANCE,MODIFICATION", "MODIFICATION,DELINQUENT", "RESOLVED,CURRENT", "CURRENT,CURRENT"})
    void illegalTransitions(LoanStatus from, LoanStatus to) {
        assertThat(from.canMoveTo(to)).isFalse();
    }
}
