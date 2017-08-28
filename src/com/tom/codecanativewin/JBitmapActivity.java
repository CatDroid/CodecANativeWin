package com.tom.codecanativewin;


import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.widget.Button;

import com.tom.codecanativewin.jni.JBitmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class JBitmapActivity extends Activity {


    private Handler mHandler = null;
    private Bitmap mTestBitmap = null; // 如果这里有引用Bitmap 会导致Bitmap无法释放 占用内存

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jbitmap);


        HandlerThread thread = new HandlerThread("JBitmapTask");
        thread.start();
        mHandler = new Handler(thread.getLooper());

        Button btn = (Button)findViewById(R.id.bGenBitmap);
        btn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bmp = BitmapFactory.decodeFile("/mnt/sdcard/test.jpg");
                        Bitmap newBitmap = JBitmap.rotateBitmapCcw90( bmp);
                        //mTestBitmap = newBitmap ; //  图片宽*高*4(rgba) = 1080*1440*4  = 6220800 近6M内存
                        try {
                            FileOutputStream os =  new FileOutputStream(new File("/mnt/sdcard/rotate.jpg"));
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

    @Override
    protected void onDestroy() {
        mHandler.getLooper().quitSafely();
        super.onDestroy();
    }
}
