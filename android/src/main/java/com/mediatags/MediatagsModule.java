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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

            // Android's MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST is unreliable
            // for MP3 ID3v2 — it frequently returns null even when a TPE2 frame is
            // present. Try it first, then fall back to reading the TPE2 frame straight
            // out of the ID3v2 tag so album-artist disambiguation works on MP3s too.
            String albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            if (albumArtist == null || albumArtist.isEmpty()) {
                albumArtist = readId3v2TextFrame(filePath, "TPE2");
            }
            resultData.putString("albumArtist", albumArtist);

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

    /**
     * Read a single ID3v2 text frame (e.g. TPE2 = album artist) directly from the
     * file, because MediaMetadataRetriever does not expose every frame on MP3.
     * Only the tag region at the head of the file is read. Returns null when the
     * frame is absent or the file has no readable ID3v2 tag.
     */
    private String readId3v2TextFrame(String filePath, String frameId) {
        InputStream is = null;
        try {
            Uri uri = Uri.parse(filePath);
            String scheme = uri.getScheme();
            if ("content".equals(scheme)) {
                is = getReactApplicationContext().getContentResolver().openInputStream(uri);
            } else {
                // Imported copies land in the app's files dir as a plain filesystem
                // path (or a file:// URI) — ContentResolver can't open those, so read
                // the file directly. This is the path getMetaData actually receives.
                String rawPath = ("file".equals(scheme) && uri.getPath() != null) ? uri.getPath() : filePath;
                is = new FileInputStream(rawPath);
            }
            if (is == null) return null;

            byte[] header = new byte[10];
            if (readFully(is, header, 10) != 10) return null;
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return null;

            int majorVersion = header[3] & 0xFF;
            int tagSize = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14)
                    | ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);
            if (tagSize <= 0 || tagSize > 5 * 1024 * 1024) return null;

            byte[] tag = new byte[tagSize];
            int read = readFully(is, tag, tagSize);
            if (read <= 0) return null;

            byte[] wanted = frameId.getBytes("ISO-8859-1");
            int pos = 0;
            while (pos + 10 <= read) {
                // A zero id byte marks the start of padding — stop scanning.
                if (tag[pos] == 0) break;

                int frameSize;
                if (majorVersion >= 4) {
                    frameSize = ((tag[pos + 4] & 0x7F) << 21) | ((tag[pos + 5] & 0x7F) << 14)
                            | ((tag[pos + 6] & 0x7F) << 7) | (tag[pos + 7] & 0x7F);
                } else {
                    frameSize = ((tag[pos + 4] & 0xFF) << 24) | ((tag[pos + 5] & 0xFF) << 16)
                            | ((tag[pos + 6] & 0xFF) << 8) | (tag[pos + 7] & 0xFF);
                }
                if (frameSize <= 0 || pos + 10 + frameSize > read) break;

                if (tag[pos] == wanted[0] && tag[pos + 1] == wanted[1]
                        && tag[pos + 2] == wanted[2] && tag[pos + 3] == wanted[3]) {
                    int dataStart = pos + 10;
                    int encoding = tag[dataStart] & 0xFF;
                    int textStart = dataStart + 1;
                    int textLen = frameSize - 1;
                    if (textLen <= 0) return null;

                    String charset;
                    switch (encoding) {
                        case 0: charset = "ISO-8859-1"; break;
                        case 1: charset = "UTF-16"; break;
                        case 2: charset = "UTF-16BE"; break;
                        default: charset = "UTF-8"; break;
                    }
                    String value = new String(tag, textStart, textLen, charset);
                    int nul = value.indexOf('\0');
                    if (nul >= 0) value = value.substring(0, nul);
                    value = value.trim();
                    return value.isEmpty() ? null : value;
                }
                pos += 10 + frameSize;
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) { }
            }
        }
    }

    private int readFully(InputStream is, byte[] buffer, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int n = is.read(buffer, total, length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }
}
