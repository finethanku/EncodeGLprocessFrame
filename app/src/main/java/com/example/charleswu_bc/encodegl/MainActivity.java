


package com.example.charleswu_bc.encodegl;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity implements GLSurfaceView.Renderer{
    private MediaCodec mcencoder;
    private Surface encodeSurface;
    private EGLSurface eglencodeSurface;
    private GLSurfaceView glsurfaceView;
    private EGLSurface eglpreviewSurface;
    public FileOutputStream fos;

    private EGLContext eglContext;
    private SampleContextFactory sampleContextFactory;
    private SampleWindowSurfaceFactory sampleWindowSurfaceFactory;

    private int cnt = 0;
    public void onSurfaceCreated(GL10 unused, EGLConfig config)
    {}

    public void onDrawFrame(GL10 gl10)
    {
        EGL10 egl10 = (EGL10)EGLContext.getEGL();

        egl10.eglMakeCurrent(egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY),
                eglencodeSurface, eglencodeSurface,eglContext );


        cnt++;
        int r = 0;
        int g = 0;
        int b = cnt*10%200+50; //r
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(r / 255.0f, g / 255.0f, b / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        try {
            Thread.sleep(100);
        }catch (Exception e){}

        egl10.eglSwapBuffers(egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY), eglencodeSurface);


        egl10.eglMakeCurrent(egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY),
                eglpreviewSurface, eglpreviewSurface, eglContext);

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(r / 255.0f, g / 255.0f, b / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        try {
            Thread.sleep(100);
        }catch (Exception e){}

        run_encode();
    }

    public void onSurfaceChanged(GL10 unused, int width, int height)
    {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mcencoder = encoder_setup();
        encodeSurface = mcencoder.createInputSurface();
        mcencoder.start();

        glsurfaceView = new GLSurfaceView(this);
        glSetup(glsurfaceView);
        try {
            fos = new FileOutputStream("/mnt/sata/try.mp4", false);
        }catch (Exception e){}
        setContentView(glsurfaceView);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        mcencoder.stop();
        mcencoder.release();
        try {
            fos.close();
        }catch (Exception e){}
    }

    private void glSetup(GLSurfaceView glSurfaceView){
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setPreserveEGLContextOnPause(true);

        sampleContextFactory = new SampleContextFactory();
        glSurfaceView.setEGLContextFactory(sampleContextFactory);
        sampleWindowSurfaceFactory = new SampleWindowSurfaceFactory();
        glSurfaceView.setEGLWindowSurfaceFactory(sampleWindowSurfaceFactory);
        glSurfaceView.setRenderer(this);
    }

    private MediaCodec encoder_setup(){
        try {
            mcencoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        }catch (Exception e){e.printStackTrace();}
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 24);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);

        mcencoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return mcencoder;
    }

    private void run_encode(){
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIdx = mcencoder.dequeueOutputBuffer(bufferInfo,10000);
        if(outputBufferIdx >= 0){
            ByteBuffer outBuffer = mcencoder.getOutputBuffers()[outputBufferIdx];

            byte[] outData = new byte[bufferInfo.size];
            outBuffer.get(outData);
            try {
                fos.write(outData, bufferInfo.offset, outData.length - bufferInfo.offset);
                fos.flush();
                mcencoder.releaseOutputBuffer(outputBufferIdx,false);
            }catch (Exception e){};
        }
    }

    private class SampleContextFactory implements GLSurfaceView.EGLContextFactory {
        private int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            int[] attr_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };

            eglContext = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attr_list);
            return eglContext;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display,EGLContext context)
        {}
    }

    private class SampleWindowSurfaceFactory implements GLSurfaceView.EGLWindowSurfaceFactory {
        private final String TAG = this.getClass().getName();
        public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display,
                                              EGLConfig config, Object nativeWindow) {
            EGLSurface result = null;
            try {
                eglpreviewSurface = egl.eglCreateWindowSurface(display, config, nativeWindow, null);
                eglencodeSurface = egl.eglCreateWindowSurface(display, config, encodeSurface, null);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "eglCreateWindowSurface (native)", e);
            }
            // this return will triger Renderer
            result = eglpreviewSurface;
            return result;
        }

        public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
            egl.eglDestroySurface(display, surface);
        }
    }
}