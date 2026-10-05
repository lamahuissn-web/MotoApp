package com.lamahuissn.motoapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The queue's file, and what survives a bad night on a phone.
 *
 * The bodies are real files in a temp directory, written by one store and read by the next — because the
 * failure this has to prevent is not «the encode threw», it is «the app was killed and the collection the
 * collector is holding cash for is gone». A simulated restart is the only test that speaks to that.
 */
class WriteQueueStoreTest {

    private fun tempStore(): Pair<WriteQueueStore, File> {
        val dir = Files.createTempDirectory("moto-queue").toFile()
        val file = File(dir, WriteQueueStore.FILE_NAME)
        return WriteQueueStore(file) to file
    }

    private val reading = QueuedWrite(
        kind = QueuedWrite.Kind.Reading,
        key = "moto-9f1c2ab34de5",
        userId = 1,
        houseId = 10,
        houseName = "بيت أبو علي",
        createdAt = "2026-10-05T18:20:00",
        period = "2026-10",
        currentReading = "210.00",
        note = "قرأتها مع صاحب البيت",
    )

    private val collection = QueuedWrite(
        kind = QueuedWrite.Kind.Collection,
        key = "moto-77aa11bb22cc",
        userId = 1,
        houseId = 5,
        houseName = "مخبز الشارع",
        createdAt = "2026-10-05T18:25:00",
        collectedOn = "2026-10-05",
        amount = "37.50",
        currency = "usd",
        note = null,
        state = QueueState.Waiting,
        lastError = "no signal",
    )

    @Test
    fun `both kinds of write come back exactly as they went in`() {
        val (store, _) = tempStore()
        store.save(listOf(reading, collection))

        val read = store.load()

        assertEquals(2, read.size)
        assertEquals(reading, read[0])
        assertEquals(collection, read[1])
    }

    @Test
    fun `the queue survives the app being killed`() {
        val (first, file) = tempStore()
        first.save(listOf(reading, collection))

        // A different store on the same file is what the next launch of the app sees.
        val second = WriteQueueStore(file)

        assertEquals(listOf(reading, collection), second.load())
        assertEquals("moto-9f1c2ab34de5", second.load()[0].key)
    }

    @Test
    fun `a settled collection keeps its key and its receipt`() {
        val (store, file) = tempStore()
        val done = collection.copy(
            state = QueueState.Done,
            lastError = null,
            receiptText = "وصلنا منكم 37.50$ — شكرًا",
        )
        store.save(listOf(done))

        val reread = WriteQueueStore(file).load().single()

        assertEquals(QueueState.Done, reread.state)
        assertEquals("moto-77aa11bb22cc", reread.key)
        assertEquals("وصلنا منكم 37.50$ — شكرًا", reread.receiptText)
        assertNull(reread.lastError)
    }

    @Test
    fun `one collector's queue is not another's`() {
        val mine = reading.copy(key = "moto-mine-abcdefgh")
        val his = collection.copy(key = "moto-his-abcdefghi", userId = 2)

        assertEquals(listOf(mine.key), QueueDecision.forUser(listOf(mine, his), 1).map { it.key })
        assertEquals(listOf(his.key), QueueDecision.forUser(listOf(mine, his), 2).map { it.key })
        assertTrue(QueueDecision.forUser(listOf(mine), 3).isEmpty())
    }

    @Test
    fun `a refused collection is still refused after the app restarts`() {
        val (store, file) = tempStore()
        store.save(listOf(collection.copy(state = QueueState.NeedsReconciliation, lastError = "لا فاتورة غير مدفوعة")))

        val afterRestart = WriteQueueStore(file).load().single()

        // The consequence, not the spelling: a refusal that came back as «recorded» would be a
        // collection the app sends a second time — for money the office already said it will not take.
        assertEquals(QueueState.NeedsReconciliation, afterRestart.state)
        assertTrue(QueueDecision.openEntries(listOf(afterRestart)).isEmpty())
    }

    @Test
    fun `every state round-trips`() {
        val (store, _) = tempStore()
        val states = QueueState.values().toList()
        store.save(states.mapIndexed { index, state -> collection.copy(key = "moto-key-$index-abcdef", state = state) })

        assertEquals(states, store.load().map { it.state })
    }

    @Test
    fun `nothing stored yet is an empty queue, not a failure`() {
        val (store, _) = tempStore()

        assertEquals(emptyList<QueuedWrite>(), store.load())
        assertTrue(WriteQueueCodec.decode(null).isEmpty())
    }

    @Test
    fun `a half-written or corrupt file is an empty queue, never a crash`() {
        val dir = Files.createTempDirectory("moto-queue-bad").toFile()

        assertTrue(WriteQueueCodec.decode("{\"version\":1,\"entries\":[{\"kind\":\"reading\",").isEmpty())
        assertTrue(WriteQueueCodec.decode("not json at all").isEmpty())
        assertTrue(WriteQueueCodec.decode("").isEmpty())
        assertTrue(WriteQueueStore(File(dir, "missing.json")).load().isEmpty())
    }

    @Test
    fun `a row the app cannot read is dropped, the rest of the queue is kept`() {
        val text = WriteQueueCodec.encode(listOf(reading))
            .replace("\"reading\"", "\"something-else\"")

        assertTrue(WriteQueueCodec.decode(text).isEmpty())

        val mixed = WriteQueueCodec.encode(listOf(reading, collection))
            .replaceFirst("\"kind\":\"reading\"", "\"kind\":\"unknown\"")
        val kept = WriteQueueCodec.decode(mixed)

        assertEquals(1, kept.size)
        assertEquals(collection.key, kept.single().key)
    }

    @Test
    fun `a save replaces the queue whole — no entry is left behind`() {
        val (store, _) = tempStore()
        store.save(listOf(reading, collection))
        store.save(listOf(collection))

        assertEquals(listOf(collection), store.load())
    }

    @Test
    fun `a value that is JSON null is nothing, not the word null`() {
        val encoded = WriteQueueCodec.encode(listOf(collection.copy(note = null, receiptText = null)))

        val reread = WriteQueueCodec.decode(encoded).single()

        assertNull(reread.note)
        assertNull(reread.receiptText)
        assertFalse(encoded.contains("\"note\":\"null\""))
    }
}
