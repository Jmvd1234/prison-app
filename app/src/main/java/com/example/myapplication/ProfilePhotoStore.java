package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import com.example.myapplication.db.AppDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** App-owned, bounded-size photos. Room stores the relative filename, never image bytes. */
final class ProfilePhotoStore {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    static final int MAX_EDGE = 768;
    private static final long MAX_INPUT_BYTES = 32L * 1024 * 1024;

    private ProfilePhotoStore() {}

    static String importPhoto(Context context, Uri uri) throws IOException {
        File input = File.createTempFile("profile-import-", ".tmp", context.getCacheDir());
        String path = "profile_photos/" + UUID.randomUUID() + ".jpg";
        File output = resolve(context, path);
        Bitmap bitmap = null;
        boolean complete = false;
        try {
            try (InputStream source = context.getContentResolver().openInputStream(uri);
                 FileOutputStream target = new FileOutputStream(input)) {
                if (source == null) throw new IOException("Photo unavailable");
                byte[] buffer = new byte[16384];
                long total = 0;
                int count;
                while ((count = source.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_INPUT_BYTES) throw new IOException("Photo too large");
                    target.write(buffer, 0, count);
                }
            }
            if (Build.VERSION.SDK_INT >= 28) {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(input), (decoder, info, source) -> {
                    int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                    float scale = Math.min(1f, MAX_EDGE / (float) Math.max(width, height));
                    decoder.setTargetSize(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
                bitmap = decodeSized(input, MAX_EDGE);
                if (bitmap != null) bitmap = orient(bitmap, input);
            }
            if (bitmap == null) throw new IOException("Not a supported image");
            // Flatten transparent images onto white and remove source metadata.
            Bitmap flattened = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(flattened);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap, 0, 0, null);
            bitmap.recycle();
            bitmap = flattened;
            File directory = output.getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Photo storage unavailable");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)) throw new IOException("Photo encoding failed");
            }
            complete = true;
            return path;
        } finally {
            if (bitmap != null) bitmap.recycle();
            input.delete();
            if (!complete && output != null) output.delete();
        }
    }

    static File resolve(Context context, String path) {
        // Only our generated filenames are accepted; never delete arbitrary supplied paths.
        if (path == null || !path.matches("profile_photos/[0-9a-fA-F-]{36}\\.jpg")) return null;
        return new File(context.getFilesDir(), path);
    }

    static Bitmap load(Context context, String path) {
        File file = resolve(context, path);
        return file != null && file.isFile() ? decodeSized(file, 512) : null;
    }

    private static Bitmap decodeSized(File file, int edge) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > edge * 2) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) return null;
        float scale = Math.min(1f, edge / (float) Math.max(bitmap.getWidth(), bitmap.getHeight()));
        if (scale < 1f) {
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
            if (resized != bitmap) bitmap.recycle();
            bitmap = resized;
        }
        return bitmap;
    }

    private static Bitmap orient(Bitmap bitmap, File file) {
        int orientation;
        try {
            orientation = new ExifInterface(file.getAbsolutePath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) { return bitmap; }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
            default: return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    /** Run on IO, after the DB operation succeeds. Also protects restored/shared references. */
    static void deleteIfUnused(Context context, String path) {
        File file = resolve(context, path);
        if (file != null && AppDatabase.getInstance(context).ProfileDao().countProfilesWithPhoto(path) == 0) {
            file.delete();
            ProfilePhotoLoader.evict(path);
        }
    }
}
