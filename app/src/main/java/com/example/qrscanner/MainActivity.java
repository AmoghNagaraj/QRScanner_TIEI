package com.example.qrscanner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;


import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;

import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private MaterialButton saveButton;
    private ImageView imageIv;
    private ImageView imageView;
    private TextView resultTv;
    private static final int CAMERA_REQUEST_CODE=100;
    private static final int STORAGE_REQUEST_CODE=101;
    private String[] cameraPermissions;
    private BarcodeScanner barcodeScanner;
    private static final String TAG="MAIN_TAG";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture ;
    private ImageCapture imageCapture;
    PreviewView previewView;
    private String textValue = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MaterialButton scanBtn = findViewById(R.id.scanBtn);
        resultTv=findViewById(R.id.resultTv);
        MaterialButton captureBtn = findViewById(R.id.captureBtn);
        previewView=findViewById(R.id.previewView);
        imageView=findViewById(R.id.imageView);
        saveButton=findViewById(R.id.saveButton);
        MaterialButton resetBtn = findViewById(R.id.resetBtn);
        cameraPermissions=new String[]{Manifest.permission.CAMERA};
        //String[] storagePermissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        BarcodeScannerOptions barcodeScannerOptions = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();

        barcodeScanner= BarcodeScanning.getClient(barcodeScannerOptions);

        resetBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                textValue="";
                imageView.setImageBitmap(null);
                resultTv.setText(textValue);
            }
        });
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(()->{
            try {
                ProcessCameraProvider cameraProvider=cameraProviderFuture.get();
                startCameraX(cameraProvider);
            }
            catch (ExecutionException | InterruptedException e){
                e.printStackTrace();
            }
        },getExecutor());

        captureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(checkCameraPermission()){
                    capturePhoto();
                }
                else {
                    requestCameraPermission();
                }
            }
        });

        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(checkCameraPermission()){
                    Bitmap bitmap = previewView.getBitmap();
                    detectResultFromImage(bitmap);
                }
                else {
                    requestCameraPermission();
                }
            }
        });
    }

    private void capturePhoto() {
        File tempFile = new File(getCacheDir(), "temp.jpg");
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(tempFile).build();
        imageCapture.takePicture(
                outputFileOptions,
                getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Bitmap bitmap = BitmapFactory.decodeFile(tempFile.getAbsolutePath());
                        imageView.setImageBitmap(bitmap);
                        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        saveButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                File photoDir = new File("/storage/emulated/0/Pictures/DefectPartData");
                                if (!photoDir.exists()) {
                                    photoDir.mkdir();
                                }
                                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                                String timeStamp = dateFormat.format(new Date());
                                String photoFilePath = photoDir.getAbsolutePath() + "/" + textValue + "_" + timeStamp + ".jpg";
                                File photoFile = new File(photoFilePath);
                                try {
                                    OutputStream outputStream = null;
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        outputStream = Files.newOutputStream(photoFile.toPath());
                                    }
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100,outputStream);
                                    assert outputStream != null;
                                    outputStream.close();
                                    Toast.makeText(MainActivity.this, "Photo has been saved successfully.", Toast.LENGTH_SHORT).show();
                                    imageView.setImageBitmap(null);

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this,"Error saving photo"+exception.getMessage(),Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    private void startCameraX(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();

        CameraSelector cameraSelector=new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview=new Preview.Builder().build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        cameraProvider.bindToLifecycle((LifecycleOwner) this,cameraSelector,preview,imageCapture);
    }

    private void detectResultFromImage(Bitmap bitmap) {
        try{
            InputImage inputImage;
            inputImage = InputImage.fromBitmap(bitmap, 0);
            Task<List<Barcode>> barcodeResult=barcodeScanner.process(inputImage)
                    .addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
                        @Override
                        public void onSuccess(List<Barcode> barcodes) {
                            extractBarCodeQRCodeInfo(barcodes);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(MainActivity.this,"Failed scanning due to "+e.getMessage(),Toast.LENGTH_SHORT).show();
                        }
                    });
        }
        catch (Exception e){
            Toast.makeText(MainActivity.this,"Failed scanning due to"+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("SetTextI18n")
    private void extractBarCodeQRCodeInfo(List<Barcode> barcodes) {
        for(Barcode barcode : barcodes){
            String rawValue = barcode.getRawValue();
            Log.d(TAG,"extractBarCodeQRInfo: rawValue:"+rawValue);
            int valueType=barcode.getValueType();
            if (valueType == Barcode.TYPE_TEXT) {
                textValue = barcode.getRawValue();
                Log.d(TAG, "Extracted Barcode QR Info: text: " + textValue);
                resultTv.setText("Value: " + textValue);
            } else {
                resultTv.setText("Value: " + rawValue);
            }
        }
    }

    private void pickImageGallery(){
        Intent intent=new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryActivityResultLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> galleryActivityResultLauncher=registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if(result.getResultCode()== Activity.RESULT_OK){
                        Intent data=result.getData();
                        assert data != null;
                        Uri imageUri = data.getData();
                        Log.d(TAG,"onActivityResult: imageUri:"+ imageUri);
                        imageIv.setImageURI(imageUri);
                    }
                    else{
                        Toast.makeText(MainActivity.this,"Cancelled",Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private void requestStoragePermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
    }

    private boolean checkCameraPermission(){
        return ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED; //&& resultStorage;
    }

    private void requestCameraPermission(){
        ActivityCompat.requestPermissions(this,cameraPermissions,CAMERA_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode){
            case CAMERA_REQUEST_CODE:{
                if(grantResults.length>0){
                    Toast.makeText(this,"Camera permission is required",Toast.LENGTH_SHORT).show();
                }
            }
            break;
            case STORAGE_REQUEST_CODE: {
                if (grantResults.length>0){
                    boolean storageAccepted=grantResults[0]==PackageManager.PERMISSION_GRANTED;

                    if(storageAccepted){
                        pickImageGallery();
                    }
                    else {
                        Toast.makeText(this,"Storage permission is required",Toast.LENGTH_SHORT).show();
                    }
                }
            }
            break;
        }

    }
}