package com.example.demonitalk

import android.os.Build
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit

object ShellUtils {

    private var rootGranted: Boolean? = null

    fun resetRootCache() {
        rootGranted = null
    }

    fun isRootAvailable(): Boolean {
        // Si ya sabemos el resultado, lo devolvemos para no re-ejecutar comandos lentos
        rootGranted?.let { return it }

        return try {
            // Intentamos ejecutar id para ver si realmente tenemos permisos root
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            
            val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(1, TimeUnit.SECONDS)
            } else {
                process.waitFor() == 0
            }

            val isRoot = finished && process.exitValue() == 0
            rootGranted = isRoot
            isRoot
        } catch (e: Exception) {
            rootGranted = false
            false
        }
    }

    fun executeCommand(command: String): Boolean {
        // Si no tenemos root, no perdemos tiempo intentando ejecutar 'su'
        if (!isRootAvailable()) return false

        var process: Process? = null
        var os: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val finished = process.waitFor(2, TimeUnit.SECONDS)
                finished && process.exitValue() == 0
            } else {
                process.waitFor() == 0
            }
        } catch (e: Exception) {
            false
        } finally {
            try { os?.close() } catch (e: Exception) {}
            try { process?.destroy() } catch (e: Exception) {}
        }
    }
}
