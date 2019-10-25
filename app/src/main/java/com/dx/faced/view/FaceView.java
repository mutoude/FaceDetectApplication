package com.dx.faced.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

public class FaceView extends View {

    private Paint mPaint;

    private List<RectF> mFaces;

    public FaceView(Context context) {
        super(context);
        init(context);
    }

    public FaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }


    private void init(Context context) {
        mPaint = new Paint();
        mPaint.setColor(Color.RED);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(8);
        mPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mFaces != null) {
            for (RectF face :
                    mFaces) {
                canvas.drawRect(face, mPaint);
            }
        }
    }


    public void setFaces(List<RectF> faces) {
        this.mFaces = faces;
        invalidate();
    }
}
