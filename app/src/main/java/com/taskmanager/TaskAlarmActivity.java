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

        Button btnDismiss = findViewById(R.id.btn_dismiss);
        btnDismiss.setOnClickListener(v -> {
            stopAlarm();
            cancelNotification(notificationId);
            finish();
        });

        Button btnSnooze = findViewById(R.id.btn_snooze);
        btnSnooze.setOnClickListener(v -> {
            stopAlarm();
            cancelNotification(notificationId);
            snoozeTask(taskId, taskTitle);
            finish();
        });

        startAlarm();
    }

    private void cancelNotification(int id) {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(id);
        }
    }

    private void snoozeTask(long taskId, String title) {
        Task tempTask = new Task(taskId, title, "Medium", "Other", false, 0, "None", null, null);
        TaskAlarmScheduler scheduler = new TaskAlarmScheduler(this);
        long snoozeTime = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes
        scheduler.scheduleTaskAlarm(tempTask, snoozeTime);
        android.widget.Toast.makeText(this, "Snoozed for 5 minutes", android.widget.Toast.LENGTH_SHORT).show();
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
