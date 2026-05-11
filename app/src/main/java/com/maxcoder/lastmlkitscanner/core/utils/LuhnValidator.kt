package com.maxcoder.lastmlkitscanner.core.utils

fun validateLuhn(cardNumber: String): Boolean {
    var sum = 0
    var alternate = false
    for (i in cardNumber.length - 1 downTo 0) {
        var n = cardNumber[i].toString().toInt()
        if (alternate) {
            n *= 2
            if (n > 9) n -= 9
        }
        sum += n
        alternate = !alternate
    }
    return sum % 10 == 0
}