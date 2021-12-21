package com.baorongzhen.vlc_camera;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import android.icu.util.Calendar;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.android.Utils;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.*;

class Coordinate{
    public int m_x;
    public int m_y;
    public Coordinate(int x, int y){
        this.m_x=x;
        this.m_y=y;
    }
}
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "vlc_camera";

    private static final String TAG_PREVIEW = "预览";

    private static final SparseIntArray ORIENTATION = new SparseIntArray();
    private static final boolean VERBOSE = false;
    private String socket_out;
    private PrintWriter pw;

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    private String mCameraId;

    private Size mPreviewSize;

    private ImageReader mImageReader;//用于接收拍照结果和访问拍摄照片的图像数据

    private ImageReader mImageReader1;//用于接收拍照结果和访问拍摄照片的图像数据

    private CameraDevice mCameraDevice;//系统中的相机设备，不是真正的硬件

    private CameraCaptureSession mCaptureSession;//系统和摄像头之间的信息通道

    private CaptureRequest mPreviewRequest;//描述了一次操作请求，拍照、预览等操作都需要先传入CaptureRequest参数，具体的参数控制也是通过CameraRequest的成员变量来设置

    private CaptureRequest.Builder mPreviewRequestBuilder;

    private AutoFitTextureView textureView;
    private TextView idtextView;
    private TextView pos_x;
    private TextView pos_y;
    private Surface mPreviewSurface;//预览绘图接口
    private ArrayList<ArrayList<Coordinate>> contours;//在图二中找到的轮廓
    private ArrayList<Coordinate> centers;//在图二中找到的中心
    private CameraManager manager; // 相机管理者，用于打开和关闭系统摄像头
    private CameraCharacteristics mCameraCharacteristics; // 相机属性，描述摄像头的各种特性
    public static final int COLOR_FormatI420 = 1;
    public static final int COLOR_FormatNV21 = 2;
    Handler mHandler1 = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message message) {
            // Your code logic here
            if(message.what==0)
            {
                idtextView.setText("0");
            }
            else if(message.what==1)
            {
                idtextView.setText("1");
            }
            else if(message.what==2)
            {
                idtextView.setText("2");
            }
            else if(message.what==3)
            {
                idtextView.setText("3");
            }
            else if(message.what==4)
            {
                idtextView.setText("4");
            }
            return true;
        }
    });
    Handler mHandler2=new Handler();
    Runnable runnable=new Runnable(){
        @Override
        public void run() {
            //要做的事情
            capture();
            mHandler2.postDelayed(this,1000);//每隔1s拍一张
        }
    };
    // Surface状态回调
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            configureTransform(width, height);
            openCamera();
        }


        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    // 摄像头状态回调
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            //开启预览
            startPreview();
        }


        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.i(TAG, "CameraDevice Disconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "CameraDevice Error");
        }
    };

    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {

        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(textureListener);
        idtextView=findViewById(R.id.tvid);
        pos_x=findViewById(R.id.tvpos_x);
        pos_y=findViewById(R.id.tvpos_y);
        setclient();//建立连接
        findViewById(R.id.takePicture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {//点击拍照的回调函数
//                capture();
                mHandler2.postDelayed(runnable, 2000);//等待2s后开始拍照
            }
        });
        findViewById(R.id.exit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {//点击退出的回调函数
                exit();
            }
        });
    }
    public BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                } break;
                default:
                {
                    Log.i(TAG, "OpenCV loaded not successfully");
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
    @Override
    protected void onPause() {
        closeCamera();
        super.onPause();
    }

    private void setupCamera(int width, int height) {
        // 获取摄像头的管理者CameraManager
        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            // 遍历所有摄像头
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                // 默认打开后置摄像头 - 忽略前置摄像头
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) continue;
                // 获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    textureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }


                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void openCamera() {
        //获取摄像头的管理者CameraManager
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        //检查权限
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //打开相机，第一个参数指示打开哪个摄像头，第二个参数stateCallback为相机的状态回调接口，第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
            manager.openCamera(mCameraId, stateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private void startPreview() {
        setupImageReader();
        SurfaceTexture mSurfaceTexture = textureView.getSurfaceTexture();
        //设置TextureView的缓冲区大小
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        //获取Surface显示预览数据
        mPreviewSurface = new Surface(mSurfaceTexture);
        try {
            getPreviewRequestBuilder();
            //创建相机捕获会话，第一个参数是捕获数据的输出Surface列表，第一个是预览的surface，第二个是拍照的surface，第二个参数是CameraCaptureSession的状态回调接口，当它创建好后会回调onConfigured方法，第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
            mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface, mImageReader.getSurface(),mImageReader1.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCaptureSession = session;
                    repeatPreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void repeatPreview() {
        mPreviewRequestBuilder.setTag(TAG_PREVIEW);
        mPreviewRequest = mPreviewRequestBuilder.build();
        //设置反复捕获数据的请求，这样预览界面就会一直有数据显示
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mPreviewCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void setupImageReader() {
        //前三个参数分别是需要的尺寸和格式，最后一个参数代表每次最多获取几帧数据

        mImageReader = ImageReader.newInstance(3120, 4160, ImageFormat.YUV_420_888, 2);
        mImageReader1 = ImageReader.newInstance(3120, 4160, ImageFormat.YUV_420_888, 2);
        final Image[] image = new Image[2];
        //监听ImageReader的事件，当有图像流数据可用时会回调onImageAvailable方法，它的参数就是预览帧数据，可以对这帧数据进行处理
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.i(TAG, "Image Available1!");
                image[0] = mImageReader.acquireLatestImage();
                // 开启线程异步保存图片
//                new Thread(new ImageSaver(image[0])).start();

            }
        }, null);
        mImageReader1.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.i(TAG, "Image Available2!");
                image[1] = mImageReader1.acquireLatestImage();
                // 开启线程异步保存图片
                new Thread(new Contour(image)).start();

            }
        }, null);
    }

    // 选择sizeMap中大于并且最接近width和height的size
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }


    // 创建预览请求的Builder（TEMPLATE_PREVIEW表示预览请求）
    private void getPreviewRequestBuilder() {
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        //设置预览的显示界面
        mPreviewRequestBuilder.addTarget(mPreviewSurface);
        MeteringRectangle[] meteringRectangles = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
        if (meteringRectangles != null && meteringRectangles.length > 0) {
            Log.d(TAG, "PreviewRequestBuilder: AF_REGIONS=" + meteringRectangles[0].getRect().toString());
        }
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
    }

    // 拍照
    private void capture() {
        try {
            //首先我们创建请求拍照的CaptureRequest
            ArrayList<CaptureRequest> captureList = new ArrayList<>();
            final CaptureRequest.Builder mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            final CaptureRequest.Builder mCaptureBuilder1 = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //获取屏幕方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();

            mCaptureBuilder.addTarget(mPreviewSurface);//给此次请求添加一个Surface对象作为图像的输出目标
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            mCaptureBuilder1.addTarget(mImageReader1.getSurface());
//            int a = mCaptureBuilder.get(CaptureRequest.CONTROL_AE_MODE);
            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            mCaptureBuilder1.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
//            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_AUTO);
//            mCaptureBuilder.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO);
//            int b=mCaptureBuilder.get(CaptureRequest.CONTROL_AE_MODE);
//            int b1=mCaptureBuilder.get(CaptureRequest.CONTROL_MODE);
//            int a2=mCaptureBuilder.get(CaptureRequest.SENSOR_SENSITIVITY);
//            long c = mCaptureBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
            long ae=125000;
            long ae1=8000000;
            mCaptureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,ae);
            mCaptureBuilder1.set(CaptureRequest.SENSOR_EXPOSURE_TIME,ae1);
//            mCaptureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,100);// ISO默认是100，所以不用设置
            mCaptureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,100);// ISO默认是100，所以不用设置
            mCaptureBuilder1.set(CaptureRequest.SENSOR_SENSITIVITY,1000);// ISO默认是100，所以不用设置
            System.out.println(mCaptureBuilder.get(CaptureRequest.SENSOR_SENSITIVITY));
            System.out.println(mCaptureBuilder1.get(CaptureRequest.SENSOR_SENSITIVITY));
//            long c1 = mCaptureBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME);


            //设置拍照方向
            mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
            mCaptureBuilder1.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
            //停止预览
            captureList.add(mCaptureBuilder.build());
            captureList.add(mCaptureBuilder1.build());
            mCaptureSession.stopRepeating();

            //开始拍照，然后回调上面的接口重启预览，因为mCaptureBuilder设置ImageReader作为target，所以会自动回调ImageReader的onImageAvailable()方法保存图片
            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {

                @Override//拍摄完成之后继续预览
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    repeatPreview();
                }
            };

//            mCaptureSession.capture(mCaptureBuilder.build(), captureCallback, null);
            mCaptureSession.captureBurst(captureList,captureCallback,null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void exit() {
        //定义一个新的对话框对象
        AlertDialog.Builder alertdialogbuilder=new AlertDialog.Builder(this);
        //设置对话框提示内容
        alertdialogbuilder.setMessage("确定要退出程序吗？");
        //定义对话框2个按钮标题及接受事件的函数
        alertdialogbuilder.setPositiveButton("确定",click1);
        alertdialogbuilder.setNegativeButton("取消",click2);
        //创建并显示对话框
        AlertDialog alertdialog1=alertdialogbuilder.create();
        alertdialog1.show();
    }
    private final DialogInterface.OnClickListener click1=new DialogInterface.OnClickListener() {
        //使用该标记是为了增强程序在编译时候的检查，如果该方法并不是一个覆盖父类的方法，在编译时编译器就会报告错误。
        @Override
        public void onClick(DialogInterface arg0,int arg1)
        {
            //当按钮click1被按下时执行结束进程
            mHandler2.removeCallbacks(runnable);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    };
    private final DialogInterface.OnClickListener click2=new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface arg0,int arg1)
        {
            //当按钮click2被按下时则取消操作
            arg0.cancel();
        }
    };
    public static Bitmap getBitmapImageFromYUV(byte[] data, int width, int height) {
        YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(new Rect(0, 0, width, height), 80, baos);
        byte[] jdata = baos.toByteArray();
        BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
        bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bitmapFatoryOptions);
        return bmp;
    }

    public static Bitmap getBitmapFromImage(Image image) {

        int w = image.getWidth(), h = image.getHeight();
        int i420Size = w * h * 3 / 2;

        Image.Plane[] planes = image.getPlanes();
        //remaining0 = rowStride*(h-1)+w => 27632= 192*143+176
        int remaining0 = planes[0].getBuffer().remaining();
        int remaining1 = planes[1].getBuffer().remaining();
        //remaining2 = rowStride*(h/2-1)+w-1 =>  13807=  192*71+176-1
        int remaining2 = planes[2].getBuffer().remaining();
        //获取pixelStride，可能跟width相等，可能不相等
        int pixelStride = planes[2].getPixelStride();
        int rowOffest = planes[2].getRowStride();
        byte[] nv21 = new byte[i420Size];
        byte[] yRawSrcBytes = new byte[remaining0];
        byte[] uRawSrcBytes = new byte[remaining1];
        byte[] vRawSrcBytes = new byte[remaining2];
        planes[0].getBuffer().get(yRawSrcBytes);
        planes[1].getBuffer().get(uRawSrcBytes);
        planes[2].getBuffer().get(vRawSrcBytes);
        if (pixelStride == w) {
            //两者相等，说明每个YUV块紧密相连，可以直接拷贝
            System.arraycopy(yRawSrcBytes, 0, nv21, 0, rowOffest * h);
            System.arraycopy(vRawSrcBytes, 0, nv21, rowOffest * h, rowOffest * h / 2 - 1);
        } else {
            byte[] ySrcBytes = new byte[w * h];
            byte[] uSrcBytes = new byte[w * h / 2 - 1];
            byte[] vSrcBytes = new byte[w * h / 2 - 1];
            for (int row = 0; row < h; row++) {
                //源数组每隔 rowOffest 个bytes 拷贝 w 个bytes到目标数组
                System.arraycopy(yRawSrcBytes, rowOffest * row, ySrcBytes, w * row, w);

                //y执行两次，uv执行一次
                if (row % 2 == 0) {
                    //最后一行需要减一
                    if (row == h - 2) {
                        System.arraycopy(vRawSrcBytes, rowOffest * row / 2, vSrcBytes, w * row / 2, w - 1);
                    } else {
                        System.arraycopy(vRawSrcBytes, rowOffest * row / 2, vSrcBytes, w * row / 2, w);
                    }
                }
            }
            System.arraycopy(ySrcBytes, 0, nv21, 0, w * h);
            System.arraycopy(vSrcBytes, 0, nv21, w * h, w * h / 2 - 1);
        }

        Bitmap bm = BitmapUtil.getBitmapImageFromYUV(nv21, w, h);

//        Matrix m = new Matrix();
//        m.setRotate(90, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
//
//        Bitmap res=Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);

        return bm;
    }
    private static boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }
    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        if (VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (VERBOSE) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
                Log.v(TAG, "buffer size " + buffer.remaining());
            }
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }
    public List<org.opencv.core.Rect> ini_process(Mat gray)
    {
        Mat blur_image=new Mat();
        Imgproc.blur(gray,blur_image,new org.opencv.core.Size(70,70));
        Mat binary=new Mat();
        Imgproc.threshold(blur_image,binary,0,255,Imgproc.THRESH_OTSU);
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy=new Mat();
        Imgproc.findContours(binary,contours,hierarchy,Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        List<org.opencv.core.Rect> rect = new ArrayList<org.opencv.core.Rect>();
        for(MatOfPoint contour :contours)
        {
//            ArrayList<Coordinate> tmp=new ArrayList<Coordinate>();
//            for( Point p: contour.toList() ){
//                tmp.add(new Coordinate((int)p.x,(int)p.y));
//            }
            org.opencv.core.Rect rect1=Imgproc.boundingRect(contour);
//            Imgproc.rectangle(binary, rect1,new Scalar(255,255,0),5);
            rect.add(rect1);
//            con.add(tmp);
        }
        return rect;
    }
    public void findContours(Mat gray,List<ArrayList<Coordinate>> contours,ArrayList<Coordinate> center)
    {
        Mat binary=new Mat();
        Imgproc.threshold(gray,binary,220,255,Imgproc.THRESH_BINARY);
        List<MatOfPoint> contour = new ArrayList<>();
        Mat hierarchy=new Mat();
        Imgproc.findContours(binary,contour,hierarchy,Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
//        System.out.println("总共找到的轮廓个数"+contour.size());
        for(MatOfPoint cont :contour)
        {
            org.opencv.core.Rect rect1=Imgproc.boundingRect(cont);
            if(rect1.width<100 || rect1.height<100)
            {
                continue;
            }
            int center_x=rect1.x+rect1.width/2;
            int center_y=rect1.y+rect1.height/2;
            ArrayList<Coordinate> tmp=new ArrayList<>();
            for( Point p: cont.toList() ){
                tmp.add(new Coordinate((int)p.x,(int)p.y));
            }
            contours.add(tmp);
            center.add(new Coordinate(center_x,center_y));
//            System.out.println(center_x+","+center_y);
        }
    }
    public double adap_threshold(List<org.opencv.core.Rect> rect,Mat gray,int width_max)
    {
        double threshold_list = 0;
        for(org.opencv.core.Rect boxx:rect)
        {
            if (boxx.width<width_max/2)
            {
                continue;
            }
            double sum = 0;
            int a=boxx.width/2+boxx.x;
            for(int k= boxx.y; k<boxx.y+boxx.height;k++)
            {
                sum=sum+gray.get(k,a)[0];
            }
            threshold_list+=sum/boxx.height;
        }
        return threshold_list/rect.size();
    }
    public void try_decode(Mat img,List<org.opencv.core.Rect> rect,double th,int[] err_list,ArrayList<Integer> id,ArrayList<Coordinate> center,ArrayList<ArrayList<Coordinate>> con_list,int  width_max)
    {
        for(int j=0;j<rect.size();j++)
        {
            if (rect.get(j).width<width_max/2)
            {
                continue;
            }
            int center_x=rect.get(j).x+rect.get(j).width/2;
            int center_y=rect.get(j).y+rect.get(j).height/2;
            org.opencv.core.Rect area=new org.opencv.core.Rect(rect.get(j).x,rect.get(j).y,rect.get(j).width,rect.get(j).height);
            Mat imageROI = new Mat(img,area);
            Mat gray_roi=new Mat();
            Imgproc.cvtColor(imageROI, gray_roi,Imgproc.COLOR_BGR2GRAY);
            Mat binary_roi=new Mat();
            Imgproc.threshold(gray_roi,binary_roi,th,255,Imgproc.THRESH_OTSU);
            Mat dst=new Mat();
            Mat kennel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new org.opencv.core.Size(3,3));
            Imgproc.erode(binary_roi,dst,kennel);
//                Utils.matToBitmap(dst,bitmap);
            ArrayList<Integer> temp=new ArrayList<Integer>();
            for(int i=0;i<rect.get(j).height;++i)
            {
                temp.add((int)dst.get(i,rect.get(j).width/2)[0]);
            }
//            Collections.reverse(temp);
            ArrayList<Integer> binlist=new ArrayList<Integer>();
            int count=1;
            for(int i=0;i<temp.size()-1;i++)
            {
                if(!temp.get(i).equals(temp.get(i + 1)))
                {
                    binlist.add(count);
                    count=1;
                }
                else
                {
                    count++;
                }
            }
            if(binlist.size()<8)
            {
                return;
            }
            List<Integer> binlist1=binlist.subList(1,binlist.size()-1);
            int th1_low= Collections.max(binlist1)/3;
            int th1_high = th1_low + 2;
            int th2_high = th1_low * 2 + 2;
            int th3_high =Collections.max(binlist1) + 2;
            int th4_high = th1_high * 4;
            ArrayList<Integer> a=new ArrayList<Integer>();
            ArrayList<Integer> assilist=new ArrayList<Integer>();
            for(int i=0;i<binlist1.size();++i)
            {
                if (temp.get(binlist.get(0) / 2) == 0)// 第一个条纹是亮条纹(索引为0），则暗条纹在偶数位置（索引为奇数）
                {
                    int x=binlist1.get(i);
                    if (x <= th1_high)
                    {
                        a.add((i + 1) % 2);
                        assilist.add((i + 1) % 2);
                    }
                    else if(x<= th2_high)
                    {
                        assilist.add((i + 1) % 2);
                        for(int k=0;k<2;k++)
                        {
                            a.add((i + 1) % 2);
                        }

                    }
                    else if(x<= th3_high)
                    {
                        assilist.add((i + 1) % 2);
                        for(int k=0;k<3;k++)
                        {
                            a.add((i + 1) % 2);
                        }

                    }
                    else if(x<= th4_high)
                    {
                        assilist.add((i + 1) % 2);
                        for(int k=0;k<4;k++)
                        {
                            a.add((i + 1) % 2);
                        }

                    }
                }
                else
                {
                    int x=binlist1.get(i);
                    if (x <= th1_high)
                    {
                        a.add(i % 2);
                        assilist.add(i % 2);
                    }
                    else if(x<= th2_high)
                    {
                        assilist.add(i % 2);
                        for(int k=0;k<2;k++)
                        {
                            a.add(i % 2);
                        }

                    }
                    else if(x<= th3_high)
                    {
                        assilist.add(i % 2);
                        for(int k=0;k<3;k++)
                        {
                            a.add(i % 2);
                        }

                    }
                    else if(x<= th4_high)
                    {
                        assilist.add(i % 2);
                        for(int k=0;k<4;k++)
                        {
                            a.add(i % 2);
                        }
                    }
                }
            }
            ArrayList<Integer> decodelist=new ArrayList<Integer>();
            int count1=1;
            for(int i=0;i<a.size()-1;++i)
            {
                if(!a.get(i).equals(a.get(i + 1)))
                {
                    decodelist.add(count1);
                    count1=1;
                }
                else
                {
                    count1++;
                }
            }

            ArrayList<Integer> position_list=new ArrayList<Integer>();
            for(int i=0;i<decodelist.size();i++)
            {
                if(decodelist.get(i)==3)
                {
                    position_list.add(i);
                }
            }
            ArrayList<Integer> diff_list_alternate=new ArrayList<Integer>();
//            ArrayList<Integer> diff_list_adjoin=new ArrayList<Integer>();
            for(int i=0;i<position_list.size()-2;i++)
            {
                diff_list_alternate.add(position_list.get(i + 2) - position_list.get(i));
            }
//            for(int i=0;i<position_list.size()-1;i++)
//            {
//                diff_list_adjoin.add(position_list.get(i + 1) - position_list.get(i));
//            }
            for(int i=0;i<diff_list_alternate.size();i++)
            {
                if(diff_list_alternate.get(i)==6)
                {
                    List<Integer> temp_decode_list_two=decodelist.subList(position_list.get(i) + 1, position_list.get(i + 2));
                    if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                            && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0)
                    {
                        err_list[j]=1;
                        id.add(1);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=1");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                            && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1)
                    {
                        err_list[j]=1;
                        id.add(1);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=1");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                            && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 2)
                    {
                        err_list[j]=1;
                        id.add(2);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=2");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                            && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                        err_list[j] = 1;
                        id.add(3);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=3");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                            && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                        err_list[j] = 1;
                        id.add(3);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=3");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                            && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                        err_list[j] = 1;
                        id.add(4);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=4");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                            && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                        err_list[j] = 1;
                        id.add(4);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=4");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                            && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                        err_list[j] = 1;
                        id.add(5);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=5");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                            && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                        err_list[j] = 1;
                        id.add(5);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=5");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) ==1
                            && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                        err_list[j] = 1;
                        id.add(6);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=6");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                            && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                        err_list[j] = 1;
                        id.add(6);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=6");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                            && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 1) {
                        err_list[j] = 1;
                        id.add(7);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=7");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                            && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                        err_list[j] = 1;
                        id.add(8);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=8");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                            && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                        err_list[j] = 1;
                        id.add(8);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=8");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                            && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                        err_list[j] = 1;
                        id.add(9);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=9");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                            && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                        err_list[j] = 1;
                        id.add(9);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=9");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                            && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                        err_list[j] = 1;
                        id.add(10);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=9");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                            && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                        err_list[j] = 1;
                        id.add(10);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=9");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                            && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                        err_list[j] = 1;
                        id.add(11);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=9");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                            && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                        err_list[j] = 1;
                        id.add(11);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=9");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                            && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                        err_list[j] = 1;
                        id.add(12);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=9");
                        break;
                    }
                    if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                            && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                        err_list[j] = 1;
                        id.add(12);
                        for(int ic=0;ic<centers.size();ic++)
                        {
                            double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                            if(distance<60)
                            {
                                center.add(centers.get(ic));
                                con_list.add(contours.get(ic));
                                break;
                            }
                        }
                        //System.out.println("ID=9");
                        break;
                    }
                }
            }

        }
    }
    public void second_decode(Mat img,List<org.opencv.core.Rect> rect,double th,int[] err_list,ArrayList<Integer> id,ArrayList<Coordinate> center,ArrayList<ArrayList<Coordinate>> con_list,int width_max)
    {
        for(int j=0;j<rect.size();j++)
        {
            if (rect.get(j).width<width_max/2)
            {
                continue;
            }
            if(err_list[j]==0) {
                int center_x = rect.get(j).x + rect.get(j).width / 2;
                int center_y = rect.get(j).y + rect.get(j).height / 2;
                org.opencv.core.Rect area = new org.opencv.core.Rect(rect.get(j).x, rect.get(j).y, rect.get(j).width, rect.get(j).height);
                Mat imageROI = new Mat(img, area);
                Mat gray_roi = new Mat();
                Imgproc.cvtColor(imageROI, gray_roi, Imgproc.COLOR_BGR2GRAY);
                Mat binary_roi = new Mat();
                Imgproc.threshold(gray_roi, binary_roi, th, 255, Imgproc.THRESH_OTSU);
                Mat dst = new Mat();
                Mat kennel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(2, 2));
                Imgproc.erode(binary_roi, dst, kennel);
                //                Utils.matToBitmap(dst,bitmap);
                ArrayList<Integer> temp = new ArrayList<Integer>();
                for(int i=0;i<rect.get(j).height;++i)
                {
                    temp.add((int)dst.get(i,rect.get(j).width/2)[0]);
                }
//                Collections.reverse(temp);
                ArrayList<Integer> binlist = new ArrayList<Integer>();
                int count = 1;
                for (int i = 0; i < temp.size() - 1; i++) {
                    if (!temp.get(i).equals(temp.get(i + 1))) {
                        binlist.add(count);
                        count = 1;
                    } else {
                        count++;
                    }
                }
                if(binlist.size()<8)
                {
                    return;
                }
                List<Integer> binlist1 = binlist.subList(1, binlist.size() - 1);
                int th1_low = Collections.max(binlist1) / 3;
                int th1_high = th1_low + 2;
                int th2_high = th1_low * 2 + 2;
                int th3_high = Collections.max(binlist1) + 2;
                int th4_high = th1_high * 4;
                ArrayList<Integer> a = new ArrayList<Integer>();
                ArrayList<Integer> assilist = new ArrayList<Integer>();
                for (int i = 0; i < binlist1.size(); ++i) {
                    if (temp.get(binlist.get(0) / 2) == 0)// 第一个条纹是亮条纹(索引为0），则暗条纹在偶数位置（索引为奇数）
                    {
                        int x = binlist1.get(i);
                        if (x <= th1_high) {
                            a.add((i + 1) % 2);
                            assilist.add((i + 1) % 2);
                        } else if (x <= th2_high) {
                            assilist.add((i + 1) % 2);
                            for (int k = 0; k < 2; k++) {
                                a.add((i + 1) % 2);
                            }

                        } else if (x <= th3_high) {
                            assilist.add((i + 1) % 2);
                            for (int k = 0; k < 3; k++) {
                                a.add((i + 1) % 2);
                            }

                        } else if (x <= th4_high) {
                            assilist.add((i + 1) % 2);
                            for (int k = 0; k < 4; k++) {
                                a.add((i + 1) % 2);
                            }

                        }
                    } else {
                        int x = binlist1.get(i);
                        if (x <= th1_high) {
                            a.add(i % 2);
                            assilist.add(i % 2);
                        } else if (x <= th2_high) {
                            assilist.add(i % 2);
                            for (int k = 0; k < 2; k++) {
                                a.add(i % 2);
                            }

                        } else if (x <= th3_high) {
                            assilist.add(i % 2);
                            for (int k = 0; k < 3; k++) {
                                a.add(i % 2);
                            }

                        } else if (x <= th4_high) {
                            assilist.add(i % 2);
                            for (int k = 0; k < 4; k++) {
                                a.add(i % 2);
                            }

                        }
                    }
                }
                ArrayList<Integer> decodelist = new ArrayList<Integer>();
                int count1 = 1;
                for (int i = 0; i < a.size() - 1; ++i) {
                    if (!a.get(i).equals(a.get(i + 1))) {
                        decodelist.add(count1);
                        count1 = 1;
                    } else {
                        count1++;
                    }
                }

                ArrayList<Integer> position_list = new ArrayList<Integer>();
                for (int i = 0; i < decodelist.size(); i++) {
                    if (decodelist.get(i) == 3) {
                        position_list.add(i);
                    }
                }
                ArrayList<Integer> diff_list_alternate = new ArrayList<Integer>();
//                ArrayList<Integer> diff_list_adjoin = new ArrayList<Integer>();
                for (int i = 0; i < position_list.size() - 2; i++) {
                    diff_list_alternate.add(position_list.get(i + 2) - position_list.get(i));
                }
//                for (int i = 0; i < position_list.size() - 1; i++) {
//                    diff_list_adjoin.add(position_list.get(i + 1) - position_list.get(i));
//                }
                for (int i = 0; i < diff_list_alternate.size(); i++) {
                    if (diff_list_alternate.get(i) == 6) {
                        List<Integer> temp_decode_list_two = decodelist.subList(position_list.get(i) + 1, position_list.get(i + 2));
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(1);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=1");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(1);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=1");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 2)
                        {
                            err_list[j]=1;
                            id.add(2);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=2");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(3);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=3");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(3);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=3");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(4);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=4");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(4);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=4");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(5);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=5");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(5);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=5");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) ==1
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(6);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=6");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(6);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=6");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 1) {
                            err_list[j] = 1;
                            id.add(7);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=7");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(8);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=8");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(8);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=8");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(9);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(9);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(10);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(10);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(11);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(11);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(12);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(12);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                    }
                }
            }
        }
    }
    public void third_decode(Mat img,List<org.opencv.core.Rect> rect,double th,int[] err_list,ArrayList<Integer> id,ArrayList<Coordinate> center,ArrayList<ArrayList<Coordinate>> con_list,int width_max)
    {
        for(int j=0;j<rect.size();j++)
        {
            if (rect.get(j).width<width_max/2)
            {
                continue;
            }
            if(err_list[j]==0) {
                int center_x = rect.get(j).x + rect.get(j).width / 2;
                int center_y = rect.get(j).y + rect.get(j).height / 2;
                org.opencv.core.Rect area = new org.opencv.core.Rect(rect.get(j).x, rect.get(j).y, rect.get(j).width, rect.get(j).height);
                Mat imageROI = new Mat(img, area);
                Mat gray_roi = new Mat();
                Imgproc.cvtColor(imageROI, gray_roi, Imgproc.COLOR_BGR2GRAY);
                Mat binary_roi = new Mat();
                Imgproc.threshold(gray_roi, binary_roi, th, 255, Imgproc.THRESH_OTSU);
                Mat dst = new Mat();
                Mat kennel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(4, 4));
                Imgproc.erode(binary_roi, dst, kennel);
                //                Utils.matToBitmap(dst,bitmap);
                ArrayList<Integer> temp = new ArrayList<Integer>();
                for(int i=0;i<rect.get(j).height;++i)
                {
                    temp.add((int)dst.get(i,rect.get(j).width/2)[0]);
                }
//                Collections.reverse(temp);
                ArrayList<Integer> binlist = new ArrayList<Integer>();
                int count = 1;
                for (int i = 0; i < temp.size() - 1; i++) {
                    if (!temp.get(i).equals(temp.get(i + 1))) {
                        binlist.add(count);
                        count = 1;
                    } else {
                        count++;
                    }
                }
                if(binlist.size()<8)
                {
                    return;
                }
                List<Integer> binlist1 = binlist.subList(1, binlist.size() - 1);
                int th1_low = Collections.max(binlist1) / 3;
                int th1_high = th1_low + 2;
                int th2_high = th1_low * 2 + 2;
                int th3_high = Collections.max(binlist1) + 2;
                int th4_high = th1_high * 4;
                ArrayList<Integer> a = new ArrayList<Integer>();
                ArrayList<Integer> assilist = new ArrayList<Integer>();
                for (int i = 0; i < binlist1.size(); ++i) {
                    if (temp.get(binlist.get(0) / 2) == 0)// 第一个条纹是亮条纹(索引为0），则暗条纹在偶数位置（索引为奇数）
                    {
                        int x = binlist1.get(i);
                        if (x <= th1_high) {
                            a.add((i + 1) % 2);
                            assilist.add((i + 1) % 2);
                        } else if (x <= th2_high) {
                            assilist.add((i + 1) % 2);
                            for (int k = 0; k < 2; k++) {
                                a.add((i + 1) % 2);
                            }

                        } else if (x <= th3_high) {
                            assilist.add((i + 1) % 2);
                            for (int k = 0; k < 3; k++) {
                                a.add((i + 1) % 2);
                            }

                        } else if (x <= th4_high) {
                            assilist.add((i + 1) % 2);
                            for (int k = 0; k < 4; k++) {
                                a.add((i + 1) % 2);
                            }

                        }
                    } else {
                        int x = binlist1.get(i);
                        if (x <= th1_high) {
                            a.add(i % 2);
                            assilist.add(i % 2);
                        } else if (x <= th2_high) {
                            assilist.add(i % 2);
                            for (int k = 0; k < 2; k++) {
                                a.add(i % 2);
                            }

                        } else if (x <= th3_high) {
                            assilist.add(i % 2);
                            for (int k = 0; k < 3; k++) {
                                a.add(i % 2);
                            }

                        } else if (x <= th4_high) {
                            assilist.add(i % 2);
                            for (int k = 0; k < 4; k++) {
                                a.add(i % 2);
                            }

                        }
                    }
                }
                ArrayList<Integer> decodelist = new ArrayList<Integer>();
                int count1 = 1;
                for (int i = 0; i < a.size() - 1; ++i) {
                    if (!a.get(i).equals(a.get(i + 1))) {
                        decodelist.add(count1);
                        count1 = 1;
                    } else {
                        count1++;
                    }
                }

                ArrayList<Integer> position_list = new ArrayList<Integer>();
                for (int i = 0; i < decodelist.size(); i++) {
                    if (decodelist.get(i) == 3) {
                        position_list.add(i);
                    }
                }
                ArrayList<Integer> diff_list_alternate = new ArrayList<Integer>();
//                ArrayList<Integer> diff_list_adjoin = new ArrayList<Integer>();
                for (int i = 0; i < position_list.size() - 2; i++) {
                    diff_list_alternate.add(position_list.get(i + 2) - position_list.get(i));
                }
//                for (int i = 0; i < position_list.size() - 1; i++) {
//                    diff_list_adjoin.add(position_list.get(i + 1) - position_list.get(i));
//                }
                for (int i = 0; i < diff_list_alternate.size(); i++) {
                    if (diff_list_alternate.get(i) == 6) {
                        List<Integer> temp_decode_list_two = decodelist.subList(position_list.get(i) + 1, position_list.get(i + 2));
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(1);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=1");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(1);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=1");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 2)
                        {
                            err_list[j]=1;
                            id.add(2);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=2");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(3);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=3");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(3);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=3");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(4);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=4");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(4);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=4");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(5);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=5");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(5);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=5");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) ==1
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(6);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=6");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(6);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=6");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 1) {
                            err_list[j] = 1;
                            id.add(7);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=7");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(8);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=8");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(8);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=8");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(9);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(9);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(10);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(10);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(11);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(11);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(12);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(12);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                    }
                }
            }
        }
    }
    public void fourth_decode(Mat img,List<org.opencv.core.Rect> rect,double th,int[] err_list,ArrayList<Integer> id,ArrayList<Coordinate> center,ArrayList<ArrayList<Coordinate>> con_list,int width_max)
    {
        for(int j=0;j<rect.size();j++)
        {
            if (rect.get(j).width<width_max/2)
            {
                continue;
            }
            if(err_list[j]==0) {
                int center_x = rect.get(j).x + rect.get(j).width / 2;
                int center_y = rect.get(j).y + rect.get(j).height / 2;
                org.opencv.core.Rect area = new org.opencv.core.Rect(rect.get(j).x, rect.get(j).y, rect.get(j).width, rect.get(j).height);
                Mat imageROI = new Mat(img, area);
                Mat gray_roi = new Mat();
                Imgproc.cvtColor(imageROI, gray_roi, Imgproc.COLOR_BGR2GRAY);
                Mat binary_roi = new Mat();
                Imgproc.threshold(gray_roi, binary_roi, th, 255, Imgproc.THRESH_OTSU);
                Mat dst = new Mat();
                Mat kennel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(1, 1));
                Imgproc.erode(binary_roi, dst, kennel);
                //                Utils.matToBitmap(dst,bitmap);
                ArrayList<Integer> temp = new ArrayList<Integer>();
                for(int i=0;i<rect.get(j).height;++i)
                {
                    temp.add((int)dst.get(i,rect.get(j).width/2)[0]);
                }
//                Collections.reverse(temp);
                ArrayList<Integer> binlist = new ArrayList<Integer>();
                int count = 1;
                for (int i = 0; i < temp.size() - 1; i++) {
                    if (!temp.get(i).equals(temp.get(i + 1))) {
                        binlist.add(count);
                        count = 1;
                    } else {
                        count++;
                    }
                }
                if(binlist.size()<8)
                {
                    return;
                }
                List<Integer> binlist1 = binlist.subList(1, binlist.size() - 1);
                int th1_low = Collections.max(binlist1) / 3;
                int th1_high = th1_low + 2;
                int th2_high = th1_low * 2 + 2;
                int th3_high = Collections.max(binlist1) + 2;
                int th4_high = th1_high * 4;
                ArrayList<Integer> a = new ArrayList<Integer>();
                ArrayList<Integer> assilist = new ArrayList<Integer>();
                for (int i = 0; i < binlist1.size(); ++i) {
                    if (temp.get(binlist.get(0) / 2) == 0)// 第一个条纹是亮条纹(索引为0），则暗条纹在偶数位置（索引为奇数）
                    {
                        int x = binlist1.get(i);
                        if (x <= th1_high) {
                            a.add((i + 1) % 2);
                            assilist.add((i + 1) % 2);
                        } else if (x <= th2_high) {
                            assilist.add((i + 1) % 2);
                            for (int k = 0; k < 2; k++) {
                                a.add((i + 1) % 2);
                            }

                        } else if (x <= th3_high) {
                            assilist.add((i + 1) % 2);
                            for (int k = 0; k < 3; k++) {
                                a.add((i + 1) % 2);
                            }

                        } else if (x <= th4_high) {
                            assilist.add((i + 1) % 2);
                            for (int k = 0; k < 4; k++) {
                                a.add((i + 1) % 2);
                            }

                        }
                    } else {
                        int x = binlist1.get(i);
                        if (x <= th1_high) {
                            a.add(i % 2);
                            assilist.add(i % 2);
                        } else if (x <= th2_high) {
                            assilist.add(i % 2);
                            for (int k = 0; k < 2; k++) {
                                a.add(i % 2);
                            }

                        } else if (x <= th3_high) {
                            assilist.add(i % 2);
                            for (int k = 0; k < 3; k++) {
                                a.add(i % 2);
                            }

                        } else if (x <= th4_high) {
                            assilist.add(i % 2);
                            for (int k = 0; k < 4; k++) {
                                a.add(i % 2);
                            }

                        }
                    }
                }
                ArrayList<Integer> decodelist = new ArrayList<Integer>();
                int count1 = 1;
                for (int i = 0; i < a.size() - 1; ++i) {
                    if (!a.get(i).equals(a.get(i + 1))) {
                        decodelist.add(count1);
                        count1 = 1;
                    } else {
                        count1++;
                    }
                }

                ArrayList<Integer> position_list = new ArrayList<Integer>();
                for (int i = 0; i < decodelist.size(); i++) {
                    if (decodelist.get(i) == 3) {
                        position_list.add(i);
                    }
                }
                ArrayList<Integer> diff_list_alternate = new ArrayList<Integer>();
//                ArrayList<Integer> diff_list_adjoin = new ArrayList<Integer>();
                for (int i = 0; i < position_list.size() - 2; i++) {
                    diff_list_alternate.add(position_list.get(i + 2) - position_list.get(i));
                }
//                for (int i = 0; i < position_list.size() - 1; i++) {
//                    diff_list_adjoin.add(position_list.get(i + 1) - position_list.get(i));
//                }
                for (int i = 0; i < diff_list_alternate.size(); i++) {
                    if (diff_list_alternate.get(i) == 6) {
                        List<Integer> temp_decode_list_two = decodelist.subList(position_list.get(i) + 1, position_list.get(i + 2));
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            id.add(1);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=1");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                            id.add(1);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=1");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 2)
                        {
                            id.add(2);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=2");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                            id.add(3);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=3");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            id.add(3);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=3");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                            id.add(4);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=4");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            id.add(4);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=4");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                            id.add(5);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=5");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) == 2 && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                            id.add(5);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=5");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) ==1
                                && temp_decode_list_two.get(3) == 1 && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            id.add(6);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=6");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                            id.add(6);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
//                                //System.out.println("每个轮廓与6号LED之间的距离："+distance);
                                if(distance<60)
                                {
//                                    System.out.println("通信图片的LED中心："+center_x+","+center_y);
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=6");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 1) {
                            err_list[j] = 1;
                            id.add(7);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=7");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(8);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=8");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(8);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=8");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(9);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 3
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(9);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 1 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(10);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(10);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(11);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 1 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 2
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(11);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 2 && temp_decode_list_two.get(1) == 1 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==2  && temp_decode_list_two.get(4) == 3 && assilist.get(position_list.get(i)) == 0) {
                            err_list[j] = 1;
                            id.add(12);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                        if (temp_decode_list_two.get(0) == 3 && temp_decode_list_two.get(1) == 2 && temp_decode_list_two.get(2) == 1
                                && temp_decode_list_two.get(3) ==1  && temp_decode_list_two.get(4) == 2 && assilist.get(position_list.get(i)) == 1) {
                            err_list[j] = 1;
                            id.add(12);
                            for(int ic=0;ic<centers.size();ic++)
                            {
                                double distance=Math.sqrt((center_x- centers.get(ic).m_x)*(center_x- centers.get(ic).m_x)+(center_y- centers.get(ic).m_y)*(center_y- centers.get(ic).m_y));
                                if(distance<60)
                                {
                                    center.add(centers.get(ic));
                                    con_list.add(contours.get(ic));
                                    break;
                                }
                            }
                            //System.out.println("ID=9");
                            break;
                        }
                    }
                }
            }
        }
    }
    public double[] ellipsefit4(Jama.Matrix ledmark)
    {
        Jama.Matrix x=ledmark.getMatrix(0,0,0,ledmark.getColumnDimension()-1);
        Jama.Matrix y=ledmark.getMatrix(1,1,0,ledmark.getColumnDimension()-1);
        Jama.Matrix temp=x.arrayTimes(x);
        Jama.Matrix temp1=x.arrayTimes(y);
        Jama.Matrix temp2=y.arrayTimes(y);
        double[] a=new double[5];
        Jama.Matrix D1=new Jama.Matrix(ledmark.getColumnDimension(),3);
        D1.setMatrix(0,ledmark.getColumnDimension()-1,0,0,temp.transpose());
        D1.setMatrix(0,ledmark.getColumnDimension()-1,1,1,temp1.transpose());
        D1.setMatrix(0,ledmark.getColumnDimension()-1,2,2,temp2.transpose());
        Jama.Matrix D2=new Jama.Matrix(ledmark.getColumnDimension(),3);
        Jama.Matrix t1=new Jama.Matrix(ledmark.getColumnDimension(),1,1);
        D2.setMatrix(0,ledmark.getColumnDimension()-1,0,0,x.transpose());
        D2.setMatrix(0,ledmark.getColumnDimension()-1,1,1,y.transpose());
        D2.setMatrix(0,ledmark.getColumnDimension()-1,2,2,t1);
        Jama.Matrix S1=D1.transpose().times(D1);
        Jama.Matrix S2=D1.transpose().times(D2);
        Jama.Matrix S3=D2.transpose().times(D2);
        Jama.Matrix T=S3.inverse().times(S2.transpose()).times(-1);
        Jama.Matrix M=S1.plus(S2.times(T));
        Jama.Matrix C1=new Jama.Matrix(new double[][] {{0,0,2},{0,-1,0},{2,0,0}});
        Jama.Matrix M1=C1.inverse().times(M);
        Jama.Matrix v=M1.eig().getV();
//        Jama.Matrix e=M1.eig().getD();
        Jama.Matrix cond=v.getMatrix(0,0,0,2).arrayTimes(v.getMatrix(2,2,0,2)).times(4).minus(v.getMatrix(1,1,0,2).arrayTimes(v.getMatrix(1,1,0,2)));
        if(cond.get(0,0)<0 && cond.get(0,1)<0 && cond.get(0,2)<0)
        {
            return a;
        }
        else
        {
            int i=0;
            for(;i<3;i++)
            {
                if(cond.get(0,i)>0)
                {
                    break;
                }
            }
            Jama.Matrix a1=v.getMatrix(0,2,i,i);
            Jama.Matrix tmp=T.times(a1);
            double[] a2=new double[6];
            a2[0]=a1.getColumnPackedCopy()[0];
            a2[1]=a1.getColumnPackedCopy()[1];
            a2[2]=a1.getColumnPackedCopy()[2];
            a2[3]=tmp.getColumnPackedCopy()[0];
            a2[4]=tmp.getColumnPackedCopy()[1];
            a2[5]=tmp.getColumnPackedCopy()[2];
            for(int j=0;j<5;j++)
            {
                a[j]=a2[j]/a2[5];
            }
        }
        return a;
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    public double[] ellipse_position(ArrayList<Integer> id_list, ArrayList<Coordinate> center_list, ArrayList<ArrayList<Coordinate>> con_list)
    {
        if(con_list.size()<2)
        {
            return new double[]{-1, -1, -1};
        }
//        System.out.println("计算结果时的两个LED："+id_list.get(0)+","+id_list.get(1));
        int h = 240;  // 实验平台高度
        double[][] array1={{24.3,85.7,155.7,23.8,84.2,155.2,24.4,84.2,154.9,25.3,84.7,156.2},{31.5,33.2,32,92.4,91.9,91.8,151.2,152.3,152.2,211.9,272.5,211.8},{h,h,h,h,h,h,h,h,h,h,h,h}};
        Jama.Matrix led_gs=new Jama.Matrix(array1);
        Jama.Matrix led_corner_gs=new Jama.Matrix(3,2);
        double[] theta=new double[2];
        double[] fai=new double[2];
        double[][] psi=new double[2][2];
        double[] judgepsi1=new double[2];
        double[] judgepsi2=new double[2];
        for(int i=0;i<2;++i)
        {
            led_corner_gs.setMatrix(0,2,i,i,led_gs.getMatrix(0,2,id_list.get(i)-1,id_list.get(i)-1)); // LED在世界坐标系中的位置坐标
        }
        Jama.Matrix a = led_corner_gs.getMatrix(0,2,1,1).minus(led_corner_gs.getMatrix(0,2,0,0));
        double b=a.norm2();
        Jama.Matrix G2P_nv_temp=a.times(1/b);
        Jama.Matrix a1=new Jama.Matrix(new double[][] {{0,1,0}});
        double rot_angle_cos=Math.acos(a1.times(G2P_nv_temp).get(0,0)/a1.norm2()/G2P_nv_temp.norm2());
        if(led_corner_gs.get(0,1)<led_corner_gs.get(0,0))
        {
            rot_angle_cos=-rot_angle_cos;
        }
        double[][] array2={{Math.cos(rot_angle_cos),-Math.sin(rot_angle_cos),0},{Math.sin(rot_angle_cos),Math.cos(rot_angle_cos),0},{0,0,1}};
        Jama.Matrix rot_mat_z_final=new Jama.Matrix(array2);
//        Jama.Matrix led_G_gs_final=rot_mat_z_final.times(led_corner_gs);
        double R=5.5;
        double f = 0.3462;  // 焦距为3000um = 3mm = 0.3cm
        double dx = 0.000112;
        double dy = 0.000112;  // 图像中像素点的物理长度1.12 * 1.12um
        double fx = f / dx;
        double fy = f / dy;
        double u0 = 2080;
        double v0 = 1560;  // 主点
        Jama.Matrix A=new Jama.Matrix(new double[][] {{fx,0,u0},{0, fy, v0},{0, 0, 1}});
        ArrayList<Jama.Matrix> led_img_rs_noisy = new ArrayList<>();
        ArrayList<Jama.Matrix> rot_mat_trans2rs_temp = new ArrayList<>();
        ArrayList<Jama.Matrix> led_img_trans_temp = new ArrayList<>();
        ArrayList<Jama.Matrix> led_nv_trans_temp = new ArrayList<>();
        ArrayList<double[]> k_cut_temp = new ArrayList<>();
        ArrayList<double[]> b_led_temp = new ArrayList<>();
        ArrayList<Integer> n_temp = new ArrayList<>();
        for(int iled=0;iled<2;iled++)
        {
            ArrayList<Coordinate> con=con_list.get(iled);
//            for(int cout=0;cout<10;cout++)
//            {
//                System.out.println("计算结果时每个LED对应的轮廓"+con.get(cout).m_x+con.get(cout).m_y);
//            }
            con.add(center_list.get(iled));
//            for(int cout1=0;cout1<2;cout1++)
//            {
//                System.out.println("计算结果时的两个LED中心坐标："+center_list.get(cout1).m_x+","+center_list.get(cout1).m_y);
//            }
            Jama.Matrix led_img_rs_noisy_temp=new Jama.Matrix(new double[3][con.size()]);
            for(int ipoint=0;ipoint<con.size();ipoint++)
            {
                Jama.Matrix temp=new Jama.Matrix(new double[][] {{con.get(ipoint).m_x},{con.get(ipoint).m_y},{1}});
                Jama.Matrix temp1=A.inverse().times(temp).times(h);
                led_img_rs_noisy_temp.setMatrix(0,2,ipoint,ipoint,temp1.times(f/temp1.get(2,0)));
            }
            led_img_rs_noisy.add(led_img_rs_noisy_temp);
            Jama.Matrix ledmk1=led_img_rs_noisy_temp.copy();
            double[] Coe_elli_img;
            Coe_elli_img=ellipsefit4(ledmk1);
            double[][] array3={{Coe_elli_img[0]*Math.pow(f,2),0.5*Coe_elli_img[1]*Math.pow(f,2),0.5*Coe_elli_img[3]*f},
                    {0.5*Coe_elli_img[1]*Math.pow(f,2),Coe_elli_img[2]*Math.pow(f,2),0.5*Coe_elli_img[4]*f},
                    {0.5*Coe_elli_img[3]*f,0.5*Coe_elli_img[4]*f,1}};
            Jama.Matrix Coe_cone_rs_mat=new Jama.Matrix(array3);
            Jama.Matrix v1_temp=Coe_cone_rs_mat.eig().getV();
            Jama.Matrix e1=Coe_cone_rs_mat.eig().getD();
            double[] diag=e1.eig().getRealEigenvalues();
            double[] diag_abs = new double[3];
            double[] sign=new double[3];
            int[] ind=new int[3];
            for(int i=0;i<3;i++)
            {
                if(diag[0]==e1.get(i,i))
                {
                    ind[0]=i;
                }
                else if(diag[1]==e1.get(i,i))
                {
                    ind[1]=i;
                }
                else
                {
                    ind[2]=i;
                }
                if(diag[i]<0)
                {
                    sign[i]=-1;
                    diag_abs[i]=-diag[i];
                }
                else
                {
                    sign[i]=1;
                    diag_abs[i]=diag[i];
                }
            }
            Jama.Matrix v1=v1_temp.getMatrix(0,2,ind);
            Jama.Matrix rot_mat_trans2rs=v1;
            Jama.Matrix rot_mat_rs2trans=rot_mat_trans2rs.transpose();
            Jama.Matrix led_img_trans=rot_mat_rs2trans.times(led_img_rs_noisy.get(iled));
            Jama.Matrix led_Gi_trans = led_img_trans.getMatrix(0,2,led_img_trans.getColumnDimension()-1,led_img_trans.getColumnDimension()-1);// 图像中的led圆心在过渡坐标系中坐标


            Jama.Matrix led_nv_trans = null;
            double[] k_cut = new double[2];
            double[] b_led = new double[2];
            int n = 0;
            if(sign[2]!=sign[0] && sign[2]!=sign[1])
            {
                double temp_min= Math.min(diag_abs[0],diag_abs[1]);
                int B = diag_abs[0]<diag_abs[1]? 0:1;
                double temp_max= Math.max(diag_abs[0],diag_abs[1]);
                double k_cut1=Math.sqrt(Math.abs(diag[0]-diag[1])/(temp_min+diag_abs[2]));
                double k_cut2=-k_cut1;
                k_cut= new double[]{k_cut1, k_cut2};
                double[] led_Ge_trans={0,0,led_Gi_trans.get(2,0)};
                double[] b_cut={led_Ge_trans[2],led_Ge_trans[2]};
                double k_cone1=Math.sqrt(temp_max/diag_abs[2]);
                double k_cone2=-k_cone1;
                double [][] Mc={{b_cut[0]/(k_cone1-k_cut1),b_cut[1]/(k_cone1-k_cut2)},{k_cone1*b_cut[0]/(k_cone1-k_cut1),k_cone1*b_cut[1]/(k_cone1-k_cut2)}};
                double [][] Nc={{b_cut[0]/(k_cone2-k_cut1),b_cut[1]/(k_cone2-k_cut2)},{k_cone2*b_cut[0]/(k_cone2-k_cut1),k_cone2*b_cut[1]/(k_cone2-k_cut2)}};
                Jama.Matrix Mc1=new Jama.Matrix(Mc);
                Jama.Matrix Nc1=new Jama.Matrix(Nc);
                double[] r = new double[2];
                r[0]=Mc1.getMatrix(0,1,0,0).minus(Nc1.getMatrix(0,1,0,0)).norm2()/2;
                r[1]=Mc1.getMatrix(0,1,1,1).minus(Nc1.getMatrix(0,1,1,1)).norm2()/2;
                b_led= new double[]{R / r[0] * b_cut[0], R / r[0] * b_cut[1]};
                if(B==1)
                {
                    led_nv_trans=new Jama.Matrix(new double[][] {{k_cut1,k_cut2},{0,0},{-1,-1}});
                    n=1;
                }
                else {
                    led_nv_trans=new Jama.Matrix(new double[][] {{0,0},{k_cut1,k_cut2},{-1,-1}});
                    n=2;
                }
            }
            if(sign[0]!=sign[2] && sign[0]!=sign[1])
            {
                double temp_min= Math.min(diag_abs[1],diag_abs[2]);
                int B = diag_abs[1]<diag_abs[2]? 1:2;
                double temp_max= Math.max(diag_abs[1],diag_abs[2]);
                double k_cut1=Math.sqrt(Math.abs(diag[1]-diag[2])/(temp_min+diag_abs[0]));
                double k_cut2=-k_cut1;
                k_cut= new double[]{k_cut1, k_cut2};
                double[] led_Ge_trans={led_Gi_trans.get(0,0),0,0};
                double[] b_cut={led_Ge_trans[0],led_Ge_trans[0]};
                double k_cone1=Math.sqrt(temp_max/diag_abs[0]);
                double k_cone2=-k_cone1;
                double [][] Mc={{b_cut[0]/(k_cone1-k_cut1),b_cut[1]/(k_cone1-k_cut2)},{k_cone1*b_cut[0]/(k_cone1-k_cut1),k_cone1*b_cut[1]/(k_cone1-k_cut2)}};
                double [][] Nc={{b_cut[0]/(k_cone2-k_cut1),b_cut[1]/(k_cone2-k_cut2)},{k_cone2*b_cut[0]/(k_cone2-k_cut1),k_cone2*b_cut[1]/(k_cone2-k_cut2)}};
                Jama.Matrix Mc1=new Jama.Matrix(Mc);
                Jama.Matrix Nc1=new Jama.Matrix(Nc);
                double[] r = new double[2];
                r[0]=Mc1.getMatrix(0,1,0,0).minus(Nc1.getMatrix(0,1,0,0)).norm2()/2;
                r[1]=Mc1.getMatrix(0,1,1,1).minus(Nc1.getMatrix(0,1,1,1)).norm2()/2;
                b_led= new double[]{R / r[0] * b_cut[0], R / r[0] * b_cut[1]};
                if(B==2)
                {
                    led_nv_trans=new Jama.Matrix(new double[][] {{-1,-1},{k_cut1,k_cut2},{0,0}});
                    n=3;
                }
                else {
                    led_nv_trans=new Jama.Matrix(new double[][] {{-1,-1},{0,0},{k_cut1,k_cut2}});
                    n=4;
                }
            }
            if(sign[1]!=sign[0] && sign[1]!=sign[2])
            {
                double temp_min= Math.min(diag_abs[0],diag_abs[2]);
                int B = diag_abs[0]<diag_abs[2]? -1:1;
                double temp_max= Math.max(diag_abs[0],diag_abs[2]);
                double k_cut1=Math.sqrt(Math.abs(diag[0]-diag[2])/(temp_min+diag_abs[1]));
                double k_cut2=-k_cut1;
                k_cut= new double[]{k_cut1, k_cut2};
                double[] led_Ge_trans={0,led_Gi_trans.get(1,0),0};
                double[] b_cut={led_Ge_trans[1],led_Ge_trans[1]};
                double k_cone1=Math.sqrt(temp_max/diag_abs[1]);
                double k_cone2=-k_cone1;
                double [][] Mc={{b_cut[0]/(k_cone1-k_cut1),b_cut[1]/(k_cone1-k_cut2)},{k_cone1*b_cut[0]/(k_cone1-k_cut1),k_cone1*b_cut[1]/(k_cone1-k_cut2)}};
                double [][] Nc={{b_cut[0]/(k_cone2-k_cut1),b_cut[1]/(k_cone2-k_cut2)},{k_cone2*b_cut[0]/(k_cone2-k_cut1),k_cone2*b_cut[1]/(k_cone2-k_cut2)}};
                Jama.Matrix Mc1=new Jama.Matrix(Mc);
                Jama.Matrix Nc1=new Jama.Matrix(Nc);
                double[] r = new double[2];
                r[0]=Mc1.getMatrix(0,1,0,0).minus(Nc1.getMatrix(0,1,0,0)).norm2()/2;
                r[1]=Mc1.getMatrix(0,1,1,1).minus(Nc1.getMatrix(0,1,1,1)).norm2()/2;
                b_led= new double[]{R / r[0] * b_cut[0], R / r[0] * b_cut[1]};
                if(B==1)
                {
                    led_nv_trans=new Jama.Matrix(new double[][] {{k_cut1,k_cut2},{-1,-1},{0,0}});
                    n=5;
                }
                else {
                    led_nv_trans=new Jama.Matrix(new double[][] {{0,0},{-1,-1},{k_cut1,k_cut2}});
                    n=6;
                }
            }
            rot_mat_trans2rs_temp.add(rot_mat_trans2rs);
            led_img_trans_temp.add(led_img_trans);
            led_nv_trans_temp.add(led_nv_trans);
            k_cut_temp.add(k_cut);
            b_led_temp.add(b_led);
            n_temp.add(n);
        }
        Jama.Matrix led_nv_rs_temp1= rot_mat_trans2rs_temp.get(0).times(led_nv_trans_temp.get(0)).times(1/(led_nv_trans_temp.get(0).getMatrix(0,2,0,0).norm2()));
        Jama.Matrix led_nv_rs_temp2= rot_mat_trans2rs_temp.get(1).times(led_nv_trans_temp.get(1)).times(1/(led_nv_trans_temp.get(1).getMatrix(0,2,0,0).norm2()));
        double[][] array3=led_nv_rs_temp1.getArray();
        double[][] array4=led_nv_rs_temp2.getArray();
        double[][] array5 = new double[3][2];
        double[][] array6 = new double[3][2];
        for(int i=0;i<array3.length;i++)//取绝对值一定要有
        {
            for(int j=0;j<array3[0].length;j++)
            {
                array5[i][j]=array3[i][j];//深复制，不然会改变array3，从而改变led_nv_rs_temp1
                array6[i][j]=array4[i][j];//深复制，不然会改变array4，从而改变led_nv_rs_temp2
                array5[i][j]=Math.abs(array5[i][j]);
                array6[i][j]=Math.abs(array6[i][j]);
            }
        }
        Jama.Matrix led_nv_rs_temp1_final=new Jama.Matrix(array5);
        Jama.Matrix led_nv_rs_temp2_final=new Jama.Matrix(array6);
        double[] diff={led_nv_rs_temp1_final.getMatrix(0,2,0,0).minus(led_nv_rs_temp2_final.getMatrix(0,2,0,0)).norm2(),
                led_nv_rs_temp1_final.getMatrix(0,2,1,1).minus(led_nv_rs_temp2_final.getMatrix(0,2,1,1)).norm2(),
                led_nv_rs_temp1_final.getMatrix(0,2,0,0).minus(led_nv_rs_temp2_final.getMatrix(0,2,1,1)).norm2(),
                led_nv_rs_temp1_final.getMatrix(0,2,1,1).minus(led_nv_rs_temp2_final.getMatrix(0,2,0,0)).norm2()};
        int Bnv=IntStream.range(0, diff.length).reduce((i, j) -> diff[i] > diff[j] ? j : i).getAsInt();
        Jama.Matrix rot_mat_trans2rs=rot_mat_trans2rs_temp.get(0);
        Jama.Matrix rot_mat_trans2rs1=rot_mat_trans2rs_temp.get(1);
        Jama.Matrix led_img_trans=led_img_trans_temp.get(0);
        Jama.Matrix led_img_trans1=rot_mat_trans2rs_temp.get(0).transpose().times(led_img_rs_noisy.get(1));
        int col=led_img_trans.getColumnDimension();
        int col1=led_img_trans1.getColumnDimension();
        int n_case=n_temp.get(0);
        int n_case1=n_temp.get(1);
        Jama.Matrix led_G_gs=led_corner_gs.getMatrix(0,2,0,0);
        Jama.Matrix led_G1_gs=led_corner_gs.getMatrix(0,2,1,1);
        double k=0;
        double k1=0;
        double bled = 0;
        double bled1=0;
        Jama.Matrix led_nv_rs_est = new Jama.Matrix(3,1);
        Jama.Matrix led_nv_rs_est1 = new Jama.Matrix(3,1);
        if(Bnv<2)
        {
            Jama.Matrix led_nv_rs_est_temp=led_nv_rs_temp1.getMatrix(0,2,Bnv,Bnv).times(led_nv_rs_temp1.get(2,Bnv)>0?-1:1);
            double irr_judge=led_img_rs_noisy.get(0).getMatrix(0,2,col-1,col-1).times(-1).transpose().times(led_nv_rs_est_temp)
                    .get(0,0)/(led_img_rs_noisy.get(0).getMatrix(0,2,col-1,col-1).norm2())/(led_nv_rs_est_temp.norm2());
            led_nv_rs_est=led_nv_rs_est_temp.times(irr_judge>0?1:-1);
            Jama.Matrix led_nv_rs_est_temp1=led_nv_rs_temp2.getMatrix(0,2,Bnv,Bnv).times(led_nv_rs_temp2.get(2,Bnv)>0?-1:1);
            double irr_judge1=led_img_rs_noisy.get(1).getMatrix(0,2,col1-1,col1-1).times(-1).transpose().times(led_nv_rs_est_temp1)
                    .get(0,0)/(led_img_rs_noisy.get(1).getMatrix(0,2,col1-1,col1-1).norm2())/(led_nv_rs_est_temp1.norm2());
            led_nv_rs_est1=led_nv_rs_est_temp1.times(irr_judge1>0?1:-1);
            k=k_cut_temp.get(0)[Bnv];
            k1=k_cut_temp.get(1)[Bnv];
            bled=b_led_temp.get(0)[Bnv];
            bled1=b_led_temp.get(1)[Bnv];
        }
        else if(Bnv==2)
        {
            Jama.Matrix led_nv_rs_est_temp=led_nv_rs_temp1.getMatrix(0,2,0,0).times(led_nv_rs_temp1.get(2,0)>0?-1:1);
            double irr_judge=led_img_rs_noisy.get(0).getMatrix(0,2,col-1,col-1).times(-1).transpose().times(led_nv_rs_est_temp)
                    .get(0,0)/(led_img_rs_noisy.get(0).getMatrix(0,2,col-1,col-1).norm2())/(led_nv_rs_est_temp.norm2());
            led_nv_rs_est=led_nv_rs_est_temp.times(irr_judge>0?1:-1);
            Jama.Matrix led_nv_rs_est_temp1=led_nv_rs_temp2.getMatrix(0,2,1,1).times(led_nv_rs_temp2.get(2,1)>0?-1:1);
            double irr_judge1=led_img_rs_noisy.get(1).getMatrix(0,2,col1-1,col1-1).times(-1).transpose().times(led_nv_rs_est_temp1)
                    .get(0,0)/(led_img_rs_noisy.get(1).getMatrix(0,2,col1-1,col1-1).norm2())/(led_nv_rs_est_temp1.norm2());
            led_nv_rs_est1=led_nv_rs_est_temp1.times(irr_judge1>0?1:-1);
            k=k_cut_temp.get(0)[0];
            k1=k_cut_temp.get(1)[1];
            bled=b_led_temp.get(0)[0];
            bled1=b_led_temp.get(1)[1];
        }
        else if(Bnv==3)
        {
            Jama.Matrix led_nv_rs_est_temp=led_nv_rs_temp1.getMatrix(0,2,1,1).times(led_nv_rs_temp1.get(2,1)>0?-1:1);
            double irr_judge=led_img_rs_noisy.get(0).getMatrix(0,2,col-1,col-1).times(-1).transpose().times(led_nv_rs_est_temp)
                    .get(0,0)/(led_img_rs_noisy.get(0).getMatrix(0,2,col-1,col-1).norm2())/(led_nv_rs_est_temp.norm2());
            led_nv_rs_est=led_nv_rs_est_temp.times(irr_judge>0?1:-1);
            Jama.Matrix led_nv_rs_est_temp1=led_nv_rs_temp2.getMatrix(0,2,0,0).times(led_nv_rs_temp2.get(2,0)>0?-1:1);
            double irr_judge1=led_img_rs_noisy.get(1).getMatrix(0,2,col1-1,col1-1).times(-1).transpose().times(led_nv_rs_est_temp1)
                    .get(0,0)/(led_img_rs_noisy.get(1).getMatrix(0,2,col1-1,col1-1).norm2())/(led_nv_rs_est_temp1.norm2());
            led_nv_rs_est1=led_nv_rs_est_temp1.times(irr_judge1>0?1:-1);
            k=k_cut_temp.get(0)[1];
            k1=k_cut_temp.get(1)[0];
            bled=b_led_temp.get(0)[1];
            bled1=b_led_temp.get(1)[0];
        }
        double[][] led_G_trans_est_array;
        double[][] led_G_trans_est_array1;
        switch (n_case)
        {
            case 1: led_G_trans_est_array= new double[][]{{bled / (led_img_trans.get(2, col - 1) / led_img_trans.get(0, col - 1) - k)},
                    {(bled / (led_img_trans.get(2, col - 1) / led_img_trans.get(0, col - 1) - k))*(led_img_trans.get(1,col-1)/led_img_trans.get(0,col-1))},
                    {(bled / (led_img_trans.get(2, col - 1) / led_img_trans.get(0, col - 1) - k))*(led_img_trans.get(2,col-1)/led_img_trans.get(0,col-1))}};
                led_G_trans_est_array1= new double[][]{{bled / (led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(0, col1 - 1) - k)},
                        {(bled / (led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(0, col1 - 1) - k))*(led_img_trans1.get(1,col1-1)/led_img_trans1.get(0,col1-1))},
                        {(bled / (led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(0, col1 - 1) - k))*(led_img_trans1.get(2,col1-1)/led_img_trans1.get(0,col1-1))}};
                break;
            case 2:led_G_trans_est_array= new double[][]{
                    {(bled / (led_img_trans.get(2, col - 1) / led_img_trans.get(1, col - 1) - k))*(led_img_trans.get(0,col-1)/led_img_trans.get(1,col-1))},
                    {bled / (led_img_trans.get(2, col - 1) / led_img_trans.get(1, col - 1) - k)},
                    {(bled / (led_img_trans.get(2, col - 1) / led_img_trans.get(1, col - 1) - k))*(led_img_trans.get(2,col-1)/led_img_trans.get(1,col-1))}};
                led_G_trans_est_array1= new double[][]{
                        {(bled / (led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(1, col1 - 1) - k))*(led_img_trans1.get(0,col1-1)/led_img_trans1.get(1,col1-1))},
                        {bled / (led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(1, col1 - 1) - k)},
                        {(bled / (led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(1, col1 - 1) - k))*(led_img_trans1.get(2,col1-1)/led_img_trans1.get(1,col1-1))}};
                break;
            case 3:led_G_trans_est_array= new double[][]{{bled / (led_img_trans.get(1, col - 1) / led_img_trans.get(0, col - 1) - k)},
                    {(bled / (led_img_trans.get(1, col - 1) / led_img_trans.get(0, col - 1) - k))*(led_img_trans.get(1,col-1)/led_img_trans.get(0,col-1))},
                    {(bled / (led_img_trans.get(1, col - 1) / led_img_trans.get(0, col - 1) - k))*(led_img_trans.get(2,col-1)/led_img_trans.get(0,col-1))}};
                led_G_trans_est_array1= new double[][]{{bled / (led_img_trans1.get(1, col1 - 1) / led_img_trans1.get(0, col1 - 1) - k)},
                        {(bled / (led_img_trans1.get(1, col1 - 1) / led_img_trans1.get(0, col1 - 1) - k))*(led_img_trans1.get(1,col1-1)/led_img_trans1.get(0,col1-1))},
                        {(bled / (led_img_trans1.get(1, col1 - 1) / led_img_trans1.get(0, col1 - 1) - k))*(led_img_trans1.get(2,col1-1)/led_img_trans1.get(0,col1-1))}};
                break;
            case 4:led_G_trans_est_array= new double[][]{{bled / (1-k*led_img_trans.get(2, col - 1) / led_img_trans.get(0, col - 1))},
                    {(bled / (1-k*led_img_trans.get(2, col - 1) / led_img_trans.get(0, col - 1)))*(led_img_trans.get(1,col-1)/led_img_trans.get(0,col-1))},
                    {(bled / (1-k*led_img_trans.get(2, col - 1) / led_img_trans.get(0, col - 1)))*(led_img_trans.get(2,col-1)/led_img_trans.get(0,col-1))}};
                led_G_trans_est_array1= new double[][]{{bled / (1-k*led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(0, col1 - 1))},
                        {(bled / (1-k*led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(0, col1 - 1)))*(led_img_trans1.get(1,col1-1)/led_img_trans1.get(0,col1-1))},
                        {(bled / (1-k*led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(0, col1 - 1)))*(led_img_trans1.get(2,col1-1)/led_img_trans1.get(0,col1-1))}};
                break;
            case 5:led_G_trans_est_array= new double[][]{{bled / (1-k*led_img_trans.get(1, col - 1) / led_img_trans.get(0, col - 1))},
                    {(bled / (1-k*led_img_trans.get(1, col - 1) / led_img_trans.get(0, col - 1)))*(led_img_trans.get(1,col-1)/led_img_trans.get(0,col-1))},
                    {(bled / (1-k*led_img_trans.get(1, col - 1) / led_img_trans.get(0, col - 1)))*(led_img_trans.get(2,col-1)/led_img_trans.get(0,col-1))}};
                led_G_trans_est_array1= new double[][]{{bled / (1-k*led_img_trans1.get(1, col1 - 1) / led_img_trans1.get(0, col1 - 1))},
                        {(bled / (1-k*led_img_trans1.get(1, col1 - 1) / led_img_trans1.get(0, col1 - 1)))*(led_img_trans1.get(1,col1-1)/led_img_trans1.get(0,col1-1))},
                        {(bled / (1-k*led_img_trans1.get(1, col1 - 1) / led_img_trans1.get(0, col1 - 1)))*(led_img_trans1.get(2,col1-1)/led_img_trans1.get(0,col1-1))}};
                break;
            case 6:led_G_trans_est_array= new double[][]{
                    {(bled / (1-k*led_img_trans.get(2, col - 1) / led_img_trans.get(1, col - 1)))*(led_img_trans.get(0,col-1)/led_img_trans.get(1,col-1))},
                    {bled / (1-k*led_img_trans.get(2, col - 1) / led_img_trans.get(1, col - 1))},
                    {(bled / (1-k*led_img_trans.get(2, col - 1) / led_img_trans.get(1, col - 1)))*(led_img_trans.get(2,col-1)/led_img_trans.get(1,col-1))}};
                led_G_trans_est_array1= new double[][]{
                        {(bled / (1-k*led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(1, col1 - 1)))*(led_img_trans1.get(0,col1-1)/led_img_trans1.get(1,col1-1))},
                        {bled / (1-k*led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(1, col1 - 1))},
                        {(bled / (1-k*led_img_trans1.get(2, col1 - 1) / led_img_trans1.get(1, col1 - 1)))*(led_img_trans1.get(2,col1-1)/led_img_trans1.get(1,col1-1))}};
                break;
            default:
                led_G_trans_est_array= new double[3][1];
                led_G_trans_est_array1= new double[3][1];
        }
        Jama.Matrix led_G_trans_est=new Jama.Matrix(led_G_trans_est_array);
        Jama.Matrix led_G1_trans_est=new Jama.Matrix(led_G_trans_est_array1);
        Jama.Matrix led_G_rs_est=rot_mat_trans2rs_temp.get(0).times(led_G_trans_est);
        Jama.Matrix led_G1_rs_est=rot_mat_trans2rs_temp.get(0).times(led_G1_trans_est);
        Jama.Matrix G2P_nv_rs_est=led_G1_rs_est.minus(led_G_rs_est).times(1/(led_G1_rs_est.minus(led_G_rs_est).norm2()));
        theta[0]=Math.asin(led_nv_rs_est.get(0,0));
        if(theta[0]>0)
        {
            theta[1]=Math.PI-theta[0];
        }
        else
        {
            theta[1]=-Math.PI-theta[0];
        }
        double rot_theta = Double.NaN;
        double rot_fai = Double.NaN;
        double rot_psi=Double.NaN;
        for(int ithe=0;ithe<theta.length;ithe++)
        {
            fai[ithe]=Math.asin(-led_nv_rs_est.get(1,0)/Math.cos(theta[ithe]));
            double fai_sin=-led_nv_rs_est.get(1,0)/Math.cos(theta[ithe]);
            double fai_cos=-led_nv_rs_est.get(2,0)/Math.cos(theta[ithe]);
            if(fai[ithe]>0 && fai_cos<0)
            {
                fai[ithe]=Math.PI-fai[ithe];
            }
            else if(fai[ithe]<0 && fai_cos<0)
            {
                fai[ithe]=-Math.PI-fai[ithe];
            }
            psi[ithe][0]=Math.asin(G2P_nv_rs_est.get(0,0)/Math.cos(theta[ithe]));
            if(psi[ithe][0]>0)
            {
                psi[ithe][1]=Math.PI-psi[ithe][0];
            }
            else
            {
                psi[ithe][1]=-Math.PI-psi[ithe][0];
            }
            double judgefai=Math.abs(led_nv_rs_est.get(2,0)+Math.cos(fai[ithe])*Math.cos(theta[ithe]));
            judgepsi1[0]=Math.abs(Math.cos(psi[ithe][0])*Math.cos(fai[ithe])+Math.sin(psi[ithe][0])*Math.sin(theta[ithe])*Math.sin(fai[ithe])-G2P_nv_rs_est.get(1,0));
            judgepsi2[0]=Math.abs(-Math.cos(psi[ithe][0])*Math.sin(fai[ithe])+Math.sin(psi[ithe][0])*Math.sin(theta[ithe])*Math.cos(fai[ithe])-G2P_nv_rs_est.get(2,0));
            judgepsi1[1]=Math.abs(Math.cos(psi[ithe][1])*Math.cos(fai[ithe])+Math.sin(psi[ithe][1])*Math.sin(theta[ithe])*Math.sin(fai[ithe])-G2P_nv_rs_est.get(1,0));
            judgepsi2[1]=Math.abs(-Math.cos(psi[ithe][1])*Math.sin(fai[ithe])+Math.sin(psi[ithe][1])*Math.sin(theta[ithe])*Math.cos(fai[ithe])-G2P_nv_rs_est.get(2,0));

            if(judgefai < 1e-7 && judgepsi1[0] < 1e-7 && judgepsi2[0] < 1e-7)
            {
                rot_theta=theta[ithe];
                rot_fai=fai[ithe];
                rot_psi = psi[ithe][0];
                break;
            }
            else if(judgefai < 1e-7 && judgepsi1[1] < 1e-7 && judgepsi2[1] < 1e-7)
            {
                rot_theta=theta[ithe];
                rot_fai=fai[ithe];
                rot_psi = psi[ithe][1];
                break;
            }

        }
        if(rot_psi==Double.NaN)
        {
            return new double[]{-1, -1, -1};
        }
        double[][] rot_mat_x_est_array={{1,0,0},{0,Math.cos(rot_fai), -Math.sin(rot_fai)},{0, Math.sin(rot_fai), Math.cos(rot_fai)}};
        double[][] rot_mat_y_est_array={{Math.cos(rot_theta), 0, Math.sin(rot_theta)},{0,1,0},{-Math.sin(rot_theta), 0, Math.cos(rot_theta)}};
        double[][] rot_mat_z_est_array={{Math.cos(rot_psi), -Math.sin(rot_psi), 0},{Math.sin(rot_psi), Math.cos(rot_psi), 0},{0,0,1}};
        Jama.Matrix rot_mat_x_est=new Jama.Matrix(rot_mat_x_est_array);
        Jama.Matrix rot_mat_y_est=new Jama.Matrix(rot_mat_y_est_array);
        Jama.Matrix rot_mat_z_est=new Jama.Matrix(rot_mat_z_est_array);
        Jama.Matrix rot_mat_rs2gs_est=rot_mat_z_est.times(rot_mat_y_est.times(rot_mat_x_est));
        Jama.Matrix rot_mat_rs2gs_est1=rot_mat_z_final.transpose().times(rot_mat_rs2gs_est);//旋转矩阵的逆和转置是一样的，因为他是实对称矩阵
        Jama.Matrix temp=new Jama.Matrix(3,2);
        temp.setMatrix(0,2,0,0,led_G_gs);
        temp.setMatrix(0,2,1,1,led_G1_gs);
        Jama.Matrix mark_GP_temp=rot_mat_rs2gs_est1.transpose().times(temp);
        Jama.Matrix tra_mat_rs2gs_est=new Jama.Matrix(3,1);
        tra_mat_rs2gs_est.set(0,0,(mark_GP_temp.get(0,0)+mark_GP_temp.get(0,1))-(led_G_rs_est.get(0,0)+led_G1_rs_est.get(0,0)));
        tra_mat_rs2gs_est.set(1,0,(mark_GP_temp.get(1,0)+mark_GP_temp.get(1,1))-(led_G_rs_est.get(1,0)+led_G1_rs_est.get(1,0)));
        tra_mat_rs2gs_est.set(2,0,(mark_GP_temp.get(2,0)+mark_GP_temp.get(2,1))-(led_G_rs_est.get(2,0)+led_G1_rs_est.get(2,0)));
        Jama.Matrix ca_gs_est=rot_mat_rs2gs_est1.times(tra_mat_rs2gs_est.times(0.5));
//        Jama.Matrix ca_gs=rot_mat_z_final.inverse().times(ca_gs_est);//忘了乘以逆
        double[] result=new double[3];
        result[0]=ca_gs_est.get(0,0);
        result[1]=ca_gs_est.get(1,0);
        result[2]=ca_gs_est.get(2,0);
        return result;
    }
    //    public class ImageSaver implements Runnable {
//        private final Image mImage;
//        public ImageSaver(Image image) {
//            mImage = image;
//        }
//        @RequiresApi(api = Build.VERSION_CODES.N)
//        @Override
//        public void run() {
////            Bitmap bitmap = getBitmapFromImage(mImage);
//            System.out.println("这是ImageSaver的图一");
//            Mat img = new Mat();
//            Utils.bitmapToMat(bitmap,img);
//            Mat gray = new Mat();
//            Imgproc.cvtColor(img,gray,Imgproc.COLOR_BGR2GRAY);
//            List<org.opencv.core.Rect> led_boxes=ini_process(gray);
//            int width_max=0;
//            for(org.opencv.core.Rect boxx:led_boxes)
//            {
//                width_max=Math.max(width_max,boxx.width);
//            }
//            double threshold=adap_threshold(led_boxes,gray,width_max);
//            int[] err_list = new int[led_boxes.size()];
//            ArrayList<Integer> id_list=new ArrayList<>();
//            ArrayList<Coordinate> center_list=new ArrayList<>();
//            ArrayList<ArrayList<Coordinate>> con_list=new ArrayList<>();
//            try_decode(img,led_boxes,threshold,err_list,id_list,center_list,con_list,width_max);
//            if(id_list.size()<4)
//            {
//                second_decode(img,led_boxes,threshold,err_list,id_list,center_list,con_list,width_max);
//                if(id_list.size()<4)
//                {
//                    third_decode(img,led_boxes,threshold,err_list,id_list,center_list,con_list,width_max);
//                    if(id_list.size()<4)
//                    {
//                        fourth_decode(img,led_boxes,threshold,err_list,id_list,center_list,con_list,width_max);
//                    }
//                }
//            }
//            mHandler1.sendEmptyMessage(id_list.size());
//            double[] position_result={-1,-1,-1};
//            if(id_list.size()>=2)
//            {
//                position_result=ellipse_position(id_list,center_list,con_list);
//            }
//            double[] finalPosition_result = position_result;
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    pos_x.setText(String.format(getResources().getString(R.string.pos_x),finalPosition_result[0]));
//                    pos_y.setText(String.format(getResources().getString(R.string.pos_y),finalPosition_result[1]));
//                }
//            });
//            socket_out=String.format(getResources().getString(R.string.pos_x),finalPosition_result[0])+":"+String.format(getResources().getString(R.string.pos_y),finalPosition_result[1]);
//            System.out.println(socket_out);
////            new Thread(runnable1).start();// 启动线程 向服务器发送信息
//            //*************利用BitmapUtil存储***********************
////            SimpleDateFormat sFormat=new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
////            Calendar calendar=Calendar.getInstance();
////            //获取系统当前时间并将其转换为string类型
////            String fileName=sFormat.format(calendar.getTime());
////            String savepath = Environment.getExternalStorageDirectory() + "/DCIM/Camera/"+fileName+".jpg";
////            BitmapUtil.saveBitmap(savepath,bitmap);
//            //*************利用OutputStream存储***********************
////            FileOutputStream fos = null;
////            try {
////                fos = new FileOutputStream(imageFile);
////                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
//////                fos.write(data, 0, data.length);
////            } catch (IOException e) {
////                e.printStackTrace();
////            } finally {
////                if (fos != null) {
////                    try {
////                        fos.close();
////                    } catch (IOException e) {
////                        e.printStackTrace();
////                    }
////                }
////            }
//            mImage.close();//一定需要close，否则不会收到新的Image回调。
//
//        }
//    }
    public class Contour implements Runnable {
        private final Image mImage1;
        private final Image mImage2;
        public Contour(Image[] image) {
            mImage1 = image[0];
            mImage2 = image[1];
        }
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void run() {
//            long startTime0 = System.currentTimeMillis();
//            System.out.println(mImage1.getFormat());
//            System.out.println(mImage2.getFormat());
//            ByteBuffer buffer2 = mImage2.getPlanes()[0].getBuffer();
//            byte[] bytes2 = new byte[buffer2.capacity()];
//            buffer2.get(bytes2);
//            Bitmap bitmap2 = BitmapFactory.decodeByteArray(bytes2, 0, bytes2.length, null);
//            int w = mImage1.getWidth(), h = mImage1.getHeight();
//            byte[] i420 = getDataFromImage(mImage1,COLOR_FormatI420);
            Bitmap bitmap1 = getBitmapFromImage(mImage1);
//            Bitmap bitmap1 = BitmapFactory.decodeByteArray(i420, 0, i420.length);;
            Bitmap bitmap2 = getBitmapFromImage(mImage2);

//            int width = mImage1.getWidth(), height = mImage1.getHeight();
//            byte[] i420bytes = getDataFromImage(mImage1, COLOR_FormatI420);
//            byte[] i420RorateBytes = BitmapUtil.rotateYUV420Degree90(i420bytes, width, height);
//            byte[] nv21bytes = BitmapUtil.I420Tonv21(i420RorateBytes, height, width);
//            Bitmap bitmap1 = BitmapUtil.getBitmapImageFromYUV(nv21bytes, height, width);

//            System.out.println("这是图一");
//            System.out.println("这是图二");
//            long endTime0 = System.currentTimeMillis();
//            System.out.println("图像转换运行时间：" + (endTime0 - startTime0) + "ms");    //输出程序运行时间
            Mat img2 = new Mat();
            Utils.bitmapToMat(bitmap2,img2);
            Mat gray2 = new Mat();
            Imgproc.cvtColor(img2,gray2,Imgproc.COLOR_BGR2GRAY);
            contours=new ArrayList<>();
            centers=new ArrayList<>();
//            long startTime = System.currentTimeMillis();
            findContours(gray2,contours,centers);
//            long endTime = System.currentTimeMillis();    //获取结束时间
//            System.out.println("找轮廓运行时间：" + (endTime - startTime) + "ms");    //输出程序运行时间
//            long startTime1 = System.currentTimeMillis();
            Mat img1 = new Mat();
            Utils.bitmapToMat(bitmap1,img1);
            Mat gray1 = new Mat();
            Imgproc.cvtColor(img1,gray1,Imgproc.COLOR_BGR2GRAY);
            List<org.opencv.core.Rect> led_boxes=ini_process(gray1);
            int width_max=0;
            for(org.opencv.core.Rect boxx:led_boxes)
            {
                width_max=Math.max(width_max,boxx.width);
            }
            double threshold=adap_threshold(led_boxes,gray1,width_max);
            int[] err_list = new int[led_boxes.size()];
            ArrayList<Integer> id_list=new ArrayList<>();
            ArrayList<Coordinate> center_list=new ArrayList<>();
            ArrayList<ArrayList<Coordinate>> con_list=new ArrayList<>();
            try_decode(img1,led_boxes,threshold,err_list,id_list,center_list,con_list,width_max);
            if(id_list.size()<4)
            {
                second_decode(img1,led_boxes,threshold,err_list,id_list,center_list,con_list,width_max);
                if(id_list.size()<4)
                {
                    third_decode(img1,led_boxes,threshold,err_list,id_list,center_list,con_list,width_max);
                    if(id_list.size()<4)
                    {
                        fourth_decode(img1,led_boxes,threshold,err_list,id_list,center_list,con_list,width_max);
                    }
                }
            }
//            System.out.println("排除一次后轮廓的数量:"+contours.size());
//            System.out.println("符合条件轮廓的数量:"+con_list.size());
//            System.out.println("中心的数量:"+con_list.size());
            mHandler1.sendEmptyMessage(id_list.size());
//            long endTime1 = System.currentTimeMillis();    //获取结束时间
//            System.out.println("通信运行时间：" + (endTime1 - startTime1) + "ms");    //输出程序运行时间
//            long startTime2 = System.currentTimeMillis();    //获取结束时间
            double[] position_result={-1,-1,-1};
            if(id_list.size()>=2)
            {
                position_result=ellipse_position(id_list,center_list,con_list);
            }
//            long endTime2 = System.currentTimeMillis();    //获取结束时间
//            System.out.println("定位运行时间：" + (endTime2 - startTime2) + "ms");    //输出程序运行时间
            double[] finalPosition_result = position_result;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pos_x.setText(String.format(getResources().getString(R.string.pos_x),finalPosition_result[0]));
                    pos_y.setText(String.format(getResources().getString(R.string.pos_y),finalPosition_result[1]));
                }
            });
            socket_out=String.format(getResources().getString(R.string.pos_x),finalPosition_result[0])
                    +":"+String.format(getResources().getString(R.string.pos_y),finalPosition_result[1])
                    +":"+String.format(getResources().getString(R.string.pos_z),finalPosition_result[2]);
//            System.out.println(socket_out);
            new Thread(runnable1).start();// 启动线程 向服务器发送信息
            //************....*利用BitmapUtil存储***********************
//            SimpleDateFormat sFormat=new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
//            Calendar calendar= Calendar.getInstance();
//            //获取系统当前时间并将其转换为string类型
//            String fileName=sFormat.format(calendar.getTime());
//            String savepath = Environment.getExternalStorageDirectory() + "/DCIM/Camera/"+fileName+".jpg";
//            BitmapUtil.saveBitmap(savepath,bitmap1);
//            String savepath1 = Environment.getExternalStorageDirectory() + "/DCIM/Camera/"+fileName+"C"+".jpg";
//            BitmapUtil.saveBitmap(savepath1,bitmap2);
            //*************利用OutputStream存储***********************
//            FileOutputStream fos = null;
//            try {
//                fos = new FileOutputStream(imageFile);
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
////                fos.write(data, 0, data.length);
//            } catch (IOException e) {
//                e.printStackTrace();
//            } finally {
//                if (fos != null) {
//                    try {
//                        fos.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
            mImage1.close();//一定需要close，否则不会收到新的Image回调。
            mImage2.close();
        }
    }

    Runnable runnable1 = new Runnable(){
        @Override
        public void run() {
            // TODO: http request.
            pw.println(socket_out);
        }
    };
    private void setclient()
    {
        new Thread()
        {
            @Override
            public void run()
            {
                super.run();
                try{
//                    Socket socket=new Socket("192.168.31.119", 9999);//笔记本
                    Socket socket=new Socket("192.168.31.236", 9999);//台式机
                    pw=new PrintWriter(socket.getOutputStream(),true);
//                    InputStreamReader instead=new InputStreamReader(socket.getInputStream());

                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}


