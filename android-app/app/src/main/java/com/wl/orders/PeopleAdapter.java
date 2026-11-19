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

// The list on the People page. Photos are loaded from YOUR server with Volley's
// ImageLoader (already in the app) - no Glide/Picasso. Until a photo is there (or if the
// person has none) the person's initials are shown instead.
public class PeopleAdapter extends RecyclerView.Adapter<PeopleAdapter.ViewHolder> {

    private final List<Person> people = new ArrayList<>();

    public void setPeople(List<Person> list) {
        people.clear();
        people.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_person, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Person p = people.get(position);
        h.name.setText(p.name);
        h.number.setText(p.number);
        h.role.setText(p.role);
        h.role.setVisibility(p.role.isEmpty() ? View.GONE : View.VISIBLE);
        h.initials.setText(initials(p.name));
        bindPhoto(h, p);
    }

    private void bindPhoto(@NonNull ViewHolder h, Person p) {
        // This row may be re-used from another person: drop that person's pending photo.
        if (h.imageRequest != null) {
            h.imageRequest.cancelRequest();
            h.imageRequest = null;
        }
        h.photo.setImageDrawable(null);
        h.photo.setVisibility(View.GONE);
        if (p.image.isEmpty()) return;

        String url = ApiConfig.PEOPLE_IMAGE_BASE_URL + p.image;
        ImageLoader loader = VolleySingleton.getInstance(h.itemView.getContext().getApplicationContext())
                .getImageLoader();
        h.imageRequest = loader.get(url, new ImageLoader.ImageListener() {
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
        }, 240, 240);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder h) {
        if (h.imageRequest != null) {
            h.imageRequest.cancelRequest();
            h.imageRequest = null;
        }
    }

    @Override
    public int getItemCount() {
        return people.size();
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
        TextView role;
        ImageLoader.ImageContainer imageRequest;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            initials = itemView.findViewById(R.id.personInitials);
            photo = itemView.findViewById(R.id.personPhoto);
            name = itemView.findViewById(R.id.personName);
            number = itemView.findViewById(R.id.personNumber);
            role = itemView.findViewById(R.id.personRole);
        }
    }
}
