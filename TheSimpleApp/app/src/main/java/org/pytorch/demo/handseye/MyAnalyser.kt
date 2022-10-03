package org.pytorch.demo.handseye

import android.annotation.SuppressLint
import android.graphics.*
import android.media.Image
import android.widget.ImageView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.ByteArrayOutputStream


typealias ObjectListener = (results: ArrayList<Result>) -> Unit

class MyAnalyser(val mModule: Module, val mResultView: ResultView, val mImageView: ImageView, private val listener: ObjectListener?) : ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        val results = analyzeImage(image, 90)
        listener!!(results)
        image.close()
    }

    fun Image.imgToBitmap(): Bitmap {
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

    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    //Overloaded for picture taken/picked, need to calculated StartX/Y and different ivScale

    @SuppressLint("UnsafeOptInUsageError")
    fun analyzeImage(image: ImageProxy, rotationDegrees: Int): ArrayList<Result> {
        val bitmap = image.image!!.imgToBitmap()
        val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)

        val ivScaleX: Float = mResultView.width.toFloat() / rotatedBitmap.width
        val ivScaleY: Float = mResultView.height.toFloat() / rotatedBitmap.height

        return processImage(rotatedBitmap,ivScaleX,ivScaleY,0f,0f)
    }

    //Picked/taken photo
    fun analyzeImage(rotatedBitmap: Bitmap): ArrayList<Result> {
        val ivScaleX =
            if (rotatedBitmap.width > rotatedBitmap.height)
                mImageView.width.toFloat() / rotatedBitmap.width
            else mImageView.height.toFloat() / rotatedBitmap.height
        val ivScaleY =
            if (rotatedBitmap.height > rotatedBitmap.width)
                mImageView.height.toFloat() / rotatedBitmap.height
            else mImageView.width.toFloat() / rotatedBitmap.width

        val startX: Float = (mImageView.width - ivScaleX * rotatedBitmap.width) / 2
        val startY: Float = (mImageView.height - ivScaleY * rotatedBitmap.height) / 2

        return processImage(rotatedBitmap, ivScaleX, ivScaleY, startX, startY)
    }

    fun processImage(rotatedBitmap:Bitmap, ivScaleX: Float, ivScaleY: Float, startX : Float, startY : Float) : ArrayList<Result>{
        val imgScaleX: Float = rotatedBitmap.width.toFloat() / PrePostProcessor.mInputWidth
        val imgScaleY: Float = rotatedBitmap.height.toFloat() / PrePostProcessor.mInputHeight
        val resizedBitmap = Bitmap.createScaledBitmap(
            rotatedBitmap,
            PrePostProcessor.mInputWidth,
            PrePostProcessor.mInputHeight,
            true
        )

        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            PrePostProcessor.NO_MEAN_RGB,
            PrePostProcessor.NO_STD_RGB
        )
        val outputTuple: Array<IValue> = mModule.forward(IValue.from(inputTensor)).toTuple()
        val outputTensor = outputTuple[0].toTensor()
        val outputs = outputTensor.dataAsFloatArray

        return PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, startX, startY)
    }
}