package com.ldang03.transaction.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
public class TransactionRequest {

    @NotBlank(message = "UserId cannot be blank")
    private String userId;

    @NotBlank(message = "DebtorAccount cannot be blank")
    private String debtorAccount;

    @NotBlank(message = "CreditorAccount cannot be blank")
    private String creditorAccount;

    @DecimalMin(value="0", inclusive=false)
    @NotNull(message = "Amount cannot be null")
    private BigDecimal amount;

}
