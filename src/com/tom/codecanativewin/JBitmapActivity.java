package com.tom.codecanativewin;


import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.tom.codecanativewin.jni.JBitmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class JBitmapActivity extends Activity {

    private final static String TAG = "JBitmapActivity";
    private final static String CONFIG_TEST_JPEG_FILE = "/mnt/sdcard/test.jpg";

    private Handler mHandler = null;
    private Handler mUIHandler = new Handler();
    private Bitmap mTestBitmap = null; // 如果这里有引用Bitmap 会导致Bitmap无法释放 占用内存

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jbitmap);


        HandlerThread thread = new HandlerThread("JBitmapTask");
        thread.start();
        mHandler = new Handler(thread.getLooper());

        // java.lang.Enum.valueOf()
        Bitmap.Config cfg = Bitmap.Config.valueOf( "ARGB_8888" );
        Log.d(TAG, "cfg is " + cfg ); // cfg is ARGB_8888

        Matrix mtx = new Matrix(); // 这个是 android.graphic.Matrix 不是 android.openGL.Matricx
                                    // 默认是3x3的单位矩阵  二维图片的仿射变换
        Log.d(TAG, "Matrix before Rotate is " + mtx + " isAffine " + mtx.isAffine() );

        mtx.postRotate(90);
        Log.d(TAG, "Matrix postRotate(90) is " + mtx );

        mtx.setRotate(45);          // Matrix方法中的setRotate()方法会先清除该矩阵，即设为单位矩阵
        Log.d(TAG, "Matrix setRotate(45) is " + mtx );
        /*
            Matrix before Rotate is
            {   [1.0, 0.0, 0.0]
                [0.0, 1.0, 0.0]
                [0.0, 0.0, 1.0] }       isAffine true

            Matrix postRotate(90) is
            {   [0.0, -1.0, 0.0]
                [1.0, 0.0, 0.0 ]
                [0.0, 0.0, 1.0 ] }

            Matrix setRotate(45) is
            {   [0.707 , -0.707 , 0.0]
                [0.707 , 0.707 ,  0.0]
                [0.0,    0.0,     1.0]}

         */


        Button btn = (Button)findViewById(R.id.bGenBitmap);
        btn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {

                        final int rotate = readPictureDegree(CONFIG_TEST_JPEG_FILE);
                        Log.d(TAG,CONFIG_TEST_JPEG_FILE +  " Exif Tag Orientation " + rotate );
                        mUIHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(JBitmapActivity.this, CONFIG_TEST_JPEG_FILE + " Exif Orient. = " + rotate , Toast.LENGTH_SHORT).show();
                            }
                        });

                        Bitmap bmp = BitmapFactory.decodeFile( CONFIG_TEST_JPEG_FILE );
                        Bitmap newBitmap = JBitmap.rotateBitmapCcw90( bmp);
                        //mTestBitmap = newBitmap ; //  图片宽*高*4(rgba) = 1080*1440*4  = 6220800 近6M内存
                        try {
                            Date date = new Date(System.currentTimeMillis());
                            SimpleDateFormat f = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
                            String fileName = "/mnt/sdcard/rotate_"  + f.format(date) +  ".jpg" ;
                            FileOutputStream os =  new FileOutputStream(new File(fileName));
                            newBitmap.compress(Bitmap.CompressFormat.JPEG,90,os);
                            os.close(); // Bitmap不需要手动调用recycle,除非在一个线程Loop中有很多Bitmap,这时候才需要主动recycle
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                });
            }
        });
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int rotate){
        if(bitmap == null)
            return null ;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        // Setting post rotate to 90
        Matrix mtx = new Matrix(); // 这个是 android.graphic.Matrix 不是 android.openGL.Matricx
        mtx.postRotate(rotate);
        // 基于原来的Bitmap产生新的Bitmap并且根据转换矩阵来转换
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }


    // ExifInterface 读取jpg文件中 EXIF信息
    private static int readPictureDegree(String path) {
        int degree  = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL/*默认值*/);
            String time = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
            Log.d(TAG,"Exif Tag DateTime " + time ); //  Exif Tag DateTime 2017:03:20 16:42:09
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    @Override
    protected void onDestroy() {
        mHandler.getLooper().quitSafely();
        super.onDestroy();
    }
}
