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
                Task task = new Task(
                        obj.getLong("id"),
                        obj.getString("title"),
                        obj.getString("priority"),
                        obj.optString("type", "Other"),
                        obj.getBoolean("isCompleted"),
                        obj.optLong("alarmTimeMillis", 0),
                        obj.optString("recurrence", "None"),
                        obj.optString("soundUri", null),
                        obj.optString("location", null)
                );
                
                // Load Tags
                String tagsCsv = obj.optString("tags", "");
                if (!tagsCsv.isEmpty()) {
                    List<String> tags = new ArrayList<>();
                    for (String t : tagsCsv.split(",")) tags.add(t.trim());
                    task.setTags(tags);
                }

                // Load Notes
                task.setNotes(obj.optString("notes", ""));

                // Load Sub-tasks
                JSONArray subArray = obj.optJSONArray("subTasks");
                if (subArray != null) {
                    List<Task.SubTask> subTasks = new ArrayList<>();
                    for (int j = 0; j < subArray.length(); j++) {
                        JSONObject subObj = subArray.getJSONObject(j);
                        subTasks.add(new Task.SubTask(
                                subObj.getString("title"),
                                subObj.getBoolean("isDone")
                        ));
                    }
                    task.setSubTasks(subTasks);
                }
                
                tasks.add(task);
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
                
                // Save Tags as CSV
                if (task.getTags() != null && !task.getTags().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < task.getTags().size(); i++) {
                        sb.append(task.getTags().get(i));
                        if (i < task.getTags().size() - 1) sb.append(",");
                    }
                    obj.put("tags", sb.toString());
                }

                // Save Notes
                obj.put("notes", task.getNotes());

                // Save Sub-tasks
                JSONArray subArray = new JSONArray();
                for (Task.SubTask sub : task.getSubTasks()) {
                    JSONObject subObj = new JSONObject();
                    subObj.put("title", sub.getTitle());
                    subObj.put("isDone", sub.isDone());
                    subArray.put(subObj);
                }
                obj.put("subTasks", subArray);

                array.put(obj);
            }
            prefs.edit().putString(key, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
