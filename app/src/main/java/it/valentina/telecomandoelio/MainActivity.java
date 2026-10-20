package it.valentina.telecomandoelio;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private SharedPreferences prefs;

    private EditText ip;
    private EditText password;
    private EditText minutes;

    private CheckBox autoMode;

    private TextView countdown;
    private TextView status;
    private TextView target;

    private Button lockButton;
    private Button unlockButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long left = intent.getLongExtra(AutoLockService.EXTRA_REMAINING, 0);
            String s = intent.getStringExtra(AutoLockService.EXTRA_STATUS);

            if (left > 0) {
                countdown.setText(format(left));
            } else if (s != null && s.toLowerCase(Locale.ROOT).contains("blocc")) {
                countdown.setText("0:00");
            }

            if (s != null) {
                status.setText(s);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        seedDefaults();
        requestNotificationsIfNeeded();

        setContentView(buildUi());
        restore();
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter f = new IntentFilter(AutoLockService.ACTION_TICK);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, f);
        }

        refreshDeadline();
    }
