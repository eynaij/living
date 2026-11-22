package com.qingying;

import android.content.Context;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 收藏的视频 ID，存在 SharedPreferences。 */
final class FavoritesStore {

    private static final String PREFS = "favorites";
    private static final String KEY_IDS = "ids";

    private FavoritesStore() { }

    static Set<String> get(Context context) {
        return new HashSet<>(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_IDS, Collections.emptySet()));
    }

    static boolean isFavorite(Context context, long videoId) {
        return get(context).contains(String.valueOf(videoId));
    }

    /** 切换收藏状态，返回切换后是否已收藏。 */
    static boolean toggle(Context context, long videoId) {
        Set<String> set = get(context);
        String key = String.valueOf(videoId);
        boolean added;
        if (set.contains(key)) {
            set.remove(key);
            added = false;
        } else {
            set.add(key);
            added = true;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_IDS, set).apply();
        return added;
    }
}
