package com.example.paseosvirtuales.model;

import com.example.paseosvirtuales.renderer.ObjectRenderer;

public class Location {
    public float lat;
    public float lon;
    public float z;

    public android.location.Location location;
    public boolean visible;
    public float distance;
    public float[] zeroMatrix;
    public ObjectRenderer virtualObject;

    public void setLocation(float latitude, float longitude) {
        location = new android.location.Location("modelLocation");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public void setDistance(Float distance) {
        this.distance = distance;
    }

    public void setVirtualObject(ObjectRenderer virtualObject) {
        this.virtualObject = virtualObject;
    }

    public void setZeroMatrix(float[] zeroMatrix) {
        this.zeroMatrix = zeroMatrix;
    }
}