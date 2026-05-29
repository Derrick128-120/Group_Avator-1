package com.taskmanager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import java.util.Calendar;

public class TaskAlarmScheduler {
    private final Context context;

    public TaskAlarmScheduler(Context context) {
        this.context = context;
    }

    /**
     * Schedule an alarm for a task at the specified time
     */
    public void scheduleTaskAlarm(Task task, long timeInMillis) {
        TaskRepository repository = new TaskRepository(context);
        if (!repository.isNotificationsEnabled()) {
            return;
        }

        // 1. Set System Alarm Clock Entry
        setSystemAlarm(task, timeInMillis);

        // 2. Set App-internal precise alarm
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        
        Intent intent = new Intent(context, TaskAlarmReceiver.class);
        intent.putExtra("task_id", task.getId());
        intent.putExtra("task_title", task.getTitle());
        intent.putExtra("sound_uri", task.getSoundUri());
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) task.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        if (alarmManager != null && timeInMillis > System.currentTimeMillis()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            timeInMillis,
                            pendingIntent
                    );
                } else {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            timeInMillis,
                            pendingIntent
                    );
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                );
            }
        }
    }

    private void setSystemAlarm(Task task, long timeInMillis) {
        if (timeInMillis <= System.currentTimeMillis()) return;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeInMillis);

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR, cal.get(Calendar.HOUR_OF_DAY));
        intent.putExtra(AlarmClock.EXTRA_MINUTES, cal.get(Calendar.MINUTE));
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, task.getTitle());
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Cancel an alarm for a task
     */
    public void cancelTaskAlarm(Task task) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        
        Intent intent = new Intent(context, TaskAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) task.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}
