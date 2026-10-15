package com.ldang03.transaction.controller;

import com.ldang03.common.exception.ExistException;
import com.ldang03.common.response.ApiResponse;
import com.ldang03.common.response.customResponse;
import com.ldang03.transaction.model.AccountResponse;
import com.ldang03.transaction.model.AccountTransfer;
import com.ldang03.transaction.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/v1/getAccount")
    public ResponseEntity<ApiResponse<AccountResponse>> getAll(@RequestBody String accountNumber) {
        return customResponse.responseSuccess(200, true,"Accounts found successfully", accountService.findAccountByAccountNumberFc(accountNumber));
    }

    @PostMapping("/v1/checkAccountExist")
    public ResponseEntity<ApiResponse<Boolean>> checkAccountExist(@RequestBody String accountNumber) {
        return customResponse.responseSuccess(200, true,"Accounts found successfully", accountService.checkAccountExist(accountNumber));
    }

    @PostMapping("/v1/transferMoney")
    public ResponseEntity<ApiResponse<String>> transferMoney(@Valid  @RequestBody AccountTransfer accountTransfer) {
        try {
            String message = accountService.transferMoney(accountTransfer);
            return customResponse.responseSuccess(200, true, "Transaction processed Approved", message);
        } catch (DataIntegrityViolationException e) {
            // Same idempotency key is being processed by another request
            throw new ExistException("Transaction with this idempotency key is being processed");
        }
    }
}
