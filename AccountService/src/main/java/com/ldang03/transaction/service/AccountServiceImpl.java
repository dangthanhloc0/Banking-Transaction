package com.ldang03.transaction.service;

import com.ldang03.common.exception.BusinessException;
import com.ldang03.common.exception.NotActiveException;
import com.ldang03.common.exception.NotEnoughMoneyException;
import com.ldang03.common.exception.NotFoundException;
import com.ldang03.transaction.entity.Account;
import com.ldang03.transaction.entity.AccountLedger;
import com.ldang03.transaction.entity.AccountStatus;
import com.ldang03.transaction.entity.Status;
import com.ldang03.transaction.model.AccountResponse;
import com.ldang03.transaction.model.AccountTransfer;
import com.ldang03.transaction.repository.AccountLedgerRepository;
import com.ldang03.transaction.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.ldang03.common.util.MaskingUtils.maskAccountNumber;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private final org.slf4j.Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    private final AccountRepository accountRepository;

    private final AccountLedgerRepository accountLedgerRepository;


    @Override
    public AccountResponse findAccountByAccountNumberFc(String accountNumber) {
        log.info("Finding account by account number: {}", maskAccountNumber(accountNumber));
        Optional<Account> account = accountRepository.findAccountByAccountNumber(accountNumber);

        if (account.isEmpty()) {
            throw new NotFoundException("Account with account number " + maskAccountNumber(accountNumber) + " not found");
        }

        return toAccountResponse(account.get());
    }

    @Override
    public Boolean checkAccountExist(String accountNumber) {
        log.info("Finding account by account number: {}", maskAccountNumber(accountNumber));
        Boolean exists = accountRepository.existsAccountsByAccountNumber(accountNumber);
        if (!exists) {
            log.info("Account with account number {} not found", maskAccountNumber(accountNumber));
            return false;
        }
        log.info("Account with account number {} found", maskAccountNumber(accountNumber));
        return true;
    }

    @Override
    @Transactional(noRollbackFor = {
            NotFoundException.class,
            NotActiveException.class,
            NotEnoughMoneyException.class})
    public String transferMoney(AccountTransfer accountTransfer) {

        log.info("Transferring money : debtorAccount : {} - creditorAccount : {} - amount : {} - idempotency key : {}",
                maskAccountNumber(accountTransfer.getDebtorAccount()), maskAccountNumber(accountTransfer.getCreditorAccount()),
                accountTransfer.getAmount(), accountTransfer.getIdempotencyKey());

        if (accountTransfer.getDebtorAccount().equalsIgnoreCase(accountTransfer.getCreditorAccount())) {
            log.info("Cannot transfer money to the same account {}",
                    maskAccountNumber(accountTransfer.getDebtorAccount()));
            throw new BusinessException("Cannot transfer money to the same account");
        }

        // 1. Kiểm tra trạng thái của idempotency key của giao dịch
        String result = checkIdempotencyKey(accountTransfer);
        if (result != null) {
            return result;
        }


        // lock 2 account lại (luôn lock số tài khoản nhỏ hơn trước để tránh deadlock)
        //  kiểm tra tài khoản bị trừ tiền (debtor): tồn tại, đang hoạt động, đủ tiền
        //  kiểm tra tài khoản nhận tiền (creditor): tồn tại, đang hoạt động
        Account debtor;
        Account creditor;
        String debtorNumber = accountTransfer.getDebtorAccount();
        String creditorNumber = accountTransfer.getCreditorAccount();


        boolean debtorFirst = debtorNumber.compareTo(creditorNumber) < 0;
        if (debtorFirst) {
            debtor = checkDebtorAccount(accountTransfer);
            creditor = checkCreditorAccount(accountTransfer);
        } else {
            creditor = checkCreditorAccount(accountTransfer);
            debtor = checkDebtorAccount(accountTransfer);
        }


        // 4. thực hiện chuyển tiền
        creditor.setAccountBalance(creditor.getAccountBalance().add(accountTransfer.getAmount()));
        debtor.setAccountBalance(debtor.getAccountBalance().subtract(accountTransfer.getAmount()));

        accountRepository.save(debtor);
        accountRepository.save(creditor);

        updateAccountTransfer(accountTransfer.getIdempotencyKey(), null, Status.SUCCESS);

        log.info("Transfer money from account {} to account {} with amount {} and idempotency key {} successfully",
                maskAccountNumber(debtorNumber), maskAccountNumber(creditorNumber),
                accountTransfer.getAmount(), accountTransfer.getIdempotencyKey());
        return "Transfer money successfully";
    }


    public Account checkCreditorAccount(AccountTransfer accountTransfer) {
        String message = "";
        String maskedNumber = maskAccountNumber(accountTransfer.getCreditorAccount());
        Optional<Account> creditor = accountRepository.findAccountByAccountNumber(accountTransfer.getCreditorAccount());
        if (creditor.isEmpty()) {
            message = "Creditor account " + maskedNumber + " not found";
            updateAccountTransfer(accountTransfer.getIdempotencyKey(), message, Status.FAILED);
            throw new NotFoundException(message);
        } else {
            // Kiểm tra account còn được còn hoạt động hay không
            Account acc = creditor.get();
            if (acc.getAccountStatus() != AccountStatus.ACTIVE) {
                message = "Creditor account " + maskedNumber + " is not active";
                updateAccountTransfer(accountTransfer.getIdempotencyKey(), message, Status.FAILED);
                throw new NotActiveException(message);
            }
        }
        return creditor.get();
    }


    public Account checkDebtorAccount(AccountTransfer accountTransfer) {
        String message = "";
        String maskedNumber = maskAccountNumber(accountTransfer.getDebtorAccount());
        Optional<Account> debtor = accountRepository.findAccountByAccountNumber(accountTransfer.getDebtorAccount());
        if (debtor.isEmpty()) {
            message = "Debtor account " + maskedNumber + " not found";
            updateAccountTransfer(accountTransfer.getIdempotencyKey(), message, Status.FAILED);
            throw new NotFoundException(message);
        } else {
            // 3. Kiểm tra account debtor có còn đủ tiền hay không
            Account acc = debtor.get();
            if (acc.getAccountStatus() != AccountStatus.ACTIVE) {
                message = "Debtor account " + maskedNumber + " is not active";
                updateAccountTransfer(accountTransfer.getIdempotencyKey(), message, Status.FAILED);
                throw new NotActiveException(message);
            }
            if (acc.getAccountBalance().compareTo(accountTransfer.getAmount()) < 0) {
                message = "Debtor account " + maskedNumber + " has insufficient balance";
                updateAccountTransfer(accountTransfer.getIdempotencyKey(), message, Status.FAILED);
                throw new NotEnoughMoneyException(message);
            }
        }
        return debtor.get();
    }


    public String checkIdempotencyKey(AccountTransfer accountTransfer) {
        var accLedger = accountLedgerRepository.findAccountLedgerByIdempotencyKey(accountTransfer.getIdempotencyKey());
        if (accLedger.isPresent()) {
            AccountLedger accountLedger = accLedger.get();
            if (accountLedger.getStatusEnum() == Status.SUCCESS) {
                log.info("Duplicate transaction - Idempotency key : {}", accountTransfer.getIdempotencyKey());
                return "Transaction already exists";
            }
            throw new BusinessException(
                    accountLedger.getFailedReason() != null ? accountLedger.getFailedReason() : "Transaction is being processed");
        }
        // Tạo accountLedeger mới với trạng thái RUNNING
        createAccountLedger(accountTransfer, Status.RUNNING);
        return null;
    }


    public void createAccountLedger(AccountTransfer accountTransfer, Status status) {
        AccountLedger accountLedger = new AccountLedger();
        accountLedger.setIdempotencyKey(accountTransfer.getIdempotencyKey());
        accountLedger.setStatusEnum(status);
        accountLedger.setTraceId(accountTransfer.getTraceId());
        accountLedger.setAmount(accountTransfer.getAmount());
        accountLedgerRepository.saveAndFlush(accountLedger);
    }

    public void updateAccountTransfer(String idempotencyKey, String errorMessage, Status status) {
        accountLedgerRepository.findAccountLedgerByIdempotencyKey(idempotencyKey).ifPresent(accountLedger -> {
            accountLedger.setStatusEnum(status);
            accountLedger.setFailedReason(errorMessage);
            accountLedgerRepository.save(accountLedger);
        });

    }


    public AccountResponse toAccountResponse(Account account) {
        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .balance(account.getAccountBalance())
                .accountStatus(account.getAccountStatus())
                .build();
    }

}
