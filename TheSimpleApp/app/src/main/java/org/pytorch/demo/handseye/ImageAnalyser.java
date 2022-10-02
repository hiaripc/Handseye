package org.pytorch.demo.handseye;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ImageAnalyser {
    protected ResultView mResultView;
    protected ImageView mImageView;
    protected Module mModule;
    protected Context appContext;

    public ImageAnalyser(ResultView mResultView, ImageView mImageView, Context appContext){
        this.mResultView = mResultView;
        this.appContext = appContext;
        this.mImageView = mImageView;
        this.mModule = null;
    }

    static class AnalysisResult {
        protected final ArrayList<Result> mResults;

        public AnalysisResult(ArrayList<Result> results) {
            mResults = results;
        }
    }
    protected Bitmap imgToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    protected Bitmap toMatrixBitmap(Bitmap bitmap, int rotationDegrees){
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

    }

    protected void loadModule(){
        try {
            if (mModule == null) {
                mModule = LiteModuleLoader.load(MainActivity.assetFilePath(appContext, "fine-tuned.torchscript.ptl"));
            }
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
        }
    }
    //Captures the images, analyse it with PrePostProcessor data, then draws on it and returns the results.

    //From video
    @Nullable
    protected ImageAnalyser.AnalysisResult analyzeImage(ImageProxy image, int rotationDegrees) {
        loadModule();

        Bitmap bitmap = imgToBitmap(image.getImage());
        bitmap = toMatrixBitmap(bitmap, rotationDegrees);

        float ivScaleX = (float)mResultView.getWidth() / bitmap.getWidth();
        float ivScaleY = (float)mResultView.getHeight() / bitmap.getHeight();

        return processImage(bitmap, ivScaleX,ivScaleY,0,0);
    }

    //Overloaded for picture taken/picked, need to calculated StartX/Y and different ivScale
    protected ImageAnalyser.AnalysisResult analyzeImage(Bitmap matrixBitmap) {
        loadModule();

        float ivScaleX = (matrixBitmap.getWidth() > matrixBitmap.getHeight() ? (float)mImageView.getWidth() / matrixBitmap.getWidth() : (float)mImageView.getHeight() / matrixBitmap.getHeight());
        float ivScaleY  = (matrixBitmap.getHeight() > matrixBitmap.getWidth() ? (float)mImageView.getHeight() / matrixBitmap.getHeight() : (float)mImageView.getWidth() / matrixBitmap.getWidth());

        float startX = (mImageView.getWidth() - ivScaleX * matrixBitmap.getWidth())/2;
        float startY = (mImageView.getHeight() -  ivScaleY * matrixBitmap.getHeight())/2;

        return processImage(matrixBitmap,ivScaleX,ivScaleY,startX,startY);
    }

    private ImageAnalyser.AnalysisResult processImage(Bitmap bitmap, float ivScaleX, float ivScaleY,float  startX, float startY){
        float imgScaleX = (float)bitmap.getWidth() / PrePostProcessor.mInputWidth;
        float imgScaleY = (float)bitmap.getHeight() / PrePostProcessor.mInputHeight;
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);

        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();

        final ArrayList<Result> results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, startX,startY);
        return new ImageAnalyser.AnalysisResult(results);
    }
}
