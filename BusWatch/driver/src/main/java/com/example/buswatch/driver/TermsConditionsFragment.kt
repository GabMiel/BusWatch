package com.example.buswatch.driver

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.buswatch.common.R as CommonR

class TermsConditionsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Using the layout from common
        val view = inflater.inflate(CommonR.layout.fragment_terms_conditions, container, false)

        val btnBack = view.findViewById<ImageButton>(CommonR.id.btnBackTerms)
        val tvContent = view.findViewById<TextView>(CommonR.id.tvTermsContent)

        btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        val termsContent = getString(CommonR.string.terms_content)
        tvContent.text = Html.fromHtml(termsContent, Html.FROM_HTML_MODE_COMPACT)

        return view
    }
}
