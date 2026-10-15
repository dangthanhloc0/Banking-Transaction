package com.ldang03.transaction.model;

import com.ldang03.transaction.entity.AccountStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class AccountResponse {

    private UUID accountId;

    private String accountNumber;

    private BigDecimal balance;

    private AccountStatus accountStatus;

}
