package com.tom.opengl.one;

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
public class GLProgram420P {

    // program id
    private int _program;
    // window position
    public final int mWinPosition;
    // texture id
    private int _textureI;
    // texture index in gles
    private int _tIindex;
    // vertices on screen
    private float[] _vertices;
    // handles
    private int _positionHandle = -1;
    private int _coordHandle = -1;
    private int _yuvhandle = -1 ;
    // gen texture id 
    private int _yuvtid = -1;
 
    // vertices buffer
    private ByteBuffer _vertice_buffer;
    private ByteBuffer _coord_buffer;
    // video width and height
    private int _video_width = -1;
    private int _video_height = -1;
 
    // fullscreen
    static float[] squareVertices = { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, };
    // left-top
    static float[] squareVertices1 = { -1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 1.0f, }; 
    // right-bottom
    static float[] squareVertices2 = { 0.0f, -1.0f, 1.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f, };
    // left-bottom
    static float[] squareVertices3 = { -1.0f, -1.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, }; 
    // right-top
    static float[] squareVertices4 = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, }; 
    // whole-texture
    private static float[] coordVertices = { 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, };

    private static final String VERTEX_SHADER = 
    		"attribute vec4 vPosition;\n" + 
    		"attribute vec2 a_texCoord;\n" + 
    		"varying vec2 tc;\n" + 
    		"void main() {\n" + 
    		"gl_Position = vPosition;\n" + 	 
    		"tc = a_texCoord;\n" + 		
    		"}\n";

    private static final String FRAGMENT_SHADER_420P = 
    		"precision mediump float;\n" + 
    		"uniform sampler2D tex_yuv;\n" + 
    		"varying vec2 tc;\n" + 
    		"vec4 planarYUV(sampler2D plane0,vec2 coordinate){\n" +
    		" float Y = texture2D(plane0, coordinate * vec2(1.0, 2./3.)                     		).r;\n" +
    		" float U = texture2D(plane0, coordinate * vec2(0.5, 1./3.) + vec2(0.0, 2./3.0)  ).r;\n" +
    		" float V = texture2D(plane0, coordinate * vec2(0.5, 1./3.) + vec2(0.0, 5./6.)  ).r;\n" +
    		" return vec4(Y, U, V, 1.0);\n" +
    		"}\n" +
    		"vec4 planarYUValign(sampler2D plane0,vec2 coordinate){\n" +
    		" float Y = texture2D(plane0, coordinate * vec2(1.0, 1./2.)                     		).r;\n" +
    		" float U = texture2D(plane0, coordinate * vec2(0.5, 1./8.) + vec2(0.0, 1./2.)  ).r;\n" +
    		" float V = texture2D(plane0, coordinate * vec2(0.5, 1./8.) + vec2(0.0, 5./8.)  ).r;\n" +
			" return vec4(Y, U, V, 1.0);\n" +
    		"}\n" +
//    		"vec4 planarYUValign2(sampler2D plane0,vec2 coordinate){\n" +
//    		" float Y = texture2D(plane0, coordinate * vec2(1.0, 1./2.)                     		).r;\n" +
//    		" float U = texture2D(plane0, coordinate * vec2(0.5, 1./8.) + vec2(0.0, 1./2.)  ).r;\n" +
//    		" float V = texture2D(plane0, coordinate * vec2(0.5, 1./8.) + vec2(0.0, 5./8.)  ).r;\n" +
//			" return vec4(Y, U, V, 1.0);\n" +
//    		"}\n" +
    		"void main() {\n" + 
    		"vec4 yuv = planarYUValign(tex_yuv , tc);\n" + 
    		"float R = (1.1643835616 * (yuv.x - 0.0625) + 1.5958 * (yuv.z - 0.5));\n" +
    		"float G = (1.1643835616 * (yuv.x - 0.0625) - 0.8129 * (yuv.z - 0.5) - 0.39173 * (yuv.y - 0.5));\n" +
    		"float B = (1.1643835616 * (yuv.x - 0.0625) + 2.017 * (yuv.y - 0.5));\n" +
            "gl_FragColor = vec4(R, G, B, 1.0); \n"  +   
            "}\n";

    /**
     * position can only be 0~4:
     * fullscreen => 0				全屏显示  整个SurfaceView显示
     * left-top => 1				左上角显示 
     * right-top => 2				右上角
     * left-bottom => 3
     * right-bottom => 4
     */
    public GLProgram420P(int position) {
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
            _tIindex = 0;
            break;
        case 2:
            _vertices = squareVertices2;
            _textureI = GLES20.GL_TEXTURE3;
            _tIindex = 3;
            break;
        case 3:
            _vertices = squareVertices3;
            _textureI = GLES20.GL_TEXTURE6;
            _tIindex = 6;
            break;
        case 4:
            _vertices = squareVertices4;
            _textureI = GLES20.GL_TEXTURE9;
            _tIindex = 9;  
            break;
        case 0:
        default:
            _vertices = squareVertices;
            _textureI = GLES20.GL_TEXTURE0;
            _tIindex = 0;
            break;
        }
    }

    public void setHW(int w , int h)
    {
    	_video_width = w;
    	_video_height = h;
    }

    public void buildProgram() {
 
        _vertice_buffer = ByteBuffer.allocateDirect(_vertices.length * 4);
        _vertice_buffer.order(ByteOrder.nativeOrder());
        _vertice_buffer.asFloatBuffer().put(_vertices);
        _vertice_buffer.position(0);

        _coord_buffer = ByteBuffer.allocateDirect(coordVertices.length * 4);
        _coord_buffer.order(ByteOrder.nativeOrder());
        _coord_buffer.asFloatBuffer().put(coordVertices);
        _coord_buffer.position(0);
        
        _program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_420P);
     
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

  
        _yuvhandle = GLES20.glGetUniformLocation(_program, "tex_yuv");
        checkGlError("glGetUniformLocation tex_y");
        if (_yuvhandle == -1) {
            throw new RuntimeException("Could not get uniform location for tex_yuv");
        }
 

        {
	        int[] textures = new int[1];
	        GLES20.glGenTextures(1, textures, 0);
	        checkGlError("glGenTextures");
	        _yuvtid = textures[0];
	 
	        GLES20.glUseProgram(_program);
	        checkGlError("glUseProgram"); // glUniform1i 要在UseProgram情况下使用 否则 glError 1282 
	        
	        GLES20.glActiveTexture(_textureI);	
	        checkGlError("glActiveTexture");
	        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _yuvtid); 	
	        checkGlError("glBindTexture");
	        GLES20.glUniform1i(_yuvhandle, _tIindex);
	        checkGlError("glUniform1i");
	        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST); // GL_NEAREST
	        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR); // GL_LINEAR
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); // GL_CLAMP_TO_EDGE 
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE); // GL_MIRRORED_REPEAT
 		 
        }        
    }

 
 
     
    public void drawFrame(Buffer yuv ) {
    
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _yuvtid); 
        checkGlError("glBindTexture YUV");
   
        // (_video_width)  *  ( _video_height * 3/2 ) 个纹理
        // 每个纹理对应 单独一个 y or u or v 
//        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, _video_width  , _video_height * 3 / 2 , 0,
//                GLES20.GL_LUMINANCE , GLES20.GL_UNSIGNED_BYTE, yuv);
//        checkGlError("glBindTexture YUV");
     
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, _video_width  , _video_height * 2  , 0,
                GLES20.GL_LUMINANCE , GLES20.GL_UNSIGNED_BYTE, yuv);
        checkGlError("glBindTexture YUV");
        
        GLES20.glVertexAttribPointer(_positionHandle, 2, GLES20.GL_FLOAT, false, 2*4, _vertice_buffer);
        checkGlError("glVertexAttribPointer mPositionHandle");
        GLES20.glEnableVertexAttribArray(_positionHandle);

        GLES20.glVertexAttribPointer(_coordHandle, 2, GLES20.GL_FLOAT, false, 2*4, _coord_buffer);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(_coordHandle);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFinish();
        
        GLES20.glDisableVertexAttribArray(_positionHandle);
        GLES20.glDisableVertexAttribArray(_coordHandle);
    }

    /**
     * create program and load shaders, fragment shader is very important.
     */
    public int createProgram(String vertexSource, String fragmentSource) {
      
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
    
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

 

    private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    
}