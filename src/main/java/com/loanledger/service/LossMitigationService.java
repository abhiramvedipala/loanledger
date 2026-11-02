package com.loanledger.service;

import com.loanledger.api.Dtos.CaseResponse;
import com.loanledger.api.Dtos.OpenCaseRequest;
import com.loanledger.api.Dtos.UpdateCaseRequest;
import com.loanledger.domain.Loan;
import com.loanledger.domain.LoanRepository;
import com.loanledger.domain.LoanStatus;
import com.loanledger.domain.LossMitigationCase;
import com.loanledger.domain.LossMitigationCaseRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A case carries the loan through the state machine; the loan's status always follows its open case. */
@Service
public class LossMitigationService {

    private final LossMitigationCaseRepository cases;
    private final LoanRepository loans;

    public LossMitigationService(LossMitigationCaseRepository cases, LoanRepository loans) {
        this.cases = cases;
        this.loans = loans;
    }

    @Transactional
    public CaseResponse open(long loanId, OpenCaseRequest request) {
        Loan loan = requireLoan(loanId);
        if (loan.getStatus() != LoanStatus.CURRENT && loan.getStatus() != LoanStatus.DELINQUENT) {
            throw Problems.conflict("Cannot open a case on a " + loan.getStatus() + " loan");
        }
        if (cases.existsByLoanIdAndResolvedAtIsNull(loanId)) {
            throw Problems.conflict("Loan " + loanId + " already has an open case");
        }
        return CaseResponse.of(cases.save(new LossMitigationCase(loanId, loan.getStatus(), request.notes())));
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> forLoan(long loanId) {
        requireLoan(loanId);
        return cases.findByLoanIdOrderByIdDesc(loanId).stream().map(CaseResponse::of).toList();
    }

    @Transactional
    public CaseResponse update(long caseId, UpdateCaseRequest request) {
        LossMitigationCase mitCase = cases.findById(caseId)
                .orElseThrow(() -> Problems.notFound("Loss-mitigation case " + caseId + " not found"));
        LoanStatus target = request.status();
        if (target != null) {
            if (!mitCase.getStatus().canMoveTo(target)) {
                throw Problems.conflict("Illegal transition " + mitCase.getStatus() + " -> " + target
                        + "; allowed: " + mitCase.getStatus().next());
            }
            mitCase.moveTo(target);
            requireLoan(mitCase.getLoanId()).setStatus(target);
        }
        if (request.notes() != null) {
            mitCase.setNotes(request.notes());
        }
        return CaseResponse.of(mitCase);
    }

    private Loan requireLoan(long id) {
        return loans.findById(id).orElseThrow(() -> Problems.notFound("Loan " + id + " not found"));
    }
}
