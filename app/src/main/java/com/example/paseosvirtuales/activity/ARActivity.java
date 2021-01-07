package com.example.paseosvirtuales.activity;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.location.Location;
import android.location.LocationListener;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

import com.example.paseosvirtuales.helper.CameraPermissionHelper;
import com.example.paseosvirtuales.helper.DataModel;
import com.example.paseosvirtuales.helper.DepthSettings;
import com.example.paseosvirtuales.helper.SnackbarHelper;
import com.example.paseosvirtuales.helper.TrackingStateHelper;
import com.example.paseosvirtuales.helper.getDataHelper;
import com.example.paseosvirtuales.renderer.BackgroundRenderer;

import com.example.paseosvirtuales.renderer.Texture;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.paseosvirtuales.R;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.gson.Gson;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ARActivity extends AppCompatActivity implements GLSurfaceView.Renderer, SensorEventListener, LocationListener {

    private static final String TAG = ARActivity.class.getSimpleName();
    private GLSurfaceView mSurfaceView;
    private final DepthSettings depthSettings = new DepthSettings();

    private boolean installRequested;

    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private final Texture depthTexture = new Texture();

    private Config mDefaultConfig;
    private Session mSession;
    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private GestureDetector mGestureDetector;
    private final TrackingStateHelper mTrackingStateHelper = new TrackingStateHelper(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar);

        mSurfaceView = findViewById(R.id.surfaceview);

        String jsonFileString = getDataHelper.getJsonFromAssets(getApplicationContext(), "sampledata/dataObj.json");
        Log.i("dataJSON", jsonFileString);

        Gson gson = new Gson();
        DataModel dataTest = gson.fromJson(jsonFileString, DataModel.class);
        Log.i("dataJSON", "x: "+ dataTest.location.x + ", y: " + dataTest.location.y);

        // Configurar el renderer.
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        mSurfaceView.setRenderer(this);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        mSurfaceView.setWillNotDraw(false);


        installRequested = false;

        // Configuracion de tocar listener
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Log.d("Toque","toque aqui");
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });

        depthSettings.onCreate(this);

        //interfaz
        ImageButton settingsButton = findViewById(R.id.settings_button);
        ImageButton backButton = findViewById(R.id.back_button);

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSession == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore necesita permisos de camara para funcionar. Si es que todavia no lo tiene
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Crear la sesión
                mSession = new Session(/* context= */ this);
                Config config = mSession.getConfig();
                if (mSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.setDepthMode(Config.DepthMode.AUTOMATIC);
                } else {
                    config.setDepthMode(Config.DepthMode.DISABLED);
                }
                mSession.configure(config);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Por favor instala ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Por favor actualiza ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Necesitas actualizar esta app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "Tu dispositivo no soporta AR";
                exception = e;
            } catch (Exception e) {
                message = "Ocurrio un error al crear la sesión de AR";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creando la sesión", exception);
                return;
            }
        }

        // el orden es importante: consulte la nota en onPause (), aquí se aplica lo contrario.
        try {
            mSession.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "La Camara no esta disponible. Intenta reiniciar la app.");
            mSession = null;
            return;
        }

        mSurfaceView.onResume();
        //displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSession != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            //displayRotationHelper.onPause();
            mSurfaceView.onPause();
            mSession.pause();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepara el renderizado de objetos. Esto implica la lectura de shaders, asi que puede lanzar una excepcion (IOException).
        try {
            // Cree la textura y se lo pasa a la sesión de ARCore para que se llene durante el update().
            depthTexture.createOnGlThread();
            mBackgroundRenderer.createOnGlThread(/*context=*/ this, depthTexture.getTextureId());
            //planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
            //pointCloudRenderer.createOnGlThread(/*context=*/ this);

            //virtualObject.createOnGlThread(/*context=*/ this, "models/goku.obj", "models/Tex_0000.jpg");
            //virtualObject.setBlendMode(BlendMode.AlphaBlending);
            //virtualObject.setDepthTexture(
            //        depthTexture.getTextureId(), depthTexture.getWidth(), depthTexture.getHeight());
            //virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        // Avisa a la sesión de ARCore que el tamaño del view cambio asi que la perspectiva de la matrix y del video son ajustadas.
        mSession.setDisplayGeometry(0,width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Limpiar la pantalla para avisar al driver que no debe cargar ningún píxel del frame anterior.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mSession == null) {
            return;
        }

        try {
            mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
            // Obtiene el frame actual de la ARSession. Cuando la  configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = mSession.update();
            Camera camera = frame.getCamera();

            if (mSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                depthTexture.updateWithDepthImageOnGlThread(frame);
            }

            // Dibujar background.
            //mBackgroundRenderer.draw(frame);
            mBackgroundRenderer.draw(frame, depthSettings.depthColorVisualizationEnabled());

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            mTrackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            if (camera.getTrackingState() == TrackingState.PAUSED) {
                messageSnackbarHelper.showMessage(
                        this, TrackingStateHelper.getTrackingFailureReasonString(camera));
                return;
            }


            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            float scaleFactor = 0.5f;

        }
        catch (Throwable t) {
            // para evitar bloquear la aplicación por excepciones no controladas.
            Log.e(TAG, "Exception en el hilo del OpenGL", t);
        }


    }
}
