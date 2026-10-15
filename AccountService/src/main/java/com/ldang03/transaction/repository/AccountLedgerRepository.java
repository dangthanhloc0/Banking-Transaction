package com.ldang03.transaction.repository;

import com.ldang03.transaction.entity.AccountLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountLedgerRepository extends JpaRepository<AccountLedger, UUID> {

    Optional<AccountLedger> findAccountLedgerByIdempotencyKey(String idempotencyKey);

}
