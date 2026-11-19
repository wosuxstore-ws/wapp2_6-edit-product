package com.wl.orders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.wl.orders.models.Account;
import java.util.ArrayList;
import java.util.List;

// The list on the Reports page: bank/wallet picture, account name, bank/wallet name, amount.
public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {

    private final List<Account> accounts = new ArrayList<>();

    public void setAccounts(List<Account> list) {
        accounts.clear();
        accounts.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Account a = accounts.get(position);
        h.name.setText(a.name);
        h.bank.setText(a.walletBankName);
        h.bank.setVisibility(a.walletBankName.isEmpty() ? View.GONE : View.VISIBLE);
        h.amount.setText(Format.moneyFixed(a.amount));
        BankLogo.load(h.logo, h.initials, a.image, a.walletBankName.isEmpty() ? a.name : a.walletBankName);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder h) {
        BankLogo.cancel(h.logo);
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView initials;
        ImageView logo;
        TextView name;
        TextView bank;
        TextView amount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            initials = itemView.findViewById(R.id.accountInitials);
            logo = itemView.findViewById(R.id.accountLogo);
            name = itemView.findViewById(R.id.accountName);
            bank = itemView.findViewById(R.id.accountBank);
            amount = itemView.findViewById(R.id.accountAmount);
        }
    }
}
