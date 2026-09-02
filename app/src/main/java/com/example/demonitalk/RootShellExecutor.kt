package com.example.demonitalk
import java.io.DataOutputStream

object RootShellExecutor {

    fun execute(command: Any): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            // Enviamos el comando a la shell root de la app
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
