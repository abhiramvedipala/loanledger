package com.loanledger.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    @Override
    @EntityGraph(attributePaths = "borrower")
    Page<Loan> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "borrower")
    Page<Loan> findByStatus(LoanStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "borrower")
    Optional<Loan> findWithBorrowerById(Long id);
}
