package com.example.buswatch

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.Timestamp

data class NotificationItem(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Timestamp? = null,
    val isRead: Boolean = false,
    val type: String = ""
)

class NotificationAdapter(
    private var notifications: List<NotificationItem>,
    private val onSelectionChanged: (Int) -> Unit,
    private val onNotificationClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    var isSelectionMode = false
        set(value) {
            field = value
            if (!value) selectedIds.clear()
            notifyDataSetChanged()
            onSelectionChanged(selectedIds.size)
        }
    
    val selectedIds = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivNotificationIcon)
        val title: TextView = view.findViewById(R.id.tvNotificationTitle)
        val message: TextView = view.findViewById(R.id.tvNotificationMessage)
        val time: TextView = view.findViewById(R.id.tvNotificationTime)
        val unreadIndicator: View = view.findViewById(R.id.unreadIndicator)
        val checkBox: CheckBox = view.findViewById(R.id.cbNotification)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = notifications[position]
        holder.title.text = item.title
        holder.message.text = item.message
        
        val timeStr = item.timestamp?.let {
            DateUtils.getRelativeTimeSpanString(it.seconds * 1000)
        } ?: ""
        holder.time.text = timeStr
        
        holder.unreadIndicator.visibility = if (item.isRead || isSelectionMode) View.GONE else View.VISIBLE
        
        // Selection Logic
        holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedIds.contains(item.id)

        val context = holder.itemView.context
        val lightYellowColor = ContextCompat.getColor(context, CommonR.color.yellow_light)
        
        val iconColor = Color.BLACK
        holder.icon.imageTintList = ColorStateList.valueOf(iconColor)
        holder.icon.backgroundTintList = ColorStateList.valueOf(lightYellowColor)

        when (item.type) {
            "trip_start" -> holder.icon.setImageResource(CommonR.drawable.ic_bus)
            "student_boarding" -> holder.icon.setImageResource(CommonR.drawable.child)
            "student_arrival" -> holder.icon.setImageResource(CommonR.drawable.ic_check)
            else -> holder.icon.setImageResource(CommonR.drawable.bell)
        }

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(item.id)
            } else {
                onNotificationClick(item)
            }
        }

        holder.checkBox.setOnClickListener {
            toggleSelection(item.id)
        }
    }

    private fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(notifications.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun unselectAll() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun isAllSelected(): Boolean {
        return notifications.isNotEmpty() && selectedIds.size == notifications.size
    }

    override fun getItemCount() = notifications.size

    fun updateList(newList: List<NotificationItem>) {
        val oldList = notifications
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition].id == newList[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition] == newList[newItemPosition]
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        notifications = newList.toList()
        diffResult.dispatchUpdatesTo(this)
        
        // If selection mode is active, check if we need to remove stale IDs
        if (isSelectionMode) {
            val currentIds = newList.map { it.id }.toSet()
            if (selectedIds.retainAll(currentIds)) {
                onSelectionChanged(selectedIds.size)
            }
        }
    }
}
