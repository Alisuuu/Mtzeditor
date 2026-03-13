package com.crosspro.noactivity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Stack;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity implements ThemeComponentAdapter.OnItemClickListener {

    private static final String TAG = "MainActivity";

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<String> saveFileLauncher;
    private ActivityResultLauncher<Intent> editorLauncher;


    private TextView titleTextView;
    private TextView authorTextView;
    private TextView designerTextView;
    private TextView versionTextView;
    private TextView uiVersionTextView;
    private TextView miuiAdapterVersionTextView;
    private RecyclerView componentsRecyclerView;
    private RecyclerView previewsRecyclerView;
    private ImageView headerWallpaperView;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabAddComponent;
    private com.google.android.material.button.MaterialButton applyThemeButton;
    private ThemeComponentAdapter adapter;
    private File rootThemeDir;
    private File currentThemeDir;
    private Stack<File> dirHistory = new Stack<>();
    private ThemeComponent componentToReplace;
    private ThemeDescription currentThemeDescription;

    private final Shizuku.OnRequestPermissionResultListener SHIZUKU_PERMISSION_LISTENER = 
        (requestCode, grantResult) -> {
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissão do Shizuku concedida!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissão do Shizuku negada.", Toast.LENGTH_SHORT).show();
            }
        };

    private final ActivityResultLauncher<Intent> replaceComponentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && componentToReplace != null) {
                    Uri uri = result.getData().getData();
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         OutputStream out = new FileOutputStream(componentToReplace.path)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                        Toast.makeText(this, "Componente substituído com sucesso!", Toast.LENGTH_SHORT).show();
                        setupRecyclerView(currentThemeDir);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Erro ao substituir componente.", Toast.LENGTH_SHORT).show();
                    }
                    componentToReplace = null;
                }
            });

    private final ActivityResultLauncher<Intent> addComponentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    // Get filename from URI if possible, or ask user? 
                    // For now, let's just copy it to currentThemeDir.
                    // This is a bit complex without a filename.
                    Toast.makeText(this, "Funcionalidade de adicionar arquivo externo em desenvolvimento.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        titleTextView = findViewById(R.id.title_textview);
        authorTextView = findViewById(R.id.author_textview);
        designerTextView = findViewById(R.id.designer_textview);
        versionTextView = findViewById(R.id.version_textview);
        uiVersionTextView = findViewById(R.id.ui_version_textview);
        miuiAdapterVersionTextView = findViewById(R.id.miui_adapter_version_textview);
        componentsRecyclerView = findViewById(R.id.components_recyclerview);
        previewsRecyclerView = findViewById(R.id.previews_recyclerview);
        headerWallpaperView = findViewById(R.id.theme_wallpaper_header);
        fabAddComponent = findViewById(R.id.fab_add_component);
        applyThemeButton = findViewById(R.id.apply_theme_button);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        com.google.android.material.button.MaterialButton manageColorsButton = findViewById(R.id.manage_colors_button);
        manageColorsButton.setOnClickListener(v -> {
            if (rootThemeDir != null && rootThemeDir.exists()) {
                Intent intent = new Intent(this, ColorEditorActivity.class);
                intent.putExtra("root_path", rootThemeDir.getAbsolutePath());
                startActivity(intent);
            } else {
                Toast.makeText(this, "Selecione um tema primeiro!", Toast.LENGTH_SHORT).show();
            }
        });

        android.widget.Button selectMtzButton = findViewById(R.id.select_mtz_button);
        android.widget.Button saveMtzButton = findViewById(R.id.save_mtz_button);
        fabAddComponent.setOnClickListener(v -> showAddComponentDialog());

        findViewById(R.id.edit_theme_info_button).setOnClickListener(v -> showEditThemeInfoDialog());
        applyThemeButton.setOnClickListener(v -> handleApplyTheme());

        Shizuku.addRequestPermissionResultListener(SHIZUKU_PERMISSION_LISTENER);

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        Log.d(TAG, "Selected file: " + uri.toString());
                        handleSelectedFile(uri);
                    }
                });

        saveFileLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"),
                uri -> {
                    if (uri != null) {
                        repackageTheme(uri);
                    }
                });

        editorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (currentThemeDir != null) {
                            setupRecyclerView(currentThemeDir);
                            if (rootThemeDir != null) {
                                updateThemeVisuals(rootThemeDir);
                            }
                        }
                    }
                });

        selectMtzButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            filePickerLauncher.launch(intent);
        });

        saveMtzButton.setOnClickListener(v -> {
            if (rootThemeDir != null) {
                saveFileLauncher.launch("themedited.mtz");
            } else {
                Toast.makeText(this, "Por favor, selecione um tema primeiro.", Toast.LENGTH_SHORT).show();
            }
        });

        // Restore state if theme exists in cache
        rootThemeDir = new File(getCacheDir(), "mtz_theme");
        if (rootThemeDir.exists() && rootThemeDir.isDirectory() && rootThemeDir.list() != null && rootThemeDir.list().length > 0) {
            currentThemeDir = rootThemeDir;
            fabAddComponent.setVisibility(View.VISIBLE);
            applyThemeButton.setVisibility(View.VISIBLE);
            updateThemeVisuals(rootThemeDir);
            currentThemeDescription = parseThemeDescription(rootThemeDir);
            if (currentThemeDescription != null) {
                updateThemeInfoUI(currentThemeDescription);
            }
            setupRecyclerView(rootThemeDir);
        }

        // Ensure accessibility service is enabled if we have Shizuku
        if (ShizukuUtils.isShizukuAvailable() && ShizukuUtils.hasPermission()) {
            if (!AccessibilityUtils.isAccessibilityServiceEnabled(this, HyperThemeService.class)) {
                AccessibilityUtils.enableAccessibilityService(getPackageName() + "/" + HyperThemeService.class.getName());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Background check to ensure service is alive
        if (ShizukuUtils.isShizukuAvailable() && ShizukuUtils.hasPermission()) {
            if (!AccessibilityUtils.isAccessibilityServiceEnabled(this, HyperThemeService.class)) {
                AccessibilityUtils.enableAccessibilityService(getPackageName() + "/" + HyperThemeService.class.getName());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(SHIZUKU_PERMISSION_LISTENER);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!dirHistory.isEmpty()) {
            File previousDir = dirHistory.pop();
            // If we are going back from an extracted module, we might want to zip it back?
            // Actually, we should only zip back when clicking "Save" or "Apply".
            // For now, let's just navigate.
            currentThemeDir = previousDir;
            setupRecyclerView(currentThemeDir);
        } else {
            super.onBackPressed();
        }
    }

    private void handleSelectedFile(Uri uri) {
        rootThemeDir = new File(getCacheDir(), "mtz_theme");
        currentThemeDir = rootThemeDir;
        dirHistory.clear();
        fabAddComponent.setVisibility(View.GONE);
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (!rootThemeDir.exists()) {
                rootThemeDir.mkdirs();
            }
            // Clear the temp directory before unzipping
            deleteRecursive(rootThemeDir);
            rootThemeDir.mkdirs();

            unzipStream(inputStream, rootThemeDir);
            inputStream.close();
            Log.d(TAG, "MTZ file unzipped successfully to: " + rootThemeDir.getAbsolutePath());
            fabAddComponent.setVisibility(View.VISIBLE);
            applyThemeButton.setVisibility(View.VISIBLE);
            updateThemeVisuals(rootThemeDir);
            currentThemeDescription = parseThemeDescription(rootThemeDir);
            if (currentThemeDescription != null) {
                updateThemeInfoUI(currentThemeDescription);
            }
            setupRecyclerView(rootThemeDir);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error unzipping MTZ file", e);
        }
    }

    private void updateThemeVisuals(File themeDir) {
        // Set header wallpaper
        File wallpaperFile = new File(themeDir, "wallpaper/default_wallpaper.jpg");
        if (wallpaperFile.exists()) {
            // Memory optimization: decode a scaled version of the wallpaper
            headerWallpaperView.setImageBitmap(decodeSampledBitmapFromFile(wallpaperFile.getAbsolutePath(), 800, 450));
            headerWallpaperView.setOnClickListener(v -> {
                ThemeComponent component = new ThemeComponent(wallpaperFile.getName(), wallpaperFile.getAbsolutePath());
                Intent intent = new Intent(this, ComponentEditorActivity.class);
                intent.putExtra("component", component);
                editorLauncher.launch(intent);
            });
        }

        // Set previews
        File previewDir = new File(themeDir, "preview");
        if (previewDir.exists() && previewDir.isDirectory()) {
            List<File> previews = new ArrayList<>();
            File[] previewFiles = previewDir.listFiles();
            if (previewFiles != null) {
                for (File f : previewFiles) {
                    if (f.getName().endsWith(".jpg") || f.getName().endsWith(".png")) {
                        previews.add(f);
                    }
                }
            }
            PreviewAdapter previewAdapter = new PreviewAdapter(previews);
            previewAdapter.setOnItemClickListener(previewFile -> {
                ThemeComponent component = new ThemeComponent(previewFile.getName(), previewFile.getAbsolutePath());
                Intent intent = new Intent(this, ComponentEditorActivity.class);
                intent.putExtra("component", component);
                editorLauncher.launch(intent);
            });
            previewsRecyclerView.setAdapter(previewAdapter);
        }
    }

    // Helper to decode scaled bitmaps and save memory
    private android.graphics.Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        final android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;
        options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565; // Use 16-bit colors to save memory
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

    private void unzipStream(InputStream is, File outputDir) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(is);
        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            File newFile = new File(outputDir, zipEntry.getName());
            if (zipEntry.isDirectory()) {
                newFile.mkdirs();
            } else {
                newFile.getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zipInputStream.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zipInputStream.closeEntry();
        }
        zipInputStream.close();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }


    private void showEditThemeInfoDialog() {
        if (currentThemeDescription == null) {
            Toast.makeText(this, "Nenhuma informação do tema carregada.", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_edit_theme_info, null);
        com.google.android.material.textfield.TextInputEditText editTitle = view.findViewById(R.id.edit_title);
        com.google.android.material.textfield.TextInputEditText editAuthor = view.findViewById(R.id.edit_author);
        com.google.android.material.textfield.TextInputEditText editDesigner = view.findViewById(R.id.edit_designer);
        com.google.android.material.textfield.TextInputEditText editVersion = view.findViewById(R.id.edit_version);
        com.google.android.material.textfield.TextInputEditText editUiVersion = view.findViewById(R.id.edit_ui_version);
        com.google.android.material.textfield.TextInputEditText editMiuiAdapter = view.findViewById(R.id.edit_miui_adapter);

        editTitle.setText(currentThemeDescription.title);
        editAuthor.setText(currentThemeDescription.author);
        editDesigner.setText(currentThemeDescription.designer);
        editVersion.setText(currentThemeDescription.version);
        editUiVersion.setText(currentThemeDescription.uiVersion);
        editMiuiAdapter.setText(currentThemeDescription.miuiAdapterVersion);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Editar Informações do Tema")
                .setView(view)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    currentThemeDescription.title = editTitle.getText().toString();
                    currentThemeDescription.author = editAuthor.getText().toString();
                    currentThemeDescription.designer = editDesigner.getText().toString();
                    currentThemeDescription.version = editVersion.getText().toString();
                    currentThemeDescription.uiVersion = editUiVersion.getText().toString();
                    currentThemeDescription.miuiAdapterVersion = editMiuiAdapter.getText().toString();

                    saveThemeDescriptionToXml();
                    updateThemeInfoUI(currentThemeDescription);
                    Toast.makeText(this, "Informações atualizadas!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveThemeDescriptionToXml() {
        File descriptionFile = new File(rootThemeDir, "description.xml");
        try (FileOutputStream fos = new FileOutputStream(descriptionFile)) {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>\n");
            sb.append("<theme>\n");
            sb.append("<version><![CDATA[").append(currentThemeDescription.version).append("]]></version>\n");
            if (currentThemeDescription.uiVersion != null) {
                sb.append("<uiVersion>").append(currentThemeDescription.uiVersion).append("</uiVersion>\n");
            }
            sb.append("<author><![CDATA[").append(currentThemeDescription.author).append("]]></author>\n");
            sb.append("<designer><![CDATA[").append(currentThemeDescription.designer).append("]]></designer>\n");
            sb.append("<title><![CDATA[").append(currentThemeDescription.title).append("]]></title>\n");
            
            if (currentThemeDescription.description != null) {
                sb.append("<description><![CDATA[").append(currentThemeDescription.description).append("]]></description>\n");
            }

            // Localized titles
            if (!currentThemeDescription.localizedTitles.isEmpty()) {
                sb.append("<titles>\n");
                for (java.util.Map.Entry<String, String> entry : currentThemeDescription.localizedTitles.entrySet()) {
                    sb.append("<title locale=\"").append(entry.getKey()).append("\"><![CDATA[").append(entry.getValue()).append("]]></title>\n");
                }
                sb.append("</titles>\n");
            }

            // Localized authors
            if (!currentThemeDescription.localizedAuthors.isEmpty()) {
                sb.append("<authors>\n");
                for (java.util.Map.Entry<String, String> entry : currentThemeDescription.localizedAuthors.entrySet()) {
                    sb.append("<author locale=\"").append(entry.getKey()).append("\"><![CDATA[").append(entry.getValue()).append("]]></author>\n");
                }
                sb.append("</authors>\n");
            }

            // Localized designers
            if (!currentThemeDescription.localizedDesigners.entrySet().isEmpty()) {
                sb.append("<designers>\n");
                for (java.util.Map.Entry<String, String> entry : currentThemeDescription.localizedDesigners.entrySet()) {
                    sb.append("<designer locale=\"").append(entry.getKey()).append("\"><![CDATA[").append(entry.getValue()).append("]]></designer>\n");
                }
                sb.append("</designers>\n");
            }

            if (currentThemeDescription.miuiAdapterVersion != null) {
                sb.append("<miuiAdapterVersion>").append(currentThemeDescription.miuiAdapterVersion).append("</miuiAdapterVersion>\n");
            }
            
            sb.append("</theme>\n");
            fos.write(sb.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao salvar description.xml", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateThemeInfoUI(ThemeDescription themeDescription) {
        titleTextView.setText("Título: " + (themeDescription.title != null ? themeDescription.title : ""));
        authorTextView.setText("Autor: " + (themeDescription.author != null ? themeDescription.author : ""));
        designerTextView.setText("Designer: " + (themeDescription.designer != null ? themeDescription.designer : ""));
        versionTextView.setText("Versão: " + (themeDescription.version != null ? themeDescription.version : ""));
        uiVersionTextView.setText("Versão da UI: " + (themeDescription.uiVersion != null ? themeDescription.uiVersion : ""));
        miuiAdapterVersionTextView.setText("Adaptador MIUI: " + (themeDescription.miuiAdapterVersion != null ? themeDescription.miuiAdapterVersion : ""));
    }

    private ThemeDescription parseThemeDescription(File themeDir) {
        File descriptionFile = new File(themeDir, "description.xml");
        if (!descriptionFile.exists()) {
            return null;
        }

        ThemeDescription themeDescription = new ThemeDescription();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            FileInputStream fis = new FileInputStream(descriptionFile);
            parser.setInput(fis, null);

            int eventType = parser.getEventType();
            String tagName = null;
            String currentLocale = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    tagName = parser.getName();
                    String locale = parser.getAttributeValue(null, "locale");
                    if (locale != null) {
                        currentLocale = locale;
                    }
                } else if (eventType == XmlPullParser.TEXT && tagName != null) {
                    String text = parser.getText();
                    if (text != null) {
                        text = text.trim();
                    }
                    if (text != null && !text.isEmpty()) {
                        switch (tagName) {
                            case "title":
                                if (currentLocale != null) {
                                    themeDescription.localizedTitles.put(currentLocale, text);
                                } else {
                                    themeDescription.title = text;
                                }
                                break;
                            case "author":
                                if (currentLocale != null) {
                                    themeDescription.localizedAuthors.put(currentLocale, text);
                                } else {
                                    themeDescription.author = text;
                                }
                                break;
                            case "designer":
                                if (currentLocale != null) {
                                    themeDescription.localizedDesigners.put(currentLocale, text);
                                } else {
                                    themeDescription.designer = text;
                                }
                                break;
                            case "version":
                                themeDescription.version = text;
                                break;
                            case "uiVersion":
                                themeDescription.uiVersion = text;
                                break;
                            case "miuiAdapterVersion":
                                themeDescription.miuiAdapterVersion = text;
                                break;
                            case "description":
                                if (currentLocale != null) {
                                    themeDescription.localizedDescriptions.put(currentLocale, text);
                                } else {
                                    themeDescription.description = text;
                                }
                                break;
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (parser.getName().equals("author") || parser.getName().equals("designer") ||
                            parser.getName().equals("title") || parser.getName().equals("description")) {
                        currentLocale = null;
                    }
                    tagName = null;
                }
                eventType = parser.next();
            }
            fis.close();
            
            // Set default values if not present
            if (themeDescription.title == null && !themeDescription.localizedTitles.isEmpty()) {
                themeDescription.title = themeDescription.localizedTitles.values().iterator().next();
            }
            if (themeDescription.author == null && !themeDescription.localizedAuthors.isEmpty()) {
                themeDescription.author = themeDescription.localizedAuthors.values().iterator().next();
            }
            if (themeDescription.designer == null && !themeDescription.localizedDesigners.isEmpty()) {
                themeDescription.designer = themeDescription.localizedDesigners.values().iterator().next();
            }
            if (themeDescription.description == null && !themeDescription.localizedDescriptions.isEmpty()) {
                themeDescription.description = themeDescription.localizedDescriptions.values().iterator().next();
            }
            
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error parsing description.xml", e);
            return null;
        }

        return themeDescription;
    }

    private void setupRecyclerView(File themeDir) {
        if (getSupportActionBar() != null) {
            if (themeDir.equals(rootThemeDir)) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            } else {
                getSupportActionBar().setDisplayShowTitleEnabled(true);
                getSupportActionBar().setTitle(themeDir.getName().replace("_extracted", ""));
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
        List<ThemeComponent> components = new ArrayList<>();
        File[] files = themeDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith("_extracted")) continue; // Hide the extracted module folders
                if (file.getName().equals("description.xml") || file.getName().equals("plugin_config.xml")) {
                    components.add(new ThemeComponent(file.getName(), file.getAbsolutePath()));
                } else if (file.isDirectory()) {
                    // Previews and Wallpapers folders
                    components.add(new ThemeComponent("[DIR] " + file.getName(), file.getAbsolutePath()));
                } else if (isZipFile(file)) {
                    components.add(new ThemeComponent("[MODULE] " + file.getName(), file.getAbsolutePath()));
                } else {
                    components.add(new ThemeComponent(file.getName(), file.getAbsolutePath()));
                }
            }
        }
        adapter = new ThemeComponentAdapter(components);
        adapter.setOnItemClickListener(this);
        componentsRecyclerView.setAdapter(adapter);
        
        // Use Grid for Root, List for others? Or Grid for all? 
        // Let's use Grid for all for consistency.
        componentsRecyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        
        // Show previews only at root
        if (themeDir.equals(rootThemeDir)) {
            findViewById(R.id.previews_recyclerview).setVisibility(View.VISIBLE);
            // wallpaper already updated in handleSelectedFile
        } else {
            findViewById(R.id.previews_recyclerview).setVisibility(View.GONE);
        }
    }

    private boolean isZipFile(File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[4];
            int read = is.read(buffer);
            if (read != 4) return false;
            // PK\x03\x04
            return buffer[0] == 0x50 && buffer[1] == 0x4B && buffer[2] == 0x03 && buffer[3] == 0x04;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void onItemClick(ThemeComponent component) {
        File file = new File(component.path);
        if (file.isDirectory()) {
            dirHistory.push(currentThemeDir);
            currentThemeDir = file;
            setupRecyclerView(currentThemeDir);
        } else if (isZipFile(file)) {
            // It's a module! Extract it to a temporary directory with the same name + _extracted
            File extractedDir = new File(file.getParent(), file.getName() + "_extracted");
            if (!extractedDir.exists()) {
                extractedDir.mkdirs();
                try (InputStream fis = new FileInputStream(file)) {
                    unzipStream(fis, extractedDir);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Erro ao extrair o módulo.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            dirHistory.push(currentThemeDir);
            currentThemeDir = extractedDir;
            setupRecyclerView(currentThemeDir);
        } else {
            Intent intent = new Intent(this, ComponentEditorActivity.class);
            intent.putExtra("component", component);
            editorLauncher.launch(intent);
        }
    }

    @Override
    public void onItemLongClick(ThemeComponent component, View view) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, view);
        popup.getMenu().add("Excluir");
        popup.getMenu().add("Substituir");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Excluir")) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Excluir")
                        .setMessage("Deseja realmente excluir o componente '" + component.name + "'?")
                        .setPositiveButton("Sim", (dialog, which) -> {
                            deleteRecursive(new File(component.path));
                            setupRecyclerView(currentThemeDir);
                            Toast.makeText(this, "Componente excluído!", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Não", null)
                        .show();
            } else if (item.getTitle().equals("Substituir")) {
                componentToReplace = component;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                replaceComponentLauncher.launch(intent);
            }
            return true;
        });
        popup.show();
    }

    private void showAddComponentDialog() {
        if (currentThemeDir == null) {
            Toast.makeText(this, "Abra um tema primeiro!", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = {"Criar Nova Pasta", "Criar Novo Componente"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Adicionar")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showCreateFileDialog(true);
                    } else if (which == 1) {
                        showCreateFileDialog(false);
                    }
                })
                .show();
    }

    private void showCreateFileDialog(boolean isDir) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(isDir ? "ex: wallpaper" : "ex: com.android.systemui");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(isDir ? "Nova Pasta" : "Novo Componente")
                .setView(input)
                .setPositiveButton("Criar", (dialog, which) -> {
                    String name = input.getText().toString();
                    if (!name.isEmpty()) {
                        File newFile = new File(currentThemeDir, name);
                        try {
                            if (isDir) {
                                newFile.mkdirs();
                            } else {
                                newFile.createNewFile();
                            }
                            setupRecyclerView(currentThemeDir);
                            Toast.makeText(this, "Criado com sucesso!", Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Erro ao criar!", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void checkBatteryOptimization() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Otimização de Bateria")
                    .setMessage("Para que o tema não seja resetado pelo sistema, defina o CrossS MTZ como 'Sem restrições' nas configurações de bateria.")
                    .setPositiveButton("Configurar", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            Intent intent = new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Pular", null)
                    .show();
        }
    }

    private void handleApplyTheme() {
        if (rootThemeDir == null) return;

        checkBatteryOptimization();

        if (!ShizukuUtils.isShizukuAvailable()) {
            Toast.makeText(this, "Shizuku não está disponível ou não está rodando.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!ShizukuUtils.hasPermission()) {
            Shizuku.requestPermission(1001);
            return;
        }

        if (!AccessibilityUtils.isAccessibilityServiceEnabled(this, HyperThemeService.class)) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Acessibilidade Necessária")
                    .setMessage("Para aplicar o tema automaticamente e ignorar erros de autorização, ative o serviço de acessibilidade do CrossS MTZ.")
                    .setPositiveButton("Ativar", (dialog, which) -> {
                        // Try to open the specific service settings via Intent first
                        try {
                            String serviceId = getPackageName() + "/" + HyperThemeService.class.getName();
                            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            // Some Android versions support pointing to the specific service
                            android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri); 
                            startActivity(intent);
                        } catch (Exception e) {
                            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Continuar Sem", (dialog, which) -> applyTheme())
                    .show();
        } else {
            applyTheme();
        }
    }

    private void applyTheme() {
        try {
            // 1. Repackage everything to a temporary MTZ in a public-ish location
            // Shell user (Shizuku) can access Android/data/com.crosspro.noactivity/files
            repackageModulesRecursive(rootThemeDir);
            File tempDir = getExternalFilesDir(null);
            if (tempDir == null) tempDir = getCacheDir();
            File tempMtz = new File(tempDir, "cross_apply_temp.mtz");
            zipDirToFile(rootThemeDir, tempMtz);

            // 2. Define MIUI path
            String miuiPath = "/sdcard/Android/data/com.android.thememanager/files/snapshot/snapshot.mtz";
            String miuiDir = "/sdcard/Android/data/com.android.thememanager/files/snapshot/";

            // 3. Use Shizuku to create directory and copy file
            ShizukuUtils.execShizuku("mkdir -p " + miuiDir);
            boolean copySuccess = ShizukuUtils.execShizuku("cp -f " + tempMtz.getAbsolutePath() + " " + miuiPath);
            
            // Set permissions so Theme Manager can read it
            ShizukuUtils.execShizuku("chmod 666 " + miuiPath);

            if (copySuccess) {
                // Save applied theme path for restoration logic in AccessibilityService
                getSharedPreferences("theme_prefs", MODE_PRIVATE).edit()
                        .putString("last_applied_mtz", tempMtz.getAbsolutePath())
                        .putBoolean("auto_restore_enabled", true)
                        .apply();
            } else {
                Toast.makeText(this, "Erro ao copiar tema. Verifique se o Shizuku tem permissão para acessar arquivos.", Toast.LENGTH_LONG).show();
                return;
            }

            // 4. Launch MIUI Theme Installer
            Intent intent = new Intent();
            intent.setClassName("com.android.thememanager", "com.android.thememanager.ApplyThemeForScreenshot");
            intent.putExtra("theme_file_path", miuiPath);
            intent.putExtra("ver2_step", "ver2_step_apply");
            intent.putExtra("api_called_from", "com.miui.themestore");
            intent.putExtra("theme_apply_flags", 1);
            
            try {
                startActivity(intent);
                Toast.makeText(this, "Gerenciador de Temas iniciado!", Toast.LENGTH_SHORT).show();
                // Nudge GC after heavy operation
                System.gc();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Erro ao abrir o instalador de temas da MIUI.", Toast.LENGTH_LONG).show();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao preparar arquivo para aplicação.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            // App backgrounded, clear image memory
            if (headerWallpaperView != null) headerWallpaperView.setImageBitmap(null);
            if (previewsRecyclerView != null) previewsRecyclerView.setAdapter(null);
            if (componentsRecyclerView != null) componentsRecyclerView.setAdapter(null);
            System.gc();
        }
    }

    private void repackageTheme(Uri uri) {
        try {
            // Before packaging the whole MTZ, we need to repackage any extracted modules!
            repackageModulesRecursive(rootThemeDir);

            OutputStream os = getContentResolver().openOutputStream(uri);
            ZipOutputStream zos = new ZipOutputStream(os);
            addDirToZip(rootThemeDir, zos, rootThemeDir);
            zos.close();
            os.close();
            Toast.makeText(this, "Tema salvo com sucesso!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao salvar o tema.", Toast.LENGTH_SHORT).show();
        }
    }

    private void repackageModulesRecursive(File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                if (file.getName().endsWith("_extracted")) {
                    // It's an extracted module! Zip it back to the original file name
                    String originalName = file.getName().substring(0, file.getName().length() - "_extracted".length());
                    File targetFile = new File(file.getParent(), originalName);
                    zipDirToFile(file, targetFile);
                    // We can choose to keep or delete the extracted dir, let's keep it for now but maybe delete it after zipping?
                    // deleteRecursive(file); 
                } else {
                    repackageModulesRecursive(file);
                }
            }
        }
    }

    private void zipDirToFile(File dir, File targetFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(targetFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            addDirToZip(dir, zos, dir);
        }
    }

    private void addDirToZip(File dir, ZipOutputStream zos, File baseDir) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[1024];

        for (File file : files) {
            if (file.isDirectory()) {
                if (file.getName().endsWith("_extracted")) continue; // Skip extracted dirs when zipping
                addDirToZip(file, zos, baseDir);
                continue;
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                String entryPath = file.getCanonicalPath().substring(baseDir.getCanonicalPath().length() + 1);
                ZipEntry zipEntry = new ZipEntry(entryPath);
                zos.putNextEntry(zipEntry);
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }
                zos.closeEntry();
            }
        }
    }
}
