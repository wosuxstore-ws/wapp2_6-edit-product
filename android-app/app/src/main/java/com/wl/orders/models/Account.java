package com.wl.orders.models;

// One row of get_accounts.php's "accounts" array (table wbaccounts + the bank/wallet it belongs to).
public class Account {
    public int id;
    public String name;           // account_name
    public int walletBankId;
    public String walletBankName; // name from wallets_and_banks, "" if that row was removed
    public String image;          // img_source of that bank/wallet
    public String amount;         // "15000.50"
}
