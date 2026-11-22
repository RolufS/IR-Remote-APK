package de.hotelremote.data

import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import java.io.File
import java.io.InputStream

object CodeDatabase {
    fun open(
        context: Context,
        stamp: Long = installStamp(context),
        source: () -> InputStream = { context.assets.open("codes.db") },
    ): SQLiteDatabase {
        val dir = context.noBackupFilesDir
        val target = File(dir, "codes-$stamp.db")
        if (!target.exists()) {
            val tmp = File(dir, "codes-$stamp.db.tmp")
            source().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                error("Code-Datenbank konnte nicht angelegt werden")
            }
        }
        dir.listFiles { f -> f.name.startsWith("codes-") && f.name.endsWith(".db") && f.name != target.name }
            ?.forEach { it.delete() }
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun installStamp(context: Context): Long {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        return info.lastUpdateTime
    }
}
