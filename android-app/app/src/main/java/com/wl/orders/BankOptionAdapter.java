package com.wl.orders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.wl.orders.models.WalletBank;
import java.util.List;

// The list inside the "Select bank or wallet" popup (picture + name, tick on the chosen one).
public class BankOptionAdapter extends RecyclerView.Adapter<BankOptionAdapter.ViewHolder> {

    public interface OnPick {
        void onPick(WalletBank bank);
    }

    private final List<WalletBank> banks;
    private final int selectedId; // -1 = nothing chosen yet
    private final OnPick listener;

    public BankOptionAdapter(List<WalletBank> banks, int selectedId, OnPick listener) {
        this.banks = banks;
        this.selectedId = selectedId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bank_option, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        WalletBank b = banks.get(position);
        h.name.setText(b.name);
        h.tick.setVisibility(b.id == selectedId ? View.VISIBLE : View.GONE);
        BankLogo.load(h.logo, h.initials, b.image, b.name);
        h.itemView.setOnClickListener(v -> listener.onPick(b));
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder h) {
        BankLogo.cancel(h.logo);
    }

    @Override
    public int getItemCount() {
        return banks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView initials;
        ImageView logo;
        TextView name;
        ImageView tick;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            initials = itemView.findViewById(R.id.bankInitials);
            logo = itemView.findViewById(R.id.bankLogo);
            name = itemView.findViewById(R.id.bankName);
            tick = itemView.findViewById(R.id.bankTick);
        }
    }
}
