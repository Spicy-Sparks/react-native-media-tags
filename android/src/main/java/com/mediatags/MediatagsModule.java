package com.mediatags;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.util.Size;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class MediatagsModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;

    public MediatagsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "Mediatags";
    }

    @ReactMethod
    public void getMetaData(String filePath, final Promise callback) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            WritableMap resultData = new WritableNativeMap();
            retriever.setDataSource(getReactApplicationContext(), Uri.parse(filePath));

            resultData.putString("artist", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
            resultData.putString("album", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
            resultData.putString("title", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
            resultData.putString("albumArtist", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));

            callback.resolve(resultData);
        }
        catch (Exception e) {
            callback.reject(e);
        }
    }

    @ReactMethod
    public void getArtwork(String filePath, Callback callback) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(getReactApplicationContext(), Uri.parse(filePath));

                byte[] audioAlbumArtBytes = retriever.getEmbeddedPicture();
                if (audioAlbumArtBytes == null) {
                    callback.invoke(0);
                    return;
                }

                String base64 = Base64.encodeToString(audioAlbumArtBytes, Base64.DEFAULT);

                callback.invoke(base64);
            } catch (Exception e) {
                callback.invoke(0);
            }
        } catch (Exception e) {
            callback.invoke(e);
        }
    }
}
