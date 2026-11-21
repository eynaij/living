package com.qingying;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** 播放记录：视频 ID → 观看位置/时长/最近播放时间，本地 SharedPreferences 保存，最多 100 条。 */
final class PlayHistoryStore {

    private static final String PREFS = "play_history";
    private static final String KEY_JSON = "json";
    private static final int MAX_ENTRIES = 100;

    private PlayHistoryStore() { }

    static synchronized void record(Context context, long id, long positionMs, long durationMs) {
        try {
            JSONObject root = read(context);
            JSONObject item = new JSONObject();
            item.put("pos", positionMs);
            item.put("dur", durationMs);
            item.put("at", System.currentTimeMillis());
            root.put(String.valueOf(id), item);
            trim(root);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_JSON, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static long position(Context context, long id) {
        long[] info = info(context, id);
        return info == null ? 0 : info[0];
    }

    /** 返回 [观看位置, 视频时长, 最近播放时间]；无记录返回 null。 */
    static long[] info(Context context, long id) {
        JSONObject item = read(context).optJSONObject(String.valueOf(id));
        if (item == null) return null;
        return new long[]{item.optLong("pos"), item.optLong("dur"), item.optLong("at")};
    }

    static int count(Context context) {
        return read(context).length();
    }

    /** 按最近播放时间倒序的视频 ID。 */
    static List<String> orderedIds(Context context) {
        return ordered(read(context));
    }

    private static List<String> ordered(JSONObject root) {
        List<String> ids = new ArrayList<>();
        for (Iterator<String> it = root.keys(); it.hasNext(); ) ids.add(it.next());
        Collections.sort(ids, (a, b) -> Long.compare(at(root, b), at(root, a)));
        return ids;
    }

    private static long at(JSONObject root, String id) {
        JSONObject item = root.optJSONObject(id);
        return item == null ? 0 : item.optLong("at");
    }

    private static JSONObject read(Context context) {
        try {
            String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_JSON, "{}");
            return new JSONObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 只保留最近 MAX_ENTRIES 条。 */
    private static void trim(JSONObject root) {
        List<String> ids = ordered(root);
        for (int i = MAX_ENTRIES; i < ids.size(); i++) root.remove(ids.get(i));
    }
}
