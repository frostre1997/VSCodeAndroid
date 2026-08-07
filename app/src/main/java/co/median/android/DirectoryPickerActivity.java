package co.median.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Simple folder browser used to pick a directory for a Git clone or to
 * open an existing repository. Returns the selected path via
 * {@link #EXTRA_PATH}.
 */
public class DirectoryPickerActivity extends AppCompatActivity {

    public static final String EXTRA_START_DIR = "startDir";
    public static final String EXTRA_PATH = "path";

    private TextView pathLabel;
    private Button selectButton;
    private ListView listView;
    private File currentDir;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_directory_picker);

        pathLabel = findViewById(R.id.picker_path);
        selectButton = findViewById(R.id.picker_select);
        listView = findViewById(R.id.picker_list);

        String start = getIntent().getStringExtra(EXTRA_START_DIR);
        File startDir = start != null ? new File(start) : Environment.getExternalStorageDirectory();
        if (!startDir.isDirectory()) {
            startDir = Environment.getExternalStorageDirectory();
        }

        selectButton.setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra(EXTRA_PATH, currentDir.getAbsolutePath());
            setResult(Activity.RESULT_OK, data);
            finish();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            DirectoryEntry entry = (DirectoryEntry) parent.getItemAtPosition(position);
            if (entry.isParent) {
                File parentDir = currentDir.getParentFile();
                if (parentDir != null) navigateTo(parentDir);
            } else {
                navigateTo(entry.file);
            }
        });

        navigateTo(startDir);
    }

    private void navigateTo(File dir) {
        currentDir = dir;
        pathLabel.setText(dir.getAbsolutePath());

        List<DirectoryEntry> entries = new ArrayList<>();
        File parent = dir.getParentFile();
        if (parent != null && parent.isDirectory()) {
            entries.add(new DirectoryEntry(null, "..", true));
        }

        File[] children = dir.listFiles();
        if (children != null) {
            List<File> folders = new ArrayList<>(Arrays.asList(children));
            Collections.sort(folders, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File f : folders) {
                if (f.isDirectory() && !f.isHidden()) {
                    entries.add(new DirectoryEntry(f, f.getName(), false));
                }
            }
        }

        listView.setAdapter(new DirectoryAdapter(this, android.R.layout.simple_list_item_1, entries));
    }

    static class DirectoryEntry {
        final File file;
        final String name;
        final boolean isParent;

        DirectoryEntry(File file, String name, boolean isParent) {
            this.file = file;
            this.name = name;
            this.isParent = isParent;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static class DirectoryAdapter extends android.widget.ArrayAdapter<DirectoryEntry> {
        DirectoryAdapter(Activity context, int resource, List<DirectoryEntry> entries) {
            super(context, resource, entries);
        }
    }
}
