package com.taskmanager;

public class Task {
    private long id;
    private String title;
    private String priority; // "High", "Medium", "Low"
    private String type;     // "Work", "Personal", "Health", "Other"
    private boolean isCompleted;
    private long alarmTimeMillis; // For alarm notifications
    private String recurrence;    // "None", "Daily", "Weekly", "Monthly"
    private String soundUri;      // Custom alarm sound
    private String location;      // Destination for the task

    public Task(long id, String title, String priority, boolean isCompleted) {
        this(id, title, priority, "Other", isCompleted, 0, "None", null, null);
    }

    public Task(long id, String title, String priority, String type, boolean isCompleted, long alarmTimeMillis) {
        this(id, title, priority, type, isCompleted, alarmTimeMillis, "None", null, null);
    }

    public Task(long id, String title, String priority, String type, boolean isCompleted, long alarmTimeMillis, String recurrence) {
        this(id, title, priority, type, isCompleted, alarmTimeMillis, recurrence, null, null);
    }

    public Task(long id, String title, String priority, String type, boolean isCompleted, long alarmTimeMillis, String recurrence, String soundUri) {
        this(id, title, priority, type, isCompleted, alarmTimeMillis, recurrence, soundUri, null);
    }

    public Task(long id, String title, String priority, String type, boolean isCompleted, long alarmTimeMillis, String recurrence, String soundUri, String location) {
        this.id = id;
        this.title = title;
        this.priority = priority;
        this.type = type;
        this.isCompleted = isCompleted;
        this.alarmTimeMillis = alarmTimeMillis;
        this.recurrence = recurrence;
        this.soundUri = soundUri;
        this.location = location;
    }

    // --- Getters ---
    public long getId()              { return id; }
    public String getTitle()         { return title; }
    public String getPriority()      { return priority; }
    public String getType()          { return type; }
    public boolean isCompleted()     { return isCompleted; }
    public long getAlarmTimeMillis() { return alarmTimeMillis; }
    public String getRecurrence()    { return recurrence; }
    public String getSoundUri()      { return soundUri; }
    public String getLocation()      { return location; }

    // --- Setters ---
    public void setTitle(String title)                    { this.title = title; }
    public void setPriority(String priority)              { this.priority = priority; }
    public void setType(String type)                      { this.type = type; }
    public void setCompleted(boolean completed)           { this.isCompleted = completed; }
    public void setAlarmTimeMillis(long alarmTimeMillis)  { this.alarmTimeMillis = alarmTimeMillis; }
    public void setRecurrence(String recurrence)          { this.recurrence = recurrence; }
    public void setSoundUri(String soundUri)              { this.soundUri = soundUri; }
    public void setLocation(String location)              { this.location = location; }
}
