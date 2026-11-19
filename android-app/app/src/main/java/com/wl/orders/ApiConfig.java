package com.wl.orders;

import android.net.Uri;

public class ApiConfig {

    // The folder on your hosting where the PHP files from php-api/ live.
    //
    // - Testing on the Android EMULATOR against a PHP server on your own
    //   computer (XAMPP/MAMP/`php -S 0.0.0.0:8000`): use 10.0.2.2, the
    //   emulator's alias for your computer's localhost:
    //       "http://10.0.2.2:8000/"
    //
    // - Real phone / live hosting: use the real domain, with https:
    //       "https://yourdomain.com/api/"
    public static final String BASE_URL = "https://wosux.com/api/";

    public static final String LOGIN_URL            = BASE_URL + "login.php";
    public static final String GET_ORDERS_URL       = BASE_URL + "get_orders.php";
    public static final String GET_ORDER_DETAIL_URL = BASE_URL + "get_order_detail.php";
    public static final String UPDATE_ORDER_STATUS_URL = BASE_URL + "update_order_status.php";
    public static final String GET_PRODUCTS_URL     = BASE_URL + "get_products.php";

    // Home > pencil icon on a product tile > Edit Product page: loads one product's
    // title / prices / delivery days, and saves them back into the products table.
    public static final String GET_PRODUCT_URL      = BASE_URL + "get_product.php";
    public static final String UPDATE_PRODUCT_URL   = BASE_URL + "update_product.php";
    public static final String GET_CATEGORIES_URL   = BASE_URL + "get_categories.php";
    public static final String GET_NOTIFICATIONS_URL = BASE_URL + "get_notifications.php";
    public static final String MARK_NOTIFICATIONS_READ_URL = BASE_URL + "mark_notifications_read.php";
    public static final String POLL_NOTIFICATIONS_URL = BASE_URL + "poll_notifications.php";

    // People page (bottom bar): roles + people, all stored in your own MySQL via these PHP files.
    public static final String GET_PEOPLE_ROLES_URL = BASE_URL + "get_people_roles.php";
    public static final String ADD_PEOPLE_ROLE_URL  = BASE_URL + "add_people_role.php";
    public static final String GET_PEOPLE_URL       = BASE_URL + "get_people.php";
    public static final String ADD_PERSON_URL       = BASE_URL + "add_person.php";

    // Public web address of the folder with the product photos
    // (server folder: public_html/wl/backend/uploads/products). The products table only
    // stores the file name (front_image), so the app adds this in front of it.
    public static final String PRODUCT_IMAGE_BASE_URL = "https://wosux.com/wl/backend/uploads/products/";

    // Public web address of the folder with the category pictures
    // (server folder: public_html/wl/backend/uploads/categories). The categories table only
    // stores the file name (image_path), so the app adds this in front of it.
    public static final String CATEGORY_IMAGE_BASE_URL = "https://wosux.com/wl/backend/uploads/categories/";

    // Public web address of the folder with the people photos
    // (server folder: public_html/api/uploads/people - created automatically by add_person.php).
    // The wpeople table only stores the file name (image), so the app adds this in front of it.
    public static final String PEOPLE_IMAGE_BASE_URL = BASE_URL + "uploads/people/";

    // Store (People page > Add Store): saves a store name into the wbstore table.
    public static final String ADD_STORE_URL = BASE_URL + "add_store.php";

    // Reports page: accounts (wbaccounts) and the bank / wallet list (wallets_and_banks).
    public static final String GET_WALLETS_BANKS_URL = BASE_URL + "get_wallets_banks.php";
    public static final String GET_ACCOUNTS_URL      = BASE_URL + "get_accounts.php";
    public static final String ADD_ACCOUNT_URL       = BASE_URL + "add_account.php";

    // Reports page > Add Bill: the people to pick from (wpeople where role_id = 4) and the save
    // call (wbbills table + the bill photo in public_html/api/uploads/bills/).
    public static final String GET_BILL_PEOPLE_URL   = BASE_URL + "get_bill_people.php";
    public static final String ADD_BILL_URL          = BASE_URL + "add_bill.php";

    // Public web address of the folder with the bank / wallet pictures
    // (server folder: public_html/api/banks_and_wallets). The wallets_and_banks table only
    // stores the file name (img_source), so the app adds this in front of it.
    public static final String BANK_WALLET_IMAGE_BASE_URL = BASE_URL + "banks_and_wallets/";

    // img_source -> full picture address. A full http(s) address is used as it is; a plain file
    // name (or a path ending in the file name) gets BANK_WALLET_IMAGE_BASE_URL in front of it.
    public static String bankImageUrl(String imgSource) {
        if (imgSource == null) return "";
        String f = imgSource.trim();
        if (f.isEmpty()) return "";
        if (f.startsWith("http://") || f.startsWith("https://")) return f;
        int slash = f.lastIndexOf('/');
        if (slash >= 0) f = f.substring(slash + 1);
        return BANK_WALLET_IMAGE_BASE_URL + Uri.encode(f);
    }
}
