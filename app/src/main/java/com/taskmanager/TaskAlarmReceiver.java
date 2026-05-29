package com.taskmanager;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import androidx.core.app.NotificationCompat;

public class TaskAlarmReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        long taskId = intent.getLongExtra("task_id", 0);
        String taskTitle = intent.getStringExtra("task_title");
        String soundUri = intent.getStringExtra("sound_uri");
        
        // Use a unique notification ID (last 9 digits of timestamp is usually safe for uniqueness in a small list)
        int notificationId = (int) (taskId % 1000000000L);

        // Show notification (this will also trigger the full-screen alarm via setFullScreenIntent)
        showTaskNotification(context, notificationId, taskId, taskTitle, soundUri);
    }
    
    private void showTaskNotification(Context context, int notificationId, long taskId, String taskTitle, String soundUri) {
        NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        String channelId = "task_reminders";
        
        // Ensure channel is created/updated with correct importance
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for task reminders");
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 500, 500});
            // We set the sound on the channel too for older Android 8.0-9.0
            if (soundUri != null) {
                channel.setSound(android.net.Uri.parse(soundUri), null);
            }
            
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
        
        Intent fullScreenIntent = new Intent(context, TaskAlarmActivity.class);
        fullScreenIntent.putExtra("task_id", taskId);
        fullScreenIntent.putExtra("task_title", taskTitle);
        fullScreenIntent.putExtra("sound_uri", soundUri);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, notificationId,
                fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_app_logo) // Use app logo instead of system icon
                .setContentTitle("Task Reminder")
                .setContentText("Time for: " + taskTitle)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(false) // Don't auto cancel so it stays in the drawer
                .setOngoing(true)    // Make it ongoing while the alarm is ringing
                .setSound(soundUri != null ? android.net.Uri.parse(soundUri) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .setVibrate(new long[]{0, 500, 500, 500})
                .setContentIntent(fullScreenPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        
        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
        }
    }
}
