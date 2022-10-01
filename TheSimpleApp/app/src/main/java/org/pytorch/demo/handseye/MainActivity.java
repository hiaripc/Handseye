// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

package org.pytorch.demo.handseye;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.UseCase;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ImageView mImageView;
    private ResultView mResultView;
    private TextureView mtextureView;
    private SurfaceTexture mSurfaceTexture;
    PreviewConfig previewConfig;
    private ProgressBar mProgressBar;
    private Bitmap mBitmap = null;
    private Module mModule;
    private long mLastAnalysisResultTime;
    private ImageAnalysis imageAnalysis;
    private Preview previewImage;
    private ImageAnalyser imageAnalyser;
    private LifecycleOwner lifecycleOwner = this;

    //Floating buttons
    private Animation rotateOpen;
    private Animation rotateClose;
    private Animation fromBottom;
    private Animation toBottom;
    private FloatingActionButton btnPickphoto;
    private FloatingActionButton btnTakephoto;
    private FloatingActionButton btnBook;
    private FloatingActionButton btnAdd;
    private FloatingActionButton btnLive;

    private boolean clickedAdd = false;

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        setContentView(R.layout.activity_main);

        rotateOpen = AnimationUtils.loadAnimation(this, R.anim.rotate_open_anim);
        rotateClose = AnimationUtils.loadAnimation(this, R.anim.rotation_close_anim);
        fromBottom = AnimationUtils.loadAnimation(this, R.anim.from_bottom_anim);
        toBottom = AnimationUtils.loadAnimation(this, R.anim.to_bottom_anim);

        mImageView = findViewById(R.id.imageView);
        mImageView.setVisibility(View.INVISIBLE);
        mResultView = findViewById(R.id.resultView);
        mResultView.setVisibility(View.INVISIBLE);

        btnPickphoto = findViewById(R.id.pickphoto_btn);
        btnTakephoto = findViewById(R.id.takephoto_btn);
        btnBook = findViewById(R.id.book_btn);

        setupCameraX();
        cameraBinding("bind", new UseCase[]{previewImage});

        btnAdd = findViewById(R.id.add_btn);
        btnAdd.setOnClickListener((View v) -> manageFloatingButtons());

        btnTakephoto.setOnClickListener((View v) -> {
            cameraBinding("unbind", new UseCase[]{previewImage, imageAnalysis});
            manageFloatingButtons();
            Intent takePicture = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(takePicture, 0);
        });

        btnPickphoto.setOnClickListener((View v) -> {
            cameraBinding("unbind", new UseCase[]{previewImage, imageAnalysis});
            manageFloatingButtons();
            Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
            startActivityForResult(pickPhoto, 1);
        });

        btnBook.setOnClickListener((View v) -> {
            manageFloatingButtons();
            cameraBinding("unbind", new UseCase[]{previewImage, imageAnalysis});
            mImageView.setVisibility(View.VISIBLE);
            try {
                mImageView.setImageBitmap(BitmapFactory.decodeFile(assetFilePath(getApplicationContext(),"book.jpg")));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        btnLive = findViewById(R.id.liveButton);
        btnLive.setOnClickListener((View v) -> {
            mImageView.setVisibility(View.INVISIBLE);
            setupCameraX();
            cameraBinding("bind", new UseCase[]{previewImage, imageAnalysis});
        });

        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);

        try {
            mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "fine-tuned.torchscript.ptl"));
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open("alphabet.txt")));
            String line;
            List<String> classes = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                classes.add(line);
            }
            PrePostProcessor.mClasses = new String[classes.size()];
            classes.toArray(PrePostProcessor.mClasses);
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }
    }

    private void cameraBinding(String operation, UseCase [] uses){
            for (UseCase aCase : uses)
                switch (operation){
                case "bind":
                    if(!CameraX.isBound(aCase))
                        CameraX.bindToLifecycle(lifecycleOwner, aCase);
                    break;
                case "unbind":
                    if(CameraX.isBound(aCase))
                        CameraX.unbind(aCase);
                    break;
                }
    }

    private void manageFloatingButtons() {
        clickedAdd = !clickedAdd;
        int visib;
        if (clickedAdd)
            visib = View.VISIBLE;
        else visib = View.INVISIBLE;

        btnTakephoto.setVisibility(visib);
        btnPickphoto.setVisibility(visib);
        btnBook.setVisibility(visib);

        if (clickedAdd) {
            btnAdd.startAnimation(rotateOpen);
            btnPickphoto.startAnimation(fromBottom);
            btnBook.startAnimation(fromBottom);
            btnTakephoto.startAnimation(fromBottom);
        } else {
            btnAdd.startAnimation(rotateClose);
            btnTakephoto.startAnimation(toBottom);
            btnTakephoto.startAnimation(toBottom);
            btnTakephoto.startAnimation(toBottom);

        }
    }


    private void setupCameraX() {
        CameraX.unbindAll();
        imageAnalyser = new ImageAnalyser(mResultView, getApplicationContext());
        mtextureView = findViewById(R.id.object_detection_texture_view);

        previewConfig = new PreviewConfig.Builder().build();
        previewImage = new Preview(previewConfig);
        previewImage.setOnPreviewOutputUpdateListener(output -> {
            //Code here is to prevent the crashing after taking/picking a picture.
            ViewGroup parent = (ViewGroup) mtextureView.getParent();
            parent.removeView(mtextureView);
            parent.addView(mtextureView,0);
            mSurfaceTexture = output.getSurfaceTexture();
            mtextureView.setSurfaceTexture(mSurfaceTexture);});

        final ImageAnalysisConfig imageAnalysisConfig =
                new ImageAnalysisConfig.Builder()
                        .setTargetResolution(new Size(480, 640))
                        .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                        .build();

        imageAnalysis = new ImageAnalysis(imageAnalysisConfig);
        imageAnalysis.setAnalyzer((image, rotationDegrees) -> {
            if (SystemClock.elapsedRealtime() - mLastAnalysisResultTime < 500) {
                return;
            }
            final ImageAnalyser.AnalysisResult result = imageAnalyser.
                    analyzeImage(imageAnalyser.imgToBitmap(image.getImage()), 90);
            if (result != null) {
                mLastAnalysisResultTime = SystemClock.elapsedRealtime();
                runOnUiThread(() -> applyToUiAnalyzeImageResult(result));
            }
        });
    }

    protected void detect(Bitmap bitmap) {
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
        mImageView.setImageBitmap(mBitmap);
        final ImageAnalyser.AnalysisResult result = imageAnalyser.analyzeImage(bitmap, 0);
        if (result != null)
            runOnUiThread(() -> applyToUiAnalyzeImageResult(result));
    }

    protected void applyToUiAnalyzeImageResult(ImageAnalyser.AnalysisResult result) {
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);
        mResultView.setResults(result.mResults);
        mResultView.invalidate();
        mResultView.setVisibility(View.VISIBLE);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            mImageView.setVisibility(View.VISIBLE);
            if (resultCode == RESULT_OK && data != null) {
                switch (requestCode) {
                    case 0:
                        mBitmap = (Bitmap) data.getExtras().get("data");
                        detect(mBitmap);
                        break;
                    case 1:
                        Uri selectedImage = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (selectedImage != null) {
                            Cursor cursor = getContentResolver().query(selectedImage,
                                    filePathColumn, null, null, null);
                            if (cursor != null) {
                                cursor.moveToFirst();
                                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                                String picturePath = cursor.getString(columnIndex);
                                mBitmap = BitmapFactory.decodeFile(picturePath);
                                cursor.close();
                            }
                        }
                        break;
                }
                detect(mBitmap);
            }
        }

    }
}
