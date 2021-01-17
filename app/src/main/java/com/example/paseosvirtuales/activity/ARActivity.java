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

public class ARActivity extends AppCompatActivity implements GLSurfaceView.Renderer, SensorEventListener, LocationListener {

    private static final String TAG = ARActivity.class.getSimpleName();

    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private final DepthSettings depthSettings = new DepthSettings();
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private final Texture depthTexture = new Texture();
    private final float[] anchorMatrix = new float[16];

    private Session session;
    private GLSurfaceView surfaceView;
    private SensorManager sensorManager;
    private Location location;
    private List<DataModel> modelsList;
    private TapHelper tapHelper;

    private float[] zeroMatrix = new float[16];
    private float[] translation = new float[]{0.0f, -0.8f, -0.8f};
    private float[] rotation = new float[]{0.0f, -1.00f, 0.0f, 0.3f};
    private Pose poseAR = new Pose(translation, rotation);
    private BackgroundRenderer backgroundRenderer = new BackgroundRenderer();

    private boolean installRequested;

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
        assert jsonFileString != null;
        Log.i("dataJSON", jsonFileString);

        Gson gson = new Gson();
        DataModel dataTest = gson.fromJson(jsonFileString, DataModel.class);
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

        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "La Camara no esta disponible. Intenta reiniciar la app.");
            session = null;
            return;
        }

        surfaceView.onResume();
    }

    private void handleLocationServices(){
        //si no se poseen los permisos de Localizacion finaliza
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);

            //Se verifica que se tenga activado las funciones de GPS y el estado de su conexion a internet
            boolean flagGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean flagNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            //se obtiene la ultim,a localizaciopn obtenida por medio del internet
            if (flagNetworkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            //se obtienen la ultima localizacion obtenida por medio del sensor GPS
            if (flagGPSEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }

        }
        catch(Exception e) {
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }



    }

    //se pausa la sesion y lo que se encuentre en la pantalla, solo si la sesion existe
    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            surfaceView.onPause();
            session.pause();
        }
    }

    private void handleSensor() {
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST);
    }

    //se calcula donde debe aparecer el objeto mediante la camara
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {

            float bearing, azimuth, pitch;
            Range<Float> azimuthRange, pitchRange;

            float[] rotationMatrixFromVector = new float[16];
            float[] updatedRotationMatrix = new float[16];
            float[] orientationValues = new float[3];

            SensorManager.getRotationMatrixFromVector(rotationMatrixFromVector, sensorEvent.values);
            SensorManager.remapCoordinateSystem(rotationMatrixFromVector, SensorManager.AXIS_X, SensorManager.AXIS_Y, updatedRotationMatrix);
            SensorManager.getOrientation(updatedRotationMatrix, orientationValues);

            //si no existen modelos cargados  finaliza el onSensorChanged
            if (modelsList.isEmpty()) {
                return;
            }

            //recorre la lista de modelos que se hayan obtenido del servidor
            for(DataModel dm: modelsList){
                bearing = location.bearingTo(dm.location.location);
                azimuth = (float) Math.toDegrees(orientationValues[0]);
                pitch = (float) Math.toDegrees(orientationValues[1]);

                azimuthRange = new Range<>(bearing - 10, bearing + 10);
                pitchRange = new Range<>(-90.0f, -45.0f);

                //si se encuentra en dispositivo en rango de la posicion del modelo 3D, se le asigna la visivisibilidad del modelo 3D
                if (azimuthRange.contains(azimuth) && pitchRange.contains(pitch)) {
                    dm.location.setVisible(true);
                } else {
                    dm.location.setVisible(false);
                }
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        this.location = location;

        //se calcula la distancia que exista entre la ubicacion de los objeto y la del dispositivo
        for(DataModel dm: modelsList){
            dm.location.setDistance(location.distanceTo(dm.location.location));
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
            // Obtiene el frame actual de la ARSession.
            Frame frame = session.update();
            Camera camera = frame.getCamera();

            //pregunta si el dispositivo es compatible con la funcion de mprofundidad de ARcore
            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                depthTexture.updateWithDepthImageOnGlThread(frame);
            }

            //TODO: hacer algo con el toque
            // Un toque por frame, solo en el screen de camara
            handleTap(frame, camera);

            // Dibujar background.
            backgroundRenderer.draw(frame, depthSettings.depthColorVisualizationEnabled());

            // Se mantiene la pantalla desbloqueada mientras exista el seguimiento.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            //Si el seguimiento se pausa muestra en pantalla un mensaje diciendo que sucedio
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                messageSnackbarHelper.showMessage(this, TrackingStateHelper.getTrackingFailureReasonString(camera));
                return;
            }


            // Se obtienen una matrix projectada de la camara
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Se obtiene la matrix de la camara y dibuja en pantalla
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Se calcula la intensidad de luz de la imagen obtenida por el frame.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // Se escalan los modelos 3D
            float scaleFactor = 0.1f;
            virtualObject.setUseDepthForOcclusion(this, depthSettings.useDepthForOcclusion());

            //renderiza todos los modelos 3D
            for (DataModel dm : modelsList) {

                // Se pregunta si el modelo 3D deberia verse en pantalla
                if(dm.location.visible) {
                    if (dm.location.zeroMatrix == null) {
                        dm.location.setZeroMatrix(getCalibrationMatrix(frame));
                    }
                }

                //en caso de que no exista una zeroMatrix para el modelo 3D, comienza con el siguiente modelo
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
            Log.e(TAG, "Exception en el hilo de OpenGL", t);
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
            //if (anchors.size() >= 20) {
            //    anchors.get(0).anchor.detach();
            //    anchors.remove(0);
            //}
        }
    }
}
