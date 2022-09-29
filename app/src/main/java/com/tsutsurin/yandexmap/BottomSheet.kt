package com.tsutsurin.yandexmap

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

class BottomSheet(context: Context): BottomSheetDialog(context) {

    var textView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bottom_sheet)
        textView = findViewById(R.id.textView)
    }
}