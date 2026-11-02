package com.loanledger.api;

import com.loanledger.api.Dtos.CaseResponse;
import com.loanledger.api.Dtos.OpenCaseRequest;
import com.loanledger.api.Dtos.UpdateCaseRequest;
import com.loanledger.service.LossMitigationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LossMitigationController {

    private final LossMitigationService service;

    public LossMitigationController(LossMitigationService service) {
        this.service = service;
    }

    @PostMapping("/loans/{loanId}/loss-mitigation-cases")
    public ResponseEntity<CaseResponse> open(@PathVariable long loanId, @Valid @RequestBody OpenCaseRequest request) {
        CaseResponse opened = service.open(loanId, request);
        return ResponseEntity.created(URI.create("/api/v1/loss-mitigation-cases/" + opened.id())).body(opened);
    }

    @GetMapping("/loans/{loanId}/loss-mitigation-cases")
    public List<CaseResponse> forLoan(@PathVariable long loanId) {
        return service.forLoan(loanId);
    }

    @PatchMapping("/loss-mitigation-cases/{caseId}")
    public CaseResponse update(@PathVariable long caseId, @Valid @RequestBody UpdateCaseRequest request) {
        return service.update(caseId, request);
    }
}
