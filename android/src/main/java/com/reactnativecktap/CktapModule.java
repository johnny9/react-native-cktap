package com.reactnativecktap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.app.Activity;
import android.nfc.tech.IsoDep;

import android.nfc.tech.NdefFormatable;

import android.app.PendingIntent;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.TagTechnology;
import android.nfc.tech.Ndef;

import android.os.Parcelable;


import com.facebook.react.bridge.*;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import org.json.JSONObject;
import org.json.JSONException;

import java.util.*;

@ReactModule(name = CktapModule.NAME)
public class CktapModule extends ReactContextBaseJavaModule implements ActivityEventListener {
    public static final String NAME = "Cktap";
    public static final String LOG_TAG = NAME;
    private TagTechnologyRequest techRequest = null;
    private WriteNdefRequest writeNdefRequest = null;
    private Context context;
    private WritableMap bgTag = null;

    private static final String ERR_CANCEL = "cancelled";
    private static final String ERR_NOT_REGISTERED = "you should requestTagEvent first";
    private static final String ERR_MULTI_REQ = "You can only issue one request at a time";
    private static final String ERR_NO_TECH_REQ = "no tech request available";
    private static final String ERR_NO_REFERENCE = "no reference available";
    private static final String ERR_TRANSCEIVE_FAIL = "transceive fail";
    private static final String ERR_API_NOT_SUPPORT = "unsupported tag api";
    private static final String ERR_GET_ACTIVITY_FAIL = "fail to get current activity";
    private static final String ERR_NO_NFC_SUPPORT = "no nfc support";

    class WriteNdefRequest {
        NdefMessage message;
        Callback callback;
        boolean format;
        boolean formatReadOnly;

        WriteNdefRequest(NdefMessage message, Callback callback, boolean format, boolean formatReadOnly) {
            this.message = message;
            this.callback = callback;
            this.format = format;
            this.formatReadOnly = formatReadOnly;
        }
    }

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

    @ReactMethod
    public void start(Callback callback) {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);
        if (nfcAdapter != null) {
            Log.d(LOG_TAG, "start");

            IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
            Activity currentActivity = getCurrentActivity();
            if (currentActivity == null) {
                callback.invoke(ERR_GET_ACTIVITY_FAIL);
                return;
            }

            currentActivity.registerReceiver(mReceiver, filter);
            Intent launchIntent = currentActivity.getIntent();
            // we consider the launching intent to be background
            bgTag = parseNfcIntent(launchIntent);
            callback.invoke();
        } else {
            Log.d(LOG_TAG, "not support in this device");
            callback.invoke(ERR_NO_NFC_SUPPORT);
        }
    }

    private WritableMap parseNfcIntent(Intent intent) {
        Log.d(LOG_TAG, "parseIntent " + intent);
        String action = intent.getAction();
        Log.d(LOG_TAG, "action " + action);
        if (action == null) {
              return null;
        }

        WritableMap parsed = null;
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        // Parcelable[] messages = intent.getParcelableArrayExtra((NfcAdapter.EXTRA_NDEF_MESSAGES));

        synchronized(this) {
            if (writeNdefRequest != null) {
            writeNdef(
                tag,
                writeNdefRequest
            );
            writeNdefRequest = null;

            // explicitly return null, to avoid extra detection
            return null;
        } else if (techRequest != null) {
            if (!techRequest.isConnected()) {
                boolean result = techRequest.connect(tag);
                if (result) {
                    techRequest.getPendingCallback().invoke(null, techRequest.getTechType());
                } else {
                    // this indicates that we get a NFC tag, but none of the user required tech is matched
                    techRequest.getPendingCallback().invoke(null, null);
                }
            }

            // explicitly return null, to avoid extra detection
            return null;
        }
    }

    if (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
        Ndef ndef = Ndef.get(tag);
        Parcelable[] messages = intent.getParcelableArrayExtra((NfcAdapter.EXTRA_NDEF_MESSAGES));
        parsed = ndef2React(ndef, messages);
        } else if (action.equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
            // if the tag contains NDEF, we want to report the content
            if (Arrays.asList(tag.getTechList()).contains(Ndef.class.getName())) {
                Ndef ndef = Ndef.get(tag);
                parsed = ndef2React(ndef, new NdefMessage[] { ndef.getCachedNdefMessage() });
            } else {
                parsed = tag2React(tag);
            }
        } else if (action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
              parsed = tag2React(tag);
        }

        return parsed;
    }

    private WritableMap tag2React(Tag tag) {
        try {
            JSONObject json = Util.tagToJSON(tag);
            return JsonConvert.jsonToReact(json);
        } catch (JSONException ex) {
            return null;
        }
    }
    
    private WritableMap ndef2React(Ndef ndef, Parcelable[] messages) {
        try {
            JSONObject json = buildNdefJSON(ndef, messages);
            return JsonConvert.jsonToReact(json);
        } catch (JSONException ex) {
            return null;
        }
    }

    JSONObject buildNdefJSON(Ndef ndef, Parcelable[] messages) {
        JSONObject json = Util.ndefToJSON(ndef);

        // ndef is null for peer-to-peer
        // ndef and messages are null for ndef format-able
        if (ndef == null && messages != null) {
            try {

                if (messages.length > 0) {
                    NdefMessage message = (NdefMessage) messages[0];
                    json.put("ndefMessage", Util.messageToJSON(message));
                    // guessing type, would prefer a more definitive way to determine type
                    json.put("type", "NDEF");
                }

                if (messages.length > 1) {
                    Log.d(LOG_TAG, "Expected one ndefMessage but found " + messages.length);
                }

            } catch (JSONException e) {
                // shouldn't happen
                Log.e(Util.TAG, "Failed to convert ndefMessage into json", e);
            }
        }
        return json;
    }


    private void writeNdef(Tag tag, WriteNdefRequest request) {
        NdefMessage message = request.message;
        Callback callback = request.callback;
        boolean formatReadOnly = request.formatReadOnly;
        boolean format = request.format;

        if (format || formatReadOnly) {
            try {
                Log.d(LOG_TAG, "ready to writeNdef");
                NdefFormatable formatable = NdefFormatable.get(tag);
                if (formatable == null) {
                    callback.invoke(ERR_API_NOT_SUPPORT);
                } else {
                    Log.d(LOG_TAG, "ready to format ndef, seriously");
                    formatable.connect();
                    if (formatReadOnly) {
                        formatable.formatReadOnly(message);
                    } else {
                        formatable.format(message);
                    }
                    callback.invoke();
                }
            } catch (Exception ex) {
                callback.invoke(ex.toString());
            }
        } else {
            try {
                Log.d(LOG_TAG, "ready to writeNdef");
                Ndef ndef = Ndef.get(tag);
                if (ndef == null) {
                    callback.invoke(ERR_API_NOT_SUPPORT);
                } else if (!ndef.isWritable()) {
                    callback.invoke("tag is not writeable");
                } else if (ndef.getMaxSize() < message.toByteArray().length) {
                    callback.invoke("tag size is not enough");
                } else {
                    Log.d(LOG_TAG, "ready to writeNdef, seriously");
                    ndef.connect();
                    ndef.writeNdefMessage(message);
                    callback.invoke();
                }
            } catch (Exception ex) {
                callback.invoke(ex.toString());
            }
        }
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        getReactApplicationContext().getJSModule(RCTNativeAppEventEmitter.class).emit(eventName, params);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(LOG_TAG, "onReceive " + intent);
            final String action = intent.getAction();

            if (action.equals(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
                final int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF);
                String stateStr = "unknown";
                switch (state) {
                    case NfcAdapter.STATE_OFF:
                        stateStr = "off";
                        break;
                    case NfcAdapter.STATE_TURNING_OFF:
                        stateStr = "turning_off";
                        break;
                    case NfcAdapter.STATE_ON:
                        stateStr = "on";
                        break;
                    case NfcAdapter.STATE_TURNING_ON:
                        stateStr = "turning_on";
                        break;
                }

                try {
                    WritableMap writableMap = Arguments.createMap();
                    writableMap.putString("state", stateStr);
                    sendEvent("NfcManagerStateChanged", writableMap);
                } catch (Exception ex) {
                    Log.d(LOG_TAG, "send nfc state change event fail: " + ex);
                }
            }
        }
    };

    public native void cardStatus(IsoDep card);
}
