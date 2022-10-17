package org.pytorch.demo.handseye

import android.content.Context
import android.service.textservice.SpellCheckerService
import android.util.Log
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo

class MySpellCheckerSession : SpellCheckerService.Session(){
    private var mLocale : String? = ""
    override fun onCreate() {
        mLocale = locale
    }

    override fun onGetSuggestions(textInfo: TextInfo?, suggestionLimit: Int): SuggestionsInfo {
        val input = textInfo?.text
        Log.e("SPELL", input!!)
        val length = input.length
        val flags = if (length <= 3)
            SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY
        else if (length <= 20)
            SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
        else 0

        val suggestion = arrayOf("aaa", "bbb", "Candidate for $input", mLocale)
        Log.e("SPELL", suggestion.toString())
        return SuggestionsInfo(flags, suggestion)

    }
}