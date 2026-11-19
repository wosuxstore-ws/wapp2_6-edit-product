package com.wl.orders.models;

// One row of get_wallets_banks.php's "items" array (table wallets_and_banks: id, name, img_source).
public class WalletBank {
    public int id;
    public String name;
    public String image; // img_source as stored: usually a file name inside api/banks_and_wallets/
}
