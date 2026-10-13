package com.ramzan.proximity

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ramzan.proximity.data.nfc.NfcTagSink
import com.ramzan.proximity.ui.navigation.MainScaffold
import com.ramzan.proximity.ui.theme.ProximityTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private val tagSink: NfcTagSink by inject()
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProximityTheme { MainScaffold() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reader mode keeps tag handling inside this app and suppresses the system tag chooser.
        nfcAdapter?.takeIf { it.isEnabled }?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V,
            null,
        )
    }

    override fun onPause() {
        nfcAdapter?.let { runCatching { it.disableReaderMode(this) } }
        super.onPause()
    }

    override fun onTagDiscovered(tag: Tag) = tagSink.onTagDiscovered(tag)
}
