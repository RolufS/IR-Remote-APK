package de.hotelremote.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CodeDatabaseTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val source: () -> InputStream =
        { javaClass.classLoader!!.getResourceAsStream("codes-fixture.db")!! }

    @Test fun copiesAssetOnFirstOpen() {
        CodeDatabase.open(ctx, stamp = 1, source = source).use { db ->
            assertTrue(File(ctx.noBackupFilesDir, "codes-1.db").exists())
            assertTrue(CodeRepository(db).meta().isNotEmpty())
        }
    }

    @Test fun newInstallStampRecopiesDatabase() {
        CodeDatabase.open(ctx, stamp = 1, source = source).close()
        CodeDatabase.open(ctx, stamp = 2, source = source).close()
        assertTrue(File(ctx.noBackupFilesDir, "codes-2.db").exists())
        assertFalse(File(ctx.noBackupFilesDir, "codes-1.db").exists())
    }
}
