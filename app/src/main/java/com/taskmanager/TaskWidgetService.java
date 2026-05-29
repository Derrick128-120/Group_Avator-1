package com.taskmanager;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.List;
import java.util.stream.Collectors;

public class TaskWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TaskRemoteViewsFactory(this.getApplicationContext());
    }
}

class TaskRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private final Context context;
    private List<Task> tasks;

    public TaskRemoteViewsFactory(Context context) {
        this.context = context;
    }

    @Override
    public void onCreate() {
        loadData();
    }

    @Override
    public void onDataSetChanged() {
        loadData();
    }

    private void loadData() {
        TaskRepository repository = new TaskRepository(context);
        tasks = repository.loadTasks().stream()
                .filter(t -> !t.isCompleted())
                .collect(Collectors.toList());
    }

    @Override
    public void onDestroy() {}

    @Override
    public int getCount() {
        return tasks == null ? 0 : tasks.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (tasks == null || position >= tasks.size()) return null;
        Task task = tasks.get(position);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_item_task);
        views.setTextViewText(R.id.widget_item_title, task.getTitle());
        views.setTextViewText(R.id.widget_item_info, task.getPriority() + " | " + task.getType());

        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
