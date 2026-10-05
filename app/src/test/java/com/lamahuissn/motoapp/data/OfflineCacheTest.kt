package com.lamahuissn.motoapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The phone's copy of the office's last answer.
 *
 * The failure this has to prevent is «the app does not open without a network», and the failure it must
 * NOT introduce is «an old balance shown as if it were fresh». The tests are about those two, and about
 * one collector's copy never being read by another on a shared phone.
 */
class OfflineCacheTest {

    private fun cache(): Pair<OfflineCache, File> {
        val dir = Files.createTempDirectory("moto-offline").toFile()
        return OfflineCache(dir) to dir
    }

    @Test
    fun `a stored answer comes back word for word`() {
        val (store, _) = cache()
        val body = """{"period":"2026-10","areas":[{"id":1,"name":"شارع الدعبول"}]}"""

        store.put(1, OfflineCache.BOARD, body)

        assertEquals(body, store.body(1, OfflineCache.BOARD))
        assertTrue(store.storedAt(1, OfflineCache.BOARD)!!.startsWith("20"))
    }

    @Test
    fun `the copy survives the app being killed`() {
        val (first, dir) = cache()
        first.put(1, OfflineCache.house(10), """{"house":{"id":10,"name":"بيت أبو علي"}}""")

        // A fresh cache on the same directory is what the next launch reads.
        assertEquals(
            """{"house":{"id":10,"name":"بيت أبو علي"}}""",
            OfflineCache(dir).body(1, OfflineCache.house(10)),
        )
    }

    @Test
    fun `the keys of a sheet and a house do not collide`() {
        val (store, _) = cache()
        store.put(1, OfflineCache.sheet(4), "sheet-body")
        store.put(1, OfflineCache.house(4), "house-body")

        assertEquals("sheet-body", store.body(1, OfflineCache.sheet(4)))
        assertEquals("house-body", store.body(1, OfflineCache.house(4)))
        assertEquals("sheet-4", OfflineCache.sheet(4))
        assertEquals("house-4", OfflineCache.house(4))
    }

    @Test
    fun `one collector's copy is not another's`() {
        val (store, _) = cache()
        store.put(1, OfflineCache.BOARD, "mine")

        assertNull(store.body(2, OfflineCache.BOARD))
    }

    @Test
    fun `signing out clears the figures and leaves nothing to read`() {
        val (store, _) = cache()
        store.put(1, OfflineCache.BOARD, "body")
        store.put(2, OfflineCache.BOARD, "his body")

        store.clear(1)

        assertNull(store.body(1, OfflineCache.BOARD))
        assertEquals("his body", store.body(2, OfflineCache.BOARD))
    }

    @Test
    fun `nothing stored is nothing read, never a failure`() {
        val (store, _) = cache()

        assertNull(store.body(1, OfflineCache.BOARD))
        assertNull(store.storedAt(1, OfflineCache.BOARD))
    }

    @Test
    fun `a corrupt copy is no copy`() {
        val (store, dir) = cache()
        store.put(1, OfflineCache.BOARD, "body")

        File(File(dir, "1"), "board.json").writeText("{not json")

        assertNull(store.body(1, OfflineCache.BOARD))
        assertFalse(File(File(dir, "1"), "board.json").length() == 0L)
    }
}
