package com.dx.faced;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.dx.faced.utils.ToastUtils;
import com.dx.faced.view.AutoFitTextureView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraHelper {

    private int PREVIEW_WIDTH = 1080;                                      //预览的宽度
    private int PREVIEW_HEIGHT = 1440;                                    //预览的高度
    private int SAVE_WIDTH = 720;                                      //保存图片的宽度
    private int SAVE_HEIGHT = 1280;                                        //保存图片的高度

    private Activity mActivity;
    private AutoFitTextureView mTextureView;

    private CameraManager mCameraManager;
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;

    private String mCameraId = "0";
    private CameraCharacteristics mCameraCharacteristics;

    private int mCameraSensorOrientation = 0;                                           //摄像头方向
    private int mCameraFacing = CameraCharacteristics.LENS_FACING_BACK;              //默认使用后置摄像头
    private int mDisplayRotation;  //手机方向
    private int mFaceDetectMode = CaptureResult.STATISTICS_FACE_DETECT_MODE_OFF;    //人脸检测模式

    private boolean canTakePic = true;                                                       //是否可以拍照
    private boolean canExchangeCamera = false;                                               //是否可以切换摄像头
    private boolean openFaceDetect = true;                                                 //是否开启人脸检测
    private Matrix mFaceDetectMatrix = new Matrix();                                          //人脸检测坐标转换矩阵
    private List<RectF> mFacesRect = new ArrayList<RectF>();                                        //保存人脸坐标信息
    private FaceDetectListener mFaceDetectListener;                        //人脸检测回调

    private Handler mCameraHandler;
    private HandlerThread handlerThread = new HandlerThread("CameraThread");

    private Size mPreviewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT);                   //预览大小
    private Size mSavePicSize = new Size(SAVE_WIDTH, SAVE_HEIGHT);//保存图片大小

    public void takePic() {

    }

    public void exchangeCamera() {
        if (mCameraDevice == null || !canExchangeCamera || !mTextureView.isAvailable()) {
            return;
        }

        mCameraFacing = (mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT) ?
                CameraCharacteristics.LENS_FACING_BACK : CameraCharacteristics.LENS_FACING_FRONT;

        mPreviewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT); //重置预览大小
        releaseCamera();
        initCameraInfo();
    }

    interface FaceDetectListener {
        void onFaceDetect(List<Face> faces, List<RectF> facesRect);
    }

    public void setFaceDetectListener(FaceDetectListener listener) {
        this.mFaceDetectListener = listener;
    }

    public CameraHelper(Activity activity, AutoFitTextureView autoFitTextureView) {
        mActivity = activity;
        mTextureView = autoFitTextureView;
        init();
    }

    private void init() {
        mDisplayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();

        handlerThread.start();
        mCameraHandler = new Handler(handlerThread.getLooper());


        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                configureTransform(width, height);
                initCameraInfo();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                releaseCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        });
    }

    private void initCameraInfo() {
        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        if (mCameraManager == null) {
            return;
        }
        String[] cameraIdList = new String[0];
        try {
            cameraIdList = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return;
        }
        if (cameraIdList.length == 0) {
            ToastUtils.showToast(mActivity, "没有可用相机");
            return;
        }

        for (String cameraId : cameraIdList) {
            CameraCharacteristics cameraCharacteristics = null;
            try {
                cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            if (cameraCharacteristics != null) {
                int facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == mCameraFacing) {
                    mCameraId = cameraId;
                    mCameraCharacteristics = cameraCharacteristics;
                }
            }
        }

        if (mCameraCharacteristics == null) {
            return;
        }
        int supportLevel = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            ToastUtils.showToast(mActivity, "相机硬件不支持新特性");
        }

        //获取摄像头方向
        mCameraSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
        StreamConfigurationMap configurationMap = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Size[] savePicSize = configurationMap.getOutputSizes(ImageFormat.JPEG);          //保存照片尺寸
        Size[] previewSize = configurationMap.getOutputSizes(SurfaceTexture.class); //预览尺寸

        boolean exchange = exchangeWidthAndHeight(mDisplayRotation, mCameraSensorOrientation);

        mSavePicSize = getBestSize(
                exchange ? mSavePicSize.getHeight() : mSavePicSize.getWidth(),
                exchange ? mSavePicSize.getWidth() : mSavePicSize.getHeight(),
                exchange ? mSavePicSize.getHeight() : mSavePicSize.getWidth(),
                exchange ? mSavePicSize.getWidth() : mSavePicSize.getHeight(),
                Arrays.asList(savePicSize));

        mPreviewSize = getBestSize(
                exchange ? mPreviewSize.getHeight() : mPreviewSize.getWidth(),
                exchange ? mPreviewSize.getWidth() : mPreviewSize.getHeight(),
                exchange ? mTextureView.getHeight() : mTextureView.getWidth(),
                exchange ? mTextureView.getWidth() : mTextureView.getHeight(),
                Arrays.asList(previewSize));


        mTextureView.getSurfaceTexture().setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());


        //根据预览的尺寸大小调整TextureView的大小，保证画面不被拉伸
        int orientation = mActivity.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        } else {
            mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }

        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 1);
        mImageReader.setOnImageAvailableListener(onImageAvailableListener, mCameraHandler);

        if (openFaceDetect) {
            initFaceDetect();
        }

        openCamera();
    }

    /**
     * 初始化人脸检测相关信息
     */
    private void initFaceDetect() {

        int faceDetectCount = mCameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);   //同时检测到人脸的数量
        int[] faceDetectModes = mCameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);//人脸检测的模式

        if (faceDetectModes == null) {
            Log.e("cxl", "faceDetectModes == null");
            return;
        }
        List<Integer> temFaceDetectModes = new ArrayList<>();
        for (int faceDetectMode : faceDetectModes) {
            temFaceDetectModes.add(faceDetectMode);
        }

        if (temFaceDetectModes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL)) {
            mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
        } else if (temFaceDetectModes.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)) {
            mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
        } else {
            mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
        }

        if (mFaceDetectMode == CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
            ToastUtils.showToast(mActivity, "相机硬件不支持人脸检测");
            return;
        }

        Rect activeArraySizeRect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE); //获取成像区域
        if (activeArraySizeRect == null) {
            Log.e("cxl", "activeArraySizeRect == null");
            return;
        }
        float scaledWidth = mPreviewSize.getWidth() / ((float) activeArraySizeRect.width());
        float scaledHeight = mPreviewSize.getHeight() / ((float) activeArraySizeRect.height());
        boolean mirror = mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT;

        mFaceDetectMatrix.setRotate((float) mCameraSensorOrientation);
        mFaceDetectMatrix.postScale(mirror ? -scaledWidth : scaledWidth, scaledHeight);
        if (exchangeWidthAndHeight(mDisplayRotation, mCameraSensorOrientation)) {
            mFaceDetectMatrix.postTranslate(mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }
    }

    //保存图片
    private ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {

        }
    };

    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                    try {
                        createCaptureSession(cameraDevice);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("cxl", "createCaptureSession异常==" + e.getMessage());
                    }
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {

                }

                @Override
                public void onError(CameraDevice cameraDevice, int i) {
                    ToastUtils.showToast(mActivity, "打开相机失败__onError");
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            ToastUtils.showToast(mActivity, "打开相机失败" + e.getMessage());
        }
    }

    /**
     * 创建预览会话
     */
    private void createCaptureSession(CameraDevice cameraDevice) throws CameraAccessException {

        final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        Surface surface = new Surface(mTextureView.getSurfaceTexture());
        captureRequestBuilder.addTarget(surface);//target为surface，就是
        captureRequestBuilder.addTarget(surface);  // 将CaptureRequest的构建器与Surface对象绑定在一起
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);     // 闪光灯
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);// 自动对焦
        if (openFaceDetect && mFaceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
            captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE);//人脸检测
        }
        // 为相机预览，创建一个CameraCaptureSession对象
        List<Surface> data = new ArrayList();
        data.add(surface);
        if (mImageReader != null) {
            data.add(mImageReader.getSurface());
        }
        cameraDevice.createCaptureSession(data, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                mCameraCaptureSession = session;
                try {
                    session.setRepeatingRequest(captureRequestBuilder.build(), mCaptureCallBack, mCameraHandler);
                } catch (Exception e) {
                    Log.e("cxl", "createCaptureSession__session.setRepeatingRequest==" + e.getMessage());
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                ToastUtils.showToast(mActivity, "开启预览会话失败");
            }
        }, mCameraHandler);
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallBack = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (openFaceDetect && mFaceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
                handleFaces(result);
            }

            canExchangeCamera = true;
            canTakePic = true;
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            ToastUtils.showToast(mActivity, "开启预览失败");
        }
    };

    /**
     * 处理人脸信息
     */
    private void handleFaces(TotalCaptureResult result) {
        Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
        if (faces == null || faces.length < 1) {
            Log.e("cxl", "没有检测到人脸");
        }
        mFacesRect.clear();

        for (Face face : faces) {
            Rect bounds = face.getBounds();
            int left = bounds.left;
            int top = bounds.top;
            int right = bounds.right;
            int bottom = bounds.bottom;

            RectF rawFaceRect = new RectF(left, top, right, bottom);
            mFaceDetectMatrix.mapRect(rawFaceRect);

            RectF resultFaceRect = (mCameraFacing == CaptureRequest.LENS_FACING_FRONT) ?
                    rawFaceRect : new RectF(rawFaceRect.left, rawFaceRect.top - mPreviewSize.getWidth(), rawFaceRect.right, rawFaceRect.bottom - mPreviewSize.getWidth());


            mFacesRect.add(resultFaceRect);

        }

        final List<Face> fFaces = Arrays.asList(faces);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mFaceDetectListener.onFaceDetect(fFaces, mFacesRect);
            }
        });
    }


    /**
     * 根据提供的参数值返回与指定宽高相等或最接近的尺寸
     *
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     * @param maxWidth     最大宽度(即TextureView的宽度)
     * @param maxHeight    最大高度(即TextureView的高度)
     * @param sizeList     支持的Size列表
     * @return 返回与指定宽高相等或最接近的尺寸
     */
    private Size getBestSize(int targetWidth, int targetHeight, int maxWidth, int maxHeight, List<Size> sizeList) {
        List<Size> bigEnough = new ArrayList<Size>();   //比指定宽高大的Size列表
        List<Size> notBigEnough = new ArrayList<Size>(); //比指定宽高小的Size列表

        for (Size size : sizeList) {

            //宽<=最大宽度  &&  高<=最大高度  &&  宽高比 == 目标值宽高比
            if (size.getWidth() <= maxWidth && size.getHeight() <= maxHeight
                    && size.getWidth() == size.getHeight() * targetWidth / targetHeight) {

                if (size.getWidth() >= targetWidth && size.getHeight() >= targetHeight) {
                    bigEnough.add(size);
                } else {
                    notBigEnough.add(size);
                }
            }
        }

        //选择bigEnough中最小的值  或 notBigEnough中最大的值
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return sizeList.get(0);
        }
    }

    private class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size size1, Size size2) {
            return java.lang.Long.signum(size1.getWidth() * size1.getHeight() - size2.getWidth() * size2.getHeight());
        }
    }


    //根据提供的屏幕方向 [displayRotation] 和相机方向 [sensorOrientation] 返回是否需要交换宽高
    private boolean exchangeWidthAndHeight(int displayRotation, int sensorOrientation) {
        boolean exchange = false;
        if (displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180) {
            if (sensorOrientation == 90 || sensorOrientation == 270) {
                exchange = true;
            }
        }

        if (displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270) {
            if (sensorOrientation == 0 || sensorOrientation == 180) {
                exchange = true;
            }
        }

        Log.e("cameraHelper", "屏幕方向==" + displayRotation);
        Log.e("cameraHelper", "相机方向==" + sensorOrientation);
        return exchange;
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0f, 0f, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0f, 0f, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    ((float) viewHeight) / ((float) mPreviewSize.getHeight()),
                    ((float) viewWidth) / ((float) mPreviewSize.getWidth()));
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate((90 * (rotation - 2)), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void releaseCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
        }
        mCameraCaptureSession = null;

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        canExchangeCamera = false;
    }


}
