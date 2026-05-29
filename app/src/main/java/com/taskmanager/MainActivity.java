package com.taskmanager;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity implements TaskAdapter.OnTaskListener {

    private TaskAdapter    adapter;
    private List<Task>     taskList;
    private List<Task>     filteredList;
    private TaskRepository repository;
    private TextView       tvTaskCount;
    private TextView       tvStats;
    private EditText       etSearch;
    private Button         btnFilterToday;
    private ImageButton    btnSettings, btnCalendar;
    private TaskAlarmScheduler alarmScheduler;
    private static final int VOICE_REQUEST_CODE = 101;
    private static final int VOICE_PLANNER_REQUEST_CODE = 104;
    private static final int NOTIF_PERMISSION_CODE = 102;
    private static final int SOUND_PICKER_CODE = 103;
    private EditText etTitleForVoice;
    private EditText etPlanForVoice;
    private String selectedSoundUri = null;
    private Button btnPickSoundInDialog;
    private boolean isTodayFilterActive = false;
    private Calendar selectedFilterDate = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        checkNotificationPermission();

        // ── Data layer ──────────────────────────────────────────
        repository = new TaskRepository(this);
        taskList   = repository.loadTasks();
        filteredList = new ArrayList<>(taskList);
        alarmScheduler = new TaskAlarmScheduler(this);

        applyTheme(repository.getAppTheme());

        // ── Views ────────────────────────────────────────────────
        RecyclerView          recyclerView = findViewById(R.id.recycler_view);
        FloatingActionButton  fab          = findViewById(R.id.fab);
        tvTaskCount                        = findViewById(R.id.tv_task_count);
        tvStats                            = findViewById(R.id.tv_stats);
        etSearch                           = findViewById(R.id.et_search);
        btnFilterToday                     = findViewById(R.id.btn_filter_today);
        Button btnPlanDay                  = findViewById(R.id.btn_plan_day);
        Button btnCalendarMaker            = findViewById(R.id.btn_calendar_maker);
        btnSettings                        = findViewById(R.id.btn_settings);
        btnCalendar                        = findViewById(R.id.btn_calendar);

        btnPlanDay.setOnClickListener(v -> showPlanDayDialog());
        btnCalendarMaker.setOnClickListener(v -> showCalendarMakerDialog());
        View                  bootOverlay  = findViewById(R.id.boot_overlay);
        View                  glowPurple   = findViewById(R.id.glow_purple);
        View                  glowPink     = findViewById(R.id.glow_pink);

        // ── Boot sequence ────────────────────────────────────────
        new Handler().postDelayed(() -> {
            bootOverlay.animate().alpha(0f).setDuration(800).withEndAction(() -> 
                bootOverlay.setVisibility(View.GONE)).start();
        }, 2000);

        // ── Background Animations ────────────────────────────────
        startGlowingAnimation(glowPurple, 0, 40, 3000);
        startGlowingAnimation(glowPink, 0, -40, 4000);

        // ── RecyclerView setup ───────────────────────────────────
        adapter = new TaskAdapter(this, filteredList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        updateTaskCount();
        updateStats();

        // ── FAB → add dialog ─────────────────────────────────────
        fab.setOnClickListener(v -> showTaskDialog(-1, null));

        // ── Settings ─────────────────────────────────────────────
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // ── Calendar ─────────────────────────────────────────────
        btnCalendar.setOnClickListener(v -> showCalendarDialog());

        // ── Filter Today ─────────────────────────────────────────
        btnFilterToday.setOnClickListener(v -> {
            isTodayFilterActive = !isTodayFilterActive;
            btnFilterToday.setAlpha(isTodayFilterActive ? 1.0f : 0.6f);
            filterTasks(etSearch.getText().toString());
        });
        btnFilterToday.setAlpha(0.6f);

        // ── Search Logic ─────────────────────────────────────────
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTasks(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterTasks(String query) {
        String lowerQuery = query.toLowerCase();
        
        List<Task> filtered = taskList.stream()
                .filter(t -> {
                    boolean matchesQuery = t.getTitle().toLowerCase().contains(lowerQuery) || 
                                          t.getType().toLowerCase().contains(lowerQuery);
                    
                    if (!isTodayFilterActive && selectedFilterDate == null) return matchesQuery;
                    
                    if (t.getAlarmTimeMillis() <= 0) return false;
                    
                    Calendar taskCal = Calendar.getInstance();
                    taskCal.setTimeInMillis(t.getAlarmTimeMillis());
                    
                    if (isTodayFilterActive) {
                        Calendar today = Calendar.getInstance();
                        return matchesQuery && 
                               taskCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                               taskCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
                    } else {
                        // Calendar date filter
                        return matchesQuery && 
                               taskCal.get(Calendar.YEAR) == selectedFilterDate.get(Calendar.YEAR) &&
                               taskCal.get(Calendar.DAY_OF_YEAR) == selectedFilterDate.get(Calendar.DAY_OF_YEAR);
                    }
                })
                .collect(Collectors.toList());
        
        filteredList.clear();
        filteredList.addAll(filtered);
        sortTasks();
        adapter.notifyDataSetChanged();
    }

    private void startGlowingAnimation(View view, float startY, float endY, int duration) {
        TranslateAnimation anim = new TranslateAnimation(0, 0, startY, endY);
        anim.setDuration(duration);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        view.startAnimation(anim);
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        CheckBox cbNotif = dialogView.findViewById(R.id.cb_notif_enabled);
        Spinner spinnerTheme = dialogView.findViewById(R.id.spinner_theme);
        Button btnHistory = dialogView.findViewById(R.id.btn_view_history);
        Button btnClose = dialogView.findViewById(R.id.btn_close_settings);

        cbNotif.setChecked(repository.isNotificationsEnabled());

        String[] themes = {"Default", "Sunset Glow", "Forest Green"};
        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, themes);
        themeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerTheme.setAdapter(themeAdapter);

        // Pre-select current theme
        String currentTheme = repository.getAppTheme();
        for (int i = 0; i < themes.length; i++) {
            if (themes[i].equals(currentTheme)) {
                spinnerTheme.setSelection(i);
                break;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        cbNotif.setOnCheckedChangeListener((v, isChecked) -> {
            repository.setNotificationsEnabled(isChecked);
            if (!isChecked) {
                for (Task task : taskList) alarmScheduler.cancelTaskAlarm(task);
                Toast.makeText(this, "All alarms disabled", Toast.LENGTH_SHORT).show();
            } else {
                long now = System.currentTimeMillis();
                for (Task task : taskList) {
                    if (!task.isCompleted() && task.getAlarmTimeMillis() > now) {
                        alarmScheduler.scheduleTaskAlarm(task, task.getAlarmTimeMillis());
                    }
                }
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            }
        });

        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedTheme = themes[position];
                if (!selectedTheme.equals(repository.getAppTheme())) {
                    repository.setAppTheme(selectedTheme);
                    applyTheme(selectedTheme);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnHistory.setOnClickListener(v -> {
            dialog.dismiss();
            showHistoryDialog();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void applyTheme(String themeName) {
        View root = findViewById(R.id.main_root_layout);
        if (root == null) return;

        switch (themeName) {
            case "Sunset Glow":
                root.setBackgroundResource(R.drawable.bg_gradient_sunset);
                break;
            case "Forest Green":
                root.setBackgroundResource(R.drawable.bg_gradient_forest);
                break;
            default:
                root.setBackgroundResource(R.drawable.bg_gradient);
                break;
        }
    }

    private void showHistoryDialog() {
        List<Task> history = repository.loadHistory();
        if (history.isEmpty()) {
            Toast.makeText(this, "No history available", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] taskTitles = new String[history.size()];
        for (int i = 0; i < history.size(); i++) {
            taskTitles[i] = history.get(i).getTitle() + (history.get(i).isCompleted() ? " (Completed)" : " (Deleted)");
        }

        new AlertDialog.Builder(this)
                .setTitle("Task History (Last 50)")
                .setItems(taskTitles, null)
                .setPositiveButton("Clear History", (dialog, which) -> {
                    repository.saveHistory(new ArrayList<>());
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showCalendarDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_calendar, null);
        android.widget.CalendarView calendarView = dialogView.findViewById(R.id.calendar_view);
        Button btnClear = dialogView.findViewById(R.id.btn_clear_filter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedFilterDate = Calendar.getInstance();
            selectedFilterDate.set(year, month, dayOfMonth);
            isTodayFilterActive = false;
            btnFilterToday.setAlpha(0.6f);
            btnCalendar.setBackgroundResource(R.drawable.btn_primary_bg);
            filterTasks(etSearch.getText().toString());
            dialog.dismiss();
        });

        btnClear.setOnClickListener(v -> {
            selectedFilterDate = null;
            btnCalendar.setBackgroundResource(R.drawable.glass_input_bg);
            filterTasks(etSearch.getText().toString());
            dialog.dismiss();
        });

        dialog.show();
    }

    // ============================================================
    //  Add / Edit dialog
    // ============================================================

    /**
     * @param editPosition  index to edit, or -1 for a new task
     * @param existingTask  the task to edit, or null for new
     */
    private void showCalendarMakerDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_calendar_maker, null);
        Button btnDate = dialogView.findViewById(R.id.btn_maker_date);
        EditText etGoal = dialogView.findViewById(R.id.et_maker_goal);
        Button btnCancel = dialogView.findViewById(R.id.btn_maker_cancel);
        Button btnGenerate = dialogView.findViewById(R.id.btn_maker_generate);

        final Calendar selectedDate = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
        btnDate.setText("Select Date: " + sdf.format(selectedDate.getTime()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnDate.setOnClickListener(v -> {
            new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                selectedDate.set(year, month, dayOfMonth);
                btnDate.setText("Select Date: " + sdf.format(selectedDate.getTime()));
            }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnGenerate.setOnClickListener(v -> {
            String goal = etGoal.getText().toString().trim().toLowerCase();
            if (goal.isEmpty()) {
                etGoal.setError("Tell me your goal for this day");
                return;
            }

            generateAISchedule(selectedDate, goal);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void generateAISchedule(Calendar date, String goal) {
        List<Task> newTasks = new ArrayList<>();
        long baseId = System.currentTimeMillis();

        if (goal.contains("code") || goal.contains("program") || goal.contains("study")) {
            newTasks.add(createTaskForDate(baseId++, "Deep Work Session", "High", "Work", date, 9, 0));
            newTasks.add(createTaskForDate(baseId++, "Code Review & Bug Fixes", "Medium", "Work", date, 11, 0));
            newTasks.add(createTaskForDate(baseId++, "Lunch Break", "Low", "Personal", date, 12, 30));
            newTasks.add(createTaskForDate(baseId++, "New Feature Implementation", "High", "Work", date, 14, 0));
            newTasks.add(createTaskForDate(baseId++, "Learning/Tutorial Time", "Medium", "Personal", date, 16, 30));
        } else if (goal.contains("fitness") || goal.contains("health") || goal.contains("workout")) {
            newTasks.add(createTaskForDate(baseId++, "Morning Jog", "High", "Health", date, 7, 0));
            newTasks.add(createTaskForDate(baseId++, "Healthy Breakfast", "Medium", "Health", date, 8, 30));
            newTasks.add(createTaskForDate(baseId++, "Yoga/Stretching", "Low", "Health", date, 12, 0));
            newTasks.add(createTaskForDate(baseId++, "Gym - Strength Training", "High", "Health", date, 17, 0));
            newTasks.add(createTaskForDate(baseId++, "Meal Prep", "Medium", "Personal", date, 19, 0));
        } else if (goal.contains("relax") || goal.contains("lazy") || goal.contains("chill")) {
            newTasks.add(createTaskForDate(baseId++, "Slow Morning / Reading", "Low", "Personal", date, 9, 0));
            newTasks.add(createTaskForDate(baseId++, "Watch a Movie", "Low", "Personal", date, 14, 0));
            newTasks.add(createTaskForDate(baseId++, "Walk in the Park", "Medium", "Health", date, 16, 0));
            newTasks.add(createTaskForDate(baseId++, "Cook Special Dinner", "Medium", "Personal", date, 18, 30));
        } else {
            // Default generic productive day
            newTasks.add(createTaskForDate(baseId++, "Important Task 1", "High", "Other", date, 10, 0));
            newTasks.add(createTaskForDate(baseId++, "Personal Errand", "Medium", "Personal", date, 13, 0));
            newTasks.add(createTaskForDate(baseId++, "Review Goals", "Low", "Other", date, 17, 0));
        }

        for (Task t : newTasks) {
            taskList.add(t);
            if (t.getAlarmTimeMillis() > System.currentTimeMillis()) {
                alarmScheduler.scheduleTaskAlarm(t, t.getAlarmTimeMillis());
            }
        }

        repository.saveTasks(taskList);
        filterTasks(etSearch.getText().toString());
        updateTaskCount();
        updateStats();
        
        Toast.makeText(this, "AI: Calendar created for " + newTasks.size() + " tasks!", Toast.LENGTH_LONG).show();
    }

    private Task createTaskForDate(long id, String title, String priority, String type, Calendar date, int hour, int minute) {
        Calendar cal = (Calendar) date.clone();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        return new Task(id, title, priority, type, false, cal.getTimeInMillis(), "None", null, null);
    }

    private void showPlanDayDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_plan_day, null);
        EditText etPlan = dialogView.findViewById(R.id.et_plan_input);
        ImageButton btnVoice = dialogView.findViewById(R.id.btn_plan_voice);
        Button btnCancel = dialogView.findViewById(R.id.btn_plan_cancel);
        Button btnSubmit = dialogView.findViewById(R.id.btn_plan_submit);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnVoice.setOnClickListener(v -> {
            etPlanForVoice = etPlan;
            startVoicePlannerInput();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String plan = etPlan.getText().toString().trim();
            if (plan.isEmpty()) {
                etPlan.setError("Please type or speak your plan");
                return;
            }
            processVoicePlan(plan);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void processVoicePlan(String input) {
        // Simple "AI" parser for the prototype
        // "buy milk at 10am then go to gym at 2pm"
        String[] parts = input.split(" then | and ");
        List<Task> newTasks = new ArrayList<>();

        for (String part : parts) {
            String title = part.trim();
            long alarmTime = 0;
            String location = null;

            // Extract time (very basic)
            if (title.contains(" at ")) {
                String[] timeParts = title.split(" at ");
                title = timeParts[0].trim();
                String timeStr = timeParts[1].toLowerCase();

                try {
                    Calendar cal = Calendar.getInstance();
                    if (timeStr.contains("am") || timeStr.contains("pm")) {
                        int hour = Integer.parseInt(timeStr.replaceAll("[^0-9]", ""));
                        if (timeStr.contains("pm") && hour < 12) hour += 12;
                        if (timeStr.contains("am") && hour == 12) hour = 0;
                        cal.set(Calendar.HOUR_OF_DAY, hour);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        if (cal.getTimeInMillis() < System.currentTimeMillis()) {
                            cal.add(Calendar.DAY_OF_YEAR, 1);
                        }
                        alarmTime = cal.getTimeInMillis();
                    }
                } catch (Exception ignored) {}
            }

            // Extract location (very basic)
            if (title.contains(" in ") || title.contains(" at ")) {
                 // Re-check for location if not already used for time
                 // For now, let's just assume if it has "at [Place]" and we didn't parse it as time
            }

            Task task = new Task(System.currentTimeMillis() + newTasks.size(), 
                                 title, "Medium", "Other", false, alarmTime, "None", null, location);
            newTasks.add(task);
            if (alarmTime > 0) {
                alarmScheduler.scheduleTaskAlarm(task, alarmTime);
            }
        }

        taskList.addAll(newTasks);
        repository.saveTasks(taskList);
        filterTasks(etSearch.getText().toString());
        updateTaskCount();
        updateStats();
        
        Toast.makeText(this, "AI Planner: Added " + newTasks.size() + " tasks!", Toast.LENGTH_LONG).show();
    }

    private void showExerciseRoutingDialog(String location) {
        String[] options = {
            "🚶 Walk there (25 mins, 120 cal)",
            "🏃 Run there (12 mins, 220 cal)",
            "🚲 Bike there (8 mins, 80 cal)",
            "🚗 Drive (ignore exercise)"
        };

        new AlertDialog.Builder(this)
            .setTitle("📍 Turn this task into exercise?")
            .setMessage("Routing to: " + location)
            .setItems(options, (dialog, which) -> {
                if (which < 3) {
                    Toast.makeText(this, "Routing started: " + options[which], Toast.LENGTH_LONG).show();
                    // In a real app, you would launch Google Maps or an internal navigation fragment here.
                }
            })
            .setNegativeButton("Maybe later", null)
            .show();
    }

    private void showTaskDialog(int editPosition, Task existingTask) {
        // Inflate the custom glass dialog layout
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_task, null);

        // ── Wire up views inside the dialog ──────────────────────
        TextView   tvDialogTitle    = dialogView.findViewById(R.id.tv_dialog_title);
        EditText   etTitle          = dialogView.findViewById(R.id.et_task_title);
        Spinner    spinnerPriority  = dialogView.findViewById(R.id.spinner_priority);
        Spinner    spinnerType      = dialogView.findViewById(R.id.spinner_type);
        EditText   etLocation       = dialogView.findViewById(R.id.et_task_location);
        Spinner    spinnerRecurrence = dialogView.findViewById(R.id.spinner_recurrence);
        Button     btnPickTime      = dialogView.findViewById(R.id.btn_pick_time);
        Button     btnPickSound     = dialogView.findViewById(R.id.btn_pick_sound);
        Button     btnAdd           = dialogView.findViewById(R.id.btn_dialog_add);
        Button     btnCancel        = dialogView.findViewById(R.id.btn_dialog_cancel);
        ImageButton btnVoice        = dialogView.findViewById(R.id.btn_voice);

        btnPickSoundInDialog = btnPickSound;
        final Calendar alarmCalendar = Calendar.getInstance();
        final boolean[] isTimeSet = {false};

        // ── Pre-fill for edit mode ────────────────────────────────
        boolean isEditMode = (existingTask != null);
        tvDialogTitle.setText(isEditMode ? "Edit Task" : "New Task");
        btnAdd.setText(isEditMode ? "Save" : "Add");

        selectedSoundUri = isEditMode ? existingTask.getSoundUri() : null;
        if (selectedSoundUri != null) {
            btnPickSound.setText("Sound Selected");
        }

        // ── Priority spinner ──────────────────────────────────────
        String[] priorities = {"High", "Medium", "Low"};
        ArrayAdapter<String> priorityAdapter = new ArrayAdapter<>(
                this, R.layout.spinner_item, priorities);
        priorityAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerPriority.setAdapter(priorityAdapter);

        // ── Type spinner ──────────────────────────────────────────
        String[] types = {"Work", "Personal", "Health", "Shopping", "Other"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this, R.layout.spinner_item, types);
        typeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);

        // ── Recurrence spinner ─────────────────────────────────────
        String[] recurrences = {"None", "Daily", "Weekly", "Monthly"};
        ArrayAdapter<String> recurrenceAdapter = new ArrayAdapter<>(
                this, R.layout.spinner_item, recurrences);
        recurrenceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerRecurrence.setAdapter(recurrenceAdapter);

        // ── Pre-fill for edit mode ────────────────────────────────
        if (isEditMode) {
            etTitle.setText(existingTask.getTitle());
            etTitle.setText(existingTask.getTitle());
            etTitle.setSelection(existingTask.getTitle().length());
            for (int i = 0; i < priorities.length; i++) {
                if (priorities[i].equals(existingTask.getPriority())) {
                    spinnerPriority.setSelection(i);
                    break;
                }
            }
            for (int i = 0; i < types.length; i++) {
                if (types[i].equals(existingTask.getType())) {
                    spinnerType.setSelection(i);
                    break;
                }
            }
            for (int i = 0; i < recurrences.length; i++) {
                if (recurrences[i].equals(existingTask.getRecurrence())) {
                    spinnerRecurrence.setSelection(i);
                    break;
                }
            }
            if (existingTask.getAlarmTimeMillis() > 0) {
                alarmCalendar.setTimeInMillis(existingTask.getAlarmTimeMillis());
                isTimeSet[0] = true;
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                btnPickTime.setText(getString(R.string.alarm_format, sdf.format(alarmCalendar.getTime())));
            }
            etLocation.setText(existingTask.getLocation());
        }

        // ── Voice Input ───────────────────────────────────────────
        btnVoice.setOnClickListener(v -> {
            etTitleForVoice = etTitle;
            startVoiceInput();
        });

        // ── Time picker listener ──────────────────────────────────
        btnPickTime.setOnClickListener(v -> {
            TimePickerDialog timePicker = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                alarmCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                alarmCalendar.set(Calendar.MINUTE, minute);
                alarmCalendar.set(Calendar.SECOND, 0);
                
                // If the time is in the past, assume they mean tomorrow
                if (alarmCalendar.getTimeInMillis() < System.currentTimeMillis()) {
                    alarmCalendar.add(Calendar.DAY_OF_YEAR, 1);
                }
                
                isTimeSet[0] = true;
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                btnPickTime.setText(getString(R.string.alarm_format, sdf.format(alarmCalendar.getTime())));
            }, alarmCalendar.get(Calendar.HOUR_OF_DAY), alarmCalendar.get(Calendar.MINUTE), false);
            timePicker.show();
        });

        // ── Sound picker listener ─────────────────────────────────
        btnPickSound.setOnClickListener(v -> {
            Intent intent = new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM);
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound");
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, 
                    selectedSoundUri != null ? android.net.Uri.parse(selectedSoundUri) : null);
            startActivityForResult(intent, SOUND_PICKER_CODE);
        });

        // ── Build the transparent AlertDialog ────────────────────
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Make the dialog window itself transparent so only our
        // glass drawable shows
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // ── Button listeners ──────────────────────────────────────
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                etTitle.setError("Task title cannot be empty");
                return;
            }
            String priority = spinnerPriority.getSelectedItem().toString();
            String type = spinnerType.getSelectedItem().toString();
            String location = etLocation.getText().toString().trim();
            String recurrence = spinnerRecurrence.getSelectedItem().toString();
            long alarmTime = isTimeSet[0] ? alarmCalendar.getTimeInMillis() : 0;

            if (isEditMode) {
                // Update existing task
                existingTask.setTitle(title);
                existingTask.setPriority(priority);
                existingTask.setType(type);
                existingTask.setLocation(location);
                existingTask.setRecurrence(recurrence);
                existingTask.setAlarmTimeMillis(alarmTime);
                existingTask.setSoundUri(selectedSoundUri);
                
                if (alarmTime > 0) {
                    alarmScheduler.scheduleTaskAlarm(existingTask, alarmTime);
                } else {
                    alarmScheduler.cancelTaskAlarm(existingTask);
                }
            } else {
                // Create new task (timestamp = unique ID)
                Task newTask = new Task(System.currentTimeMillis(), title, priority, type, false, alarmTime, recurrence, selectedSoundUri, location);
                taskList.add(newTask);
                
                if (alarmTime > 0) {
                    alarmScheduler.scheduleTaskAlarm(newTask, alarmTime);
                }
            }

            filterTasks(etSearch.getText().toString());
            repository.saveTasks(taskList);
            updateTaskCount();
            updateStats();
            updateWidget();
            dialog.dismiss();

            if (!location.isEmpty()) {
                showExerciseRoutingDialog(location);
            }
            
            if (alarmTime > 0) {
                Toast.makeText(this, getString(R.string.alarm_set_toast), Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak task title...");
        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported", Toast.LENGTH_SHORT).show();
        }
    }

    private void startVoicePlannerInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell me your plan for today...");
        try {
            startActivityForResult(intent, VOICE_PLANNER_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty() && etTitleForVoice != null) {
                etTitleForVoice.setText(result.get(0));
            }
        } else if (requestCode == VOICE_PLANNER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                if (etPlanForVoice != null) {
                    etPlanForVoice.setText(result.get(0));
                } else {
                    processVoicePlan(result.get(0));
                }
            }
        } else if (requestCode == SOUND_PICKER_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                selectedSoundUri = uri.toString();
                if (btnPickSoundInDialog != null) {
                    btnPickSoundInDialog.setText("Sound Selected");
                }
            }
        }
    }

    // ============================================================
    //  TaskAdapter.OnTaskListener callbacks
    // ============================================================

    @Override
    public void onDelete(int position) {
        Task task = filteredList.get(position);
        alarmScheduler.cancelTaskAlarm(task);

        // Save to history before removing
        List<Task> history = repository.loadHistory();
        history.add(0, task); // Add at the beginning
        if (history.size() > 50) history.remove(history.size() - 1); // Keep last 50
        repository.saveHistory(history);

        taskList.remove(task);
        filteredList.remove(position);
        adapter.notifyItemRemoved(position);
        repository.saveTasks(taskList);
        updateTaskCount();
        updateStats();
        updateWidget();
    }

    @Override
    public void onToggleComplete(int position) {
        Task task = filteredList.get(position);
        task.setCompleted(!task.isCompleted());
        
        if (task.isCompleted()) {
            alarmScheduler.cancelTaskAlarm(task);
            handleRecurrence(task);

            // Add to history when completed
            List<Task> history = repository.loadHistory();
            history.add(0, task);
            if (history.size() > 50) history.remove(history.size() - 1);
            repository.saveHistory(history);
        } else if (task.getAlarmTimeMillis() > System.currentTimeMillis()) {
            alarmScheduler.scheduleTaskAlarm(task, task.getAlarmTimeMillis());
        }
        
        filterTasks(etSearch.getText().toString());
        repository.saveTasks(taskList);
        updateTaskCount();
        updateStats();
        updateWidget();
    }

    private void handleRecurrence(Task task) {
        if ("None".equals(task.getRecurrence())) return;

        Calendar next = Calendar.getInstance();
        if (task.getAlarmTimeMillis() > 0) {
            next.setTimeInMillis(task.getAlarmTimeMillis());
        }

        switch (task.getRecurrence()) {
            case "Daily":   next.add(Calendar.DAY_OF_YEAR, 1); break;
            case "Weekly":  next.add(Calendar.WEEK_OF_YEAR, 1); break;
            case "Monthly": next.add(Calendar.MONTH, 1); break;
        }

        Task nextTask = new Task(System.currentTimeMillis(), task.getTitle(), task.getPriority(), 
                task.getType(), false, next.getTimeInMillis(), task.getRecurrence(), task.getSoundUri(), task.getLocation());
        taskList.add(nextTask);
        if (nextTask.getAlarmTimeMillis() > System.currentTimeMillis()) {
            alarmScheduler.scheduleTaskAlarm(nextTask, nextTask.getAlarmTimeMillis());
        }
    }

    @Override
    public void onEdit(int position) {
        showTaskDialog(position, filteredList.get(position));
    }

    // ============================================================
    //  Helpers
    // ============================================================

    /** Incomplete tasks first, then completed tasks (stable sort). */
    private void sortTasks() {
        Collections.sort(filteredList, (t1, t2) -> {
            if (t1.isCompleted() == t2.isCompleted()) return 0;
            return t1.isCompleted() ? 1 : -1;
        });
    }

    /** Update the "X tasks remaining" counter. */
    private void updateTaskCount() {
        long remaining = taskList.stream().filter(t -> !t.isCompleted()).count();
        tvTaskCount.setText(remaining == 1
                ? "1 task remaining"
                : remaining + " tasks remaining");
    }

    private void updateStats() {
        if (taskList.isEmpty()) {
            tvStats.setText("0% Done");
            return;
        }
        long completed = taskList.stream().filter(Task::isCompleted).count();
        int percent = (int) ((completed * 100) / taskList.size());
        tvStats.setText(percent + "% Done");
    }

    private void updateWidget() {
        Intent intent = new Intent(this, TaskWidgetProvider.class);
        intent.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = android.appwidget.AppWidgetManager.getInstance(getApplication())
                .getAppWidgetIds(new android.content.ComponentName(getApplication(), TaskWidgetProvider.class));
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "task_reminders";
            CharSequence name = "Task Reminders";
            String description = "Notifications for task reminders";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            channel.enableVibration(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIF_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notifications disabled. Enable in settings.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
