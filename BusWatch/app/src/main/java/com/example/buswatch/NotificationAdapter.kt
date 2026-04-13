package com.example.buswatch

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
    private val onNotificationClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivNotificationIcon)
        val title: TextView = view.findViewById(R.id.tvNotificationTitle)
        val message: TextView = view.findViewById(R.id.tvNotificationMessage)
        val time: TextView = view.findViewById(R.id.tvNotificationTime)
        val unreadIndicator: View = view.findViewById(R.id.unreadIndicator)
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
        
        holder.unreadIndicator.visibility = if (item.isRead) View.GONE else View.VISIBLE
        
        // Dynamic icon based on type or content
        when {
            item.title.contains("Approved", ignoreCase = true) -> {
                holder.icon.setImageResource(CommonR.drawable.ic_check)
                holder.icon.backgroundTintList = holder.itemView.context.getColorStateList(android.R.color.holo_green_light)
            }
            item.title.contains("Rejected", ignoreCase = true) -> {
                holder.icon.setImageResource(CommonR.drawable.ic_close)
                holder.icon.backgroundTintList = holder.itemView.context.getColorStateList(android.R.color.holo_red_light)
            }
            else -> {
                holder.icon.setImageResource(CommonR.drawable.ic_search)
                holder.icon.backgroundTintList = holder.itemView.context.getColorStateList(CommonR.color.yellow_primary)
            }
        }

        holder.itemView.setOnClickListener { onNotificationClick(item) }
    }

    override fun getItemCount() = notifications.size

    fun updateList(newList: List<NotificationItem>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = notifications.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return notifications[oldItemPosition].id == newList[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return notifications[oldItemPosition] == newList[newItemPosition]
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        notifications = newList
        diffResult.dispatchUpdatesTo(this)
    }
}
