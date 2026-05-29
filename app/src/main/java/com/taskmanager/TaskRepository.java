package com.taskmanager;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TaskRepository {

    private static final String PREFS_NAME = "TaskFlowPrefs";
    private static final String TASKS_KEY  = "tasks";
    private static final String HISTORY_KEY = "history_tasks";
    private static final String SETTINGS_NOTIF_KEY = "notifications_enabled";
    private static final String THEME_KEY = "app_theme";

    private final SharedPreferences prefs;

    public TaskRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getAppTheme() {
        return prefs.getString(THEME_KEY, "Default");
    }

    public void setAppTheme(String theme) {
        prefs.edit().putString(THEME_KEY, theme).apply();
    }

    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(SETTINGS_NOTIF_KEY, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(SETTINGS_NOTIF_KEY, enabled).apply();
    }

    /** Load all tasks from SharedPreferences. Returns empty list on error. */
    public List<Task> loadTasks() {
        return loadTasksFromKey(TASKS_KEY);
    }

    public List<Task> loadHistory() {
        return loadTasksFromKey(HISTORY_KEY);
    }

    private List<Task> loadTasksFromKey(String key) {
        List<Task> tasks = new ArrayList<>();
        String json = prefs.getString(key, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                tasks.add(new Task(
                        obj.getLong("id"),
                        obj.getString("title"),
                        obj.getString("priority"),
                        obj.optString("type", "Other"),
                        obj.getBoolean("isCompleted"),
                        obj.optLong("alarmTimeMillis", 0),
                        obj.optString("recurrence", "None"),
                        obj.optString("soundUri", null),
                        obj.optString("location", null)
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tasks;
    }

    /** Persist all tasks to SharedPreferences as a JSON array string. */
    public void saveTasks(List<Task> tasks) {
        saveTasksToKey(tasks, TASKS_KEY);
    }

    public void saveHistory(List<Task> tasks) {
        saveTasksToKey(tasks, HISTORY_KEY);
    }

    private void saveTasksToKey(List<Task> tasks, String key) {
        try {
            JSONArray array = new JSONArray();
            for (Task task : tasks) {
                JSONObject obj = new JSONObject();
                obj.put("id",          task.getId());
                obj.put("title",       task.getTitle());
                obj.put("priority",    task.getPriority());
                obj.put("type",        task.getType());
                obj.put("isCompleted", task.isCompleted());
                obj.put("alarmTimeMillis", task.getAlarmTimeMillis());
                obj.put("recurrence",   task.getRecurrence());
                obj.put("soundUri",     task.getSoundUri());
                obj.put("location",     task.getLocation());
                array.put(obj);
            }
            prefs.edit().putString(key, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
