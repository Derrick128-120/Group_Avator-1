package com.taskmanager;

import android.app.KeyguardManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class TaskAlarmActivity extends AppCompatActivity {

    private Ringtone ringtone;
    private Vibrator vibrator;
    private int secondsLeft = 60;
    private android.os.Handler timerHandler = new android.os.Handler();
    private TextView tvTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }

        setContentView(R.layout.activity_alarm);

        String taskTitle = getIntent().getStringExtra("task_title");
        long taskId = getIntent().getLongExtra("task_id", 0);
        int notificationId = (int) (taskId % 1000000000L);
        
        TextView tvTaskName = findViewById(R.id.tv_task_name);
        tvTaskName.setText(taskTitle != null ? taskTitle : "Task Due!");

        tvTimer = findViewById(R.id.tv_auto_dismiss_timer);
        startCountdown();

        Button btnDismiss = findViewById(R.id.btn_dismiss);
        btnDismiss.setOnClickListener(v -> {
            stopAlarm();
            cancelNotification(notificationId);
            finish();
        });

        Button btnSnooze = findViewById(R.id.btn_snooze);
        btnSnooze.setOnClickListener(v -> {
            showSnoozeOptions(taskId, taskTitle, notificationId);
        });

        startAlarm();
    }

    private void startCountdown() {
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (secondsLeft > 0) {
                    secondsLeft--;
                    if (tvTimer != null) tvTimer.setText("Auto-dismissing in " + secondsLeft + "s");
                    timerHandler.postDelayed(this, 1000);
                } else {
                    stopAlarm();
                    finish();
                }
            }
        }, 1000);
    }

    private void showSnoozeOptions(long taskId, String title, int notifId) {
        String[] options = {"5 Minutes", "10 Minutes", "15 Minutes"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Snooze Duration")
                .setItems(options, (dialog, which) -> {
                    int minutes = (which + 1) * 5;
                    stopAlarm();
                    cancelNotification(notifId);
                    snoozeTask(taskId, title, minutes);
                    finish();
                }).show();
    }

    private void cancelNotification(int id) {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(id);
        }
    }

    private void snoozeTask(long taskId, String title, int minutes) {
        Task tempTask = new Task(taskId, title, "Medium", "Other", false, 0, "None", null, null);
        TaskAlarmScheduler scheduler = new TaskAlarmScheduler(this);
        long snoozeTime = System.currentTimeMillis() + (minutes * 60 * 1000);
        scheduler.scheduleTaskAlarm(tempTask, snoozeTime);
        android.widget.Toast.makeText(this, "Snoozed for " + minutes + " minutes", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void startAlarm() {
        // Start ringtone
        String soundUriStr = getIntent().getStringExtra("sound_uri");
        Uri alarmUri = null;
        if (soundUriStr != null) {
            alarmUri = Uri.parse(soundUriStr);
        } else {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }

        if (alarmUri == null) {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        ringtone = RingtoneManager.getRingtone(this, alarmUri);
        if (ringtone != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes aa = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                ringtone.setAudioAttributes(aa);
            }
            ringtone.play();
        }

        // Start vibration
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            long[] pattern = {0, 500, 500};
            vibrator.vibrate(pattern, 0);
        }
    }

    private void stopAlarm() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAlarm();
    }
}
