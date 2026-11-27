package de.hotelremote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** App, Receiver und Benachrichtigungen nutzen eigene Instanzen auf derselben Datei: nichts darf verloren gehen. */
class RemoteStoreConcurrencyTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test fun twoInstancesWritingFromManyThreadsLoseNothing() {
        val file = File(tmp.root, "remotes.json")
        // Zweite Instanz über einen anderen, gleichwertigen Pfad.
        val stores = listOf(RemoteStore(file), RemoteStore(File(tmp.root, "./remotes.json")))
        stores[0].add(SavedRemote(id = "ac", lastUsed = 0, ac = testAc()))
        val threads = 8
        val perThread = 15
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val errors = mutableListOf<Throwable>()
        repeat(threads) { t ->
            pool.execute {
                start.await()
                val store = stores[t % 2]
                try {
                    repeat(perThread) { i ->
                        store.add(SavedRemote(id = "t$t-$i", lastUsed = (t * 100 + i).toLong()))
                        store.updateAcState("ac", AcState(true, "cool", 20.0 + (i % 5), "auto"))
                        store.markUsed("t$t-0", 10_000L + i)
                        store.all()
                    }
                } catch (e: Throwable) {
                    synchronized(errors) { errors += e }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))
        assertEquals(emptyList<Throwable>(), errors)
        val all = RemoteStore(file).all()
        assertEquals(1 + threads * perThread, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.first { it.id == "ac" }.ac != null)
        // Keine Reste: weder kaputte Datei (.bak) noch Temp-Dateien.
        assertEquals(listOf("remotes.json"), tmp.root.list()!!.sorted())
    }
}
