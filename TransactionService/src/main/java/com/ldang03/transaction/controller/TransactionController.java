package com.ldang03.transaction.controller;

import com.ldang03.common.response.ApiResponse;
import com.ldang03.common.response.customResponse;
import com.ldang03.transaction.model.TransactionRequest;
import com.ldang03.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/v1/transactions")
    public ResponseEntity<ApiResponse<String>> createTransaction(@RequestBody @Valid TransactionRequest request,
                                                                 @RequestHeader("Idempotency-Key") String key) {
        String message = transactionService.processTransaction(request,key);
        return customResponse.responseSuccess(202,true, "Transaction processed Approved", message);
    }


}
