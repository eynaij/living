package com.qingying;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
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
        // 浅色主题下导航栏按钮用深色
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

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
            intent.putExtra("bucketId", bucketId);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadVideos();
    }

    private void loadVideos() {
        executor.execute(() -> {
            List<Video> result = VideoStore.queryVideos(this, bucketId);
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
            ((TextView) view.findViewById(R.id.meta)).setText(Fmt.size(video.size));

            TextView star = view.findViewById(R.id.star);
            updateStar(star, FavoritesStore.isFavorite(VideoListActivity.this, video.id));
            star.setOnClickListener(v -> {
                boolean favorite = FavoritesStore.toggle(VideoListActivity.this, video.id);
                updateStar(star, favorite);
            });

            ThumbLoader.load(VideoListActivity.this, view.findViewById(R.id.thumb), video.id);
            return view;
        }

        private void updateStar(TextView star, boolean favorite) {
            star.setText(favorite ? "★" : "☆");
            star.setTextColor(favorite ? 0xFF14B8A6 : 0xFFB9C6CF);
        }
    }
}
