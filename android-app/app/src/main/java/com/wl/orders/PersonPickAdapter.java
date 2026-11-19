package com.wl.orders;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.wl.orders.models.Person;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// The list inside the "Select person" popup on the Add Bill screen: photo (or initials), name,
// number, and a tick on the chosen one. filter() is the search box above the list.
public class PersonPickAdapter extends RecyclerView.Adapter<PersonPickAdapter.ViewHolder> {

    public interface OnPick {
        void onPick(Person person);
    }

    private final List<Person> all;
    private final List<Person> shown = new ArrayList<>();
    private final int selectedId; // -1 = nobody chosen yet
    private final OnPick listener;

    public PersonPickAdapter(List<Person> all, int selectedId, OnPick listener) {
        this.all = all;
        this.selectedId = selectedId;
        this.listener = listener;
        shown.addAll(all);
    }

    // Keeps the people whose name contains the typed text (not case-sensitive). When the text is
    // only digits / + - ( ) and spaces it is also matched against the phone number, ignoring the
    // dashes and spaces ("0300123" finds "0300-123 4567"). Returns how many people are left.
    public int filter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        boolean numberSearch = q.matches("[0-9+\\-\\s()]+");
        String qDigits = q.replaceAll("[^0-9]", "");

        shown.clear();
        for (Person p : all) {
            boolean match = q.isEmpty()
                    || p.name.toLowerCase(Locale.getDefault()).contains(q)
                    || (numberSearch && !qDigits.isEmpty() && p.number.replaceAll("[^0-9]", "").contains(qDigits));
            if (match) shown.add(p);
        }
        notifyDataSetChanged();
        return shown.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_person_option, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Person p = shown.get(position);
        h.name.setText(p.name);
        h.number.setText(p.number);
        h.tick.setVisibility(p.id == selectedId ? View.VISIBLE : View.GONE);
        h.initials.setText(initials(p.name));
        bindPhoto(h, p);
        h.itemView.setOnClickListener(v -> listener.onPick(p));
    }

    private void bindPhoto(@NonNull ViewHolder h, Person p) {
        // This row may be re-used from another person: drop that person's pending photo.
        cancelPhoto(h);
        h.photo.setImageDrawable(null);
        h.photo.setVisibility(View.GONE);
        if (p.image.isEmpty()) return;

        ImageLoader loader = VolleySingleton.getInstance(h.itemView.getContext().getApplicationContext())
                .getImageLoader();
        h.imageRequest = loader.get(ApiConfig.PEOPLE_IMAGE_BASE_URL + p.image, new ImageLoader.ImageListener() {
            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                Bitmap bitmap = response.getBitmap();
                if (bitmap != null) { // null = the first "still loading" callback
                    h.photo.setImageBitmap(bitmap);
                    h.photo.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onErrorResponse(VolleyError error) {
                // Keep the initials. Common causes: wrong PEOPLE_IMAGE_BASE_URL, file missing, 403.
            }
        }, 160, 160);
    }

    private void cancelPhoto(ViewHolder h) {
        if (h.imageRequest != null) {
            h.imageRequest.cancelRequest();
            h.imageRequest = null;
        }
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder h) {
        cancelPhoto(h);
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    // "Ali Khan" -> "AK", "Sara" -> "S"
    private static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        String s = parts[0].substring(0, 1);
        if (parts.length > 1 && !parts[1].isEmpty()) s += parts[1].substring(0, 1);
        return s.toUpperCase();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView initials;
        ImageView photo;
        TextView name;
        TextView number;
        ImageView tick;
        ImageLoader.ImageContainer imageRequest;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            initials = itemView.findViewById(R.id.pickInitials);
            photo = itemView.findViewById(R.id.pickPhoto);
            name = itemView.findViewById(R.id.pickName);
            number = itemView.findViewById(R.id.pickNumber);
            tick = itemView.findViewById(R.id.pickTick);
        }
    }
}
