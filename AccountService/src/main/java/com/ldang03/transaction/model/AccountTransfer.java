package com.ldang03.transaction.model;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AccountTransfer {

    @NotBlank(message = "IdempotencyKey cannot be blank")
    private String idempotencyKey;

    @NotBlank(message = "DebtorAccount cannot be blank")
    private String debtorAccount;

    @NotBlank(message = "CreditorAccount cannot be blank")
    private String creditorAccount;

    @DecimalMin(value="0", inclusive=false)
    @NotNull(message = "Amount cannot be null")
    private BigDecimal amount;

    @NotNull(message = "TraceId cannot be null")
    private String traceId;

}
