package com.qingying;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_PERM = 100;

    private final List<Folder> folders = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FolderAdapter adapter;
    private LinearLayout empty;
    private TextView emptyText;
    private TextView grantBtn;
    private TextView folderCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 浅色主题下导航栏按钮用深色
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        ListView list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        emptyText = findViewById(R.id.emptyText);
        grantBtn = findViewById(R.id.grantBtn);
        folderCount = findViewById(R.id.folderCount);

        adapter = new FolderAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Folder folder = folders.get(position);
            Intent intent = new Intent(MainActivity.this, VideoListActivity.class);
            intent.putExtra("bucketId", folder.bucketId);
            intent.putExtra("bucketName", folder.name);
            startActivity(intent);
        });

        grantBtn.setOnClickListener(v -> requestPermission());

        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Folder folder = folders.get(position);
            if (VideoStore.FAVORITES_BUCKET.equals(folder.bucketId)
                    || VideoStore.HISTORY_BUCKET.equals(folder.bucketId)) return true;
            boolean pinned = PinnedFolders.toggle(this, folder.bucketId);
            Toast.makeText(this,
                    pinned ? "已置顶「" + folder.name + "」" : "已取消置顶「" + folder.name + "」",
                    Toast.LENGTH_SHORT).show();
            loadFolders();
            return true;
        });

        if (hasPermission()) loadFolders();
        else requestPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermission()) loadFolders();
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            // Android 14+ 用户可能只授权了“选择的照片和视频”
            return Build.VERSION.SDK_INT >= 34
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        requestPermissions(new String[]{permission}, REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PERM) {
            if (hasPermission()) loadFolders();
            else showEmpty("需要视频访问权限才能读取视频", true);
        }
    }

    private void loadFolders() {
        executor.execute(() -> {
            Map<String, Folder> map = new LinkedHashMap<>();
            Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.BUCKET_ID,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME
            };
            String sort = MediaStore.Video.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = getContentResolver().query(uri, projection, null, null, sort)) {
                if (cursor != null) {
                    int iId = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    int iDuration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                    int iBucket = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID);
                    int iBucketName = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);

                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(iId);
                        long duration = cursor.isNull(iDuration) ? 0 : cursor.getLong(iDuration);
                        String bucketId = cursor.isNull(iBucket) ? "0" : cursor.getString(iBucket);
                        String bucketName = cursor.isNull(iBucketName) ? "" : cursor.getString(iBucketName);

                        Folder folder = map.get(bucketId);
                        if (folder == null) {
                            folder = new Folder();
                            folder.bucketId = bucketId;
                            folder.name = bucketName == null || bucketName.trim().isEmpty()
                                    ? "其他视频" : bucketName;
                            folder.coverId = id;
                            map.put(bucketId, folder);
                        }
                        folder.count++;
                        folder.totalMs += Math.max(0, duration);
                    }
                }
            } catch (Exception ignored) {
                // 查询失败时展示空状态
            }

            List<Folder> result = new ArrayList<>();

            // 最近播放：有记录时固定显示在最顶部
            List<String> historyIds = PlayHistoryStore.orderedIds(this);
            if (!historyIds.isEmpty()) {
                Folder history = new Folder();
                history.bucketId = VideoStore.HISTORY_BUCKET;
                history.name = "最近播放";
                history.count = historyIds.size();
                try {
                    history.coverId = Long.parseLong(historyIds.get(0));
                } catch (NumberFormatException ignored) {
                }
                result.add(history);
            }

            // 收藏夹固定在顶部（最近播放之后）
            Folder favorites = new Folder();
            favorites.bucketId = VideoStore.FAVORITES_BUCKET;
            favorites.name = "我的收藏";
            favorites.count = FavoritesStore.get(this).size();
            favorites.coverId = -1;
            for (String id : FavoritesStore.get(this)) {
                try {
                    favorites.coverId = Long.parseLong(id);
                    break;
                } catch (NumberFormatException ignored) {
                }
            }
            result.add(favorites);

            // 置顶文件夹排在收藏夹之后，其余保持原顺序
            Set<String> pinned = PinnedFolders.get(this);
            for (Folder folder : map.values()) {
                if (pinned.contains(folder.bucketId)) result.add(folder);
            }
            for (Folder folder : map.values()) {
                if (!pinned.contains(folder.bucketId)) result.add(folder);
            }

            runOnUiThread(() -> {
                folders.clear();
                folders.addAll(result);
                adapter.notifyDataSetChanged();
                if (folders.isEmpty()) {
                    showEmpty(hasPermission() ? "没有找到视频" : "需要视频访问权限才能读取视频",
                            !hasPermission());
                } else {
                    empty.setVisibility(View.GONE);
                    folderCount.setText("共 " + map.size() + " 个文件夹 · 长按可置顶");
                }
            });
        });
    }

    private void showEmpty(String text, boolean showGrant) {
        empty.setVisibility(View.VISIBLE);
        emptyText.setText(text);
        grantBtn.setVisibility(showGrant ? View.VISIBLE : View.GONE);
        folderCount.setText("");
    }

    private class FolderAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return folders.size();
        }

        @Override
        public Object getItem(int position) {
            return folders.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null
                    ? convertView
                    : getLayoutInflater().inflate(R.layout.row_folder, parent, false);
            Folder folder = folders.get(position);

            ((TextView) view.findViewById(R.id.name)).setText(folder.name);
            String meta = folder.count + " 个视频";
            if (folder.totalMs > 0) meta += " · " + Fmt.duration(folder.totalMs);
            ((TextView) view.findViewById(R.id.meta)).setText(meta);
            view.findViewById(R.id.pin).setVisibility(
                    !VideoStore.FAVORITES_BUCKET.equals(folder.bucketId)
                            && PinnedFolders.isPinned(MainActivity.this, folder.bucketId)
                            ? View.VISIBLE : View.GONE);
            if (folder.coverId > 0) {
                ThumbLoader.load(MainActivity.this, view.findViewById(R.id.thumb), folder.coverId);
            } else {
                view.findViewById(R.id.thumb).setTag(null);
                ((android.widget.ImageView) view.findViewById(R.id.thumb))
                        .setImageResource(R.drawable.ic_video);
            }
            return view;
        }
    }
}
