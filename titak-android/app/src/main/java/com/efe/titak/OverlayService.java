package com.efe.titak;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * OverlayService — Ekran üstü kayan widget servisi
 * SYSTEM_ALERT_WINDOW iznini kullanarak her uygulamanın üstünde görünür.
 */
public class OverlayService extends Service implements BotEngine.BotListener {

    private static final String CHANNEL_ID = "tt123_overlay";
    private static final int NOTIF_ID = 1;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private TextView tvStatus, tvSession, tvTotal, tvLog;
    private ImageButton btnPower, btnMinimize;
    private LinearLayout bodyLayout;

    private boolean minimized = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        BotEngine.get().setListener(this);
        createOverlayView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        BotEngine.get().stop();
        BotEngine.get().setListener(null);
        if (overlayView != null) windowManager.removeView(overlayView);
    }

    // ── Overlay Widget Oluştur ─────────────────────────────────
    private void createOverlayView() {
        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 8;
        params.y = 80;

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_widget, null);

        tvStatus   = overlayView.findViewById(R.id.tv_ov_status);
        tvSession  = overlayView.findViewById(R.id.tv_ov_session);
        tvTotal    = overlayView.findViewById(R.id.tv_ov_total);
        tvLog      = overlayView.findViewById(R.id.tv_ov_log);
        btnPower   = overlayView.findViewById(R.id.btn_ov_power);
        btnMinimize= overlayView.findViewById(R.id.btn_ov_minimize);
        bodyLayout = overlayView.findViewById(R.id.ov_body);

        btnPower.setOnClickListener(v -> {
            if (BotEngine.get().isActive()) BotEngine.get().stop();
            else BotEngine.get().start();
        });

        btnMinimize.setOnClickListener(v -> {
            minimized = !minimized;
            bodyLayout.setVisibility(minimized ? View.GONE : View.VISIBLE);
            btnMinimize.setText(minimized ? "+" : "─");
        });

        // Sürükleme
        setupDrag(overlayView);

        windowManager.addView(overlayView, params);
        updateUI(false, 0, 0, "Durduruldu");
    }

    // ── Sürükleme Mantığı ─────────────────────────────────────
    private void setupDrag(View view) {
        View header = view.findViewById(R.id.ov_header);
        header.setOnTouchListener(new View.OnTouchListener() {
            int initX, initY;
            float initTouchX, initTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x;
                        initY = params.y;
                        initTouchX = event.getRawX();
                        initTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initX + (int)(initTouchX - event.getRawX());
                        params.y = initY + (int)(event.getRawY() - initTouchY);
                        windowManager.updateViewLayout(overlayView, params);
                        return true;
                }
                return false;
            }
        });
    }

    // ── UI Güncelle ───────────────────────────────────────────
    private void updateUI(boolean active, int session, int total, String status) {
        if (overlayView == null) return;
        overlayView.post(() -> {
            tvStatus.setText(status);
            tvSession.setText(String.valueOf(session));
            tvTotal.setText(String.valueOf(total));
            btnPower.setImageResource(active ? R.drawable.ic_stop : R.drawable.ic_play);

            // Log son satırı
            List<String> logs = BotEngine.get().getLogs();
            if (!logs.isEmpty()) {
                tvLog.setText(logs.get(logs.size() - 1));
            }
        });
    }

    // ── BotListener ───────────────────────────────────────────
    @Override
    public void onStateChanged(boolean active, int session, int total, String status) {
        updateUI(active, session, total, status);
        updateNotification(status);
    }

    @Override
    public void onLog(String message) {
        if (tvLog != null) {
            overlayView.post(() -> tvLog.setText(message));
        }
    }

    // ── Bildirim ──────────────────────────────────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "(efe) Bot", NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("TikTok otomatik jeton toplayıcı");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(
            this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("(efe) — tiktok 123")
            .setContentText("Bot çalışıyor...")
            .setSmallIcon(R.drawable.ic_coin)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String status) {
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("(efe) — tiktok 123")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_coin)
            .setOngoing(true)
            .build();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, n);
    }
}
