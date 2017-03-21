package com.tom.codecanativewin;


import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.tom.codecanativewin.jni.ABuffer;

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

    }
}
