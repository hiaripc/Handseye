// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.
package org.pytorch.demo.handseye

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.common.util.concurrent.ListenableFuture
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.demo.handseye.databinding.ActivityMainBinding
import java.io.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding : ActivityMainBinding
    private lateinit var mTextView : TextView
    private lateinit var mImageView : ImageView
    private lateinit var mResultView : ResultView
    private lateinit var mPreviewView : PreviewView
    private lateinit var mProgressBar :ProgressBar

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var objectAnalyser: MyAnalyser? = null
    private lateinit var cameraExecutor : ExecutorService
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var mModule: Module? = null

    //Floating buttons
    private lateinit var rotateOpen : Animation
    private lateinit var rotateClose  : Animation
    private lateinit var fromBottom : Animation
    private lateinit var toBottom : Animation
    private lateinit var btnPickphoto : FloatingActionButton
    private lateinit var btnTakephoto : FloatingActionButton
    private lateinit var btnBook : FloatingActionButton
    private lateinit var btnAdd : FloatingActionButton
    private lateinit var btnLive : FloatingActionButton
    private var clickedAdd = false

    private fun checkPermissions(){
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1
            )
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    private fun loadAsset(){
        try {
            mModule = LiteModuleLoader.load(
                assetFilePath(
                    applicationContext,
                    "fine-tuned.torchscript.ptl"
                )
            )
            val br = BufferedReader(InputStreamReader(assets.open("alphabet.txt")))
            var line: String?
            val classes: MutableList<String?> = ArrayList()
            while (br.readLine().also { line = it } != null) {
                classes.add(line)
            }
            PrePostProcessor.mClasses = classes.toTypedArray()
        } catch (e: IOException) {
            Log.e("Object Detection", "Error reading assets", e)
            finish()
        }
    }

    private fun bindViewComponents(){
        mTextView = viewBinding.textView
        mImageView = viewBinding.imageView
        mResultView = viewBinding.resultView
        mPreviewView = viewBinding.previewView
        mProgressBar = viewBinding.progressBar
        btnPickphoto = viewBinding.pickphotoBtn
        btnTakephoto = viewBinding.takephotoBtn
        btnBook = viewBinding.bookBtn
        btnAdd = viewBinding.addBtn
        btnLive = viewBinding.liveButton
        rotateOpen = AnimationUtils.loadAnimation(this, R.anim.rotate_open_anim)
        rotateClose  = AnimationUtils.loadAnimation(this, R.anim.rotation_close_anim)
        fromBottom = AnimationUtils.loadAnimation(this, R.anim.from_bottom_anim)
        toBottom = AnimationUtils.loadAnimation(this, R.anim.to_bottom_anim)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //TODO: to not crash on first start, something like that is needed
        //if(!checkPermissions()) checkPermissions
        checkPermissions()
        loadAsset()
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        bindViewComponents()
        setContentView(viewBinding.root)

        objectAnalyser = MyAnalyser(mModule!!,mResultView,mImageView, null)

        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        setupModernCamera()

        btnAdd.setOnClickListener { manageFloatingButtons() }
        btnTakephoto.setOnClickListener {
            manageFloatingButtons()
            manageViewPhoto(false)
            cameraProviderFuture.get().unbindAll()
            val takePicture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(takePicture, 0)
        }
        btnPickphoto.setOnClickListener {
            manageFloatingButtons()
            manageViewPhoto(false)
            cameraProviderFuture.get().unbindAll()
            val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            startActivityForResult(pickPhoto, 1)
        }
        btnBook.setOnClickListener {
            manageFloatingButtons()
            manageViewPhoto(false)
            cameraProviderFuture.get().unbindAll()
            try {
                mImageView.setImageBitmap(
                    BitmapFactory.decodeFile(
                        assetFilePath(
                            applicationContext,
                            "book.jpg"
                        )
                    )
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        btnLive.setOnClickListener {
            manageViewPhoto(true)
            cameraProviderFuture.get().unbindAll()
            cameraProviderFuture.get().bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        }

    }

    private fun manageViewPhoto(live: Boolean){
        mResultView.visibility = View.INVISIBLE
        if (live) {
            mImageView.visibility = View.INVISIBLE
            mPreviewView.visibility = View.VISIBLE
        }
        else {
            mImageView.visibility = View.VISIBLE
            mPreviewView.visibility = View.INVISIBLE
        }
    }

    private fun manageFloatingButtons() {
        clickedAdd = !clickedAdd
        val visib: Int = if (clickedAdd) View.VISIBLE else View.INVISIBLE
        btnTakephoto.visibility = visib
        btnPickphoto.visibility = visib
        btnBook.visibility = visib
        if (clickedAdd) {
            btnAdd.startAnimation(rotateOpen)
            btnPickphoto.startAnimation(fromBottom)
            btnBook.startAnimation(fromBottom)
            btnTakephoto.startAnimation(fromBottom)
        } else {
            btnAdd.startAnimation(rotateClose)
            btnTakephoto.startAnimation(toBottom)
            btnTakephoto.startAnimation(toBottom)
            btnTakephoto.startAnimation(toBottom)
        }
    }

    private fun setupModernCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val provider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                //.setTargetResolution(Size(480,640))
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.previewView.surfaceProvider)
                }

            // capture code
            imageCapture = ImageCapture.Builder().build()

            // analyze code
            imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(480,640))
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, MyAnalyser(mModule!!, mResultView, mImageView) { results ->
                        if (results.size > 0) Log.d("Result:", results[0].classIndex.toString() + " " + results[0].score)
                        runOnUiThread { applyToUiAnalyzeImage(results) }
                    })
                }

            // Select back camera as a default
            // For debug purposes, front camera is easier to test
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                provider.unbindAll()
                // Bind use cases to camera
                provider.bindToLifecycle(this, cameraSelector, preview)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    private fun detect(bitmap: Bitmap?, rotationDegrees: Int) {
        mProgressBar.visibility = ProgressBar.VISIBLE

        val rotatedBitmap = objectAnalyser?.rotateBitmap(bitmap!!, rotationDegrees)
        mImageView.setImageBitmap(rotatedBitmap)

        val results = objectAnalyser?.analyzeImage(bitmap!!)
        if (results != null) runOnUiThread { applyToUiAnalyzeImage(results) }
    }

    private fun applyToUiAnalyzeImage(results: ArrayList<Result>) {
        Log.d("DETECT ", "applying")

        if (results.isEmpty()) Log.e("DETECT ", "No results.")

        mProgressBar.visibility = ProgressBar.INVISIBLE
        mResultView.setResults(results)
        mResultView.invalidate()
        mResultView.visibility = View.VISIBLE

        for (result: Result in results){
            Log.d("DETECT ", PrePostProcessor.mClasses[result.classIndex].toString())

        }
    }

    // TODO when all the important features are finished, this could be updated in a non-deprecated way
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_CANCELED) {
            var rotationDegrees = 0
            var mBitmap : Bitmap? = null
            if (resultCode == Activity.RESULT_OK && data != null) {
                when (requestCode) {
                    0 -> {
                        mBitmap = data.extras?.get("data") as Bitmap
                        rotationDegrees = 0
                    }
                    1 -> {
                        val selectedImage: Uri? = data.data
                        val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
                        if (selectedImage != null) {
                            val cursor: Cursor? = contentResolver.query(
                                selectedImage,
                                filePathColumn, null, null, null
                            )
                            if (cursor != null) {
                                cursor.moveToFirst()
                                val columnIndex = cursor.getColumnIndex(filePathColumn[0])
                                val picturePath = cursor.getString(columnIndex)
                                mBitmap = BitmapFactory.decodeFile(picturePath)
                                rotationDegrees = 0
                                cursor.close()
                            }
                        }
                    }
                }
                //mImageView!!.setImageBitmap(mBitmap)
                detect(mBitmap, rotationDegrees)
            }
        }
    }

    companion object {
        private const val TAG = "Handseye-kotlin"
        @JvmStatic
        @Throws(IOException::class)
        fun assetFilePath(context: Context, assetName: String): String {
            val file = File(context.filesDir, assetName)
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
            context.assets.open(assetName).use { `is` ->
                FileOutputStream(file).use { os ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (`is`.read(buffer).also { read = it } != -1) {
                        os.write(buffer, 0, read)
                    }
                    os.flush()
                }
                return file.absolutePath
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}