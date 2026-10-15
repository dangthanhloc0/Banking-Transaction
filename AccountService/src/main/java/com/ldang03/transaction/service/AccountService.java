package com.ldang03.transaction.service;

import com.ldang03.transaction.model.AccountResponse;
import com.ldang03.transaction.model.AccountTransfer;

public interface AccountService {

    AccountResponse findAccountByAccountNumberFc(String accountNumber);

    Boolean checkAccountExist(String accountNumber);

    String transferMoney(AccountTransfer accountTransfer);

}
