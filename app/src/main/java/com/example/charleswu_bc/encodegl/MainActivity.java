


package com.example.charleswu_bc.encodegl;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class MainActivity extends Activity implements SurfaceHolder.Callback{
    Codec decoder;
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        decoder = new Codec(holder.getSurface() , "/mnt/usbdisk/usb-disk1/15_720p.mp4");
        decoder.start();

    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        setContentView(surfaceView);

    }

    public class Codec extends Thread{
        private MediaCodec mcdecoder;
        private MediaExtractor extractor;
        private Surface surface;
        private String url;
        private Encode encoder;
        private ByteBuffer outputBuffer;
        public FileOutputStream fos;

        private int gl_cnt = 0;

        public Codec(Surface surface, String url) {
            this.surface = surface;
            this.url = url;
            try {
                fos = new FileOutputStream("/mnt/sata/try.mp4",false);
            }catch (Exception e){};
            encoder = new Encode(fos,surface);
        }
        @Override
        public void run(){
            extractor = new MediaExtractor();
            try {
                extractor.setDataSource(url);
            } catch (Exception e) {
            };

            for (int i = 0; i < extractor.getTrackCount(); ++i) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    try {
                        mcdecoder = MediaCodec.createDecoderByType(mime);
                    } catch (Exception e) {}
                    mcdecoder.configure(format, null, null, 0);
                }
            }
            mcdecoder.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean isEOS = false;
            long startMs = System.currentTimeMillis();
            ByteBuffer inputBuffer;
            do {
                int inIdx = mcdecoder.dequeueInputBuffer(10000);
                if (inIdx >= 0) {
                    inputBuffer = mcdecoder.getInputBuffers()[inIdx];
                    int sampleSize = extractor.readSampleData(inputBuffer, 0) < 0 ? 0 : extractor.readSampleData(inputBuffer, 0);
                    if(sampleSize == 0) {
                        mcdecoder.queueInputBuffer(inIdx, 0, sampleSize,0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    }
                    else{
                        mcdecoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }

                int outIdx = mcdecoder.dequeueOutputBuffer(info, 10000);
                if (outIdx < 0) continue;
                outputBuffer = mcdecoder.getOutputBuffers()[outIdx];

                // We use a very simple clock to keep the video FPS, or the video
                // playback will be too fast
                while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                    try {
                        sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }

                mcdecoder.releaseOutputBuffer(outIdx, true);
                encoder.run_GLencode(gl_cnt++);
                if(outputBuffer != null) {
//                    encoder.run_encode(outputBuffer);

                }

            } while (!isEOS);
            mcdecoder.stop();
            mcdecoder.release();
            extractor.release();
            try {
                fos.close();
            }catch (Exception e){};

        }

    }

    private class Encode{
        public MediaCodec mcencoder;
        public FileOutputStream fos;

        private InputSurface inputSurface;

        public Encode(FileOutputStream fos,Surface surface){
            this.fos = fos;
            try {
                mcencoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            }catch (Exception e){
                e.printStackTrace();
            };
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000/*125000*/);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 24);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);

            mcencoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(mcencoder.createInputSurface()); //

            mcencoder.start();

        }

        public void run_GLencode(int frameIndex){
            int color1 = frameIndex*10%100+100;
            int color2 = frameIndex*10%100+100;
            int color3 = frameIndex*10%100+150;
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            GLES20.glClearColor(color1/255.0f, color2/255.0f, color3/255.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            inputSurface.makeCurrent();
            inputSurface.setPresentationTime(frameIndex * 2000);
            inputSurface.swapBuffers();


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



        public void run_encode(ByteBuffer outputBuffer){
            byte [] data = new byte[1280*720*3/2];
            outputBuffer.get(data,0,1280*720*3/2);

            int inputBufferIdx = mcencoder.dequeueInputBuffer(0);
            if(inputBufferIdx >=0) {
                ByteBuffer inputBuffer = mcencoder.getInputBuffers()[inputBufferIdx];
                inputBuffer.clear();
                inputBuffer.put(data);
                mcencoder.queueInputBuffer(inputBufferIdx, 0, data.length, 0, 0);
            }
            else{
                return;
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIdx = mcencoder.dequeueOutputBuffer(bufferInfo,0);
            if(outputBufferIdx >= 0){
                ByteBuffer outBuffer = mcencoder.getOutputBuffers()[outputBufferIdx];

                byte[] outData = new byte[bufferInfo.size];
                outBuffer.get(outData);
                try {
                    fos.write(outData, bufferInfo.offset, outData.length - bufferInfo.offset);
                    fos.flush();
                    mcencoder.releaseOutputBuffer(outputBufferIdx,false);
//                        outputBufferIdx = mcencoder.dequeueOutputBuffer(bufferInfo,0);
                }catch (Exception e){};
            }
        }
    }
}