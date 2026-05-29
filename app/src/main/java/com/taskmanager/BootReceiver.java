package com.taskmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.List;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            TaskRepository repository = new TaskRepository(context);
            TaskAlarmScheduler scheduler = new TaskAlarmScheduler(context);
            
            List<Task> tasks = repository.loadTasks();
            long now = System.currentTimeMillis();
            
            for (Task task : tasks) {
                if (!task.isCompleted() && task.getAlarmTimeMillis() > now) {
                    scheduler.scheduleTaskAlarm(task, task.getAlarmTimeMillis());
                }
            }
        }
    }
}
