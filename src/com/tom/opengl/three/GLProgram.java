package com.tom.opengl.three;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.opengl.GLES20;

/**
 * step to use:
 * 1. new GLProgram()
 * 2. buildProgram()
 * 3. buildTextures()
 * 4. drawFrame()
 */
public class GLProgram {

    // program id
    private int _program;
    // window position
    public final int mWinPosition;
    // texture id
    private int _textureI;
    private int _textureII;
    private int _textureIII;
    // texture index in gles
    private int _tIindex;
    private int _tIIindex;
    private int _tIIIindex;
    // vertices on screen
    private float[] _vertices;
    // handles
    private int _positionHandle = -1, _coordHandle = -1;
    private int _yhandle = -1, _uhandle = -1, _vhandle = -1;
    private int _ytid = -1, _utid = -1, _vtid = -1;
    // vertices buffer
    private ByteBuffer _vertice_buffer;
    private ByteBuffer _coord_buffer;
    // video width and height
    private int _video_width = -1;
    private int _video_height = -1;
    // flow control
    private boolean isProgBuilt = false;

    /**
     * position can only be 0~4:
     * fullscreen => 0				全屏显示  整个SurfaceView显示
     * left-top => 1				左上角显示 
     * right-top => 2				右上角
     * left-bottom => 3
     * right-bottom => 4
     */
    public GLProgram(int position) {
        if (position < 0 || position > 4) {
            throw new RuntimeException("Index can only be 0 to 4");
        }
        mWinPosition = position;
        setup(mWinPosition);
    }

    /**
     * prepared for later use
     */
    public void setup(int position) {
        switch (mWinPosition) {
        case 1:
            _vertices = squareVertices1;
            _textureI = GLES20.GL_TEXTURE0;
            _textureII = GLES20.GL_TEXTURE1;
            _textureIII = GLES20.GL_TEXTURE2;
            _tIindex = 0;
            _tIIindex = 1;
            _tIIIindex = 2;
            break;
        case 2:
            _vertices = squareVertices2;
            _textureI = GLES20.GL_TEXTURE3;
            _textureII = GLES20.GL_TEXTURE4;
            _textureIII = GLES20.GL_TEXTURE5;
            _tIindex = 3;
            _tIIindex = 4;
            _tIIIindex = 5;
            break;
        case 3:
            _vertices = squareVertices3;
            _textureI = GLES20.GL_TEXTURE6;
            _textureII = GLES20.GL_TEXTURE7;
            _textureIII = GLES20.GL_TEXTURE8;
            _tIindex = 6;
            _tIIindex = 7;
            _tIIIindex = 8;
            break;
        case 4:
            _vertices = squareVertices4;
            _textureI = GLES20.GL_TEXTURE9;
            _textureII = GLES20.GL_TEXTURE10;
            _textureIII = GLES20.GL_TEXTURE11;
            _tIindex = 9;
            _tIIindex = 10;
            _tIIIindex = 11;
            break;
        case 0:
        default:
            _vertices = squareVertices;
            _textureI = GLES20.GL_TEXTURE0;
            _textureII = GLES20.GL_TEXTURE1;
            _textureIII = GLES20.GL_TEXTURE2;
            _tIindex = 0;
            _tIIindex = 1;
            _tIIIindex = 2;
            break;
        }
    }

    public boolean isProgramBuilt() {
        return isProgBuilt;
    }

    public void buildProgram() {
        // TODO 
    	// createBuffers(_vertices, coordVertices);
    	 createBuffers(_vertices) ;
    	// 放在 void update(int w, int h) @ GLFrameRender.java 调用   所以不会用 setup的 postion
        if (_program <= 0) {
            _program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        }

        /*
         * get handle for "vPosition" and "a_texCoord"
         */
        _positionHandle = GLES20.glGetAttribLocation(_program, "vPosition");
        checkGlError("glGetAttribLocation vPosition");
        if (_positionHandle == -1) {
            throw new RuntimeException("Could not get attribute location for vPosition");
        }
        _coordHandle = GLES20.glGetAttribLocation(_program, "a_texCoord");
        checkGlError("glGetAttribLocation a_texCoord");
        if (_coordHandle == -1) {
            throw new RuntimeException("Could not get attribute location for a_texCoord");
        }

        /*
         * get uniform location for y/u/v, we pass data through these uniforms
         * 
         * Sample2D
         */
        _yhandle = GLES20.glGetUniformLocation(_program, "tex_y");
        checkGlError("glGetUniformLocation tex_y");
        if (_yhandle == -1) {
            throw new RuntimeException("Could not get uniform location for tex_y");
        }
        _uhandle = GLES20.glGetUniformLocation(_program, "tex_u");
        checkGlError("glGetUniformLocation tex_u");
        if (_uhandle == -1) {
            throw new RuntimeException("Could not get uniform location for tex_u");
        }
        _vhandle = GLES20.glGetUniformLocation(_program, "tex_v");
        checkGlError("glGetUniformLocation tex_v");
        if (_vhandle == -1) {
            throw new RuntimeException("Could not get uniform location for tex_v");
        }

        isProgBuilt = true;
        
        {
	        int[] textures = new int[3];
	        GLES20.glGenTextures(3, textures, 0);
	        checkGlError("glGenTextures");
	        _ytid = textures[0];
	        _utid = textures[1];
	        _vtid = textures[2];
	        
	        
	        GLES20.glUseProgram(_program);
	        checkGlError("glUseProgram"); // glUniform1i 要在UseProgram情况下使用 否则 glError 1282 
	        
	        GLES20.glActiveTexture(_textureI);	
	        checkGlError("glActiveTexture");
	        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _ytid); 	
	        checkGlError("glBindTexture");
	        GLES20.glUniform1i(_yhandle, _tIindex);
	        checkGlError("glUniform1i");
	        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
	        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
 		 
	        GLES20.glActiveTexture(_textureII);					 
	        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _utid);	 
	        GLES20.glUniform1i(_uhandle, _tIIindex);	
	        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
	        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

	        GLES20.glActiveTexture(_textureIII);
	        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _vtid);
	        GLES20.glUniform1i(_vhandle, _tIIIindex);
	        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
	        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
	         
        }
        
    }

    /**
     * build a set of textures, one for R, one for G, and one for B.
     */
    public void buildTextures(Buffer y, Buffer u, Buffer v, int width, int height) {
        boolean videoSizeChanged = (width != _video_width || height != _video_height);
        if (videoSizeChanged) {
            _video_width = width;
            _video_height = height;
        }


        // building texture for Y data
//        if ( _ytid < 0 ||  videoSizeChanged) {
//            if (_ytid >= 0) {
//                GLES20.glDeleteTextures(1, new int[] { _ytid }, 0);
//                checkGlError("glDeleteTextures");
//            }
//            // GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
//            int[] textures = new int[1];
//            GLES20.glGenTextures(1, textures, 0);
//            checkGlError("glGenTextures");
//            _ytid = textures[0];
//        }
        /*
         * 	每个纹理单元都有 GL_TEXTURE_1D  GL_TEXTURE_2D GL_TEXTURE_3D (纹理目标 '纹理目标'的'默认纹理'都是0 通过glBindTexture把'纹理目标'和新的'纹理 '进行绑定 )
         *  GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);
         *  绑定一个已命名的纹理(纹理名字) 到一个 纹理目标 (texturing target)
         *  实际上 这时候 纹理 成为了 纹理目标的别名 
         *  (In effect, the texture targets become aliases for the textures currently bound to them)
         *  
         *  SurfaceTexture:
         *  纹理对象使用GL_TEXTURE_EXTERNAL_OES作为纹理目标
         *  每次纹理绑定的时候，都要绑定到GL_TEXTURE_EXTERNAL_OES，而不是GL_TEXTURE_2D
         *  
         *  任何需要从纹理中采样的OpenGL ES 2.0 shader都需要声明其对此扩展的使用，例如，使用指令”#extension GL_OES_EGL_image_external:require”
         *  这些shader也必须使用samplerExternalOES采样方式来访问纹理
         *  
         *  
         *   默认情况下当前活跃的纹理单元为0 				所以一般首先 设置纹理单元  GLES20.glActiveTexture(  GLES20.GL_TEXTURE0 );
         *   当绑定纹理目标时，所作用的是当前活跃的纹理单元			然后  GLES20.glBindTexture 绑定纹理对象到 当前纹理单元的 纹理目标
         */
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _ytid);
        checkGlError("glBindTexture Y");
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, _video_width, _video_height, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, y);
        checkGlError("glTexImage2D Y");
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // building texture for U data
//        if ( /*_utid < 0 || */videoSizeChanged) {
//            if (_utid >= 0) {
//                GLES20.glDeleteTextures(1, new int[] { _utid }, 0);
//                checkGlError("glDeleteTextures");
//            }
//            int[] textures = new int[1];
//            GLES20.glGenTextures(1, textures, 0);
//            checkGlError("glGenTextures");
//            _utid = textures[0];
//        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _utid); 
        checkGlError("glBindTexture U");
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, _video_width / 2, _video_height / 2, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, u);
        checkGlError("glBindTexture U");
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // building texture for V data
//        if (_vtid < 0 || videoSizeChanged) {
//            if (_vtid >= 0) {
//                GLES20.glDeleteTextures(1, new int[] { _vtid }, 0);
//                checkGlError("glDeleteTextures");
//            }
//            int[] textures = new int[1];
//            GLES20.glGenTextures(1, textures, 0);
//            checkGlError("glGenTextures");
//            _vtid = textures[0];
//        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _vtid);
        checkGlError("glBindTexture V");
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, _video_width / 2, _video_height / 2, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, v);
        checkGlError("glBindTexture V");
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }
 
    /*
     * 
     * 纹理的类型  纹理的等级  像素数据的格式   纹理图像的宽度和高度  边框大小  像素数据的格式  像素值的数据类型  像素数据
     * glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, Bit.Width, Bit.Height, 0, GL_RGB, GL_UNSIGNED_BYTE, Pixels);
     * 由于format是GL_RGB type是GL_UNSIGNED_BYTE 所以 会从data读取三个byte数据 作为 一个纹素的r g b 
     * 
     * 
     * 把图片提交到 指定纹理 的 指定纹理目标 
     * void android.opengl.GLES20.glTexImage2D(
     * 		int target, int level, int internalformat,  // << internalformat 
     * 		int width, int height,  // support  2D texture images that are at least 64 texels wide/high 
     * 		int border, 
     * 		
     * 		// 下面三个参数 代表 数据在内存中的呈现方式 
     * 
     * 		int format, // << format	GL_ALPHA, GL_RGB, GL_RGBA, GL_LUMINANCE, and GL_LUMINANCE_ALPHA
     * 		int type, 	//				GL_UNSIGNED_BYTE, GL_UNSIGNED_SHORT_5_6_5, GL_UNSIGNED_SHORT_4_4_4_4, and GL_UNSIGNED_SHORT_5_5_5_1.
     * 		Buffer pixels)
     * 
     * 		数据以byte或者short(根据type)来读取
     * 		如果type是GL_UNSIGNED_BYTE 每个byte作为一个颜色分量
     * 		如果type是GL_UNSIGNED_SHORT_** 每个short作为一个单独纹理元素的所有分量(根据format来分配颜色分量)
     * 		
     * 		根据format,颜色分量 有 1/2/3/4个值作为一组  
     * 
     * 		width × height 纹素   这些纹理是从相邻的存储单元  除非当所有的width texels读取完毕  那么读指针会跳到  4字节边界 对齐 可以通过 glPixelStorei 修改成1/2/4/8对齐
     * 	
     * 		读取第一个元素 对应 左下角 的 texture image		
     * 		读取后面的元素  从 左 到 右 处理  填入 texture image
     * 		最后到达  texture image	的  右上角
     * 
     * 		所有颜色分量 会转成浮点数
     * 		如果类型type是 GL_UNSIGNED_BYTE 每个颜色分量 除以2^8-1
     * 		如果类型type是GL_UNSIGNED_SHORT_* 每个颜色分量 除以  2^N - 1  N是每颜色分量占的位数
     * 
     * 		(_video_width/2,_video_height/2) --> (1,1) --> 插值???
     * 
     *      GL_APHPA			按照ALPHA值存储纹理单元			RGBA = (0, 0, 0, X)
	 *		GL_LUMINANCE		按照亮度值存储纹理单元			RGBA = (X, X, X, 1)	data的每个单元(GL_UNSIGNED_BYTE) 作为一个单独的值 luminance, r g b都是同样的luminance值  sharder中可以用r/g/b a是1  
	 *		GL_LUMINANCE_ALPHA	按照亮度和alpha值存储纹理单元	RGBA = (X, X, X, Y) data的每两个单元(GL_UNSIGNED_BYTE 相邻两个字节 作为一个luminance/alpha对 )
	 *		GL_RGB				按照RGB成分存储纹理单元
	 *		GL_RGBA				按照RGBA成分存储纹理单元
     */

    /**
     * render the frame
     * the YUV data will be converted to RGB by shader.
     */
    public void drawFrame() {
        //GLES20.glUseProgram(_program);
        //checkGlError("glUseProgram");

        // 与纹理无关的  设置  顶点和纹理坐标 
        GLES20.glVertexAttribPointer(_positionHandle, 2, GLES20.GL_FLOAT, false, 2*4, _vertice_buffer);
        checkGlError("glVertexAttribPointer mPositionHandle");
        GLES20.glEnableVertexAttribArray(_positionHandle);

        // 纹理坐标数组
        // android图像坐标系统与Opengl es 坐标系统不一致 
        //
        GLES20.glVertexAttribPointer(_coordHandle, 2, GLES20.GL_FLOAT, false, 2*4, _coord_buffer);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(_coordHandle);

        // 把 纹理对象 分派到 不同的纹理单元
        										// make it a habit to always call glActiveTexture. 
        										// That way, when you suddenly need to use more than one texture, 
        										// you won't break the world
        										// only needed if you are going to use multiple texture units 
//        GLES20.glActiveTexture(_textureI);					//	把纹理单元GLES20.GL_TEXTURE0  设置 当前纹理单元 
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _ytid); 	//	把 纹理ID _ytid 绑定到 当前纹理单元 GL_TEXTURE0 的 纹理目标 GL_TEXTURE_2D 
//        GLES20.glUniform1i(_yhandle, _tIindex); //	纹理采样器 Sample2D 分配一个位置值 一个纹理位置值通常称为一个纹理单元(Texture Unit) 
//        			//	_ytid 是 纹理ID
//        			//  _textureI 是  纹理单元  GLES20.GL_TEXTURE0 GLES20.GL_TEXTURE1
//        			//	_tIindex 是  纹理单元 0 1 2 ... 
//        			// 	_yhandle  is 'fragment sharder's Sample2D'
//        			//  把_yhandle设置成 纹理单元 _tIindex
//        GLES20.glActiveTexture(_textureII);					 
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _utid);	 
//        GLES20.glUniform1i(_uhandle, _tIIindex);			 
//
//        GLES20.glActiveTexture(_textureIII);
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _vtid);
//        GLES20.glUniform1i(_vhandle, _tIIIindex);

        /*
         *    	 
         *  glDrawArrays(int mode, int first,int count)
			参数1：有三种取值 (三种绘制一系列三角形的方式)
			1.GL_TRIANGLES：每三个顶点之间绘制    三角形之间不连接
			2.GL_TRIANGLE_FAN：以V0V1V2,V0V2V3,V0V3V4，……的形式绘制三角形
			3.GL_TRIANGLE_STRIP：顺序在每三个顶点之间均绘制三角形。这个方法可以保证从相同的方向上所有三角形均被绘制。以V0V1V2,V1V2V3,V2V3V4……的形式绘制三角形
					三角形的方向 在 剔除的时候 是有作用的 
			参数2：从数组缓存中的哪一位开始绘制，一般都定义为0
			参数3：顶点的数量
         *  
         */
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFinish();

        GLES20.glDisableVertexAttribArray(_positionHandle);
        GLES20.glDisableVertexAttribArray(_coordHandle);
    }

    /**
     * create program and load shaders, fragment shader is very important.
     */
    public int createProgram(String vertexSource, String fragmentSource) {
        // create shaders
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        // just check

        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    /**
     * create shader with given source.
     */
    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    /**
     * these two buffers are used for holding vertices, screen vertices and texture vertices.
     */
    void createBuffers(float[] vert) {
        _vertice_buffer = ByteBuffer.allocateDirect(vert.length * 4);
        _vertice_buffer.order(ByteOrder.nativeOrder());
        _vertice_buffer.asFloatBuffer().put(vert);
        _vertice_buffer.position(0);

        if (_coord_buffer == null) {
            _coord_buffer = ByteBuffer.allocateDirect(coordVertices.length * 4);
            _coord_buffer.order(ByteOrder.nativeOrder());
            _coord_buffer.asFloatBuffer().put(coordVertices);
            _coord_buffer.position(0);
        }
    }

    private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    static float[] squareVertices = { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, }; // fullscreen

    // surfaceView只有 左上角有显示
    static float[] squareVertices1 = { -1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 1.0f, }; // left-top

    static float[] squareVertices2 = { 0.0f, -1.0f, 1.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f, }; // right-bottom

    static float[] squareVertices3 = { -1.0f, -1.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, }; // left-bottom

    static float[] squareVertices4 = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, }; // right-top

    private static float[] coordVertices = { 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, };// whole-texture

    private static final String VERTEX_SHADER = 
    		"attribute vec4 vPosition;\n" + 
    		"attribute vec2 a_texCoord;\n" + 
    		"varying vec2 tc;\n" + 
    		"void main() {\n" + 
    		"gl_Position = vPosition;\n" + 	//	使用模型视图矩阵以及投影矩阵进行顶点变换 顶点shader至少需要一个变量 gl_Position 通常要用模型视图矩阵以及投影矩阵进行变换
    		"tc = a_texCoord;\n" + 			//	纹理坐标生成和变换
    		"}\n";
    // 一旦你使用了顶点shader 顶点处理器的所有固定功能都将被替换
    // 所以你不能只编写法线变换的shader 而指望固定功能帮你完成纹理坐标生成
    // 顶点处理器并不知道连接信息   它只是操作顶点而不是面  比如顶点处理器不能进行背面剔除

    private static final String FRAGMENT_SHADER = 
    		"precision mediump float;\n" + 
    		// 2D纹理 
    		"uniform sampler2D tex_y;\n" + 
    		"uniform sampler2D tex_u;\n" + 
    		"uniform sampler2D tex_v;\n" + 
    		"varying vec2 tc;\n" + 
    		"void main() {\n" + 
    		// texture2D 得到一个纹素 texel 这是一个纹理图片中的像素  参数是simpler2D以及纹理坐标
            "vec4 c = vec4((texture2D(tex_y, tc).b - 16./255.) * 1.164);\n" + // 内置的纹理查找函数/built-in texture lookup functions
            "vec4 U = vec4(texture2D(tex_u, tc).b - 128./255.);\n" + 
            "vec4 V = vec4(texture2D(tex_v, tc).b - 128./255.);\n" + 
            "c += V * vec4(1.596, -0.813, 0, 0);\n" +  // .g
            "c += U * vec4(0, -0.392, 2.017, 0);\n" +  // .b
            "c.a = 1.0;\n" + 
            "gl_FragColor = c;\n" +  // 计算片断的最终颜色gl_FragColor 当要渲染到多个目标时计算gl_FragData
            "}\n";
    // 固定功能将被取代 所以不能使用片断shader对片断材质化 同时用固定功能进行雾化
    // 片断处理器只对每个片断独立进行操作，并不知道相邻片断的内容
    // 片断shader不能访问帧缓存 所以混合（blend）这样的操作只能发生在这之后
    
    /*
     *  R = 1.164(Y - 16) + 1.596(V - 128)
     *  G = 1.164(Y - 16) - 0.813(V - 128) - 0.391(U - 128)
     * 	B = 1.164(Y - 16)                  + 2.018(U - 128)
		
		    1.164	+1.596		0		(U - 128)
		    1.164	-0.813	-0.391		(V - 128)
			1.164   0   	 2.018		(Y - 16)
				
     * 
     * RGB Full and RGB Limited exist because of this difference. 
     * TV programs and movies use the 16-235 range of values
     * 
     * RGB称作全范围彩色Full Range，0~255，PC Level
     * 
     * RGB和YUV(YCrCb）都有Full Range和Limited Range模式
     * 但是YUV不使用Full Range YUV属于Studio Level
     * 
     * */
       
}