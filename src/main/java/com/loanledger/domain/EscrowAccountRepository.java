package com.loanledger.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscrowAccountRepository extends JpaRepository<EscrowAccount, Long> {

    Optional<EscrowAccount> findByLoanId(Long loanId);
}
