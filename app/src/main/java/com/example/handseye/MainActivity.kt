package com.example.handseye

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executor
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : AppCompatActivity(), View.OnClickListener,
    ImageAnalysis.Analyzer {
    //private var picture_bt: Button? = null
    private var analysis_bt: Button? = null
    //private var pView: PreviewView? = null
    private var viewFinder: PreviewView? = null
    private var imageCap: ImageCapture? = null
    private var imageAnl: ImageAnalysis? = null
    private var analysis_on = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (!checkPermission()) requestPermission()

        /*if (! checkStoragePermission())
            requestStoragePermission();
        */
        //picture_bt = findViewById(R.id.btnClick)
        analysis_bt = findViewById(R.id.btnSee)
        //pView = findViewById(R.id.previewView)
        //picture_bt.setOnClickListener(this)

        /* NB:
            analysis_bt.setOnClickListener(this)
            You can't write this in Kotlin. That's cause analysis_bt is a variable,
            so Kotlin doesn't permit to use a smart cast.
            Following the right way to do it:
        */
        analysis_bt?.let { it.setOnClickListener(this)}
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

        viewFinder = findViewById(R.id.viewFinder)

        analysis_on = false
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
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE),
            PERMISSION_REQUEST_CODE
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
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            //Permission is not granted
            false
        } else true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(applicationContext, "Permission Granted", Toast.LENGTH_SHORT).show()

                // everything is ok, move on
            } else {
                Toast.makeText(applicationContext, "Permission denied", Toast.LENGTH_SHORT).show()
                //Handle this... asking again or shutting down
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
                    it.setSurfaceProvider(viewFinder!!.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview)

            } catch(exc: Exception) {
                Log.e("Handseye App", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private val executor: Executor
        private get() = ContextCompat.getMainExecutor(this)

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnSee -> capturePhoto()
            R.id.btnSee -> analysis_on = !analysis_on
        }
    }

    private fun capturePhoto() {
        val photoDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .toString() + "/CameraXPhotos"
        )
        if (!photoDir.exists()) {
            Toast.makeText(
                this@MainActivity,
                "Photo dir didn't existed.$photoDir",
                Toast.LENGTH_SHORT
            ).show()
            photoDir.mkdir()
        }
        Toast.makeText(this@MainActivity, "Photo dir DID existed.$photoDir", Toast.LENGTH_SHORT)
            .show()
        val date = Date()
        val timestamp = date.time.toString()
        val photoFilePath = photoDir.absolutePath + "/" + timestamp + ".jpg"
        val photoFile = File(photoFilePath)
    /*
        //using capture use case to run takepicture
        imageCap.takePicture(
            Builder(photoFile).build(),
            executor,
            object : OnImageSavedCallback() {
                //manage success and failure
                fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        this@MainActivity,
                        "Photo has been saved successfully.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error saving photo:" + exception.getMessage(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        )
        */
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

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }
}