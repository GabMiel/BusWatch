package com.example.buswatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR

class FAQAdapter(private val faqList: List<FAQListItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_QUESTION = 1
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeaderTitle: TextView = view.findViewById(CommonR.id.tvHeaderTitle)
    }

    class FAQViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestion: TextView = view.findViewById(CommonR.id.tvQuestion)
        val tvAnswer: TextView = view.findViewById(CommonR.id.tvAnswer)
        val ivChevron: ImageView = view.findViewById(CommonR.id.ivChevron)
    }

    override fun getItemViewType(position: Int): Int {
        return when (faqList[position]) {
            is FAQListItem.Header -> TYPE_HEADER
            is FAQListItem.Question -> TYPE_QUESTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(CommonR.layout.item_faq_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(CommonR.layout.item_faq, parent, false)
            FAQViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = faqList[position]) {
            is FAQListItem.Header -> {
                (holder as HeaderViewHolder).tvHeaderTitle.text = item.title
            }
            is FAQListItem.Question -> {
                val faqHolder = holder as FAQViewHolder
                faqHolder.tvQuestion.text = item.question
                faqHolder.tvAnswer.text = item.answer
                
                val isExpanded = item.isExpanded
                faqHolder.tvAnswer.visibility = if (isExpanded) View.VISIBLE else View.GONE
                faqHolder.ivChevron.rotation = if (isExpanded) 180f else 0f

                faqHolder.itemView.setOnClickListener {
                    item.isExpanded = !item.isExpanded
                    notifyItemChanged(position)
                }
            }
        }
    }

    override fun getItemCount() = faqList.size
}

sealed class FAQListItem {
    data class Header(val title: String) : FAQListItem()
    data class Question(
        val question: String,
        val answer: String,
        var isExpanded: Boolean = false
    ) : FAQListItem()
}

data class FAQItem(
    val question: String,
    val answer: String,
    var isExpanded: Boolean = false
)
