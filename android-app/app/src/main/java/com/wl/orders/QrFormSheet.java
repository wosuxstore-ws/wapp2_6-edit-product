package com.wl.orders;

import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The popup that opens after a QR is scanned. Every field is already filled from the QR; the person
 * can correct the editable ones, then Save (or Cancel / swipe down / back to scan something else).
 */
final class QrFormSheet {

    interface Listener {
        /** Save was tapped and every field is valid. {@code item} holds the (possibly corrected) values. */
        void onSave(QrItem item);

        /** The popup is gone (saved, cancelled, swiped away or back pressed). */
        void onClosed();
    }

    private final AppCompatActivity activity;
    private final QrItem scanned;
    private final Listener listener;
    private final BottomSheetDialog dialog;

    private EditText etName, etSku, etNumber, etBill, etBuy, etMin, etMax, etDate, etCategory, etStore;
    private TextView tvError;

    private QrFormSheet(AppCompatActivity activity, QrItem scanned, Listener listener) {
        this.activity = activity;
        this.scanned = scanned;
        this.listener = listener;
        this.dialog = new BottomSheetDialog(activity, R.style.ThemeOverlay_WLOrders_BottomSheetDialog);
    }

    /** Builds the popup and shows it. Returns the dialog so the caller can dismiss it if the page closes. */
    static BottomSheetDialog show(AppCompatActivity activity, QrItem item, Listener listener) {
        QrFormSheet s = new QrFormSheet(activity, item, listener);
        s.build();
        s.dialog.show();
        return s.dialog;
    }

    private void build() {
        View sheet = activity.getLayoutInflater().inflate(R.layout.sheet_qr_form, null);

        etName = sheet.findViewById(R.id.qrName);
        etSku = sheet.findViewById(R.id.qrSku);
        etNumber = sheet.findViewById(R.id.qrNumber);
        etBill = sheet.findViewById(R.id.qrBill);
        etBuy = sheet.findViewById(R.id.qrBuy);
        etMin = sheet.findViewById(R.id.qrMin);
        etMax = sheet.findViewById(R.id.qrMax);
        etDate = sheet.findViewById(R.id.qrDate);
        etCategory = sheet.findViewById(R.id.qrCategory);
        etStore = sheet.findViewById(R.id.qrStore);
        tvError = sheet.findViewById(R.id.qrError);

        ((TextView) sheet.findViewById(R.id.qrSubtitle))
                .setText(activity.getString(R.string.qr_form_filled, scanned.id));

        etName.setText(scanned.name);
        etSku.setText(scanned.sku);
        etNumber.setText(scanned.number);
        etBill.setText(scanned.billId);
        etBuy.setText(scanned.buyPrice);
        etMin.setText(scanned.minPrice);
        etMax.setText(scanned.maxPrice);
        etDate.setText(scanned.date);
        etCategory.setText(scanned.categoryId);
        etStore.setText(scanned.storeId);

        // a field turns normal again as soon as the person edits it
        for (EditText e : new EditText[]{etName, etBuy, etMin, etMax}) {
            e.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) { markOk(e); }
            });
        }

        etDate.setOnClickListener(v -> pickDate());
        sheet.findViewById(R.id.qrClose).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.qrCancel).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.qrSave).setOnClickListener(v -> save());

        dialog.setContentView(sheet);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> {
            View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (container != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(container);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.setOnDismissListener(d -> listener.onClosed());
    }

    private void save() {
        boolean ok = true;
        if (text(etName).isEmpty()) ok = markBad(etName);
        if (!isPrice(text(etBuy))) ok = markBad(etBuy);
        if (!isPrice(text(etMin))) ok = markBad(etMin);
        if (!isPrice(text(etMax))) ok = markBad(etMax);
        if (!isDate(text(etDate))) ok = markBad(etDate);
        if (!ok) {
            tvError.setVisibility(View.VISIBLE);
            return;
        }
        tvError.setVisibility(View.GONE);

        QrItem edited = new QrItem(scanned.id, scanned.sku, scanned.number, scanned.billId,
                text(etName), text(etBuy), text(etMin), text(etMax), text(etDate),
                text(etCategory).isEmpty() ? "0" : text(etCategory), text(etStore));
        listener.onSave(edited);
        dialog.dismiss();
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        String cur = text(etDate);
        if (isDate(cur)) {
            c.set(Integer.parseInt(cur.substring(0, 4)), Integer.parseInt(cur.substring(5, 7)) - 1,
                    Integer.parseInt(cur.substring(8, 10)));
        }
        new DatePickerDialog(activity,
                (view, y, m, d) -> {
                    etDate.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d));
                    markOk(etDate);
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ---- small helpers ---------------------------------------------------------------------

    private boolean markBad(EditText e) {
        e.setBackgroundResource(R.drawable.bg_input_error);
        return false;
    }

    private void markOk(EditText e) {
        e.setBackgroundResource(R.drawable.bg_input_gray);
    }

    private static String text(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    /** Same rule the QR Maker uses for prices: digits, optionally up to 2 decimals. */
    private static boolean isPrice(String s) {
        return s.matches("\\d{1,10}(\\.\\d{1,2})?");
    }

    private static boolean isDate(String s) {
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false);
        try {
            f.parse(s);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
}
