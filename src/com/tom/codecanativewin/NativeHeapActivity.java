package com.tom.codecanativewin;


import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.tom.codecanativewin.jni.ABuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class NativeHeapActivity extends Activity {


    /**
     *  研究Native层Malloc在 Native Heap上的显示
     *
     */

    private final String TAG = "NativeHeapActivity";

    private Button mFreeBtn = null;
    private Button mMallocBtn = null;
    private ArrayList<Long> mPtrs = new ArrayList<Long>();

    private Button mDelBtn = null;
    private Button mNewBtn = null;
    private ArrayList<Long> mNewPtrs = new ArrayList<Long>();


    private Button mNewByteArrayBtn = null;
    private Button mDelByteArrayBtn = null;
    private ArrayList<Long> mByteArrayPtrs = new ArrayList<Long>();


    private Button mNewBitmapBtn = null;
    private Button mDelBitmapBtn = null;
    private ArrayList<Long> mBitmapPtrs = new ArrayList<Long>();
    private final String BITMAP_URL = "/mnt/sdcard/test.jpg" ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_native_heap);


        mMallocBtn = (Button)findViewById(R.id.bMalloc);
        mMallocBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Long ptr = ABuffer.native_malloc(1000*1000);
                mPtrs.add(ptr);
                Log.d(TAG,"malloc ptr = " + Long.toHexString( ptr ) );
            }
        });


        mFreeBtn = (Button)findViewById(R.id.bFree);
        mFreeBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if( mPtrs.size() == 0){
                    Toast.makeText(NativeHeapActivity.this,"Malloc缓冲队列已经清空", Toast.LENGTH_LONG).show();
                }else{
                    Long ptr = mPtrs.remove(0);
                    ABuffer.native_free(ptr);
                    Log.d(TAG, "free ptr = " + Long.toHexString( ptr ) );
                }
            }
        });


        mNewBtn = (Button)findViewById(R.id.bNew);
        mNewBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Long ptr = ABuffer.native_new(1024*1024);
                mNewPtrs.add(ptr);
                Log.d(TAG,"new  ptr = " + Long.toHexString( ptr ) );
            }
        });


        mDelBtn = (Button)findViewById(R.id.bDel);
        mDelBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if( mNewPtrs.size() == 0){
                    Toast.makeText(NativeHeapActivity.this,"New缓冲队列已经清空", Toast.LENGTH_LONG).show();
                }else{
                    Long ptr = mNewPtrs.remove(0);
                    ABuffer.native_del(ptr);
                    Log.d(TAG, "del ptr = " + Long.toHexString( ptr ) );
                }
            }
        });


        mNewByteArrayBtn = (Button)findViewById(R.id.bNewByteArray);
        mNewByteArrayBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                byte[] temp = new byte[1024*1024];
                Long ptr = ABuffer.native_new_byteArray(temp);
                mByteArrayPtrs.add(ptr);
                Log.d(TAG,"new  ptr = " + Long.toHexString( ptr ) + " byte[5] = "+ Integer.toHexString( 0x000000FF & temp[5] ) );
            }
        });


        mDelByteArrayBtn = (Button)findViewById(R.id.bDelByteArray);
        mDelByteArrayBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if( mByteArrayPtrs.size() == 0){
                    Toast.makeText(NativeHeapActivity.this,"New缓冲队列已经清空", Toast.LENGTH_LONG).show();
                }else{
                    final Long ptr = mByteArrayPtrs.remove(0);
//                    ABuffer.native_del_byteArray(ptr);
//                    Log.d(TAG, "del ptr = " + Long.toHexString( ptr ) );
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            ABuffer.native_del_byteArray(ptr);
                            Log.d(TAG, "del ptr = " + Long.toHexString( ptr ) );
                        }
                    }).start();


                }
            }
        });



        mNewBitmapBtn = (Button)findViewById(R.id.bNewBitmap);
        mNewBitmapBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
            File file = new File(BITMAP_URL);
            if( ! file.exists() ) {
                Toast.makeText(getApplicationContext(),BITMAP_URL+" 不存在", Toast.LENGTH_LONG).show();
                return ;
            }
            try{
                InputStream inputStream = new FileInputStream(BITMAP_URL);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bmp = BitmapFactory.decodeStream(inputStream, null, options);
                Long ptr = ABuffer.native_new_Bitmap( bmp);
                mBitmapPtrs.add(ptr);
                bmp.recycle();
                bmp = null;
            }catch (Exception ex ){
                Log.e(TAG, "Exception ex = " + ex.getMessage() );
                ex.printStackTrace();
            }
            }
        });


        mDelBitmapBtn = (Button)findViewById(R.id.bDelBitmap);
        mDelBitmapBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if( mBitmapPtrs.size() == 0){
                    Toast.makeText(NativeHeapActivity.this,"New缓冲队列已经清空", Toast.LENGTH_LONG).show();
                }else{
                    final Long ptr = mBitmapPtrs.remove(0);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            ABuffer.native_del_Bitmap(ptr);
                            Log.d(TAG, "del ptr = " + Long.toHexString( ptr ) );
                        }
                    }).start();


                }
            }
        });





    }
}
