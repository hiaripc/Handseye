package com.example.handseye

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.handseye.databinding.ActivityMainBinding
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import java.io.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity(), View.OnClickListener, ImageAnalysis.Analyzer, Runnable{

    private lateinit var viewBinding: ActivityMainBinding
    
    private val executor: Executor
        private get() = ContextCompat.getMainExecutor(this)


    //private var picture_bt: Button? = null
    private var analysis_bt: Button? = null
    //private var pView: PreviewView? = null
    private var viewFinder: PreviewView? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnl: ImageAnalysis? = null
    private var analysis_on = false
    private var mModule: Module? = null
    private lateinit var cameraExecutor: ExecutorService
    private val mBitmap: Bitmap? = null

    companion object {
        private const val TAG = "Handseye"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }


    // This analyzer should implement fun analize, a function that would operate with the taken
    // picture.
    // Useful links:
    //   pytorch mobile - https://github.com/pytorch/android-demo-app/tree/master/ObjectDetection
    //   android app analyze - https://developer.android.com/codelabs/camerax-getting-started
    private class YoloAnalyzer() : ImageAnalysis.Analyzer {

        fun Image.toBitmap(): Bitmap {
            val yBuffer = planes[0].buffer // Y
            val vuBuffer = planes[2].buffer // VU

            val ySize = yBuffer.remaining()
            val vuSize = vuBuffer.remaining()

            val nv21 = ByteArray(ySize + vuSize)

            yBuffer.get(nv21, 0, ySize)
            vuBuffer.get(nv21, ySize, vuSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            /*image.image.toBitmap()
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()*/
        }
    }

    @Throws(IOException::class)
    private fun assetFilePath(context: Context, assetName: String?): String? {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName!!).use { `is` ->
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


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        /* NB:
            analysis_bt.setOnClickListener(this)
            You can't write this in Kotlin. That's cause analysis_bt is a variable,
            so Kotlin doesn't permit to use a smart cast.
            Following the right way to do it:
        */
        viewBinding.btnSee.setOnClickListener { takePhoto() }
        cameraExecutor = Executors.newSingleThreadExecutor()
        //analysis_bt?.setOnClickListener(capturePhoto())
        /* Explanation:
            let operator is a Kotlin Scope function which permits to execute in the object
            context, in which 'it' refers to the calling object.
            In Kotlin is not possible to assign a null value to an object, unless is initialized
            with the operator '?'
            var a: String = "abc"
            var b: String? = "def"
            a = null // OK
            b = null // !!! ERROR

            The safe call operator '?.' only execute that action if the calling object is not null.
            It's the same to write if (b is not null).

         */

        //viewFinder = findViewById(R.id.viewFinder)
        viewFinder = viewBinding.viewFinder

        analysis_on = false
        /*
        val provider: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)
        provider.addListener({
            try {
                //val cameraProvider: ProcessCameraProvider = provider.get()
                startCamera()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, executor)
        */
        try {
            mModule = LiteModuleLoader.load(
                this.assetFilePath(
                    applicationContext,
                    "yolov5s.torchscript.ptl"
                )
            )
            val br = BufferedReader(InputStreamReader(assets.open("classes.txt")))

            var line: String?
            val classes: MutableList<String?> = ArrayList()
            while (br.readLine().also { line = it } != null) {
                classes.add(line)
            }
            PrePostProcessor.mClasses = arrayOfNulls<String>(classes.size)
            PrePostProcessor.mClasses = classes.toTypedArray()
            //classes.toArray(PrePostProcessor.mClasses)
        } catch (e: IOException) {
            Log.e("Object Detection", "Error reading assets", e)
            finish()
        }

    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HandseyePics")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun detect(mBitmap : Bitmap){

        var mImgScaleX = mBitmap.getWidth().toFloat() / PrePostProcessor.mInputWidth
        var mImgScaleY = mBitmap.getHeight().toFloat() / PrePostProcessor.mInputHeight

        /*var mIvScaleX = if (mBitmap.getWidth() > mBitmap.getHeight()) mImageView.getWidth()
            .toFloat() / mBitmap.getWidth() else mImageView.getHeight()
            .toFloat() / mBitmap.getHeight()
        var mIvScaleY = if (mBitmap.getHeight() > mBitmap.getWidth()) mImageView.getHeight()
            .toFloat() / mBitmap.getHeight() else mImageView.getWidth()
            .toFloat() / mBitmap.getWidth()

        var mStartX = (mImageView.getWidth() - mIvScaleX * mBitmap.getWidth()) / 2
        var mStartY = (mImageView.getHeight() - mIvScaleY * mBitmap.getHeight()) / 2
        */
        val thread = Thread(this@MainActivity)
        thread.start()
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA),
            REQUEST_CODE_PERMISSIONS
        )
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE),
            REQUEST_CODE_PERMISSIONS
        )
        ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_CODE_PERMISSIONS
        )
    }

    private fun checkPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            //Permission is not granted
            false
        } else true
    }

    private fun checkStoragePermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
            ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //Permission is not granted
            false
        } else true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // capture code
            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnSee -> capturePhoto()
            //R.id.btnSee -> analysis_on = !analysis_on
        }
    }

    private fun capturePhoto() {
        print(" - - - - CAPTURING PICTURE - - - - ")
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    override fun analyze(image: ImageProxy) {
        if (analysis_on) {
            var conv = toBitmap(image)
            conv = toGrayscale(conv)
            //viewFinder!!.set .setImageBitmap(conv)
        }
        image.close()
    }

    private fun toBitmap(image: ImageProxy): Bitmap {
        val yBuffer: ByteBuffer = image.getPlanes().get(0).getBuffer()
        val uBuffer: ByteBuffer = image.getPlanes().get(2).getBuffer()
        val vBuffer: ByteBuffer = image.getPlanes().get(2).getBuffer()
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        //U and V are swapped
        yBuffer[nv21, 0, ySize]
        vBuffer[nv21, 0, uSize]
        uBuffer[nv21, 0, vSize]
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.height, yuvImage.height), 75, out)
        val imageBytes = out.toByteArray()
        var res = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val mat = Matrix()
        //angle is the desired angle to rotate
        mat.postRotate(90f)
        res = Bitmap.createBitmap(res, 0, 0, res.width, res.height, mat, true)
        return res
    }

    fun toGrayscale(bmpOriginal: Bitmap): Bitmap {
        val width: Int
        val height: Int
        height = bmpOriginal.height
        width = bmpOriginal.width
        val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmpGrayscale)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val f = ColorMatrixColorFilter(cm)
        paint.colorFilter = f
        c.drawBitmap(bmpOriginal, 0f, 0f, paint)
        return bmpGrayscale
    }

    override fun run() {
        TODO("Not yet implemented")
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}