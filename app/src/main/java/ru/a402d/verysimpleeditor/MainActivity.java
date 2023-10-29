package ru.a402d.verysimpleeditor;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuCompat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    static final int MAX_SIZE = 500000;
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final Handler handler = new Handler(Looper.getMainLooper());

    private EditText inputArea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        inputArea = findViewById(R.id.input_area);
    }

    // --------------
    // Menu
    // ---------------

    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.open_file){
            openDocument();
        }
        if(item.getItemId() == R.id.save_file){
            saveDocument();
        }

       if (item.getItemId() == R.id.speechrequest) { // -------------------------------------
           displaySpeechRecognizer();
       }

        return super.onOptionsItemSelected(item);
    }

    // ----------------
    // Open
    // ----------------
    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::fileSelected
    );
    void openDocument() {
        try{
            mGetContent.launch("*/*");
        }catch (Exception e){
            Toast.makeText(this,R.string.need_external_fm,Toast.LENGTH_LONG).show();
        }
    }

    private void fileSelected(Uri p) {
        if(p == null) return;

        executor.execute(()->{
            try {
                final String string = readTxtFile(getContentResolver().openInputStream(p),MAX_SIZE,this);
                handler.post(()->inputArea.setText(string));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        });

    }

    static String readTxtFile(InputStream inputStream, int limit, Context context) {
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        long l = 0;
        String line;
        try {

            br = new BufferedReader(new InputStreamReader(inputStream));
            while ((line = br.readLine()) != null) {
                l += line.length();
                if (l > limit) {
                    return context.getString(R.string.ErrorOverFileSize)+"\n\n";
                }
                sb.append(line).append("\n");
            }

        } catch (Exception e) {
            if(e.getLocalizedMessage()!=null) {
                sb.append("\nERROR\n").append(e.getLocalizedMessage()).append("\n");
            }
            e.printStackTrace();

        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }

    // ------------------
    // SAVE AS
    // ------------------
    private final ActivityResultLauncher<Intent> mSaveContent = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            this::saveSelected);

    void saveDocument(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault());
        String cTime = sdf.format(new Date());

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, cTime+".txt");
        mSaveContent.launch(intent);
    }

    private void saveSelected(ActivityResult result){
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                final Uri uri = data.getData();
                if(uri == null){
                    // show error
                    return;
                }
                final String text = inputArea.getText().toString();
                executor.execute(() -> {
                    try {
                        OutputStream outputStream =  getContentResolver().openOutputStream(uri);
                        Objects.requireNonNull(outputStream).write(text.getBytes());
                        outputStream.flush();
                        outputStream.close();
                    } catch (Exception e) {
                        // show error
                        e.printStackTrace();
                    }

                });

            }
        }

    }

    private final ActivityResultLauncher<Intent>  speechActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    try{
                        Intent data = result.getData();
                        List<String> results = Objects.requireNonNull(data).getStringArrayListExtra(
                                RecognizerIntent.EXTRA_RESULTS);

                        String spokenText = Objects.requireNonNull(results).get(0);
                        inputArea.getText().insert(inputArea.getSelectionStart(), spokenText+" ");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

    private void displaySpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        try {
            speechActivityResultLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, getString(R.string.need_speach_engine), Toast.LENGTH_LONG).show();
        }
    }
}