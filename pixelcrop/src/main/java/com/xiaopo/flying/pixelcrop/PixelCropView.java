package com.xiaopo.flying.pixelcrop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import static java.lang.Math.sqrt;

/**
 * Pixel Crop View
 * Created by snowbean on 16-10-14.
 */
public class PixelCropView extends View {
    private static final String TAG = "PixelCropView";

    public enum ActionMode {
        NONE,
        DRAG,
        ZOOM,
    }

    private CropWrapper mCropWrapper;
    private int mBorderOffset = 50;
    private float mMaxScale = 2f;
    private Paint mBorderPaint;
    private Border mCropBorder;

    private float mDownX;
    private float mDownY;
    private float mOldDistance;

    //缩放点
    private PointF mScalePoint;
    private Matrix mPreMatrix;
    private Matrix mPreSizeMatrix;

    //触摸事件开始时的缩放比
    private float mPreZoom;
    private ActionMode mCurrentMode;
    private boolean mIsRotateState;
    private float mRotateDegree;
    //不同旋转角度下的最小缩放比
    private float mMinScale;

    private int mBorderColor = Color.parseColor("#ddcbcbcb");

    public PixelCropView(Context context) {
        super(context);
    }

    public PixelCropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBorderPaint = new Paint();
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setColor(mBorderColor);
        mBorderPaint.setStrokeWidth(3);

        mPreMatrix = new Matrix();
        mPreSizeMatrix = new Matrix();

        mScalePoint = new PointF();
    }

    public PixelCropView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mCropBorder = new Border(new RectF(mBorderOffset,
                mBorderOffset,
                w - mBorderOffset,
                w - mBorderOffset));

        if (mCropWrapper != null) {
            setUpDefaultCropBorder(w, h);

            //使图片移动缩放至剪裁框内
            setWrapperToFitBorder();

            mPreMatrix.set(mCropWrapper.getMatrix());
            mPreSizeMatrix.set(mCropWrapper.getMatrix());
        }

    }

    //根据图片的长宽比确定剪裁边框的大小
    private void setUpDefaultCropBorder(int w, int h) {
        if (mCropWrapper != null) {
            int width = mCropWrapper.getWidth();
            int height = mCropWrapper.getHeight();

            if (width > height) {
                int bWidth = w - 2 * mBorderOffset;
                int bHeight = bWidth * height / width;
                mCropBorder = new Border(new RectF(
                        mBorderOffset,
                        (w - bHeight) / 2,
                        w - mBorderOffset,
                        (w + bHeight) / 2
                ));
            } else {
                int bHeight = w - 2 * mBorderOffset;
                int bWidth = bHeight * width / height;
                mCropBorder = new Border(new RectF(
                        (w - bWidth) / 2,
                        mBorderOffset,
                        (w + bWidth) / 2,
                        w - mBorderOffset
                ));
            }
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //画边框和阴影
        if (mCropBorder != null && mCropWrapper != null) {

            //半透明的完整图片
            mCropWrapper.draw(canvas, 100);

            //图片高亮部分
            canvas.save();
            canvas.clipRect(mCropBorder.getRect());
            mCropWrapper.draw(canvas);
            canvas.restore();

            //边框
            canvas.drawRect(mCropBorder.getRect(), mBorderPaint);

            //网格线
            mBorderPaint.setStrokeWidth(1);
            if (mIsRotateState) {
                mCropBorder.drawGrid(canvas, mBorderPaint, 9, 9);
            } else if (mCurrentMode == ActionMode.DRAG || mCurrentMode == ActionMode.ZOOM) {
                mCropBorder.drawGrid(canvas, mBorderPaint, 3, 3);
            }

            //画边框
            mBorderPaint.setStrokeWidth(5);
            mBorderPaint.setColor(Color.WHITE);
            mCropBorder.drawCorner(canvas, mBorderPaint);

            mBorderPaint.setColor(mBorderColor);
            mBorderPaint.setStrokeWidth(3);
        }


    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = MotionEventCompat.getActionMasked(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();

                if (mCropWrapper != null) {
                    mPreMatrix.set(mCropWrapper.getMatrix());
                }

                //TODO 判断手指按下的位置确定ActionMode
                mCurrentMode = ActionMode.DRAG;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                mOldDistance = calculateDistance(event);
                mScalePoint = calculateMidPoint(event);

                mPreZoom = mCropWrapper.getScaleFactor();

                if (event.getPointerCount() == 2) {
                    mCurrentMode = ActionMode.ZOOM;
                }

                break;

            case MotionEvent.ACTION_MOVE:
                switch (mCurrentMode) {
                    case NONE:
                        break;
                    case DRAG:
                        handleDragEvent(event);
                        break;
                    case ZOOM:
                        handleZoomEvent(event);
                        break;
                }
                invalidate();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                mCurrentMode = ActionMode.NONE;
                float currentZoom = mCropWrapper.getScaleFactor();

                mPreSizeMatrix.postScale(currentZoom / mPreZoom, currentZoom / mPreZoom,
                        mCropBorder.centerX(), mCropBorder.centerY());
                break;

            case MotionEvent.ACTION_UP:
                mCurrentMode = ActionMode.NONE;
                mPreMatrix.set(mCropWrapper.getMatrix());
                invalidate();

                break;
        }

        return true;
    }

    private void handleZoomEvent(MotionEvent event) {
        if (mCropWrapper != null) {
            float newDistance = calculateDistance(event);

            float scale = newDistance / mOldDistance;

            if (mPreZoom * scale <= mMinScale) {
                postScale(mMinScale / mCropWrapper.getScaleFactor(),
                        mMinScale / mCropWrapper.getScaleFactor(),
                        mScalePoint.x,
                        mScalePoint.y,
                        null);

                letImageContainsBorder(0, 0, null);

            } else {
                postScale(scale,
                        scale,
                        mScalePoint.x,
                        mScalePoint.y,
                        mPreMatrix);

                if (scale < 1f) {
                    letImageContainsBorder(0, 0, null);
                }
            }
        }
    }


    private void handleDragEvent(MotionEvent event) {

        postTranslate(event.getX() - mDownX,
                event.getY() - mDownY,
                mPreMatrix);

        letImageContainsBorder(event.getX() - mDownX, event.getY() - mDownY, mPreMatrix);

    }

    private void letImageContainsBorder(float preX, float preY, Matrix preMatrix) {
        if (!isImageContainsBorder()) {
            final float[] imageIndents = CropUtil.calculateImageIndents(mCropWrapper, mCropBorder);
            float deltaX = -(imageIndents[0] + imageIndents[2]);
            float deltaY = -(imageIndents[1] + imageIndents[3]);

            postTranslate(preX + deltaX,
                    preY + deltaY,
                    preMatrix);
        }
    }

    private boolean isImageContainsBorder() {
        return CropUtil.judgeIsImageContainsBorder(mCropWrapper, mCropBorder, mRotateDegree);
    }

    //TODO
    public void rotate(int degrees) {
        mRotateDegree = degrees;

        if (degrees > 0) {
            for (float i = degrees - 1; i <= degrees; i += 0.2) {
                rotate(i);
            }
        } else {
            for (float i = degrees + 1; i >= degrees; i -= 0.2) {
                rotate(i);
            }
        }

        mMinScale = CropUtil.calculateMinScale(mCropWrapper, mCropBorder, degrees);

    }


    private void rotate(float degrees) {
        if (mCropWrapper == null) return;

        if (mCropBorder != null) {

            postRotate(degrees,
                    mCropBorder.centerX(),
                    mCropBorder.centerY(),
                    mPreSizeMatrix);


            float tempScale = CropUtil.calculateRotateScale(mCropBorder.width(), mCropBorder.height(), degrees);

            postScale(tempScale,
                    tempScale,
                    mCropBorder.centerX(),
                    mCropBorder.centerY(),
                    null);

        }

        invalidate();
    }

    private PointF calculateMidPoint(MotionEvent event) {
        if (event == null || event.getPointerCount() < 2)
            return new PointF();
        float x = (event.getX(0) + event.getX(1)) / 2;
        float y = (event.getY(0) + event.getY(1)) / 2;
        return new PointF(x, y);
    }

    //计算两点间的距离
    private float calculateDistance(MotionEvent event) {
        if (event == null || event.getPointerCount() < 2)
            return 0f;
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);

        return (float) sqrt(x * x + y * y);
    }

    public void setCropBitmap(Bitmap bitmap) {
        //TODO Matrix create
        mCropWrapper = new CropWrapper(new BitmapDrawable(getResources(), bitmap), new Matrix());
        setWrapperToFitBorder();

        invalidate();
    }

    public void setRotateState(boolean rotateState) {
        mIsRotateState = rotateState;
        invalidate();
    }


    private void setWrapperToFitBorder() {
        int offsetX = getWidth() / 2 - mCropWrapper.getWidth() / 2;
        int offsetY = getWidth() / 2 - mCropWrapper.getHeight() / 2;

        if (mCropWrapper != null) {
            mCropWrapper.getMatrix()
                    .setTranslate(offsetX, offsetY);
        }

        if (mCropBorder != null) {
            float scaleX = mCropBorder.width() / mCropWrapper.getWidth();
            float scaleY = mCropBorder.height() / mCropWrapper.getHeight();

            mMinScale = scaleX;

            mCropWrapper.getMatrix()
                    .postScale(scaleX,
                            scaleY,
                            mCropWrapper.getMappedCenterPoint().x,
                            mCropWrapper.getMappedCenterPoint().y);
        }

        invalidate();
    }

    private void postScale(float sx, float sy, float px, float py, Matrix preMatrix) {
        if (mCropWrapper == null) return;
        if (preMatrix != null) {
            mCropWrapper.getMatrix().set(preMatrix);
        }
        mCropWrapper.getMatrix().postScale(sx, sy, px, py);
    }

    private void postTranslate(float x, float y, Matrix preMatrix) {
        if (mCropWrapper == null) return;
        if (preMatrix != null) {
            mCropWrapper.getMatrix().set(preMatrix);
        }
        mCropWrapper.getMatrix().postTranslate(x, y);
    }

    private void postRotate(float rotateDegrees, float px, float py, Matrix preMatrix) {
        if (mCropWrapper == null) return;
        if (preMatrix != null) {
            mCropWrapper.getMatrix().set(preMatrix);
        }
        mCropWrapper.getMatrix().postRotate(rotateDegrees, px, py);
    }
}
