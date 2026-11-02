package com.loanledger.service;

import com.loanledger.api.Dtos.BorrowerRequest;
import com.loanledger.api.Dtos.CreateLoanRequest;
import com.loanledger.api.Dtos.LoanResponse;
import com.loanledger.api.Dtos.PaymentRequest;
import com.loanledger.api.Dtos.PaymentResponse;
import com.loanledger.api.Dtos.PostingResponse;
import com.loanledger.api.Dtos.ScheduleResponse;
import com.loanledger.domain.Amortization;
import com.loanledger.domain.Borrower;
import com.loanledger.domain.BorrowerRepository;
import com.loanledger.domain.EscrowAccount;
import com.loanledger.domain.EscrowAccountRepository;
import com.loanledger.domain.Loan;
import com.loanledger.domain.LoanRepository;
import com.loanledger.domain.LoanState;
import com.loanledger.domain.LoanStatus;
import com.loanledger.domain.Payment;
import com.loanledger.domain.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {

    private final LoanRepository loans;
    private final BorrowerRepository borrowers;
    private final EscrowAccountRepository escrows;
    private final PaymentRepository payments;

    public LoanService(LoanRepository loans, BorrowerRepository borrowers,
                       EscrowAccountRepository escrows, PaymentRepository payments) {
        this.loans = loans;
        this.borrowers = borrowers;
        this.escrows = escrows;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> list(LoanStatus status, Pageable pageable) {
        Page<Loan> page = status == null ? loans.findAll(pageable) : loans.findByStatus(status, pageable);
        return page.map(loan -> LoanResponse.of(loan, null));
    }

    @Transactional(readOnly = true)
    public LoanResponse get(long id) {
        Loan loan = loans.findWithBorrowerById(id).orElseThrow(() -> loanNotFound(id));
        return LoanResponse.of(loan, escrowOf(id));
    }

    /** An existing borrower is matched by email, so one borrower can hold several loans. */
    @Transactional
    public LoanResponse create(CreateLoanRequest request) {
        BorrowerRequest b = request.borrower();
        Borrower borrower = borrowers.findByEmail(b.email())
                .orElseGet(() -> borrowers.save(new Borrower(b.firstName(), b.lastName(), b.email())));
        Loan loan = loans.save(new Loan(borrower, request.principal(), request.annualRate(), request.termMonths(),
                request.originationDate(), Amortization.monthlyEscrow(request.annualTax(), request.annualInsurance())));
        EscrowAccount escrow = escrows.save(new EscrowAccount(loan.getId(), request.annualTax(), request.annualInsurance()));
        return LoanResponse.of(loan, escrow);
    }

    @Transactional(readOnly = true)
    public ScheduleResponse schedule(long id) {
        Loan loan = requireLoan(id);
        return ScheduleResponse.of(id, Amortization.schedule(loan.getPrincipal(), loan.getAnnualRate(),
                loan.getTermMonths(), loan.getOriginationDate()));
    }

    @Transactional
    public PostingResponse post(long loanId, PaymentRequest request) {
        Loan loan = requireLoan(loanId);
        EscrowAccount escrow = escrowOf(loanId);
        LoanState.Posting posting = compute(loan, escrow, request);
        loan.setState(posting.after());
        escrow.deposit(posting.allocation().escrow());
        Payment saved = payments.save(new Payment(loanId, request.paymentDate(), posting.allocation()));
        return PostingResponse.of(saved.getId(), loanId, request, posting);
    }

    /** Same calculation as {@link #post}, with nothing saved. */
    @Transactional(readOnly = true)
    public PostingResponse simulate(long loanId, PaymentRequest request) {
        LoanState.Posting posting = compute(requireLoan(loanId), escrowOf(loanId), request);
        return PostingResponse.of(null, loanId, request, posting);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> history(long loanId, Pageable pageable) {
        requireLoan(loanId);
        return payments.findByLoanId(loanId, pageable).map(PaymentResponse::of);
    }

    private LoanState.Posting compute(Loan loan, EscrowAccount escrow, PaymentRequest request) {
        if (request.paymentDate().isBefore(loan.getOriginationDate())) {
            throw Problems.unprocessable("Payment date is before the loan's origination date " + loan.getOriginationDate());
        }
        LoanState state = loan.state();
        if (state.paidOff()) {
            throw Problems.unprocessable("Loan is paid off");
        }
        try {
            return state.apply(request.amount(), loan.terms(escrow.getMonthlyAmount()));
        } catch (IllegalArgumentException e) {
            throw Problems.unprocessable(e.getMessage());
        }
    }

    private Loan requireLoan(long id) {
        return loans.findById(id).orElseThrow(() -> loanNotFound(id));
    }

    private EscrowAccount escrowOf(long loanId) {
        return escrows.findByLoanId(loanId).orElseThrow(() -> Problems.notFound("No escrow account for loan " + loanId));
    }

    private static RuntimeException loanNotFound(long id) {
        return Problems.notFound("Loan " + id + " not found");
    }
}
