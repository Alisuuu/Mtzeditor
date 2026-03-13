package com.crosspro.noactivity;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ColorEditorActivity extends AppCompatActivity implements ColorAdapter.OnColorReplaceListener {

    private RecyclerView recyclerView;
    private ProgressBar loadingProgress;
    private TextView noColorsTextView;
    private TextView colorStatsText;
    private androidx.appcompat.widget.SearchView searchView;
    private ColorAdapter adapter;
    private ChipGroup categoryChips;
    
    private List<ColorEntry> allColorEntries = new ArrayList<>();
    private List<ColorEntry> displayedEntries = new ArrayList<>();
    private File rootDir;
    private static final String TAG = "ColorEditorActivity";

    // General hex color pattern - Ordered from longest to shortest to avoid partial matches
    // Also added negative lookahead to ensure we don't match part of a longer hex string
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![0-9a-fA-F])");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_editor);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.colors_recyclerview);
        loadingProgress = findViewById(R.id.loading_progress);
        noColorsTextView = findViewById(R.id.no_colors_textview);
        colorStatsText = findViewById(R.id.color_stats_text);
        categoryChips = findViewById(R.id.category_chips);
        searchView = findViewById(R.id.color_search_view);

        categoryChips.setOnCheckedChangeListener((group, checkedId) -> applyFilter());
        
        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override public boolean onQueryTextChange(String q) { applyFilter(); return true; }
        });

        String rootPath = getIntent().getStringExtra("root_path");
        if (rootPath != null) {
            rootDir = new File(rootPath);
            scanColors();
        } else {
            finish();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        showExitConfirmation();
        return true;
    }

    @Override
    public void onBackPressed() {
        showExitConfirmation();
    }

    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Sair do Editor")
                .setMessage("Deseja realmente sair? Alterações não salvas podem ser perdidas.")
                .setPositiveButton("Sair", (d, w) -> super.onBackPressed())
                .setNegativeButton("Continuar Editando", null)
                .show();
    }

    private void applyFilter() {
        int chipId = categoryChips.getCheckedChipId();
        String query = searchView.getQuery().toString().toLowerCase().trim();

        String targetCategory = "Tudo";
        if (chipId == R.id.chip_text) targetCategory = "Texto";
        else if (chipId == R.id.chip_icons) targetCategory = "Ícones";
        else if (chipId == R.id.chip_areas) targetCategory = "Áreas";
        else if (chipId == R.id.chip_others) targetCategory = "Outros";

        displayedEntries.clear();
        for (ColorEntry entry : allColorEntries) {
            boolean matchesCategory = targetCategory.equals("Tudo") || entry.category.equals(targetCategory);
            boolean matchesSearch = query.isEmpty() || entry.hexValue.toLowerCase().contains(query);
            
            if (!matchesSearch && !query.isEmpty()) {
                for (ColorOccurrence o : entry.occurrences) {
                    if ((o.filePath != null && o.filePath.toLowerCase().contains(query)) ||
                        (o.attributeName != null && o.attributeName.toLowerCase().contains(query)) ||
                        (o.entryName != null && o.entryName.toLowerCase().contains(query))) {
                        matchesSearch = true;
                        break;
                    }
                }
            }
            
            if (matchesCategory && matchesSearch) {
                displayedEntries.add(entry);
            }
        }
        
        if (adapter != null) adapter.notifyDataSetChanged();
        noColorsTextView.setVisibility(displayedEntries.isEmpty() ? View.VISIBLE : View.GONE);
        if (colorStatsText != null) {
            colorStatsText.setText("Mostrando " + displayedEntries.size() + " de " + allColorEntries.size() + " cores únicas");
        }
    }

    private void scanColors() {
        new ScanColorsTask().execute(rootDir);
    }

    private class ScanColorsTask extends AsyncTask<File, Void, List<ColorEntry>> {
        @Override
        protected void onPreExecute() {
            loadingProgress.setVisibility(View.VISIBLE);
            noColorsTextView.setVisibility(View.GONE);
            if (colorStatsText != null) colorStatsText.setText("Analisando arquivos...");
        }

        @Override
        protected List<ColorEntry> doInBackground(File... files) {
            Map<String, ColorEntry> colorMap = new HashMap<>();
            scanDir(files[0], colorMap);
            
            List<ColorEntry> list = new ArrayList<>(colorMap.values());
            Collections.sort(list, (a, b) -> Integer.compare(b.getCount(), a.getCount()));
            return list;
        }

        private void scanDir(File dir, Map<String, ColorEntry> colorMap) {
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (file.isDirectory()) {
                    if (file.getName().endsWith("_extracted")) {
                        // Scan extracted folders too
                        scanDir(file, colorMap);
                    } else {
                        scanDir(file, colorMap);
                    }
                } else if (file.getName().endsWith(".xml")) {
                    scanFileContent(file.getAbsolutePath(), null, colorMap);
                } else if (isZipFile(file)) {
                    scanZip(file, colorMap);
                }
            }
        }

        private void scanZip(File zipFile, Map<String, ColorEntry> colorMap) {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (ze.getName().endsWith(".xml")) {
                        // Scan the entry without fully unzipping to disk
                        BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                        String line;
                        int lineNum = 1;
                        while ((line = reader.readLine()) != null) {
                            processLine(line, lineNum, zipFile.getAbsolutePath(), ze.getName(), colorMap);
                            lineNum++;
                        }
                    }
                    zis.closeEntry();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error scanning zip: " + zipFile.getName(), e);
            }
        }

        private void scanFileContent(String filePath, String zipPath, Map<String, ColorEntry> colorMap) {
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                int lineNum = 1;
                while ((line = reader.readLine()) != null) {
                    processLine(line, lineNum, filePath, null, colorMap);
                    lineNum++;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error scanning file: " + filePath, e);
            }
        }

        private void processLine(String line, int lineNum, String containerPath, String entryName, Map<String, ColorEntry> colorMap) {
            Matcher m = HEX_PATTERN.matcher(line);
            while (m.find()) {
                String hex = m.group().toUpperCase();
                String attr = findAttributeName(line, m.start());
                String category = categorize(attr);
                
                String key = hex + "_" + category;
                if (!colorMap.containsKey(key)) {
                    colorMap.put(key, new ColorEntry(hex, category));
                }
                
                String displayPath = containerPath;
                if (entryName != null) displayPath += " (" + entryName + ")";
                
                colorMap.get(key).occurrences.add(new ColorOccurrence(containerPath, lineNum, line.trim(), attr, entryName));
            }
        }

        private String findAttributeName(String line, int matchStart) {
            // Try to find what's before the color (e.g., attr=" or <tag name=")
            String before = line.substring(0, matchStart).trim();
            if (before.endsWith("=") || before.endsWith("=\"") || before.endsWith("='")) {
                // It's likely an attribute
                int lastSpace = Math.max(before.lastIndexOf(' '), before.lastIndexOf('\t'));
                if (lastSpace != -1) {
                    String attr = before.substring(lastSpace + 1).replace("=", "").replace("\"", "").replace("'", "");
                    if (!attr.isEmpty()) return attr;
                }
            }
            // If it's a tag value, try to find the tag name or a 'name' attribute in the tag
            int tagStart = before.lastIndexOf('<');
            if (tagStart != -1) {
                String tag = before.substring(tagStart + 1);
                if (tag.contains("name=")) {
                    int nameStart = tag.indexOf("name=") + 6;
                    int nameEnd = tag.indexOf('"', nameStart);
                    if (nameEnd != -1) return tag.substring(nameStart, nameEnd);
                }
                int space = tag.indexOf(' ');
                if (space != -1) return tag.substring(0, space);
                return tag;
            }
            return "unknown";
        }

        private String categorize(String attr) {
            String a = attr.toLowerCase();
            if (a.contains("textcolor") || a.contains("text_color") || a.contains("text")) return "Texto";
            if (a.contains("tint") || a.contains("icon")) return "Ícones";
            if (a.contains("background") || a.contains("solid") || a.contains("stroke") || a.contains("color") || a.contains("fill")) return "Áreas";
            return "Outros";
        }

        private boolean isZipFile(File file) {
            try (InputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[4];
                int read = is.read(buffer);
                return read == 4 && buffer[0] == 0x50 && buffer[1] == 0x4B && buffer[2] == 0x03 && buffer[3] == 0x04;
            } catch (IOException e) { return false; }
        }

        @Override
        protected void onPostExecute(List<ColorEntry> result) {
            loadingProgress.setVisibility(View.GONE);
            allColorEntries = result;
            applyFilter();
            if (adapter == null) {
                adapter = new ColorAdapter(displayedEntries);
                adapter.setOnColorReplaceListener(ColorEditorActivity.this);
                recyclerView.setAdapter(adapter);
            } else {
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onReplaceAll(ColorEntry entry) {
        View v = getLayoutInflater().inflate(R.layout.dialog_color_picker, null);
        TriangleColorPickerView picker = v.findViewById(R.id.triangle_picker);
        SeekBar alphaBar = v.findViewById(R.id.seekbar_alpha);
        EditText hexInput = v.findViewById(R.id.edit_hex_value);
        View contrastPrev = v.findViewById(R.id.contrast_preview);
        LinearLayout recentCont = v.findViewById(R.id.recent_colors_container);
        android.widget.GridLayout presetGrid = v.findViewById(R.id.preset_colors_grid);

        // Standard Material colors
        int[] presets = {0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 
                        0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 
                        0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFF795548, 0xFF9E9E9E};

        // Populate Presets
        for (int c : presets) {
            View dot = createColorDot(c, p -> {
                picker.setColor(p);
                updateColorInDialog(p, alphaBar.getProgress(), hexInput, contrastPrev, picker);
            });
            presetGrid.addView(dot);
        }

        // Load and Populate Recent
        String recentStr = getSharedPreferences("theme_prefs", MODE_PRIVATE).getString("recent_colors", "");
        if (!recentStr.isEmpty()) {
            for (String s : recentStr.split(",")) {
                try {
                    int c = Color.parseColor(s);
                    recentCont.addView(createColorDot(c, p -> {
                        picker.setColor(p);
                        updateColorInDialog(p, alphaBar.getProgress(), hexInput, contrastPrev, picker);
                    }));
                } catch (Exception ignored) {}
            }
        }

        // Initial setup
        int initialColor;
        try { initialColor = Color.parseColor(normalizeHex(entry.hexValue)); } 
        catch (Exception e) { initialColor = Color.BLACK; }

        picker.setColor(initialColor);
        alphaBar.setProgress(Color.alpha(initialColor));
        updateColorInDialog(initialColor, Color.alpha(initialColor), hexInput, contrastPrev, picker);

        // Listeners
        picker.setOnColorChangedListener(c -> updateColorInDialog(c, alphaBar.getProgress(), hexInput, contrastPrev, picker));
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) updateColorInDialog(picker.getColor(), p, hexInput, contrastPrev, picker);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        new AlertDialog.Builder(this)
                .setTitle("Escolher Cor")
                .setView(v)
                .setPositiveButton("Substituir", (d, w) -> {
                    String val = hexInput.getText().toString().trim();
                    if (isValidHex(val)) {
                        saveRecentColor(val);
                        new ReplaceTask(entry, val).execute();
                    } else Toast.makeText(this, "Hex inválido", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private View createColorDot(int color, ColorDotListener l) {
        View dot = new View(this);
        int size = (int)(32 * getResources().getDisplayMetrics().density);
        int margin = (int)(4 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(margin, margin, margin, margin);
        dot.setLayoutParams(lp);
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(color);
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        gd.setStroke(2, Color.parseColor("#444444"));
        dot.setBackground(gd);
        dot.setOnClickListener(v -> l.onColorPicked(color));
        return dot;
    }

    private interface ColorDotListener { void onColorPicked(int color); }

    private void updateColorInDialog(int color, int alpha, EditText hexInput, View contrastPrev, TriangleColorPickerView picker) {
        int finalColor = (color & 0x00FFFFFF) | (alpha << 24);
        hexInput.setText(String.format("#%08X", finalColor));
        contrastPrev.setBackgroundColor(finalColor);
    }

    private void saveRecentColor(String hex) {
        android.content.SharedPreferences prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        String recent = prefs.getString("recent_colors", "");
        if (recent.contains(hex)) return;
        List<String> list = new ArrayList<>(java.util.Arrays.asList(recent.split(",")));
        if (list.size() >= 8) list.remove(list.size() - 1);
        list.add(0, hex);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).isEmpty()) continue;
            sb.append(list.get(i)).append(i == list.size() - 1 ? "" : ",");
        }
        prefs.edit().putString("recent_colors", sb.toString()).apply();
    }

    private String normalizeHex(String hex) {
        if (!hex.startsWith("#")) return "#000000";
        if (hex.length() == 4) { // #RGB
            return "#" + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2) + hex.charAt(3) + hex.charAt(3);
        }
        if (hex.length() == 5) { // #ARGB
            return "#" + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2) + hex.charAt(3) + hex.charAt(3) + hex.charAt(4) + hex.charAt(4);
        }
        return hex;
    }

    private boolean isValidHex(String h) {
        if (h == null || !h.startsWith("#")) return false;
        String val = h.substring(1);
        int len = val.length();
        // Android supports #RGB, #ARGB, #RRGGBB, #AARRGGBB
        if (len != 3 && len != 4 && len != 6 && len != 8) return false;
        
        for (char c : val.toCharArray()) {
            if (Character.digit(c, 16) == -1) return false;
        }
        return true;
    }

    private class ReplaceTask extends AsyncTask<Void, Void, Boolean> {
        private ColorEntry entry;
        private String newHex;
        private ProgressDialog pd;

        public ReplaceTask(ColorEntry e, String n) { this.entry = e; this.newHex = n; }

        @Override
        protected void onPreExecute() { pd = ProgressDialog.show(ColorEditorActivity.this, "Substituindo", "Processando...", true); }

        @Override
        protected Boolean doInBackground(Void... v) {
            Map<String, List<ColorOccurrence>> groups = new HashMap<>();
            for (ColorOccurrence o : entry.occurrences) {
                if (!groups.containsKey(o.filePath)) groups.put(o.filePath, new ArrayList<>());
                groups.get(o.filePath).add(o);
            }
            for (String path : groups.keySet()) {
                try {
                    File f = new File(path);
                    if (f.getName().endsWith(".xml")) {
                        replaceInNormalFile(path, groups.get(path), entry.hexValue, newHex);
                    } else {
                        // It's a zip file (module)
                        replaceInZipFile(path, groups.get(path), entry.hexValue, newHex);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error replacing in: " + path, e);
                    return false;
                }
            }
            return true;
        }

        private void replaceInNormalFile(String path, List<ColorOccurrence> occs, String oldHex, String newHex) throws IOException {
            File f = new File(path);
            File tmp = new File(path + ".tmp");
            try (BufferedReader r = new BufferedReader(new FileReader(f));
                 BufferedWriter w = new BufferedWriter(new FileWriter(tmp))) {
                String line;
                int lineNum = 1;
                while ((line = r.readLine()) != null) {
                    for (ColorOccurrence o : occs) {
                        if (o.lineNumber == lineNum) {
                            line = line.replace(oldHex, newHex);
                            // Also handle lowercase if it was found that way
                            line = line.replace(oldHex.toLowerCase(), newHex);
                        }
                    }
                    w.write(line);
                    w.newLine();
                    lineNum++;
                }
            }
            if (f.delete()) tmp.renameTo(f);
        }

        private void replaceInZipFile(String zipPath, List<ColorOccurrence> occs, String oldHex, String newHex) throws IOException {
            File zipFile = new File(zipPath);
            File tempZip = new File(zipPath + ".tmp");
            
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
                 ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
                
                ZipEntry ze;
                byte[] buffer = new byte[4096];
                while ((ze = zis.getNextEntry()) != null) {
                    zos.putNextEntry(new ZipEntry(ze.getName()));
                    
                    boolean shouldModify = false;
                    for (ColorOccurrence o : occs) {
                        if (ze.getName().equals(o.entryName)) {
                            shouldModify = true;
                            break;
                        }
                    }
                    
                    if (shouldModify) {
                        // Read, modify, and write
                        BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                        BufferedWriter writer = new BufferedWriter(new java.io.OutputStreamWriter(zos));
                        String line;
                        int lineNum = 1;
                        while ((line = reader.readLine()) != null) {
                            for (ColorOccurrence o : occs) {
                                if (ze.getName().equals(o.entryName) && o.lineNumber == lineNum) {
                                    line = line.replace(oldHex, newHex);
                                    line = line.replace(oldHex.toLowerCase(), newHex);
                                }
                            }
                            writer.write(line);
                            writer.newLine();
                            lineNum++;
                        }
                        writer.flush(); // Crucial to flush to ZipOutputStream
                    } else {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                    zis.closeEntry();
                }
            }
            if (zipFile.delete()) tempZip.renameTo(zipFile);
        }

        @Override
        protected void onPostExecute(Boolean s) {
            pd.dismiss();
            if (s) { scanColors(); Toast.makeText(ColorEditorActivity.this, "Sucesso!", Toast.LENGTH_SHORT).show(); }
            else Toast.makeText(ColorEditorActivity.this, "Erro durante a substituição!", Toast.LENGTH_SHORT).show();
        }
    }
}

