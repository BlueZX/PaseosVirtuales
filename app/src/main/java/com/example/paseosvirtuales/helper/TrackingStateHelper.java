package com.example.paseosvirtuales.helper;

import android.app.Activity;
import android.view.WindowManager;

import com.google.ar.core.Camera;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;

//Mensajes del seguimiento
public final class TrackingStateHelper {
    private static final String INSUFFICIENT_FEATURES_MESSAGE = "No se puede encontrar nada. Apunte el dispositivo a una superficie con más textura o color.";
    private static final String EXCESSIVE_MOTION_MESSAGE = "Te estás moviendo muy rapido. porfavor, mueve tu celular más lento.";
    private static final String INSUFFICIENT_LIGHT_MESSAGE = "Muy oscuro, muevete a un lugar mas iluminado";
    private static final String BAD_STATE_MESSAGE = "Seguimiento perdido debido a un mal funcionamiento del dispositivo o la aplicación. Intente reiniciar la experiencia de RA.";
    private static final String CAMERA_UNAVAILABLE_MESSAGE = "Otra aplicacion esta ocupando la camara. Toca aquí o intenta cerrando la otra aplicación.";

    private final Activity activity;

    private TrackingState previousTrackingState;

    public TrackingStateHelper(Activity activity) {
        this.activity = activity;
    }

    public void updateKeepScreenOnFlag(TrackingState trackingState) {
        if (trackingState == previousTrackingState) {
            return;
        }

        previousTrackingState = trackingState;

        switch (trackingState) {
            case PAUSED:
            case STOPPED:
                activity.runOnUiThread(() -> activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            break;
            case TRACKING:
                activity.runOnUiThread(() -> activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            break;
        }
    }

    public static String getTrackingFailureReasonString(Camera camera) {
        TrackingFailureReason reason = camera.getTrackingFailureReason();

        switch (reason) {
            case NONE:
                return "";
            case BAD_STATE:
                return BAD_STATE_MESSAGE;
            case INSUFFICIENT_LIGHT:
                return INSUFFICIENT_LIGHT_MESSAGE;
            case EXCESSIVE_MOTION:
                return EXCESSIVE_MOTION_MESSAGE;
            case INSUFFICIENT_FEATURES:
                return INSUFFICIENT_FEATURES_MESSAGE;
            case CAMERA_UNAVAILABLE:
                return CAMERA_UNAVAILABLE_MESSAGE;
        }

        return "Error de seguimiento desconocido. ocasionado por: " + reason;
    }
}
