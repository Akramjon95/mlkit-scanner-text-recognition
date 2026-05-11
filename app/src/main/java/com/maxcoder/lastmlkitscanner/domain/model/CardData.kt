package com.maxcoder.lastmlkitscanner.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CardData(
    val number: String = "",
    val expiry: String = "",
    val holderName: String = "",
    val voteCount: Int = 0,
    val total: Int = 0
) : Parcelable