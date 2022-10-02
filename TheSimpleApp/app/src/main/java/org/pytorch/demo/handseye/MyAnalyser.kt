package org.pytorch.demo.handseye

import android.annotation.SuppressLint
import android.graphics.*
import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.ByteArrayOutputStream

typealias ObjectListener = (results: ArrayList<Result>) -> Unit

class MyAnalyser(val mModule: Module, val mResultView: ResultView, private val listener: ObjectListener) : ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        val results = analyzeImage(image.image!!.imgToBitmap(), 90)
        listener(results)
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

    @SuppressLint("UnsafeOptInUsageError")
    fun analyzeImage(bitmap: Bitmap, rotationDegrees: Int): ArrayList<Result> {
        //bitmap = imgToBitmap(image.getImage());
        //if (foto)
        //unbind
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true)
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

        val imgScaleX: Float = bitmap.getWidth().toFloat() / PrePostProcessor.mInputWidth
        val imgScaleY: Float = bitmap.getHeight().toFloat() / PrePostProcessor.mInputHeight
        val ivScaleX: Float = mResultView.getWidth().toFloat() / bitmap.getWidth()
        val ivScaleY: Float = mResultView.getHeight().toFloat() / bitmap.getHeight()

        return PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0f, 0f)
    }
}