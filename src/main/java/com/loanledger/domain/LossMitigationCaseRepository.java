package com.loanledger.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LossMitigationCaseRepository extends JpaRepository<LossMitigationCase, Long> {

    boolean existsByLoanIdAndResolvedAtIsNull(Long loanId);

    List<LossMitigationCase> findByLoanIdOrderByIdDesc(Long loanId);
}
