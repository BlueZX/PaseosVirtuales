package com.example.paseosvirtuales.activity;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.renderscript.Float3;

import com.example.paseosvirtuales.helper.CameraPermissionHelper;
import com.example.paseosvirtuales.helper.LocationPermissionHelper;
import com.example.paseosvirtuales.helper.TapHelper;
import com.example.paseosvirtuales.model.DataModel;
import com.example.paseosvirtuales.helper.DepthSettings;
import com.example.paseosvirtuales.helper.SnackbarHelper;
import com.example.paseosvirtuales.helper.TrackingStateHelper;
import com.example.paseosvirtuales.helper.getDataHelper;
import com.example.paseosvirtuales.renderer.BackgroundRenderer;

import com.example.paseosvirtuales.renderer.ObjectRenderer;
import com.example.paseosvirtuales.renderer.Texture;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;


import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.paseosvirtuales.R;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.content.Context.SENSOR_SERVICE;

public class ARActivity extends AppCompatActivity implements GLSurfaceView.Renderer, SensorEventListener, LocationListener {

    private static final String TAG = ARActivity.class.getSimpleName();
    private GLSurfaceView surfaceView;
    private final DepthSettings depthSettings = new DepthSettings();

    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Location location;

    private boolean installRequested;
    private boolean flagGPSEnabled;
    private boolean flagNetworkEnabled;
    private boolean locationServiceAvailable;
    private DataModel dataTest;
    private List<DataModel> modelsList;

    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private final Texture depthTexture = new Texture();

    private Config defaultConfig;
    private TapHelper tapHelper;
    private Session session;
    private BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private GestureDetector gestureDetector;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

    private final float[] anchorMatrix = new float[16];
    private float[] zeroMatrix = new float[16];
    private static final float[] DEFAULT_COLOR = new float[] {0f, 0f, 0f, 0f};

    private float[] translation = new float[]{0.0f, -0.8f, -0.8f};
    private float[] rotation = new float[]{0.0f, -1.00f, 0.0f, 0.3f};

    private Pose poseAR = new Pose(translation, rotation);

    private static class ColoredAnchor {
        public final Anchor anchor;
        public final float[] color;

        public ColoredAnchor(Anchor a, float[] color4f) {
            this.anchor = a;
            this.color = color4f;
        }
    }

    private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar);

        surfaceView = findViewById(R.id.surfaceview);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        //TODO: llenar lista de modelos
        modelsList = new ArrayList<>();

        String jsonFileString = getDataHelper.getJsonFromAssets(getApplicationContext(), "sampledata/dataObj.json");
        Log.i("dataJSON", jsonFileString);

        Gson gson = new Gson();
        dataTest = gson.fromJson(jsonFileString, DataModel.class);
        dataTest.location.setLocation(dataTest.location.lat, dataTest.location.lon);
        Log.i("dataJSON", "latitud: "+ dataTest.location.lat + ", longitud: " + dataTest.location.lon);
        modelsList.add(dataTest);

        Matrix.setIdentityM(zeroMatrix, 0);
        poseAR.toMatrix(anchorMatrix, 0);

        // Configurar el renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);


        installRequested = false;

        // Configuracion de tocar pantalla
        tapHelper = new TapHelper(/*context=*/ this);
        surfaceView.setOnTouchListener(tapHelper);

        depthSettings.onCreate(this);

        //interfaz
        ImageButton settingsButton = findViewById(R.id.settings_button);
        ImageButton backButton = findViewById(R.id.back_button);

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
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

                handleSensor();

                // Si no se tienen los permisos de localizacion, se solicitan
                if (!LocationPermissionHelper.hasLocationPermission(this)) {
                    LocationPermissionHelper.requestLocationPermission(this);
                }else{
                    handleLocationServices();
                }

                // ARCore necesita permisos de camara para funcionar. Si es que todavia no lo tiene
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Crear la sesión
                session = new Session(/* context= */ this);
                Config config = session.getConfig();
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.setDepthMode(Config.DepthMode.AUTOMATIC);
                } else {
                    config.setDepthMode(Config.DepthMode.DISABLED);
                }
                session.configure(config);
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

        // el orden es importante: consulte la nota en onPause(), aquí se aplica lo contrario.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "La Camara no esta disponible. Intenta reiniciar la app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        //displayRotationHelper.onResume();
    }

    private void handleLocationServices(){
        //si no se poseen los permisos de Localizacion finaliza
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            locationManager = (LocationManager) this.getSystemService(this.LOCATION_SERVICE);

            //Se verifica que se tenga activado las funciones de GPS y el estado de su conexion a internet
            flagGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            flagNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!flagGPSEnabled && !flagNetworkEnabled) {
                // GPS y conexion a internet activados
                locationServiceAvailable = true;
            }
            else{
                // no se puede obtener la localizacion o la conexion a internet en el dispositivo
                locationServiceAvailable = false;
            }

            if (flagGPSEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
                if (locationManager != null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            }

            if (flagGPSEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

                if (locationManager != null) {
                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
            }

        }
        catch(Exception e) {
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }



    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            //displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    private void handleSensor() {
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST);
    }

    // S i hay un cambi
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {

            float azimuth, pitch, bearing;
            Range<Float> azimuthRange, pitchRange;

            float[] rotationMatrixFromVector = new float[16];
            float[] updatedRotationMatrix = new float[16];
            float[] orientationValues = new float[3];

            SensorManager.getRotationMatrixFromVector(rotationMatrixFromVector, sensorEvent.values);
            SensorManager.remapCoordinateSystem(rotationMatrixFromVector, SensorManager.AXIS_X, SensorManager.AXIS_Y, updatedRotationMatrix);
            SensorManager.getOrientation(updatedRotationMatrix, orientationValues);

            if (modelsList.isEmpty()) {
                return;
            }

            for(DataModel dm: modelsList){
                bearing = location.bearingTo(dm.location.location);
                azimuth = (float) Math.toDegrees(orientationValues[0]);
                pitch = (float) Math.toDegrees(orientationValues[1]);

                azimuthRange = new Range<>(bearing - 10, bearing + 10);
                pitchRange = new Range<>(-90.0f, -45.0f);

                if (azimuthRange.contains(azimuth) && pitchRange.contains(pitch)) {
                    dm.location.setVisible(true);
                } else {
                    dm.location.setVisible(false);
                }
                Log.d("visible","es:" + dm.location.visible );
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        this.location = location;

        for(DataModel dm: modelsList){
            dm.location.setDistance(location.distanceTo(dm.location.location));
            Log.d("distancia","distancia init:" + dm.location.distance);
        }

    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepara el renderizado de objetos. Esto implica la lectura de shaders, asi que puede lanzar una excepcion (IOException).
        try {
            // Cree la textura y se lo pasa a la sesión de ARCore para que se llene durante el update().
            depthTexture.createOnGlThread();
            backgroundRenderer.createOnGlThread(/*context=*/ this, depthTexture.getTextureId());
            //planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
            //pointCloudRenderer.createOnGlThread(/*context=*/ this);

            virtualObject.createOnGlThread(/*context=*/ this, "models/esca.obj", "models/esca.jpg");
            virtualObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
            virtualObject.setDepthTexture(depthTexture.getTextureId(), depthTexture.getWidth(), depthTexture.getHeight());
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

        } catch (IOException e) {
            Log.e(TAG, "Error al leer los archivos del modelo 3D", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        // Avisa a la sesión de ARCore que el tamaño del view cambio asi que la perspectiva de la matrix y del video son ajustadas.
        session.setDisplayGeometry(0,width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Limpiar la pantalla para avisar al driver que no debe cargar ningún píxel del frame anterior.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());
            // Obtiene el frame actual de la ARSession. Cuando la  configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = session.update();
            Camera camera = frame.getCamera();

            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                depthTexture.updateWithDepthImageOnGlThread(frame);
            }

            // Un toque por frame, solo en el screen de camara
            handleTap(frame, camera);

            // Dibujar background.
            //backgroundRenderer.draw(frame);
            backgroundRenderer.draw(frame, depthSettings.depthColorVisualizationEnabled());

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

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

            //escala los modelos 3D
            float scaleFactor = 0.1f;
            virtualObject.setUseDepthForOcclusion(this, depthSettings.useDepthForOcclusion());

            //renderiza todos los modelos 3D asignando color
            /*for (ColoredAnchor coloredAnchor : anchors) {
                //if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
                //    continue;
                //}
                // Get the current pose of an Anchor in world space. The Anchor pose is updated
                // during calls to session.update() as ARCore refines its estimate of the world.
                coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0);

                // Update and draw the model and its shadow.
                virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
                virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
            }*/

            for (DataModel dm : modelsList) {
                if(dm.location.visible) {
                    if (dm.location.zeroMatrix == null) {
                        dm.location.setZeroMatrix(getCalibrationMatrix(frame));
                    }
                }

                if (dm.location.zeroMatrix == null) {
                    break;
                }

                Matrix.multiplyMM(viewmtx, 0, viewmtx, 0, dm.location.zeroMatrix, 0);

                virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
                virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba);

            }

        }
        catch (Throwable t) {
            // para evitar bloquear la aplicación por excepciones no controladas.
            Log.e(TAG, "Exception en el hilo del OpenGL", t);
        }


    }

    public float[] getCalibrationMatrix(Frame frame) {
        float[] t = new float[3];
        float[] m = new float[16];

        frame.getCamera().getPose().getTranslation(t, 0);
        float[] z = frame.getCamera().getPose().getZAxis();
        Float3 zAxis = new Float3(z[0], z[1], z[2]);
        zAxis.y = 0;

        double rotate = Math.atan2(zAxis.x, zAxis.z);

        Matrix.setIdentityM(m, 0);
        Matrix.translateM(m, 0, t[0], t[1], t[2]);
        Matrix.rotateM(m, 0, (float) Math.toDegrees(rotate), 0, 1, 0);
        return m;
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        //if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) { //Tracking es si hay un terreno plano
        if (tap != null) {
            Log.d("Touch","Toque");

            for(DataModel dm: modelsList){
                messageSnackbarHelper.showMessageWithDismiss(this, "dispositivo:"+location.toString()+ ", objeto:"+ dm.location.location.toString());
            }

            //si toco la pantalla se pone el objeto 3D
            //TODO: cambiarlo al cargar poir posicion y no por toque
            //Solo pueden existir 21 elemnentos en pantalla, si hay mas borra el primero que cargo, por tema de memoria
            if (anchors.size() >= 20) {
                anchors.get(0).anchor.detach();
                anchors.remove(0);
            }
        }
    }
}
