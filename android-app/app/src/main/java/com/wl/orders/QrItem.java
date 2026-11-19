package com.wl.orders;

/**
 * Everything one Wosux product QR carries. Values are kept as text, exactly as the QR Maker wrote them.
 */
public final class QrItem {
    public final String id;          // full sticker id, e.g. WS01-11-4
    public final String sku;         // WS01   -> wbproducts.sku
    public final String number;      // 11     -> number in the batch
    public final String billId;      // 4      -> wbbills.id
    public final String name;
    public final String buyPrice;
    public final String minPrice;
    public final String maxPrice;
    public final String date;        // YYYY-MM-DD
    public final String categoryId;  // 0 when the product has no numeric category
    public final String storeId;

    public QrItem(String id, String sku, String number, String billId, String name,
                  String buyPrice, String minPrice, String maxPrice, String date,
                  String categoryId, String storeId) {
        this.id = id;
        this.sku = sku;
        this.number = number;
        this.billId = billId;
        this.name = name;
        this.buyPrice = buyPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.date = date;
        this.categoryId = categoryId;
        this.storeId = storeId;
    }
}
