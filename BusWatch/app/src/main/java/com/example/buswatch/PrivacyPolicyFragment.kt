package com.example.buswatch

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.buswatch.common.R as CommonR

class PrivacyPolicyFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Using common layout
        val view = inflater.inflate(CommonR.layout.fragment_privacy_policy, container, false)

        val btnBack = view.findViewById<ImageButton>(CommonR.id.btnBackPrivacy)
        val tvContent = view.findViewById<TextView>(CommonR.id.tvPrivacyContent)

        btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        val privacyContent = getString(CommonR.string.privacy_content)
        tvContent.text = Html.fromHtml(privacyContent, Html.FROM_HTML_MODE_COMPACT)
        
        tvContent.movementMethod = LinkMovementMethod.getInstance()

        return view
    }
}
