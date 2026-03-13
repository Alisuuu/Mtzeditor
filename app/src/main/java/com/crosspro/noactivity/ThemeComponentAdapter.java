package com.crosspro.noactivity;

import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class ThemeComponentAdapter extends RecyclerView.Adapter<ThemeComponentAdapter.ViewHolder> {

    private List<ThemeComponent> components;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ThemeComponent component);
        void onItemLongClick(ThemeComponent component, View view);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public ThemeComponentAdapter(List<ThemeComponent> components) {
        this.components = components;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_theme_component, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ThemeComponent component = components.get(position);
        
        String displayName = component.name;
        if (component.name.startsWith("[MODULE] ")) {
            displayName = getFriendlyModuleName(component.name.substring(9));
        } else if (component.name.startsWith("[DIR] ")) {
            displayName = "Pasta: " + component.name.substring(6);
        } else if (component.name.equals("description.xml")) {
            displayName = "Informações do Tema";
        } else if (component.name.equals("plugin_config.xml")) {
            displayName = "Configuração de Plugins";
        }
        
        holder.componentNameTextView.setText(displayName);

        File file = new File(component.path);
        if (file.exists() && file.isFile()) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
                // Memory optimization: decode scaled version of thumbnail
                holder.componentPreviewImageView.setImageBitmap(decodeSampledBitmapFromFile(file.getAbsolutePath(), 150, 150));
            } else {
                holder.componentPreviewImageView.setImageResource(R.mipmap.ic_launcher);
            }
        } else {
            holder.componentPreviewImageView.setImageResource(R.mipmap.ic_launcher);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(component);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(component, v);
                return true;
            }
            return false;
        });
    }

    private android.graphics.Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        final android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;
        options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565; // Save memory
        return android.graphics.BitmapFactory.decodeFile(path, options);
    }

    private int calculateInSampleSize(android.graphics.BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private String getFriendlyModuleName(String moduleName) {
        switch (moduleName) {
            case "com.android.systemui": return "System UI / Barra de Status";
            case "com.miui.home": return "Tela Inicial / Launcher";
            case "com.android.phone": return "Telefone / Discador";
            case "com.android.contacts": return "Contatos";
            case "com.android.mms": return "Mensagens / SMS";
            case "com.miui.securitycenter": return "Central de Segurança";
            case "com.xiaomi.misettings": return "Configurações MIUI";
            case "framework-res": return "UI do Sistema Global";
            case "lockscreen": return "Tela de Bloqueio";
            case "icons": return "Pacote de Ícones";
            default: return moduleName;
        }
    }

    @Override
    public int getItemCount() {
        return components.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView componentNameTextView;
        public ImageView componentPreviewImageView;

        public ViewHolder(View view) {
            super(view);
            componentNameTextView = view.findViewById(R.id.component_name_textview);
            componentPreviewImageView = view.findViewById(R.id.component_preview_imageview);
        }
    }
}
