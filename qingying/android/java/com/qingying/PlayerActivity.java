package com.qingying;

import android.app.Activity;
import android.content.ContentUris;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerActivity extends Activity implements SurfaceHolder.Callback {

    /** 长按右侧时的加速倍率。 */
    private static final float BOOST_SPEED = 3.0f;
    /** 倍速按钮可选档位。 */
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    /** 长按判定时长。 */
    private static final long LONG_PRESS_MS = 350;

    private final Handler ui = new Handler(Looper.getMainLooper());

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
    private SeekBar seekBar;

    private Uri videoUri;
    private float speed = 1f;
    private boolean prepared;
    private boolean boosting;
    private boolean rewinding;
    private boolean finished;
    private int lastPosition;

    private float downX;
    private float downY;
    private long downAt;
    private boolean rightZone;
    private boolean leftZone;

    private final Runnable hideBadge = () -> badge.setVisibility(View.GONE);

    private final Runnable hideControls = () -> {
        if (player != null && player.isPlaying() && !boosting && !rewinding) {
            controls.setVisibility(View.GONE);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (prepared && player != null) {
                int position = player.getCurrentPosition();
                int duration = Math.max(1, player.getDuration());
                seekBar.setMax(duration);
                seekBar.setProgress(position);
                timeView.setText(Fmt.duration(position) + " / " + Fmt.duration(duration));
                updatePlayIcon();
                ui.postDelayed(this, 400);
            }
        }
    };

    private final Runnable startBoost = () -> {
        if (player == null || !prepared || !player.isPlaying()) return;
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

        long id = getIntent().getLongExtra("id", -1);
        String name = getIntent().getStringExtra("name");
        videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);

        surface = findViewById(R.id.surface);
        controls = findViewById(R.id.controls);
        badge = findViewById(R.id.badge);
        titleView = findViewById(R.id.title);
        timeView = findViewById(R.id.time);
        playView = findViewById(R.id.playBtn);
        speedView = findViewById(R.id.speedBtn);
        hint = findViewById(R.id.hint);
        seekBar = findViewById(R.id.seekBar);

        titleView.setText(name != null ? name : "视频");
        speedView.setText(speedText(speed));

        surface.getHolder().addCallback(this);
        findViewById(R.id.gesture).setOnTouchListener(this::onGesture);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        playView.setOnClickListener(v -> togglePlay());
        speedView.setOnClickListener(v -> cycleSpeed());
        findViewById(R.id.back10).setOnClickListener(v -> jumpBy(-10_000));
        findViewById(R.id.forward10).setOnClickListener(v -> jumpBy(10_000));

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

        ui.postDelayed(() -> hint.setVisibility(View.GONE), 5000);
        ui.postDelayed(hideControls, 4000);
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
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build());
            player.setDisplay(holder);
            player.setDataSource(this, videoUri);
            player.setOnPreparedListener(p -> {
                prepared = true;
                int duration = Math.max(1, p.getDuration());
                seekBar.setMax(duration);
                if (lastPosition > 0) seek(lastPosition);
                p.start();
                finished = false;
                applySpeed(speed);
                updatePlayIcon();
                ui.post(tick);
                setControlsVisible(true);
            });
            player.setOnCompletionListener(p -> {
                finished = true;
                updatePlayIcon();
                showBadge("播放结束", 1200);
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
        speedView.setText(speedText(speed));
        if (player != null && player.isPlaying()) applySpeed(speed);
        showBadge("倍速 " + speedText(speed), 900);
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
                if (Math.abs(event.getX() - downX) > dp(36)
                        || Math.abs(event.getY() - downY) > dp(36)) {
                    cancelLongPress();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean acted = boosting || rewinding;
                cancelLongPress();
                stopBoost();
                stopRewind();
                if (!acted && SystemClock.uptimeMillis() - downAt < 250) {
                    setControlsVisible(controls.getVisibility() != View.VISIBLE);
                }
                return true;

            default:
                return false;
        }
    }

    /** 左右两侧中间区域：右侧加速，左侧快退。上下 26%~76% 之间避开顶栏和底部控制条。 */
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
        controls.setVisibility(visible ? View.VISIBLE : View.GONE);
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

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && prepared) {
            lastPosition = player.getCurrentPosition();
            if (player.isPlaying()) player.pause();
        }
        ui.removeCallbacks(hideControls);
        setControlsVisible(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }
}
