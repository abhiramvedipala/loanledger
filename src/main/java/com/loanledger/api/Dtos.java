package com.loanledger.api;

import com.loanledger.domain.Amortization;
import com.loanledger.domain.EscrowAccount;
import com.loanledger.domain.Loan;
import com.loanledger.domain.LoanState;
import com.loanledger.domain.LoanStatus;
import com.loanledger.domain.LossMitigationCase;
import com.loanledger.domain.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;

/** Request and response bodies for /api/v1. */
public final class Dtos {

    private Dtos() {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    public record BorrowerRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email) {
    }

    public record CreateLoanRequest(
            @NotNull @Valid BorrowerRequest borrower,
            @NotNull @DecimalMin("1000.00") @Digits(integer = 12, fraction = 2) BigDecimal principal,
            @NotNull @DecimalMin("0") @DecimalMax("30") @Digits(integer = 2, fraction = 3) BigDecimal annualRate,
            @Min(1) @Max(600) int termMonths,
            @NotNull LocalDate originationDate,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal annualTax,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal annualInsurance) {
    }

    public record EscrowResponse(BigDecimal balance, BigDecimal monthlyAmount, BigDecimal annualTax,
                                 BigDecimal annualInsurance) {
    }

    /** {@code escrow} is only filled on single-loan reads, to keep the list query to one join. */
    public record LoanResponse(long id, String loanNumber, String borrowerName, String borrowerEmail,
                               BigDecimal principal, BigDecimal annualRate, int termMonths, LocalDate originationDate,
                               LoanStatus status, BigDecimal monthlyPayment, BigDecimal currentBalance,
                               LocalDate nextDueDate, BigDecimal interestDue, BigDecimal principalDue,
                               BigDecimal escrowDue, EscrowResponse escrow) {
        public static LoanResponse of(Loan l, EscrowAccount e) {
            return new LoanResponse(l.getId(), l.getLoanNumber(),
                    l.getBorrower().getFirstName() + " " + l.getBorrower().getLastName(), l.getBorrower().getEmail(),
                    l.getPrincipal(), l.getAnnualRate(), l.getTermMonths(), l.getOriginationDate(), l.getStatus(),
                    l.getMonthlyPayment(), l.getCurrentBalance(), l.getNextDueDate(), l.getInterestDue(),
                    l.getPrincipalDue(), l.getEscrowDue(), e == null ? null
                    : new EscrowResponse(e.getBalance(), e.getMonthlyAmount(), e.getAnnualTax(), e.getAnnualInsurance()));
        }
    }

    public record ScheduleResponse(long loanId, BigDecimal monthlyPayment, BigDecimal totalInterest,
                                   BigDecimal totalPaid, List<Amortization.Row> rows) {
        public static ScheduleResponse of(long loanId, List<Amortization.Row> rows) {
            BigDecimal interest = rows.stream().map(Amortization.Row::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = rows.stream().map(Amortization.Row::payment).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ScheduleResponse(loanId, rows.get(0).payment(), interest, paid, rows);
        }
    }

    public record PaymentRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull @PastOrPresent LocalDate paymentDate) {
    }

    /** Result of applying a payment; {@code paymentId} is null when simulated. */
    public record PostingResponse(Long paymentId, long loanId, LocalDate paymentDate, BigDecimal amount,
                                  BigDecimal interest, BigDecimal principal, BigDecimal escrow,
                                  BigDecimal extraPrincipal, BigDecimal shortfall,
                                  BigDecimal balanceAfter, LocalDate nextDueDateAfter) {
        public static PostingResponse of(Long paymentId, long loanId, PaymentRequest request, LoanState.Posting posting) {
            LoanState.Allocation a = posting.allocation();
            return new PostingResponse(paymentId, loanId, request.paymentDate(),
                    a.interest().add(a.principal()).add(a.escrow()), a.interest(), a.principal(), a.escrow(),
                    a.extraPrincipal(), a.shortfall(), posting.after().balance(), posting.after().nextDueDate());
        }
    }

    public record PaymentResponse(long id, long loanId, LocalDate paymentDate, BigDecimal amount,
                                  BigDecimal interest, BigDecimal principal, BigDecimal escrow) {
        public static PaymentResponse of(Payment p) {
            return new PaymentResponse(p.getId(), p.getLoanId(), p.getPaymentDate(), p.getAmount(),
                    p.getInterestAmount(), p.getPrincipalAmount(), p.getEscrowAmount());
        }
    }

    public record OpenCaseRequest(@Size(max = 2000) String notes) {
    }

    public record UpdateCaseRequest(LoanStatus status, @Size(max = 2000) String notes) {
    }

    public record CaseResponse(long id, long loanId, LoanStatus status, String notes,
                               Instant openedAt, Instant updatedAt, Instant resolvedAt) {
        public static CaseResponse of(LossMitigationCase c) {
            return new CaseResponse(c.getId(), c.getLoanId(), c.getStatus(), c.getNotes(),
                    c.getOpenedAt(), c.getUpdatedAt(), c.getResolvedAt());
        }
    }
}
