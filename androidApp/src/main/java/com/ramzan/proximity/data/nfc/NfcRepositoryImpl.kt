package com.ramzan.proximity.data.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.ramzan.proximity.core.Clock
import com.ramzan.proximity.core.DispatcherProvider
import com.ramzan.proximity.domain.model.DomainError
import com.ramzan.proximity.domain.model.DomainResult
import com.ramzan.proximity.domain.model.NfcReadEvent
import com.ramzan.proximity.domain.model.TagProblem
import com.ramzan.proximity.domain.repository.NfcRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class NfcRepositoryImpl(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : NfcRepository, NfcTagSink {
    private class PendingWrite(val text: String) {
        val result = CompletableDeferred<DomainResult<Unit>>()
    }

    private val reads = MutableSharedFlow<NfcReadEvent>(
        extraBufferCapacity = READ_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val pendingWrite = AtomicReference<PendingWrite?>(null)

    override val readEvents: Flow<NfcReadEvent> = reads

    override fun onTagDiscovered(tag: Tag) {
        scope.launch(dispatchers.io) {
            val pending = pendingWrite.getAndSet(null)
            if (pending != null) {
                pending.result.complete(write(tag, pending.text))
            } else {
                reads.emit(read(tag))
            }
        }
    }

    override suspend fun writeNextTag(text: String): DomainResult<Unit> {
        val pending = PendingWrite(text)
        if (!pendingWrite.compareAndSet(null, pending)) {
            return DomainResult.Failure(DomainError.Unknown("A write is already waiting for a tag"))
        }
        try {
            return pending.result.await()
        } finally {
            pendingWrite.compareAndSet(pending, null)
        }
    }

    private fun read(tag: Tag): NfcReadEvent {
        val now = clock.nowMs()
        val ndef = Ndef.get(tag) ?: return NfcReadEvent.Failed(DomainError.Tag(TagProblem.NOT_NDEF), now)
        return try {
            ndef.use {
                it.connect()
                val text = it.ndefMessage?.records.orEmpty().firstNotNullOfOrNull(::textOf)
                if (text != null) NfcReadEvent.Text(text, now) else NfcReadEvent.NoTextRecord(now)
            }
        } catch (error: Exception) {
            NfcReadEvent.Failed(DomainError.Tag(error.toProblem()), now)
        }
    }

    private fun write(tag: Tag, text: String): DomainResult<Unit> {
        val message = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", text)))
        val ndef = Ndef.get(tag)
        if (ndef != null) return writeNdef(ndef, message)
        val formatable = NdefFormatable.get(tag)
        if (formatable != null) return format(formatable, message)
        return DomainResult.Failure(DomainError.Tag(TagProblem.NOT_NDEF))
    }

    private fun writeNdef(ndef: Ndef, message: NdefMessage): DomainResult<Unit> = try {
        ndef.use {
            it.connect()
            when {
                !it.isWritable -> failure(TagProblem.READ_ONLY)
                message.toByteArray().size > it.maxSize -> failure(TagProblem.TOO_SMALL)
                else -> {
                    it.writeNdefMessage(message)
                    DomainResult.Success(Unit)
                }
            }
        }
    } catch (error: Exception) {
        failure(error.toProblem())
    }

    private fun format(formatable: NdefFormatable, message: NdefMessage): DomainResult<Unit> = try {
        formatable.use {
            it.connect()
            it.format(message)
            DomainResult.Success(Unit)
        }
    } catch (error: Exception) {
        failure(if (error is TagLostException) TagProblem.LOST else TagProblem.FORMAT_FAILED)
    }

    private fun textOf(record: NdefRecord): String? =
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
            decodeTextPayload(record.payload)
        } else {
            null
        }

    private fun Exception.toProblem(): TagProblem =
        if (this is TagLostException) TagProblem.LOST else TagProblem.IO_FAILURE

    private fun failure(problem: TagProblem) = DomainResult.Failure(DomainError.Tag(problem))

    private companion object {
        const val READ_BUFFER = 16
    }
}
