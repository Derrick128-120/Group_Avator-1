package com.taskmanager;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private final List<Task> tasks;
    private final Context context;
    private final OnTaskListener listener;

    // -------------------------------------------------------
    //  Listener interface – MainActivity implements this
    // -------------------------------------------------------
    public interface OnTaskListener {
        void onDelete(int position);
        void onToggleComplete(int position);
        void onEdit(int position);
    }

    public TaskAdapter(Context context, List<Task> tasks, OnTaskListener listener) {
        this.context  = context;
        this.tasks    = tasks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);

        // --- Title + strikethrough ---
        holder.tvTitle.setText(task.getTitle());
        if (task.isCompleted()) {
            holder.tvTitle.setPaintFlags(
                    holder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvTitle.setAlpha(0.45f);
            holder.itemView.setAlpha(0.75f);
        } else {
            holder.tvTitle.setPaintFlags(
                    holder.tvTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvTitle.setAlpha(1f);
            holder.itemView.setAlpha(1f);
        }

        // Prevent checkbox listener from firing during rebind
        holder.cbComplete.setOnCheckedChangeListener(null);
        holder.cbComplete.setChecked(task.isCompleted());

        // --- Priority badge + Type ---
        String priorityText = "● " + task.getPriority();
        if (task.getType() != null && !task.getType().isEmpty()) {
            priorityText += " | " + task.getType();
        }
        holder.tvPriority.setText(priorityText);

        switch (task.getPriority()) {
            case "High":
                holder.tvPriority.setText("● High");
                holder.tvPriority.setTextColor(context.getColor(R.color.priority_high));
                break;
            case "Medium":
                holder.tvPriority.setText("● Medium");
                holder.tvPriority.setTextColor(context.getColor(R.color.priority_medium));
                break;
            default: // Low
                holder.tvPriority.setText("● Low");
                holder.tvPriority.setTextColor(context.getColor(R.color.priority_low));
                break;
        }

        // --- Alarm time ---
        if (task.getAlarmTimeMillis() > 0 && !task.isCompleted()) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault());
            holder.tvAlarmTime.setText(context.getString(R.string.alarm_item_prefix, sdf.format(new Date(task.getAlarmTimeMillis()))));
            holder.tvAlarmTime.setVisibility(View.VISIBLE);
        } else {
            holder.tvAlarmTime.setVisibility(View.GONE);
        }

        // --- Location ---
        if (task.getLocation() != null && !task.getLocation().isEmpty()) {
            holder.tvLocation.setText(task.getLocation());
            holder.tvLocation.setVisibility(View.VISIBLE);
        } else {
            holder.tvLocation.setVisibility(View.GONE);
        }

        // --- Click listeners ---
        holder.cbComplete.setOnCheckedChangeListener((btn, isChecked) ->
                listener.onToggleComplete(holder.getAdapterPosition()));

        holder.btnDelete.setOnClickListener(v ->
                listener.onDelete(holder.getAdapterPosition()));

        holder.btnEdit.setOnClickListener(v ->
                listener.onEdit(holder.getAdapterPosition()));

        holder.itemView.setOnLongClickListener(v -> {
            listener.onEdit(holder.getAdapterPosition());
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    // -------------------------------------------------------
    //  ViewHolder
    // -------------------------------------------------------
    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView    tvTitle, tvPriority, tvAlarmTime, tvLocation;
        CheckBox    cbComplete;
        ImageButton btnDelete, btnEdit;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle    = itemView.findViewById(R.id.tv_task_title);
            tvPriority = itemView.findViewById(R.id.tv_priority);
            tvAlarmTime = itemView.findViewById(R.id.tv_alarm_time);
            tvLocation = itemView.findViewById(R.id.tv_location);
            cbComplete = itemView.findViewById(R.id.cb_complete);
            btnDelete  = itemView.findViewById(R.id.btn_delete);
            btnEdit    = itemView.findViewById(R.id.btn_edit);
        }
    }
}
