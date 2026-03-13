package com.crosspro.noactivity;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ViewHolder> {

    private final List<ColorEntry> colorEntries;
    private OnColorReplaceListener listener;

    public interface OnColorReplaceListener {
        void onReplaceAll(ColorEntry entry);
    }

    public void setOnColorReplaceListener(OnColorReplaceListener listener) {
        this.listener = listener;
    }

    public ColorAdapter(List<ColorEntry> colorEntries) {
        this.colorEntries = colorEntries;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_color_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ColorEntry entry = colorEntries.get(position);
        holder.hexTextView.setText(entry.hexValue);
        holder.countTextView.setText(entry.getCount() + " ocorrências");

        // Building usage summary
        StringBuilder usage = new StringBuilder("Em: ");
        java.util.Set<String> attributes = new java.util.HashSet<>();
        for (ColorOccurrence o : entry.occurrences) {
            if (o.attributeName != null) attributes.add(o.attributeName);
            if (attributes.size() >= 4) break; // Limit list size
        }
        if (attributes.isEmpty()) {
            usage.append("Sem atributo");
        } else {
            usage.append(String.join(", ", attributes));
        }
        holder.usageTextView.setText(usage.toString());

        try {
            String hex = entry.hexValue;
            if (hex.startsWith("#")) {
                if (hex.length() == 4) { // #RGB -> #RRGGBB
                    char r = hex.charAt(1);
                    char g = hex.charAt(2);
                    char b = hex.charAt(3);
                    hex = "#" + r + r + g + g + b + b;
                } else if (hex.length() == 5) { // #ARGB -> #AARRGGBB
                    char a = hex.charAt(1);
                    char r = hex.charAt(2);
                    char g = hex.charAt(3);
                    char b = hex.charAt(4);
                    hex = "#" + a + a + r + r + g + g + b + b;
                }
            }
            int color = Color.parseColor(hex);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setStroke(2, Color.LTGRAY);
            holder.colorPreview.setBackground(drawable);
        } catch (Exception e) {
            holder.colorPreview.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.replaceAllButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReplaceAll(entry);
            }
        });
    }

    @Override
    public int getItemCount() {
        return colorEntries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View colorPreview;
        TextView hexTextView;
        TextView countTextView;
        TextView usageTextView;
        View replaceAllButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            colorPreview = itemView.findViewById(R.id.color_preview);
            hexTextView = itemView.findViewById(R.id.hex_textview);
            countTextView = itemView.findViewById(R.id.count_textview);
            usageTextView = itemView.findViewById(R.id.usage_textview);
            replaceAllButton = itemView.findViewById(R.id.replace_all_button);
        }
    }
}
