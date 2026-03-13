package com.crosspro.noactivity;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ComponentEditorActivity extends AppCompatActivity {

    private ImageView imageView;
    private Button replaceImageButton;
    private EditText editText;
    private RecyclerView resourceRecyclerView;
    private Button saveButton;
    private ThemeComponent component;
    private List<ResourceEntry> resourceEntries;
    private boolean isResourceXml = false;
    private String xmlTemplate = null;
    private Uri pendingImageUri = null;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    pendingImageUri = uri;
                    imageView.setImageURI(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_component_editor);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.editor_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        imageView = findViewById(R.id.editor_image_preview);
        replaceImageButton = findViewById(R.id.editor_replace_image_button);
        editText = findViewById(R.id.editor_text_content);
        resourceRecyclerView = findViewById(R.id.editor_resource_list);
        saveButton = findViewById(R.id.editor_save_button);

        component = (ThemeComponent) getIntent().getSerializableExtra("component");

        if (component != null) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(component.name.replace("[MODULE] ", "").replace("[DIR] ", ""));
            }
            String name = component.name.toLowerCase();
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
                imageView.setVisibility(View.VISIBLE);
                replaceImageButton.setVisibility(View.VISIBLE);
                editText.setVisibility(View.GONE);
                resourceRecyclerView.setVisibility(View.GONE);
                imageView.setImageBitmap(BitmapFactory.decodeFile(component.path));
                
                replaceImageButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
            } else if (name.endsWith("theme_values.xml") || name.endsWith("theme_fallback.xml")) {
                isResourceXml = true;
                imageView.setVisibility(View.GONE);
                editText.setVisibility(View.GONE);
                resourceRecyclerView.setVisibility(View.VISIBLE);
                loadResourceXml(component.path);
            } else if (name.endsWith(".xml") || name.endsWith(".txt") || name.endsWith(".prop")) {
                imageView.setVisibility(View.GONE);
                editText.setVisibility(View.VISIBLE);
                resourceRecyclerView.setVisibility(View.GONE);
                editText.setText(readFileContent(component.path));
            } else {
                imageView.setVisibility(View.GONE);
                editText.setVisibility(View.GONE);
                resourceRecyclerView.setVisibility(View.GONE);
                Toast.makeText(this, "Tipo de arquivo não suportado para edição.", Toast.LENGTH_SHORT).show();
            }
        }

        saveButton.setOnClickListener(v -> {
            saveChanges();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void loadResourceXml(String path) {
        resourceEntries = new ArrayList<>();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            FileInputStream fis = new FileInputStream(path);
            parser.setInput(fis, "UTF-8");

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if (tagName.equals("MIUI_Theme_Values")) {
                        xmlTemplate = parser.getAttributeValue(null, "template");
                    } else if (tagName.equals("color") || tagName.equals("dimen") || tagName.equals("bool") ||
                            tagName.equals("integer") || tagName.equals("drawable") || tagName.equals("string")) {
                        String name = parser.getAttributeValue(null, "name");
                        String pkg = parser.getAttributeValue(null, "package");
                        
                        String value = "";
                        if (parser.next() == XmlPullParser.TEXT) {
                            value = parser.getText();
                        }
                        
                        if (name != null) {
                            resourceEntries.add(new ResourceEntry(tagName, name, value != null ? value.trim() : "", pkg));
                        }
                    }
                }
                eventType = parser.next();
            }
            fis.close();

            ResourceAdapter adapter = new ResourceAdapter(resourceEntries);
            resourceRecyclerView.setAdapter(adapter);
            resourceRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao analisar o XML de recursos.", Toast.LENGTH_SHORT).show();
            // Fallback to text editor
            isResourceXml = false;
            resourceRecyclerView.setVisibility(View.GONE);
            editText.setVisibility(View.VISIBLE);
            editText.setText(readFileContent(path));
        }
    }

    private void saveChanges() {
        if (component != null) {
            try {
                if (pendingImageUri != null) {
                    saveImage(pendingImageUri, component.path);
                } else if (isResourceXml) {
                    try (FileOutputStream fos = new FileOutputStream(component.path)) {
                        fos.write(generateXmlContent().getBytes(StandardCharsets.UTF_8));
                    }
                } else if (editText.getVisibility() == View.VISIBLE) {
                    try (FileOutputStream fos = new FileOutputStream(component.path)) {
                        fos.write(editText.getText().toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                Toast.makeText(this, "Alterações salvas.", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Erro ao salvar as alterações.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveImage(Uri uri, String destPath) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(destPath)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private String generateXmlContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n");
        sb.append("<MIUI_Theme_Values");
        if (xmlTemplate != null && !xmlTemplate.isEmpty()) {
            sb.append(" template=\"").append(xmlTemplate).append("\"");
        }
        sb.append(">\n");

        for (ResourceEntry entry : resourceEntries) {
            if (entry.name == null || entry.value == null) continue;
            sb.append("    <").append(entry.type).append(" name=\"").append(entry.name).append("\"");
            if (entry.pkg != null && !entry.pkg.isEmpty()) {
                sb.append(" package=\"").append(entry.pkg).append("\"");
            }
            sb.append(">").append(entry.value).append("</").append(entry.type).append(">\n");
        }

        sb.append("</MIUI_Theme_Values>");
        return sb.toString();
    }

    private String readFileContent(String path) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content.toString();
    }
}
