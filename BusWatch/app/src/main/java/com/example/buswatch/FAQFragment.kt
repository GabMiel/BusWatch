package com.example.buswatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR

class FAQFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(CommonR.layout.fragment_faq, container, false)

        val btnBack = view.findViewById<ImageButton>(CommonR.id.btnBackFAQ)
        val rvFAQ = view.findViewById<RecyclerView>(CommonR.id.rvFAQ)

        btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        val faqList = mutableListOf<FAQListItem>()

        // 1. General Tracking
        faqList.add(FAQListItem.Header(getString(CommonR.string.faq_category_tracking)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q1), getString(CommonR.string.faq_a1)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q4), getString(CommonR.string.faq_a4)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q9), getString(CommonR.string.faq_a9)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q11), getString(CommonR.string.faq_a11)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q13), getString(CommonR.string.faq_a13)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q14), getString(CommonR.string.faq_a14)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q16), getString(CommonR.string.faq_a16)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q25), getString(CommonR.string.faq_a25)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q27), getString(CommonR.string.faq_a27)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q29), getString(CommonR.string.faq_a29)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q30), getString(CommonR.string.faq_a30)))

        // 2. Notifications & Settings
        faqList.add(FAQListItem.Header(getString(CommonR.string.faq_category_notifications)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q2), getString(CommonR.string.faq_a2)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q22), getString(CommonR.string.faq_a22)))

        // 3. Account & Profile
        faqList.add(FAQListItem.Header(getString(CommonR.string.faq_category_account)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q3), getString(CommonR.string.faq_a3)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q7), getString(CommonR.string.faq_a7)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q10), getString(CommonR.string.faq_a10)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q12), getString(CommonR.string.faq_a12)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q15), getString(CommonR.string.faq_a15)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q18), getString(CommonR.string.faq_a18)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q20), getString(CommonR.string.faq_a20)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q24), getString(CommonR.string.faq_a24)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q26), getString(CommonR.string.faq_a26)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q28), getString(CommonR.string.faq_a28)))

        // 4. Security & Safety
        faqList.add(FAQListItem.Header(getString(CommonR.string.faq_category_safety)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q5), getString(CommonR.string.faq_a5)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q8), getString(CommonR.string.faq_a8)))

        // 5. Support & Feedback
        faqList.add(FAQListItem.Header(getString(CommonR.string.faq_category_support)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q6), getString(CommonR.string.faq_a6)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q17), getString(CommonR.string.faq_a17)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q19), getString(CommonR.string.faq_a19)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q21), getString(CommonR.string.faq_a21)))
        faqList.add(FAQListItem.Question(getString(CommonR.string.faq_q23), getString(CommonR.string.faq_a23)))

        rvFAQ.layoutManager = LinearLayoutManager(requireContext())
        rvFAQ.adapter = FAQAdapter(faqList)

        return view
    }
}
