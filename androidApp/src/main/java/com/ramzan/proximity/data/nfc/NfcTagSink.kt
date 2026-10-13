package com.ramzan.proximity.data.nfc

import android.nfc.Tag

/** Entry point for tags delivered by the Activity's NFC reader mode. */
fun interface NfcTagSink {
    fun onTagDiscovered(tag: Tag)
}
