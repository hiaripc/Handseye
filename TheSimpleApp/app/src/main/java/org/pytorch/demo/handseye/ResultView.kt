// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.
package org.pytorch.demo.handseye

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ResultView//constructor(context: Context?) : super(context) {}
    (context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var mPaintRectangle: Paint? = null
    private var mPaintText: Paint? = null
    private var mResults: ArrayList<Result>? = null
    private var mPath = Path()
    private var mRectF = RectF()

    init {
        mPaintRectangle = Paint()
        mPaintRectangle!!.color = Color.YELLOW
        mPaintText = Paint()
    }

    //TODO quando ci sono due frame, in uno dei due non scrive la prediction...
    //poco male, ma se si ha voglia di debuggare, prego:
    //una soluzione comoda potrebbe essere passare direttamente solo il migliore risultato.
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mResults == null) return
        mPath.reset()
        for (result in mResults!!) {
            //Log.d("onDraw: ", result.classIndex.toString() + " " +result.rect.toString())
            mPaintRectangle!!.strokeWidth = 5f
            mPaintRectangle!!.style = Paint.Style.STROKE
            canvas.drawRect(result.rect, mPaintRectangle!!)
            mRectF.set(
                result.rect.left.toFloat(),
                result.rect.top.toFloat(),
                (result.rect.left + TEXT_WIDTH).toFloat(), (result.rect.top + TEXT_HEIGHT).toFloat()
            )
            /* old code
            val mPath = Path()

            val mRectF = RectF(
                result.rect.left.toFloat(),
                result.rect.top.toFloat(),
                (result.rect.left + TEXT_WIDTH).toFloat(), (result.rect.top + TEXT_HEIGHT).toFloat()
            )*/

            mPath.addRect(mRectF, Path.Direction.CW)
            mPaintText!!.color = Color.MAGENTA
            canvas.drawPath(mPath, mPaintText!!)
            mPaintText!!.color = Color.WHITE
            mPaintText!!.strokeWidth = 0f
            mPaintText!!.style = Paint.Style.FILL
            mPaintText!!.textSize = 32f
            canvas.drawText(
                String.format(
                    "%s %.2f",
                    PrePostProcessor.mClasses[result.classIndex], result.score
                ),
                (result.rect.left + TEXT_X).toFloat(),
                (result.rect.top + TEXT_Y).toFloat(), mPaintText!!
            )
        }
    }

    fun setResults(results: ArrayList<Result>?) {
        mResults = results
    }

    companion object {
        private const val TEXT_X = 40
        private const val TEXT_Y = 35
        private const val TEXT_WIDTH = 260
        private const val TEXT_HEIGHT = 50
    }
}