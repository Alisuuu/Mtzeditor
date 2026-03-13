package com.crosspro.noactivity;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ResourceAdapter extends RecyclerView.Adapter<ResourceAdapter.ViewHolder> {

    private final List<ResourceEntry> entries;

    public ResourceAdapter(List<ResourceEntry> entries) {
        this.entries = entries;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_resource_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ResourceEntry entry = entries.get(position);
        
        // Remove existing listener if any to avoid multiple updates
        if (holder.textWatcher != null) {
            holder.valueEditText.removeTextChangedListener(holder.textWatcher);
        }

        holder.nameTextView.setText(entry.name);
        holder.typeTextView.setText(entry.type);
        holder.valueEditText.setText(entry.value);

        if ("color".equals(entry.type)) {
            holder.colorPreview.setVisibility(View.VISIBLE);
            updateColorPreview(holder.colorPreview, entry.value);
            holder.colorPreview.setOnClickListener(v -> showColorPicker(holder.itemView.getContext(), entry, holder));
        } else {
            holder.colorPreview.setVisibility(View.GONE);
            holder.colorPreview.setOnClickListener(null);
        }

        holder.textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                entry.value = s.toString();
                if ("color".equals(entry.type)) {
                    updateColorPreview(holder.colorPreview, entry.value);
                }
            }
        };
        holder.valueEditText.addTextChangedListener(holder.textWatcher);
    }

    private void showColorPicker(android.content.Context context, ResourceEntry entry, ViewHolder holder) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null);
        TriangleColorPickerView picker = view.findViewById(R.id.triangle_picker);
        SeekBar sbAlpha = view.findViewById(R.id.seekbar_alpha);
        EditText editHex = view.findViewById(R.id.edit_hex_value);

        // Initial values
        int initialColor = Color.BLACK;
        try {
            initialColor = Color.parseColor(normalizeHex(entry.value));
        } catch (Exception ignored) {}

        picker.setColor(initialColor);
        sbAlpha.setProgress(Color.alpha(initialColor));
        editHex.setText(entry.value);

        picker.setOnColorChangedListener(color -> {
            int alpha = sbAlpha.getProgress();
            int finalColor = (color & 0x00FFFFFF) | (alpha << 24);
            editHex.setText(String.format("#%08X", finalColor));
        });

        sbAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int color = picker.getColor();
                int finalColor = (color & 0x00FFFFFF) | (progress << 24);
                editHex.setText(String.format("#%08X", finalColor));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        new AlertDialog.Builder(context)
                .setTitle("Seletor de Cores")
                .setView(view)
                .setPositiveButton("Selecionar", (dialog, which) -> {
                    String finalHex = editHex.getText().toString();
                    holder.valueEditText.setText(finalHex);
                    entry.value = finalHex;
                    updateColorPreview(holder.colorPreview, finalHex);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private String normalizeHex(String hex) {
        if (hex == null || !hex.startsWith("#")) return "#000000";
        if (hex.length() == 4) { // #RGB
            return "#" + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2) + hex.charAt(3) + hex.charAt(3);
        }
        if (hex.length() == 5) { // #ARGB
            return "#" + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2) + hex.charAt(3) + hex.charAt(3) + hex.charAt(4) + hex.charAt(4);
        }
        return hex;
    }

    private void updateColorPreview(View view, String value) {
        try {
            if (value != null && value.startsWith("#")) {
                view.setBackgroundColor(Color.parseColor(value));
            }
        } catch (Exception e) {
            view.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        TextView typeTextView;
        EditText valueEditText;
        View colorPreview;
        TextWatcher textWatcher;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.resource_name);
            typeTextView = itemView.findViewById(R.id.resource_type);
            valueEditText = itemView.findViewById(R.id.resource_value);
            colorPreview = itemView.findViewById(R.id.color_preview);
        }
    }
}
