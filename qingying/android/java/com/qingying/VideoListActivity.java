package com.qingying;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoListActivity extends Activity {

    private final List<Video> videos = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private VideoAdapter adapter;
    private TextView countView;
    private TextView emptyText;
    private String bucketId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_list);

        bucketId = getIntent().getStringExtra("bucketId");
        String bucketName = getIntent().getStringExtra("bucketName");
        if (bucketName == null) bucketName = "视频";

        ((TextView) findViewById(R.id.title)).setText(bucketName);
        countView = findViewById(R.id.count);
        emptyText = findViewById(R.id.emptyText);
        findViewById(R.id.back).setOnClickListener(v -> finish());

        ListView list = findViewById(R.id.list);
        adapter = new VideoAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Video video = videos.get(position);
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("id", video.id);
            intent.putExtra("name", video.name);
            startActivity(intent);
        });

        loadVideos();
    }

    private void loadVideos() {
        executor.execute(() -> {
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
            if (bucketId == null || "0".equals(bucketId)) {
                selection = MediaStore.Video.Media.BUCKET_ID + " IS NULL";
                args = null;
            } else {
                selection = MediaStore.Video.Media.BUCKET_ID + "=?";
                args = new String[]{bucketId};
            }
            String sort = MediaStore.Video.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = getContentResolver().query(uri, projection, selection, args, sort)) {
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

            runOnUiThread(() -> {
                videos.clear();
                videos.addAll(result);
                adapter.notifyDataSetChanged();
                countView.setText(videos.size() + " 个视频");
                emptyText.setVisibility(videos.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private class VideoAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return videos.size();
        }

        @Override
        public Object getItem(int position) {
            return videos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null
                    ? convertView
                    : getLayoutInflater().inflate(R.layout.row_video, parent, false);
            Video video = videos.get(position);

            ((TextView) view.findViewById(R.id.name)).setText(video.name);
            TextView duration = view.findViewById(R.id.duration);
            if (video.durationMs > 0) {
                duration.setVisibility(View.VISIBLE);
                duration.setText(Fmt.duration(video.durationMs));
            } else {
                duration.setVisibility(View.GONE);
            }
            String meta = Fmt.size(video.size);
            ((TextView) view.findViewById(R.id.meta)).setText(meta);
            ThumbLoader.load(VideoListActivity.this, view.findViewById(R.id.thumb), video.id);
            return view;
        }
    }
}
