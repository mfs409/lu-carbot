package edu.lehigh.cse.paclab.carbot.support;

import java.io.IOException;
import java.util.List;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * This class really just builds the background where the feed from the camera is displayed. There is no OpenCV material here.
 * @author ArmonShariati
 *
 */
public abstract class CameraViewBase extends SurfaceView implements SurfaceHolder.Callback, Runnable {

	private static final String TAG = "CameraViewBase";
	
	private Camera mCamera;
	private SurfaceHolder mHolder;
	private int mFrameWidth;
	private int mFrameHeight;
	private byte [] mFrame;
	private boolean mThreadRun;
	private byte [] mBuffer;
	
	/**
	 * Constructor creates a callback so we may be notified if a surface has been created or destroyed
	 */
	public CameraViewBase(Context context) {
		super(context);
		Log.i(TAG, "CameraViewBase constructor");
		mHolder = getHolder();
		mHolder.addCallback(this);
	}
	
	public void setPreview () throws IOException {
		//Create a preview texture for SDK's beyond honeycomb
		//I think this may be a requirement....
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			mCamera.setPreviewTexture(new SurfaceTexture(10));
		else
			mCamera.setPreviewDisplay(null);
	}
	
	public boolean openCamera()
	{
		Log.i(TAG, "openning camera");
		//Incase the camera was being occupied earlier...
		releaseCamera();
		mCamera = Camera.open();
		if(mCamera == null)
		{
			Log.e(TAG, "Can't Open Camera");
			return false;
		}
		
		//What am I really loading into mFrame?
		mCamera.setPreviewCallbackWithBuffer(new PreviewCallback() {
			public void onPreviewFrame(byte[] data, Camera camera) {
                synchronized (CameraViewBase.this) {
                    System.arraycopy(data, 0, mFrame, 0, data.length);
                    CameraViewBase.this.notify(); 
                }
                camera.addCallbackBuffer(mBuffer);
            }
		});
		return true;
		
	}
	
	public void releaseCamera() {
		// TODO Auto-generated method stub
		Log.i(TAG, "Releasing Camera");
		mThreadRun = false;
		synchronized (this) {
	        if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);
                mCamera.release();
                mCamera = null;
            }
        }
		onPreviewStopped();
	}
	
	public void setupCamera(int width, int height) {
		Log.i(TAG, "Setting up Camera");
		
		synchronized (this)
		{
			if (mCamera != null)
			{
				Camera.Parameters params = mCamera.getParameters();
				List<Camera.Size> sizes = params.getSupportedPreviewSizes();
				mFrameWidth = width;
				mFrameHeight = height;
				
				//selecting optimal camera preview size
				{
					int minDiff = Integer.MAX_VALUE;
					for (Camera.Size size : sizes)
					{
						if (Math.abs(size.height - height) < minDiff){
							mFrameWidth = size.width;
							mFrameHeight = size.height;
							minDiff = Math.abs(size.height - height);
						}
						
					}
				}
				
				params.setPreviewSize(getFrameWidth(),getFrameHeight());
				
				//tries to stream continuous video to the surface preview
				List<String> FocusModes = params.getSupportedFocusModes();
				if (FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                {
                	params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                }
				
				mCamera.setParameters(params);
                
                // Now allocate the buffer
                // Still not exactly sure what the buffer for...
                params = mCamera.getParameters();
                int size = params.getPreviewSize().width * params.getPreviewSize().height;
                size  = size * ImageFormat.getBitsPerPixel(params.getPreviewFormat()) / 8;
                mBuffer = new byte[size];
                /* The buffer where the current frame will be copied */
                mFrame = new byte [size];
                mCamera.addCallbackBuffer(mBuffer);

    			try {
    				setPreview();
    			} catch (IOException e) {
    				Log.e(TAG, "mCamera.setPreviewDisplay/setPreviewTexture fails: " + e);
    			}

                /* Notify that the preview is about to be started and deliver preview size */
                onPreviewStarted(params.getPreviewSize().width, params.getPreviewSize().height);

                /* Now we can start a preview */
                mCamera.startPreview();
				
	
			}
		}
	}
	public void surfaceChanged(SurfaceHolder _holder, int format, int width, int height) {
        Log.i(TAG, "surfaceChanged");
        setupCamera(width, height);
    }

	/**
	 * Begin streaming video. Break off from main UI thread
	 */
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");
        (new Thread(this)).start();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
        releaseCamera();
    }
	
	/* The bitmap returned by this method shall be owned by the child and released in onPreviewStopped() */
    protected abstract Bitmap processFrame(byte[] data);

    /**
     * NOTE: These comments where taken from OpenCV
    * This method is called when the preview process is being started. It is called before the first frame delivered and processFrame is called
    * It is called with the width and height parameters of the preview process. It can be used to prepare the data needed during the frame processing.
    * @param previewWidth - the width of the preview frames that will be delivered via processFrame
    * @param previewHeight - the height of the preview frames that will be delivered via processFrame
    */
    protected abstract void onPreviewStarted(int previewWidtd, int previewHeight);
	
	/**
	 * NOTE: These comments where taken from OpenCV
     * This method is called when preview is stopped. When this method is called the preview stopped and all the processing of frames already completed.
     * If the Bitmap object returned via processFrame is cached - it is a good time to recycle it.
     * Any other resources used during the preview can be released.
     */
    protected abstract void onPreviewStopped();
	
	public int getFrameWidth() {
        return mFrameWidth;
    }

    public int getFrameHeight() {
        return mFrameHeight;
    }
    
    public void run() {
        mThreadRun = true;
        Log.i(TAG, "Starting processing thread");
        while (mThreadRun) {
            Bitmap bmp = null;

            synchronized (this) {
                try {
                    this.wait();
                    bmp = processFrame(mFrame);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (bmp != null) {
                Canvas canvas = mHolder.lockCanvas();
                if (canvas != null) {
                    canvas.drawBitmap(bmp, (canvas.getWidth() - getFrameWidth()) / 2, (canvas.getHeight() - getFrameHeight()) / 2, null);
                    mHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }
	
	
}