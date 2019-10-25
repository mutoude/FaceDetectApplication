package com.dx.faced;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.graphics.RectF;
import android.hardware.camera2.params.Face;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.dx.faced.utils.ToastUtils;
import com.dx.faced.view.AutoFitTextureView;
import com.dx.faced.view.FaceView;
import com.qw.soul.permission.SoulPermission;
import com.qw.soul.permission.bean.Permission;
import com.qw.soul.permission.callbcak.CheckRequestPermissionListener;

import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraHelper.FaceDetectListener, View.OnClickListener {

    private CameraHelper cameraHelper;
    private AutoFitTextureView textureView;

    private View btnTakePic;
    private View btnStart;
    private View ivExchange;

    private FaceView faceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        textureView = findViewById(R.id.textureView);
        faceView = findViewById(R.id.faceView);

        btnTakePic = findViewById(R.id.btnTakePic);
        btnTakePic.setOnClickListener(this);
        ivExchange = findViewById(R.id.ivExchange);
        ivExchange.setOnClickListener(this);
        requestCameraPermission();
        requestReadPhoneStatePermission();
    }

    private void requestReadPhoneStatePermission() {
        SoulPermission.getInstance()
                .checkAndRequestPermission(Manifest.permission.READ_PHONE_STATE, new CheckRequestPermissionListener() {
                    @Override
                    public void onPermissionOk(Permission permission) {


                    }

                    @Override
                    public void onPermissionDenied(Permission permission) {
                        ToastUtils.showToast(MainActivity.this, "读取手机状态授权失败");
                        requestCameraPermission();
                    }
                });
    }

    private void requestCameraPermission() {
        SoulPermission.getInstance()
                .checkAndRequestPermission(Manifest.permission.CAMERA, new CheckRequestPermissionListener() {
                    @Override
                    public void onPermissionOk(Permission permission) {
                        cameraHelper = new CameraHelper(MainActivity.this, textureView);
                        cameraHelper.setFaceDetectListener(MainActivity.this);

                    }

                    @Override
                    public void onPermissionDenied(Permission permission) {
                        ToastUtils.showToast(MainActivity.this, "相机授权失败");
                        requestCameraPermission();
                    }
                });
    }

    @Override
    public void onFaceDetect(List<Face> faces, List<RectF> facesRect) {
        faceView.setFaces(facesRect);
        if (facesRect == null || facesRect.size() < 1) {
            Log.e("cxl", "facesRect==空");
        }
    }

    @Override
    public void onClick(View view) {
        if (view.equals(btnTakePic)) {
            cameraHelper.takePic();
        } else if (view.equals(ivExchange)) {
            cameraHelper.exchangeCamera();
        }
    }
}
