package com.identixia.facerecognitionsdk.ui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;

import com.identixia.facerecognitionsdk.FaceBox;

import java.io.IOException;
import java.io.InputStream;

public class Utils {

    /**
     * Crop a face from {@code src} using the engine box ({@code x1..y2} in source pixels).
     * Pads both axes (like Windows {@code _face_jpeg_b64}) so chin/forehead are not clipped,
     * and shifts the window into bounds instead of shrinking asymmetrically.
     */
    public static Bitmap cropFace(Bitmap src, FaceBox faceBox) {
        if (src == null || src.isRecycled() || faceBox == null) return null;
        int boxW = Math.max(1, faceBox.x2 - faceBox.x1);
        int boxH = Math.max(1, faceBox.y2 - faceBox.y1);
        float padX = boxW * 0.20f;
        float padY = boxH * 0.20f;
        float left = faceBox.x1 - padX;
        float top = faceBox.y1 - padY;
        float right = faceBox.x2 + padX;
        float bottom = faceBox.y2 + padY;

        int iw = src.getWidth();
        int ih = src.getHeight();
        float cropW = right - left;
        float cropH = bottom - top;
        if (cropW > iw) {
            left = 0;
            cropW = iw;
        } else {
            left = Math.max(0f, Math.min(left, iw - cropW));
        }
        if (cropH > ih) {
            top = 0;
            cropH = ih;
        } else {
            top = Math.max(0f, Math.min(top, ih - cropH));
        }

        int x = Math.round(left);
        int y = Math.round(top);
        int w = Math.max(1, Math.min(iw - x, Math.round(cropW)));
        int h = Math.max(1, Math.min(ih - y, Math.round(cropH)));
        if (w <= 1 || h <= 1) return null;

        try {
            return Bitmap.createBitmap(src, x, y, w, h);
        } catch (Exception e) {
            return null;
        }
    }

    /** Landmark xy mapped into the same crop window as {@link #cropFace} (no forced 200×200). */
    public static float[] mapLandmarksToCrop(Bitmap src, FaceBox faceBox, int outW, int outH) {
        int n = Math.max(0, Math.min(faceBox.landmarkCount, faceBox.landmarks_68.length / 2));
        float[] out = new float[n * 2];
        if (src == null || src.isRecycled() || n == 0 || outW <= 0 || outH <= 0) return out;
        int boxW = Math.max(1, faceBox.x2 - faceBox.x1);
        int boxH = Math.max(1, faceBox.y2 - faceBox.y1);
        float padX = boxW * 0.20f;
        float padY = boxH * 0.20f;
        float left = faceBox.x1 - padX;
        float top = faceBox.y1 - padY;
        float right = faceBox.x2 + padX;
        float bottom = faceBox.y2 + padY;
        int iw = src.getWidth();
        int ih = src.getHeight();
        float cropW = right - left;
        float cropH = bottom - top;
        if (cropW > iw) {
            left = 0;
            cropW = iw;
        } else {
            left = Math.max(0f, Math.min(left, iw - cropW));
        }
        if (cropH > ih) {
            top = 0;
            cropH = ih;
        } else {
            top = Math.max(0f, Math.min(top, ih - cropH));
        }
        if (cropW < 1f || cropH < 1f) return out;
        float sx = outW / cropW;
        float sy = outH / cropH;
        for (int i = 0; i < n; i++) {
            out[i * 2] = (faceBox.landmarks_68[i * 2] - left) * sx;
            out[i * 2 + 1] = (faceBox.landmarks_68[i * 2 + 1] - top) * sy;
        }
        return out;
    }

    public static int getOrientation(Context context, Uri photoUri) {
        Cursor cursor = context.getContentResolver().query(
                photoUri,
                new String[]{MediaStore.Images.ImageColumns.ORIENTATION},
                null, null, null);
        if (cursor == null) return -1;
        try {
            if (cursor.getCount() != 1) return -1;
            cursor.moveToFirst();
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    public static Bitmap getCorrectlyOrientedImage(Context context, Uri photoUri) throws IOException {
        InputStream is = context.getContentResolver().openInputStream(photoUri);
        BitmapFactory.Options dbo = new BitmapFactory.Options();
        dbo.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, dbo);
        if (is != null) is.close();

        int orientation = getOrientation(context, photoUri);

        is = context.getContentResolver().openInputStream(photoUri);
        Bitmap srcBitmap = BitmapFactory.decodeStream(is);
        if (is != null) is.close();
        if (srcBitmap == null) {
            throw new IOException("Could not decode image");
        }

        if (orientation > 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(orientation);
            srcBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth(), srcBitmap.getHeight(), matrix, true);
        }
        return srcBitmap;
    }
}
