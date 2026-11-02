package com.loanledger.api;

import com.loanledger.api.Dtos.CreateLoanRequest;
import com.loanledger.api.Dtos.LoanResponse;
import com.loanledger.api.Dtos.PageResponse;
import com.loanledger.api.Dtos.PaymentRequest;
import com.loanledger.api.Dtos.PaymentResponse;
import com.loanledger.api.Dtos.PostingResponse;
import com.loanledger.api.Dtos.ScheduleResponse;
import com.loanledger.domain.LoanStatus;
import com.loanledger.service.LoanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

    private final LoanService service;

    public LoanController(LoanService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<LoanResponse> list(@RequestParam(required = false) LoanStatus status,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(service.list(status, PageRequest.of(page, size, Sort.by("id"))));
    }

    /** Oldest due date first: the loans furthest behind are at the top of the work queue. */
    @GetMapping("/delinquent")
    public PageResponse<LoanResponse> delinquent(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(service.list(LoanStatus.DELINQUENT,
                PageRequest.of(page, size, Sort.by("nextDueDate", "id"))));
    }

    @PostMapping
    public ResponseEntity<LoanResponse> create(@Valid @RequestBody CreateLoanRequest request) {
        LoanResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/loans/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public LoanResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/schedule")
    public ScheduleResponse schedule(@PathVariable long id) {
        return service.schedule(id);
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<PostingResponse> postPayment(@PathVariable long id, @Valid @RequestBody PaymentRequest request) {
        PostingResponse posted = service.post(id, request);
        return ResponseEntity.created(URI.create("/api/v1/loans/" + id + "/payments/" + posted.paymentId())).body(posted);
    }

    @PostMapping("/{id}/payments/simulate")
    public PostingResponse simulatePayment(@PathVariable long id, @Valid @RequestBody PaymentRequest request) {
        return service.simulate(id, request);
    }

    @GetMapping("/{id}/payments")
    public PageResponse<PaymentResponse> paymentHistory(@PathVariable long id,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "paymentDate", "id");
        return PageResponse.of(service.history(id, PageRequest.of(page, size, newestFirst)));
    }
}
