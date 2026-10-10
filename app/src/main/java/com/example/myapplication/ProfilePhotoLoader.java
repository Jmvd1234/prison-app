package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small asynchronous thumbnail cache; stale requests cannot paint a recycled row. */
final class ProfilePhotoLoader {
    private static final ExecutorService READS = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };

    static void show(ImageView view, String path, int placeholder) {
        Object request = new Object();
        view.setTag(R.id.profile_photo_request, request);
        view.setImageResource(placeholder);
        if (path == null || path.isEmpty()) return;
        Bitmap cached = CACHE.get(path);
        if (cached != null) { view.setImageBitmap(cached); return; }
        Context context = view.getContext().getApplicationContext();
        WeakReference<ImageView> reference = new WeakReference<>(view);
        READS.execute(() -> {
            Bitmap loaded;
            try { loaded = ProfilePhotoStore.load(context, path); }
            catch (RuntimeException error) { loaded = null; }
            Bitmap photo = loaded;
            if (photo != null) CACHE.put(path, photo);
            MAIN.post(() -> {
                ImageView target = reference.get();
                if (target != null && target.getTag(R.id.profile_photo_request) == request && photo != null) {
                    target.setImageBitmap(photo);
                }
            });
        });
    }

    static void evict(String path) { if (path != null) CACHE.remove(path); }
}
