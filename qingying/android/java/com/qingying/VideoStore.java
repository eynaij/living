package com.qingying;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 视频查询与按名称排序，列表页和播放页共用。 */
final class VideoStore {

    static final String FAVORITES_BUCKET = "__favorites__";

    private VideoStore() { }

    static List<Video> queryVideos(Context context, String bucketId) {
        List<Video> result = new ArrayList<>();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
        };
        String selection;
        String[] args;

        if (FAVORITES_BUCKET.equals(bucketId)) {
            Set<String> favorites = FavoritesStore.get(context);
            if (favorites.isEmpty()) return result;
            StringBuilder sb = new StringBuilder(MediaStore.Video.Media._ID).append(" IN (");
            args = favorites.toArray(new String[0]);
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(',');
                sb.append('?');
            }
            selection = sb.append(')').toString();
        } else if (bucketId == null || "0".equals(bucketId)) {
            selection = MediaStore.Video.Media.BUCKET_ID + " IS NULL";
            args = null;
        } else {
            selection = MediaStore.Video.Media.BUCKET_ID + "=?";
            args = new String[]{bucketId};
        }

        String sort = MediaStore.Video.Media.DATE_ADDED + " DESC";
        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, args, sort)) {
            if (cursor != null) {
                int iId = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int iName = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int iDuration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int iSize = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                while (cursor.moveToNext()) {
                    Video video = new Video();
                    video.id = cursor.getLong(iId);
                    video.name = cursor.isNull(iName) ? "未命名视频" : cursor.getString(iName);
                    video.durationMs = cursor.isNull(iDuration) ? 0 : cursor.getLong(iDuration);
                    video.size = cursor.isNull(iSize) ? 0 : cursor.getLong(iSize);
                    result.add(video);
                }
            }
        } catch (Exception ignored) {
        }

        sortByName(result);
        return result;
    }

    /** 按名称排序，数字按数值比较（第 2 集排在第 10 集前面）。 */
    static void sortByName(List<Video> videos) {
        Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(videos, (a, b) -> compareNatural(a.name, b.name, collator));
    }

    private static int compareNatural(String a, String b, Collator collator) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String numA = stripZeros(a.substring(startA, i));
                String numB = stripZeros(b.substring(startB, j));
                if (numA.length() != numB.length()) return numA.length() - numB.length();
                int cmp = numA.compareTo(numB);
                if (cmp != 0) return cmp;
            } else {
                int cmp = collator.compare(String.valueOf(ca), String.valueOf(cb));
                if (cmp != 0) return cmp;
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    private static String stripZeros(String value) {
        int i = 0;
        while (i < value.length() - 1 && value.charAt(i) == '0') i++;
        return value.substring(i);
    }
}
