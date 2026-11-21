package com.qingying;

import android.content.Context;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 置顶的文件夹 bucketId，存在 SharedPreferences。 */
final class PinnedFolders {

    private static final String PREFS = "pinned_folders";
    private static final String KEY_IDS = "ids";

    private PinnedFolders() { }

    static Set<String> get(Context context) {
        return new HashSet<>(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_IDS, Collections.emptySet()));
    }

    static boolean isPinned(Context context, String bucketId) {
        return get(context).contains(bucketId);
    }

    /** 切换置顶状态，返回切换后是否已置顶。 */
    static boolean toggle(Context context, String bucketId) {
        Set<String> set = get(context);
        boolean added;
        if (set.contains(bucketId)) {
            set.remove(bucketId);
            added = false;
        } else {
            set.add(bucketId);
            added = true;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_IDS, set).apply();
        return added;
    }
}
