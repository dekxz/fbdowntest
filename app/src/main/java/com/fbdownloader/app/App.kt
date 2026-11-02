package com.fbdownloader.app

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import kotlin.concurrent.thread

class App : Application() {

    companion object {
        /** Completes with null on success, or with an error message. */
        val ready = CompletableDeferred<String?>()

        /** True when the bundled aria2c binary was initialised (optional speed-up). */
        @Volatile var aria2Ready = false
    }

    override fun onCreate() {
        super.onCreate()
        thread(name = "ytdlp-init") {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                ready.complete(null)
            } catch (e: Exception) {
                ready.complete("Failed to initialise yt-dlp: ${e.message}")
            }
            // aria2c is optional: a failure here must never break normal downloads.
            // Reflection so the init works whichever singleton style the library uses.
            try {
                val cls = Class.forName("com.yausername.aria2c.Aria2c")
                val instance = cls.getMethod("getInstance").invoke(null)
                cls.getMethod("init", android.content.Context::class.java).invoke(instance, this)
            } catch (_: Throwable) {
                // fall through; the native-lib check below decides
            }
            aria2Ready = File(applicationInfo.nativeLibraryDir, "libaria2c.so").exists()
        }
    }
}
