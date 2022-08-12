package com.reactnativecktap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.content.Intent;
import android.app.Activity;
import android.nfc.tech.IsoDep;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.module.annotations.ReactModule;

@ReactModule(name = CktapModule.NAME)
public class CktapModule extends ReactContextBaseJavaModule implements ActivityEventListener {
    public static final String NAME = "Cktap";

    public CktapModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

     /** Called when host (activity/service) receives an {@link Activity#onActivityResult} call. */
    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, @Nullable Intent data) {
        Log.d(NAME, "onActivityResult");
    }

    /** Called when a new intent is passed to the activity */
    @Override
    public void onNewIntent(Intent intent) {
        Log.d(NAME, "onNewIntent");

    }


    // Example method
    // See https://reactnative.dev/docs/native-modules-android
    @ReactMethod
    public void multiply(double a, double b, Promise promise) {
        promise.resolve(a * b);
    }

    static {
        System.loadLibrary("native-lib");
    }

    public native void cardStatus(IsoDep card);
}
