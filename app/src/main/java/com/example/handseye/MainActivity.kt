package com.example.handseye

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.handseye.databinding.ActivityMainBinding
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias YoloListener = (results: ArrayList<Result>) -> Unit

class MainActivity : AppCompatActivity(), Runnable{

    private lateinit var viewBinding: ActivityMainBinding

    private var viewFinder: PreviewView? = null
    private var imageCapture: ImageCapture? = null
    // this property if true adds the ImageAnalysis case to the lifecycle of the camera
    // if false, real time analysis will not start
    private var analysisOn = true
    private var mModule: Module? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var mBitmap: Bitmap
    private var mResultView: ResultView? = null
    private var txtResult: TextView? = null
    private var detectionUri: Uri = Uri.parse(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString() + "/Pictures/HandseyePics/detection.jpg")

    private var mImgScaleX: Float? = null
    private var mImgScaleY: Float? = null
    private var mIvScaleX: Float? = null
    private  var mIvScaleY:Float? = null
    private  var mStartX:Float? = null
    private  var mStartY:Float? = null

    private val model : String = "fine-tuned.torchscript.ptl"
    private val classes : String = "alphabet.txt"

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
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }.toTypedArray()
    }

    /*
    This analyzer should implement fun analize, a function that would operate with the taken
    picture. Useful links:
    pytorch mobile - https://github.com/pytorch/android-demo-app/tree/master/ObjectDetection
    android app analyze - https://developer.android.com/codelabs/camerax-getting-started
    */
    private class YoloAnalyzer(val module: Module, val finderWidth: Int, val finderHeight: Int, private val listener: YoloListener) : ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {

            fun Image.imgToBitmap(): Bitmap? {
                val yBuffer = planes[0].buffer
                val uBuffer = planes[1].buffer
                val vBuffer = planes[2].buffer
                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()
                val nv21 = ByteArray(ySize + uSize + vSize)
                yBuffer[nv21, 0, ySize]
                vBuffer[nv21, ySize, vSize]
                uBuffer[nv21, ySize + vSize, uSize]
                val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
                val imageBytes = out.toByteArray()
                return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            }

            val bitmap : Bitmap? = image.image!!.imgToBitmap()

            image.close()

            val resizedBitmap = Bitmap.createScaledBitmap(
                bitmap!!,
                PrePostProcessor.mInputWidth,
                PrePostProcessor.mInputHeight,
                true
            )
            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                resizedBitmap,
                PrePostProcessor.NO_MEAN_RGB,
                PrePostProcessor.NO_STD_RGB
            )

            val outputTuple = module.forward(IValue.from(inputTensor)).toTuple()
            val outputTensor = outputTuple[0].toTensor()
            val outputs = outputTensor.dataAsFloatArray

            val mImgScaleX = bitmap.width.toFloat() / PrePostProcessor.mInputWidth
            val mImgScaleY = bitmap.height.toFloat() / PrePostProcessor.mInputHeight

            val mIvScaleX = if (bitmap.width > bitmap.height) finderWidth
                .toFloat() / bitmap.width else finderHeight.toFloat() / bitmap.height
            val mIvScaleY = if (bitmap.height > bitmap.width) finderHeight
                .toFloat() / bitmap.height else finderWidth.toFloat() / bitmap.width

            val mStartX = (finderWidth - mIvScaleX * bitmap.width) / 2
            val mStartY = (finderHeight - mIvScaleY * bitmap.height) / 2

            val results = PrePostProcessor.outputsToNMSPredictions(
                outputs,
                mImgScaleX,
                mImgScaleY,
                mIvScaleX,
                mIvScaleY,
                mStartX,
                mStartY
            )

            listener(results)

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

        mResultView = viewBinding.resultView
        txtResult = viewBinding.txtResult
        viewFinder = viewBinding.viewFinder


        viewBinding.btnSee.setOnClickListener { takePhoto() }
        viewBinding.btnDetect.setOnClickListener { detect() }
        cameraExecutor = Executors.newSingleThreadExecutor()

        /* NB:
        analysis_bt.setOnClickListener(this)
        You can't write this in Kotlin. That's cause analysis_bt is a variable,
        so Kotlin doesn't permit to use a smart cast.
        Following the right way to do it:
        */
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

        try {
        /*  Loads the model which has to be saved into the assets folder
            if the model changes, some proprieties of PrePostProcessor need to be modified:
            inputWidth/Height, outputRow/Column
        */
            mModule = LiteModuleLoader.load(
                this.assetFilePath(
                    applicationContext,
                    model
                )
            )
        /*  This line loades the classes detected by the chosen model, saved in the assets folder.
            The PrePostProcessor needs these to label the objects.
            Every row is a class.
        */
            val br = BufferedReader(InputStreamReader(assets.open(classes)))

            var line: String?
            val classes: MutableList<String?> = ArrayList()
            while (br.readLine().also { line = it } != null) {
                classes.add(line)
            }
            PrePostProcessor.mClasses = arrayOfNulls<String>(classes.size)
            PrePostProcessor.mClasses = classes.toTypedArray()
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
                    print(msg)
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun detect(){
        print("Uri: $detectionUri")
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Set up image capture listener, which is triggered after photo has
        // been taken
        fun ImageProxy.convertImageProxyToBitmap(): Bitmap {
            val buffer = planes[0].buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    val msg = "Detection..."
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                    mBitmap = image.convertImageProxyToBitmap()

                    if (mImgScaleX == null) {
                        mImgScaleX = mBitmap.width.toFloat() / PrePostProcessor.mInputWidth
                        mImgScaleY = mBitmap.height.toFloat() / PrePostProcessor.mInputHeight

                        mIvScaleX = if (mBitmap.width > mBitmap.height) viewFinder!!.getWidth()
                            .toFloat() / mBitmap.width else viewFinder!!.getHeight().toFloat() / mBitmap.height
                        mIvScaleY = if (mBitmap.height > mBitmap.width) viewFinder!!.getHeight()
                            .toFloat() / mBitmap.height else viewFinder!!.getWidth().toFloat() / mBitmap.width

                        mStartX = (viewFinder!!.getWidth() - mIvScaleX!! * mBitmap.width) / 2
                        mStartY = (viewFinder!!.getHeight() - mIvScaleY!! * mBitmap.height) / 2
                    }
                    image.close()
                    val thread = Thread(this@MainActivity)
                    thread.start()
                }
            })
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

            // analyze code
            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, YoloAnalyzer(mModule!!, viewFinder!!.width, viewFinder!!.height) { results ->
                        Log.d(TAG, "Detection.")

                        runOnUiThread {
                            //mButtonDetect.setEnabled(true)
                            //mButtonDetect.setText(getString(R.string.detect))
                            //mProgressBar.setVisibility(ProgressBar.INVISIBLE)
                            mResultView!!.setResults(results)
                            mResultView!!.invalidate()
                            mResultView!!.visibility = View.VISIBLE
                            var str = ""
                            results.let {for(it in results) {str += it.toString()} }
                            txtResult!!.text = str
                        }

                    })
                }
            Log.d(TAG, "Backpressure strategy: ${imageAnalyzer.backpressureStrategy}")

            // Select back camera as a default
            // For debug purposes, front camera is easier to test
            //val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                if (analysisOn) {
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer)
                } else {
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture)
                }
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    override fun run() {
        //Thread.sleep(3000)
        //val bitmap = BitmapFactory.decodeFile(detectionUri)

        //val bitmap = BitmapFactory.decodeFile(detectionUri.path)

        val resizedBitmap = Bitmap.createScaledBitmap(
            mBitmap,
            PrePostProcessor.mInputWidth,
            PrePostProcessor.mInputHeight,
            true
        )
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            PrePostProcessor.NO_MEAN_RGB,
            PrePostProcessor.NO_STD_RGB
        )
        val outputTuple = mModule!!.forward(IValue.from(inputTensor)).toTuple()
        val outputTensor = outputTuple[0].toTensor()
        val outputs = outputTensor.dataAsFloatArray
        val results = PrePostProcessor.outputsToNMSPredictions(
            outputs,
            mImgScaleX!!,
            mImgScaleY!!,
            mIvScaleX!!,
            mIvScaleY!!,
            mStartX!!,
            mStartY!!
        )

        runOnUiThread {
            //mButtonDetect.setEnabled(true)
            //mButtonDetect.setText(getString(R.string.detect))
            //mProgressBar.setVisibility(ProgressBar.INVISIBLE)
            mResultView!!.setResults(results)
            mResultView!!.invalidate()
            mResultView!!.setVisibility(View.VISIBLE)

        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /* This code isn't used anymore, but it could be useful if we change direction for some reason.
       If it's safe to delete this, proceed.

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
    }*/
}