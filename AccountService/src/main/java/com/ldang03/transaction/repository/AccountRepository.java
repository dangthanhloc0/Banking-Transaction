package com.ldang03.transaction.repository;

import com.ldang03.transaction.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findAccountByAccountNumber(String accountNumber);

    Boolean existsAccountsByAccountNumber(String accountNumber);
}
