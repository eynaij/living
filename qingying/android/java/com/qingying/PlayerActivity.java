package com.qingying;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class PlayerActivity extends Activity implements SurfaceHolder.Callback {

    /** 长按右侧时的加速倍率。 */
    private static final float BOOST_SPEED = 3.0f;
    /** 倍速按钮可选档位。 */
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    /** 长按判定时长。 */
    private static final long LONG_PRESS_MS = 350;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MediaPlayer player;
    private SurfaceView surface;
    private View controls;
    private View badge;
    private TextView badgeText;
    private TextView titleView;
    private TextView timeView;
    private TextView playView;
    private TextView speedView;
    private TextView hint;
    private TextView favBtn;
    private ImageView lockBtn;
    private TextView prevBtn;
    private TextView nextBtn;
    private SeekBar seekBar;
    /** 单帧时长（毫秒），按视频帧率计算，取不到时按 25fps 处理。 */
    private long frameMs = 40;

    private View playlistPanel;
    private TextView playlistTitle;
    private PlaylistAdapter playlistAdapter;

    private final List<Video> playlist = new ArrayList<>();
    private int index;
    private long currentId;
    private String bucketId;
    private Uri videoUri;

    private float speed = 1f;
    /** 倍速是否已锁定：锁定后单击倍速按钮不再切换，长按可解锁。 */
    private boolean speedLocked;
    /** 锁定时显示在倍速文字左侧的白色线性小锁图标（与旋转锁定同款风格）。 */
    private Drawable speedLockIcon;
    private SharedPreferences prefs;
    private boolean prepared;
    private boolean boosting;
    private boolean rewinding;
    private boolean finished;
    private boolean rotationLocked;
    private int lastPosition;

    private float downX;
    private float downY;
    private long downAt;
    private boolean rightZone;
    private boolean leftZone;
    /** 双击判定窗口（毫秒）。 */
    private static final long DOUBLE_TAP_MS = 300;
    private long lastTapAt;
    private Runnable pendingTap;

    /** 横滑拖动进度：是否正在拖动、拖动起点位置、目标位置（毫秒）。 */
    private boolean scrubbing;
    private int scrubStartMs;
    private int scrubTargetMs;

    private final Runnable hideBadge = () -> badge.setVisibility(View.GONE);

    private final Runnable hideControls = () -> {
        if (player != null && player.isPlaying() && !boosting && !rewinding) {
            controls.setVisibility(View.GONE);
            lockBtn.setVisibility(View.GONE);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (prepared && player != null) {
                int position = player.getCurrentPosition();
                int duration = Math.max(1, player.getDuration());
                seekBar.setMax(duration);
                if (!scrubbing) {
                    seekBar.setProgress(position);
                    timeView.setText(Fmt.duration(position) + " / " + Fmt.duration(duration));
                }
                updatePlayIcon();
                ui.postDelayed(this, 400);
            }
        }
    };

    private final Runnable startBoost = () -> {
        if (player == null || !prepared || !player.isPlaying()) return;
        cancelPendingTap();
        boosting = true;
        applySpeed(BOOST_SPEED);
        showBadge("▶▶ 3x 快进", 0);
        controls.setVisibility(View.GONE);
    };

    private final Runnable rewindStep = new Runnable() {
        @Override
        public void run() {
            if (!rewinding || player == null || !prepared) return;
            int position = player.getCurrentPosition();
            seek(Math.max(0, position - 1200));
            ui.postDelayed(this, 130);
        }
    };

    private final Runnable startRewind = () -> {
        if (player == null || !prepared) return;
        cancelPendingTap();
        rewinding = true;
        showBadge("◀◀ 快退中", 0);
        ui.post(rewindStep);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        hideSystemBars();

        currentId = getIntent().getLongExtra("id", -1);
        bucketId = getIntent().getStringExtra("bucketId");
        String name = getIntent().getStringExtra("name");
        // 从“最近播放”进入时，恢复到上次观看位置
        lastPosition = (int) getIntent().getLongExtra("startMs", 0);
        videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, currentId);

        // 恢复上次的播放倍速与锁定状态
        prefs = getSharedPreferences("qingying_prefs", MODE_PRIVATE);
        speed = prefs.getFloat("speed", 1f);
        speedLocked = prefs.getBoolean("speedLocked", false);

        surface = findViewById(R.id.surface);
        controls = findViewById(R.id.controls);
        badge = findViewById(R.id.badge);
        titleView = findViewById(R.id.title);
        timeView = findViewById(R.id.time);
        playView = findViewById(R.id.playBtn);
        speedView = findViewById(R.id.speedBtn);
        hint = findViewById(R.id.hint);
        favBtn = findViewById(R.id.favBtn);
        lockBtn = findViewById(R.id.lockBtn);
        prevBtn = findViewById(R.id.prevBtn);
        nextBtn = findViewById(R.id.nextBtn);
        seekBar = findViewById(R.id.seekBar);
        playlistPanel = findViewById(R.id.playlistPanel);
        playlistTitle = findViewById(R.id.playlistTitle);

        titleView.setText(name != null ? name : "视频");
        setupSpeedLockIcon();
        refreshSpeedView();

        surface.getHolder().addCallback(this);
        // 屏幕尺寸或方向变化时重新按比例计算画面大小
        findViewById(R.id.root).addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, orr, ob) -> adjustVideoSize());
        findViewById(R.id.gesture).setOnTouchListener(this::onGesture);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        playView.setOnClickListener(v -> togglePlay());
        speedView.setOnClickListener(v -> cycleSpeed());
        speedView.setOnLongClickListener(v -> { toggleSpeedLock(); return true; });
        findViewById(R.id.back10).setOnClickListener(v -> jumpBy(-10_000));
        findViewById(R.id.forward10).setOnClickListener(v -> jumpBy(10_000));
        findViewById(R.id.framePrev).setOnClickListener(v -> stepFrame(-1));
        findViewById(R.id.frameNext).setOnClickListener(v -> stepFrame(1));
        prevBtn.setOnClickListener(v -> playAt(index - 1));
        nextBtn.setOnClickListener(v -> playAt(index + 1));
        favBtn.setOnClickListener(v -> toggleFavorite());
        findViewById(R.id.captureBtn).setOnClickListener(v -> captureFrame());
        lockBtn.setOnClickListener(v -> toggleRotationLock());
        findViewById(R.id.playlistClose).setOnClickListener(v -> hidePlaylist());

        ListView playlistView = findViewById(R.id.playlist);
        playlistAdapter = new PlaylistAdapter();
        playlistView.setAdapter(playlistAdapter);
        playlistView.setOnItemClickListener((parent, view, position, id) -> {
            hidePlaylist();
            playAt(position);
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    timeView.setText(Fmt.duration(progress) + " / " + Fmt.duration(bar.getMax()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                ui.removeCallbacks(hideControls);
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                seek(bar.getProgress());
                setControlsVisible(true);
            }
        });

        updateFavoriteIcon();
        loadPlaylist(name);

        ui.postDelayed(() -> hint.setVisibility(View.GONE), 5000);
        ui.postDelayed(hideControls, 4000);
    }

    // ------------------------------------------------------------------ 播放列表

    private void loadPlaylist(String currentName) {
        executor.execute(() -> {
            List<Video> result = VideoStore.queryVideos(this, bucketId);
            runOnUiThread(() -> {
                playlist.clear();
                playlist.addAll(result);
                index = -1;
                for (int i = 0; i < playlist.size(); i++) {
                    if (playlist.get(i).id == currentId) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) {
                    // 查询结果不含当前视频时，把当前视频插到最前，保证可以播放
                    Video current = new Video();
                    current.id = currentId;
                    current.name = currentName != null ? currentName : "视频";
                    playlist.add(0, current);
                    index = 0;
                }
                playlistAdapter.notifyDataSetChanged();
                updateNavButtons();
            });
        });
    }

    private void playAt(int newIndex) {
        if (newIndex < 0 || newIndex >= playlist.size() || newIndex == index) return;
        recordHistory();
        index = newIndex;
        Video video = playlist.get(index);
        currentId = video.id;
        videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, currentId);

        titleView.setText(video.name);
        updateFavoriteIcon();
        prepared = false;
        finished = false;
        lastPosition = 0;
        releasePlayer();
        seekBar.setProgress(0);
        timeView.setText("00:00 / 00:00");
        playlistAdapter.notifyDataSetChanged();
        updateNavButtons();
        if (surface.getHolder().getSurface().isValid()) {
            initPlayer(surface.getHolder());
        }
        setControlsVisible(true);
    }

    private void updateNavButtons() {
        prevBtn.setAlpha(index > 0 ? 1f : 0.3f);
        nextBtn.setAlpha(index < playlist.size() - 1 ? 1f : 0.3f);
        playlistTitle.setText("播放列表 " + (index + 1) + "/" + Math.max(1, playlist.size()));
    }

    private void showPlaylist() {
        playlistPanel.setVisibility(View.VISIBLE);
        controls.setVisibility(View.GONE);
        lockBtn.setVisibility(View.GONE);
        ui.removeCallbacks(hideControls);
        playlistAdapter.notifyDataSetChanged();
    }

    private void hidePlaylist() {
        playlistPanel.setVisibility(View.GONE);
        setControlsVisible(true);
    }

    private class PlaylistAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return playlist.size();
        }

        @Override
        public Object getItem(int position) {
            return playlist.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null
                    ? convertView
                    : getLayoutInflater().inflate(R.layout.row_playlist_item, parent, false);
            Video video = playlist.get(position);
            TextView name = view.findViewById(R.id.itemName);
            name.setText(video.name);
            boolean current = position == index;
            name.setTextColor(current ? 0xFF7FB4FF : 0xFFDDE5EE);
            view.findViewById(R.id.itemMark).setVisibility(current ? View.VISIBLE : View.GONE);
            return view;
        }
    }

    // ------------------------------------------------------------------ 收藏 & 旋转锁定

    private void toggleFavorite() {
        boolean favorite = FavoritesStore.toggle(this, currentId);
        updateFavoriteIcon();
        showBadge(favorite ? "已收藏 ★" : "已取消收藏", 900);
    }

    private void updateFavoriteIcon() {
        boolean favorite = FavoritesStore.isFavorite(this, currentId);
        favBtn.setText(favorite ? "★" : "☆");
        favBtn.setTextColor(favorite ? 0xFFFFC94D : 0xFFFFFFFF);
    }

    private void toggleRotationLock() {
        rotationLocked = !rotationLocked;
        if (rotationLocked) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            lockBtn.setImageResource(R.drawable.ic_lock_closed);
            showBadge("已锁定屏幕方向", 900);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            lockBtn.setImageResource(R.drawable.ic_lock_open);
            showBadge("已恢复自动旋转", 900);
        }
    }

    // ------------------------------------------------------------------ 截图

    /** 截取当前帧并保存到系统相册（Pictures/轻影）。 */
    private void captureFrame() {
        if (!prepared || player == null) {
            Toast.makeText(this, "视频还未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 200);
            Toast.makeText(this, "请允许存储权限后再截图", Toast.LENGTH_SHORT).show();
            return;
        }
        setControlsVisible(true);
        final long positionUs = player.getCurrentPosition() * 1000L;
        executor.execute(() -> {
            Bitmap bitmap = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, videoUri);
                bitmap = retriever.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST);
            } catch (Exception ignored) {
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }
            boolean saved = bitmap != null && saveFrame(bitmap);
            runOnUiThread(() -> Toast.makeText(this,
                    saved ? "截图已保存到相册" : "截图失败，请重试", Toast.LENGTH_SHORT).show());
        });
    }

    private boolean saveFrame(Bitmap bitmap) {
        String name = "QY_" + System.currentTimeMillis() + ".jpg";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/轻影");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return false;
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    return out != null && bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "轻影");
                if (!dir.exists() && !dir.mkdirs()) return false;
                File file = new File(dir, name);
                try (OutputStream out = new FileOutputStream(file)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) return false;
                }
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 播放器

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (player == null) {
            initPlayer(holder);
        } else if (prepared) {
            try {
                player.setDisplay(holder);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (player != null && prepared) {
            try {
                player.setDisplay(holder);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (player != null) {
            try {
                player.setDisplay(null);
            } catch (Exception ignored) {
            }
        }
    }

    private void initPlayer(SurfaceHolder holder) {
        detectFrameRate();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build());
            player.setDisplay(holder);
            player.setDataSource(this, videoUri);
            player.setOnVideoSizeChangedListener((p, width, height) -> adjustVideoSize());
            player.setOnPreparedListener(p -> {
                prepared = true;
                int duration = Math.max(1, p.getDuration());
                seekBar.setMax(duration);
                adjustVideoSize();
                if (lastPosition > 0 && lastPosition < duration - 2000) seek(lastPosition);
                p.start();
                // 开始播放就写入记录，即使马上退出也能在最近播放里看到
                recordHistory();
                finished = false;
                applySpeed(speed);
                updatePlayIcon();
                ui.post(tick);
                setControlsVisible(true);
            });
            player.setOnCompletionListener(p -> {
                finished = true;
                recordHistory();
                updatePlayIcon();
                if (index < playlist.size() - 1) {
                    showBadge("播放结束，即将播放下一个", 1200);
                    ui.postDelayed(() -> playAt(index + 1), 1300);
                } else {
                    showBadge("播放结束", 1200);
                }
                setControlsVisible(true);
            });
            player.setOnErrorListener((p, what, extra) -> {
                Toast.makeText(this, "无法播放该视频", Toast.LENGTH_SHORT).show();
                finish();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "无法播放该视频", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * 保持视频原始宽高比：按可用空间等比缩放并居中，多余部分留黑边，不拉伸变形。
     */
    private void adjustVideoSize() {
        if (player == null) return;
        int videoWidth;
        int videoHeight;
        try {
            videoWidth = player.getVideoWidth();
            videoHeight = player.getVideoHeight();
        } catch (Exception e) {
            return;
        }
        if (videoWidth <= 0 || videoHeight <= 0) return;

        View parent = (View) surface.getParent();
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        if (parentWidth <= 0 || parentHeight <= 0) return;

        float videoAspect = (float) videoWidth / videoHeight;
        float parentAspect = (float) parentWidth / parentHeight;

        int width;
        int height;
        if (videoAspect > parentAspect) {
            // 视频更宽：宽度占满，高度按比例（上下留黑边）
            width = parentWidth;
            height = Math.round(parentWidth / videoAspect);
        } else {
            // 视频更高：高度占满，宽度按比例（左右留黑边）
            height = parentHeight;
            width = Math.round(parentHeight * videoAspect);
        }

        ViewGroup.LayoutParams params = surface.getLayoutParams();
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            surface.setLayoutParams(params);
        }
    }

    private void releasePlayer() {
        ui.removeCallbacks(tick);
        cancelLongPress();
        stopBoost();
        stopRewind();
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private void togglePlay() {
        if (!prepared || player == null) return;
        if (player.isPlaying()) {
            player.pause();
            setControlsVisible(true);
        } else {
            if (finished) {
                seek(0);
                finished = false;
            }
            player.start();
            applySpeed(speed);
            setControlsVisible(true);
            ui.postDelayed(hideControls, 3000);
        }
        updatePlayIcon();
    }

    private void jumpBy(int deltaMs) {
        if (!prepared || player == null) return;
        int target = player.getCurrentPosition() + deltaMs;
        target = Math.max(0, Math.min(player.getDuration(), target));
        seek(target);
        showBadge(deltaMs > 0 ? "+10s" : "−10s", 700);
        setControlsVisible(true);
    }

    /** 逐帧步进：自动暂停，按帧率前进/后退一帧。 */
    private void stepFrame(int direction) {
        if (!prepared || player == null) return;
        if (player.isPlaying()) {
            player.pause();
            updatePlayIcon();
        }
        long target = player.getCurrentPosition() + direction * frameMs;
        target = Math.max(0, Math.min(player.getDuration(), target));
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                player.seekTo((int) target, MediaPlayer.SEEK_CLOSEST);
            } else {
                player.seekTo((int) target);
            }
        } catch (Exception ignored) {
        }
        seekBar.setProgress((int) target);
        timeView.setText(Fmt.duration((int) target) + " / " + Fmt.duration(player.getDuration()));
        showBadge("逐帧 · " + Fmt.duration((int) target), 700);
        setControlsVisible(true);
    }

    /** 读取视频帧率，计算单帧时长。 */
    private void detectFrameRate() {
        frameMs = 40;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, videoUri);
            String fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            if (fps != null) {
                float value = Float.parseFloat(fps);
                if (value > 0) frameMs = Math.max(1, Math.round(1000f / value));
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void seek(int position) {
        if (player == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                player.seekTo(position, MediaPlayer.SEEK_PREVIOUS_SYNC);
            } else {
                player.seekTo(position);
            }
        } catch (Exception ignored) {
        }
    }

    private void applySpeed(float value) {
        if (player == null || !prepared) return;
        try {
            if (player.isPlaying()) {
                PlaybackParams params = player.getPlaybackParams();
                params.setSpeed(value);
                player.setPlaybackParams(params);
            }
        } catch (Exception ignored) {
            // 个别设备不支持变速播放，忽略
        }
    }

    private void cycleSpeed() {
        if (speedLocked) {
            Toast.makeText(this, "倍速已锁定，长按倍速按钮解锁", Toast.LENGTH_SHORT).show();
            setControlsVisible(true);
            return;
        }
        int index = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < SPEEDS.length; i++) {
            float diff = Math.abs(SPEEDS[i] - speed);
            if (diff < best) {
                best = diff;
                index = i;
            }
        }
        speed = SPEEDS[(index + 1) % SPEEDS.length];
        refreshSpeedView();
        if (player != null && player.isPlaying()) applySpeed(speed);
        showBadge("倍速 " + speedText(speed), 900);
        persistSpeed();
        setControlsVisible(true);
    }

    /** 初始化倍速锁定小锁图标：白色线性、透明填充，与旋转锁定同款风格。 */
    private void setupSpeedLockIcon() {
        speedLockIcon = getDrawable(R.drawable.ic_lock_closed);
        int s = dp(14);
        speedLockIcon.setBounds(0, 0, s, s);
        speedView.setCompoundDrawablePadding(dp(4));
    }

    /** 刷新倍速按钮：锁定时在文字左侧显示小锁图标，解锁时隐藏。 */
    private void refreshSpeedView() {
        speedView.setText(speedText(speed));
        speedView.setCompoundDrawables(speedLocked ? speedLockIcon : null, null, null, null);
    }

    /** 持久化当前倍速。 */
    private void persistSpeed() {
        if (prefs != null) prefs.edit().putFloat("speed", speed).apply();
    }

    /** 长按倍速按钮：切换倍速锁定状态。 */
    private void toggleSpeedLock() {
        speedLocked = !speedLocked;
        if (prefs != null) prefs.edit().putBoolean("speedLocked", speedLocked).apply();
        refreshSpeedView();
        showBadge(speedLocked ? "倍速已锁定" : "倍速已解锁", 900);
        setControlsVisible(true);
    }

    private String speedText(float value) {
        String text = String.valueOf(value);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + "x";
    }

    private void updatePlayIcon() {
        boolean playing = player != null && player.isPlaying();
        playView.setText(playing ? "❚❚" : "▶");
    }

    // ------------------------------------------------------------------ 手势

    private boolean onGesture(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downAt = SystemClock.uptimeMillis();
                rightZone = inZone(view, event, true);
                leftZone = inZone(view, event, false);
                if (rightZone) ui.postDelayed(startBoost, LONG_PRESS_MS);
                else if (leftZone) ui.postDelayed(startRewind, LONG_PRESS_MS);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!scrubbing && prepared && player != null
                        && Math.abs(dx) > dp(24) && Math.abs(dx) > Math.abs(dy)) {
                    // 横向滑动优先：进入拖动进度模式，取消长按和点击判定
                    scrubbing = true;
                    scrubStartMs = player.getCurrentPosition();
                    scrubTargetMs = scrubStartMs;
                    cancelLongPress();
                    cancelPendingTap();
                    setControlsVisible(true);
                }
                if (scrubbing) {
                    updateScrub(view, dx);
                } else if (Math.abs(dx) > dp(36) || Math.abs(dy) > dp(36)) {
                    cancelLongPress();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean acted = boosting || rewinding || scrubbing;
                cancelLongPress();
                stopBoost();
                stopRewind();
                if (scrubbing) finishScrub();
                if (!acted && SystemClock.uptimeMillis() - downAt < 250) {
                    if (playlistPanel.getVisibility() == View.VISIBLE) {
                        hidePlaylist();
                    } else {
                        handleTap(leftZone);
                    }
                }
                return true;

            default:
                return false;
        }
    }

    /** 横滑过程中根据位移计算目标位置并实时预览。 */
    private void updateScrub(View view, float dx) {
        if (player == null) return;
        int duration = Math.max(1, player.getDuration());
        int width = Math.max(1, view.getWidth());
        // 滑满整屏对应时长的 1/5，最长 120 秒，长短视频都能比较精细地拖动
        long fullWidthMs = Math.max(1000L, Math.min(duration / 5L, 120_000L));
        long delta = (long) (dx / width * fullWidthMs);
        scrubTargetMs = (int) Math.max(0, Math.min(duration, scrubStartMs + delta));
        seekBar.setProgress(scrubTargetMs);
        String arrow = delta >= 0 ? "▶ +" : "◀ −";
        showBadge(arrow + Fmt.duration(Math.abs((int) delta)) + "\n"
                + Fmt.duration(scrubTargetMs) + " / " + Fmt.duration(duration), 0);
    }

    /** 松手后正式跳转到目标位置。 */
    private void finishScrub() {
        scrubbing = false;
        seek(scrubTargetMs);
        showBadge("已定位 " + Fmt.duration(scrubTargetMs), 700);
        if (finished && player != null && scrubTargetMs < player.getDuration() - 500) {
            finished = false;
            player.start();
            updatePlayIcon();
        }
    }

    /** 单击延迟判定：300ms 内再来一次点击即为双击，切换播放/暂停。 */
    private void handleTap(boolean left) {
        long now = SystemClock.uptimeMillis();
        if (pendingTap != null && now - lastTapAt < DOUBLE_TAP_MS) {
            cancelPendingTap();
            lastTapAt = 0;
            togglePlay();
            return;
        }
        lastTapAt = now;
        pendingTap = () -> {
            pendingTap = null;
            if (left) showPlaylist();
            else setControlsVisible(controls.getVisibility() != View.VISIBLE);
        };
        ui.postDelayed(pendingTap, DOUBLE_TAP_MS);
    }

    private void cancelPendingTap() {
        if (pendingTap != null) {
            ui.removeCallbacks(pendingTap);
            pendingTap = null;
        }
    }

    /** 左右两侧中间区域：长按右侧加速，长按左侧快退，点击左侧打开播放列表。 */
    private boolean inZone(View view, MotionEvent event, boolean right) {
        float width = view.getWidth();
        float height = view.getHeight();
        if (width <= 0 || height <= 0) return false;
        float x = event.getX();
        float y = event.getY();
        if (y < height * 0.26f || y > height * 0.76f) return false;
        return right ? x > width * 0.60f : x < width * 0.40f;
    }

    private void cancelLongPress() {
        ui.removeCallbacks(startBoost);
        ui.removeCallbacks(startRewind);
    }

    private void stopBoost() {
        if (!boosting) return;
        boosting = false;
        ui.removeCallbacks(hideBadge);
        badge.setVisibility(View.GONE);
        applySpeed(speed);
    }

    private void stopRewind() {
        if (!rewinding) return;
        rewinding = false;
        ui.removeCallbacks(rewindStep);
        ui.removeCallbacks(hideBadge);
        badge.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ 界面

    private void setControlsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        controls.setVisibility(visibility);
        lockBtn.setVisibility(visibility);
        ui.removeCallbacks(hideControls);
        if (visible) ui.postDelayed(hideControls, 4000);
    }

    private void showBadge(String text, long autoHideMs) {
        badgeText = (TextView) badge;
        badgeText.setText(text);
        badge.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideBadge);
        if (autoHideMs > 0) ui.postDelayed(hideBadge, autoHideMs);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // ------------------------------------------------------------------ 生命周期

    @Override
    protected void onResume() {
        super.onResume();
        // 个别机型 surfaceCreated 可能早于回调注册，这里兜底初始化
        if (player == null && surface != null && surface.getHolder().getSurface().isValid()) {
            initPlayer(surface.getHolder());
        }
    }

    /** 记录当前观看位置，供“最近播放”列表和续播使用。 */
    private void recordHistory() {
        if (player == null || !prepared || currentId < 0) return;
        try {
            PlayHistoryStore.record(this, currentId, player.getCurrentPosition(), player.getDuration());
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && prepared) {
            lastPosition = player.getCurrentPosition();
            recordHistory();
            if (player.isPlaying()) player.pause();
        }
        ui.removeCallbacks(hideControls);
        setControlsVisible(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recordHistory();
        ui.removeCallbacksAndMessages(null);
        releasePlayer();
        executor.shutdownNow();
    }
}
