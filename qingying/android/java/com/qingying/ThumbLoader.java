package com.qingying;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 异步加载视频缩略图，带内存缓存，避免列表滑动卡顿。 */
final class ThumbLoader {

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ThumbLoader() { }

    static void load(Context context, ImageView view, long videoId) {
        final String key = String.valueOf(videoId);
        view.setTag(key);

        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(R.drawable.ic_video);

        final Context app = context.getApplicationContext();
        POOL.execute(() -> {
            Bitmap bitmap = null;
            try {
                Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId);
                if (Build.VERSION.SDK_INT >= 29) {
                    bitmap = app.getContentResolver().loadThumbnail(uri, new Size(256, 256), null);
                } else {
                    bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                            app.getContentResolver(), videoId,
                            MediaStore.Video.Thumbnails.MINI_KIND, null);
                }
            } catch (Exception ignored) {
                // 缩略图失败时保留占位图
            }
            final Bitmap result = bitmap;
            if (result != null) CACHE.put(key, result);
            MAIN.post(() -> {
                if (key.equals(view.getTag()) && result != null) {
                    view.setImageBitmap(result);
                }
            });
        });
    }
}
