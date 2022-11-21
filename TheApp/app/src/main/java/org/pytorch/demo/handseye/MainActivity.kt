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
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.service.textservice.SpellCheckerService
import android.util.Log
import android.util.Size
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.textservice.*
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

class MainActivity : AppCompatActivity(), SpellCheckerSessionListener{
    private lateinit var viewBinding : ActivityMainBinding
    private lateinit var mTextView : TextView
    private lateinit var mImageView : ImageView
    private lateinit var mResultView : ResultView
    private lateinit var mPreviewView : PreviewView
    private lateinit var mProgressBar :ProgressBar
    private lateinit var mAccuracyBar: SeekBar
    private lateinit var mAccuracyLayout: LinearLayout
    private lateinit var mAccuracyTextView: TextView
    //private val  mySpellChecker : SpellCheckerService.Session = MySpellCheckerService().createSession()

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var objectAnalyser: MyAnalyser? = null
    private lateinit var cameraExecutor : ExecutorService
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var mModule: Module? = null
    private var lastPrediction : Int = -1
    private var countPrediction : Int = 0

    //Number of frame to consider a prediction valid to be printed in textView
    private val countPredictionThresh = 2

    private val takePictureLauncher  = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onTakePictureResult(
            result
        )
    }
    private val pickPictureLauncher  = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onPickPictureResult(
            result
        )
    }

    //Floating buttons
    private lateinit var rotateOpen : Animation
    private lateinit var rotateClose  : Animation
    private lateinit var fromBottom : Animation
    private lateinit var toBottom : Animation
    private lateinit var btnPickPicture : FloatingActionButton
    private lateinit var btnTakePicture : FloatingActionButton
    private lateinit var btnBook : FloatingActionButton
    private lateinit var btnAdd : FloatingActionButton
    private lateinit var btnLive : FloatingActionButton
    private var clickedAdd = false
    private var clickedLive = false
    private var clickedBook = false

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
        mAccuracyBar = viewBinding.accuracyBar
        mAccuracyLayout = viewBinding.accuracyLayout
        mAccuracyTextView = viewBinding.accuracyText
        btnPickPicture = viewBinding.pickphotoBtn
        btnTakePicture = viewBinding.takephotoBtn
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
        /*
        //Usage: (text, max_suggestion_number)
        Log.e("SPELL", "starting")
        //mySpellChecker.onGetSuggestions(TextInfo("Hel"), 2)
        val tsm = getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as TextServicesManager
        val spellCheckSession = tsm.newSpellCheckerSession(null,null,this,true)

        spellCheckSession!!.getSuggestions(TextInfo("aiot"), 5)
        */
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
        btnTakePicture.setOnClickListener {
            cameraProviderFuture.get().unbindAll()
            manageFloatingButtons()
            manageViewPhoto(0)
            val takePicture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureLauncher.launch(takePicture)
        }
        btnPickPicture.setOnClickListener {
            cameraProviderFuture.get().unbindAll()
            manageFloatingButtons()
            manageViewPhoto(0)
            val pickPicture = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            pickPictureLauncher.launch(pickPicture)
        }
        btnBook.setOnClickListener {
            cameraProviderFuture.get().unbindAll()
            manageFloatingButtons()
            manageViewPhoto(0)
            clickedBook = true
            mAccuracyLayout.visibility = View.VISIBLE
            mTextView.visibility = View.INVISIBLE

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
            cameraProviderFuture.get().unbindAll()
            clickedLive = !clickedLive
            manageViewPhoto(1)
            if (clickedLive) {
                btnLive.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9D0006"))
                cameraProviderFuture.get()
                    .bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                mTextView.text = ""
            } else {
                cameraProviderFuture.get()
                    .bindToLifecycle(this, cameraSelector, preview)
                btnLive.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F9F5D7"))
            }
        }
        mTextView.setOnClickListener {
            mTextView.text = ""
        }

        mAccuracyBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                mAccuracyTextView.text = String.format("%.2f", (p1.toFloat()/100))
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                if (p0 != null) PrePostProcessor.mThreshold = (p0.progress.toFloat() / 100)
            }

        })

    }

    private fun manageViewPhoto(case: Int){
        mResultView.visibility = View.INVISIBLE

        if(clickedBook) {
            mAccuracyLayout.visibility = View.INVISIBLE
            mTextView.visibility = View.VISIBLE
            clickedBook = false
        }

        when(case){
            //Picture
            0 -> {
                mImageView.visibility = View.VISIBLE
                mPreviewView.visibility = View.INVISIBLE
            }
            //Camera active
            1 -> {
                mImageView.visibility = View.INVISIBLE
                mPreviewView.visibility = View.VISIBLE
            }
        }
    }

    private fun manageFloatingButtons() {
        clickedAdd = !clickedAdd
        val visib: Int = if (clickedAdd) View.VISIBLE else View.INVISIBLE
        btnTakePicture.visibility = visib
        btnPickPicture.visibility = visib
        btnBook.visibility = visib
        if (clickedAdd) {
            btnAdd.startAnimation(rotateOpen)
            btnPickPicture.startAnimation(fromBottom)
            btnBook.startAnimation(fromBottom)
            btnTakePicture.startAnimation(fromBottom)
        } else {
            btnAdd.startAnimation(rotateClose)
            btnTakePicture.startAnimation(toBottom)
            btnTakePicture.startAnimation(toBottom)
            btnTakePicture.startAnimation(toBottom)
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
                    it.setAnalyzer(cameraExecutor, MyAnalyser(mModule!!, mResultView, mImageView) { result ->
                        if (result != null) {
                            val textResult = getTextResults(result, false)
                            runOnUiThread { applyToUiAnalyzeImage(result,textResult) }
                        }
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
        mTextView.text = ""

        val rotatedBitmap = objectAnalyser?.rotateBitmap(bitmap!!, rotationDegrees)
        mImageView.setImageBitmap(rotatedBitmap)

        val results = objectAnalyser?.analyzeImage(bitmap!!)
        if (results != null) {
            val textResult = getTextResults(results,true)
            runOnUiThread { applyToUiAnalyzeImage(results, textResult)}
        }
    }

    private fun getTextResults(result: Result?, isPicture : Boolean) : String {
        //For our use case, we should consider only one prediction in the image.
        //ATM, considering the best among results.
        //Only if the same prediction persist for at least 2 frames, we are actually writing it
        var textResult = ""
        if (result == null) return textResult

        val predictionText = PrePostProcessor.mClasses[result.classIndex].toString()

        //If is a photo, just return the best value.
        if (isPicture) return predictionText

        //Check if the prediction is not sporadic
        val predictionIdx = result.classIndex
        if (lastPrediction == predictionIdx) {
            countPrediction++
            if (countPrediction == countPredictionThresh) {
                textResult = predictionText
                countPrediction = 0
            }
        } else {
            lastPrediction = predictionIdx
            countPrediction = 0
        }

        return textResult
    }
    private fun applyToUiAnalyzeImage(result: Result?, textResult: String) {
        Log.d("DETECT ", "applying")

        if (result == null) Log.e("DETECT ", "No results.")
        Log.d(
            "Result:",
            result!!.classIndex.toString() + " " + result.score
        )
        mProgressBar.visibility = ProgressBar.INVISIBLE
        mResultView.setResult(result)
        mResultView.invalidate()
        mResultView.visibility = View.VISIBLE

        if (textResult.isNotBlank()) mTextView.text = String.format("%s %s", mTextView.text, textResult)

    }

    private fun onPickPictureResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val selectedImage: Uri? = result.data!!.data
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
                    val mBitmap = BitmapFactory.decodeFile(picturePath)
                    val rotationDegrees = 0
                    cursor.close()
                    detect(mBitmap, rotationDegrees)
                }
                else Log.e("Pick photo", "There is some problem with")
            }
        }
    }

    private fun onTakePictureResult(result : ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val mBitmap = result.data!!.extras?.get("data") as Bitmap
            val rotationDegrees = 0
            detect(mBitmap, rotationDegrees)
        }
        else Log.e("Pick photo", "There is some problem with")

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

    override fun onGetSuggestions(suggestions: Array<out SuggestionsInfo>?) {
        var s = ""
        if (suggestions == null || suggestions.isEmpty()) {
            Log.e("SPELLGET", "No suggestions")
            return
        }
        for (suggestion in suggestions){
            Log.e("SPELLGET","New suggestion")
            val count = suggestion.suggestionsCount
            Log.e("SPELLGET", "$count suggestions")
            if (count > 0) {
                for (i in 0..count) {
                    s = "$s, ${suggestion.getSuggestionAt(i)}"
                }
                Log.e("SPELLGET",s)
            }
        }

    }

    override fun onGetSentenceSuggestions(p0: Array<out SentenceSuggestionsInfo>?) {
        var s = ""
        for (suggestion in p0!!){
            s = "$s,"

        }
        Log.e("SPELLGET","")
    }
}