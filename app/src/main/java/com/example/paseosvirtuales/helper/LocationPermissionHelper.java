package com.example.paseosvirtuales.helper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public final class LocationPermissionHelper {
    public static final int LOCATION_PERMISSIONS_CODE = 99;
    private static final String LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;

    // Verifica que se tenga los permisos concedidos para ocupar la localización en la aplicación.
    public static boolean hasLocationPermission(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    // Verifica que se tenga los permisos necesarios ocupar la aplicación, en caso de no poseerlos los solicita
    public static void requestLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity, new String[] {LOCATION_PERMISSION}, LOCATION_PERMISSIONS_CODE);
    }

    // Verifica si se necesita mostrar por que se necesita el permiso de la localizacion
    public static boolean shouldShowRequestPermissionRationale(Activity activity) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, LOCATION_PERMISSION);
    }

    // Inicia la configuracion de la aplicacion para dar el permiso.
    public static void launchPermissionSettings(Activity activity) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }


}
