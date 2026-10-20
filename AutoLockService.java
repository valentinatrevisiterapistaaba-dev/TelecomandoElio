package it.valentina.telecomandoelio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoLockService extends Service {
    public static final String ACTION_START = "it.valentina.telecomandoelio.START";
    public static final String ACTION_TICK = "it.valentina.telecomandoelio.TICK";
    public static final String EXTRA_REMAINING = "remaining";
    public static final String EXTRA_STATUS = "status";

    private static final String CHANNEL = "automode";
    private static final int NOTIF_ID = 71;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private PowerManager.WakeLock wakeLock;
    private long deadline;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long left = deadline - System.currentTimeMillis();

            if (left <= 0) {
                broadcast(0, "Blocco automatico in corso…");
                executeAutoBlock();
                return;
            }

            broadcast(left, "AutoMode attivo");
            updateNotification(left);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);

        int minutes = Math.max(1, Math.min(60, p.getInt("minutes", 5)));
        deadline = System.currentTimeMillis() + minutes * 60_000L;

        p.edit().putLong("deadline", deadline).apply();

        startForeground(
                NOTIF_ID,
                notification("AutoMode attivo", minutes * 60_000L)
        );

        acquireWakeLock(minutes);

        handler.removeCallbacks(tick);
        handler.post(tick);

        return START_NOT_STICKY;
    }

    private void executeAutoBlock() {
        handler.removeCallbacks(tick);

        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        final String ip = p.getString("ip", "192.168.1.10");
        final String pw = p.getString("password", "2222");

        executor.execute(() -> {
            FullyClient.Result r1 = FullyClient.send(this, ip, pw, "enableLockedMode");

            try {
                Thread.sleep(650);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            FullyClient.Result r2 = FullyClient.send(this, ip, pw, "startScreensaver");

            p.edit().putBoolean("locked", r1.ok).apply();

            String msg;
            if (r1.ok && r2.ok) {
                msg = "Bloccato + screensaver attivato";
            } else if (!r1.ok && !r2.ok) {
                msg = "ERRORE: blocco e screensaver non riusciti";
            } else if (!r1.ok) {
                msg = "ATTENZIONE: screensaver sì, blocco no";
            } else {
                msg = "ATTENZIONE: blocco sì, screensaver no";
            }

            stopEverything(msg);
        });
    }

    private void acquireWakeLock(int minutes) {
        releaseWakeLock();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TelecomandoElio:AutoMode"
        );

        wakeLock.acquire((minutes + 1L) * 60_000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void stopEverything(String status) {
        handler.removeCallbacks(tick);
        releaseWakeLock();

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .remove("deadline")
                .apply();

        broadcast(0, status);

        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        stopSelf();
    }

    private void broadcast(long remaining, String status) {
        Intent i = new Intent(ACTION_TICK);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_REMAINING, remaining);
        i.putExtra(EXTRA_STATUS, status);
        sendBroadcast(i);
    }

    private Notification notification(String title, long left) {
        Intent open = new Intent(this, MainActivity.class);

        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String text = format(left) + " al blocco automatico";

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }

        return b
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(long left) {
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        nm.notify(NOTIF_ID, notification("AutoMode attivo", left));
    }

    private static String format(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return (s / 60) + ":" + String.format(Locale.US, "%02d", s % 60);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL,
                    "AutoMode",
                    NotificationManager.IMPORTANCE_LOW
            );

            ch.setDescription("Timer per il blocco automatico di Elio");

            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        releaseWakeLock();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
