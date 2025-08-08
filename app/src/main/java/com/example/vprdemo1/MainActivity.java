package com.example.vprdemo1;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;
import org.opencv.android.OpenCVLoader;

import com.example.vprdemo1.databinding.ActivityMainBinding;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "OCV";

    // Used to load the 'vprdemo1' library on application startup.
    static {
        System.loadLibrary("vprdemo1");
    }

    public native String cvVersion();

//    private ActivityMainBinding binding;
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        binding = ActivityMainBinding.inflate(getLayoutInflater());
//        setContentView(binding.getRoot());
//
//        // Example of a call to a native method
//        TextView tv = binding.sampleText;
//        tv.setText(stringFromJNI());
//    }

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        if (OpenCVLoader.initLocal()){
            Log.i(TAG, "OpenCV loaded");
        }
        else{
            Log.e(TAG, "OpenCV init failed");
            Toast.makeText(this, "OpenCV init failed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        TextView tv = findViewById(R.id.sample_text);
        tv.setText("OpenCV: " + cvVersion());
    }

    /**
     * A native method that is implemented by the 'vprdemo1' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}