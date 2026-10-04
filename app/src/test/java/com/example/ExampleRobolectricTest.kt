package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerController
import com.example.data.model.TtsDefaults
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify valid wav header conversion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SonicVoice TTS", appName)
        assertEquals("sonic-3.6", TtsDefaults.models.first().id)
        assertTrue(AudioPlayerController.containsBanglaCharacters("নমস্কার এবং স্বাগতম"))

        val tempWav = File(context.cacheDir, "test_out.wav")
        val rawPcm = ByteArray(4800) { (it % 120).toByte() }
        AudioPlayerController.writeRawPcmOrWavToStandardWavFile(
            inputBytes = rawPcm,
            defaultSampleRate = 24000,
            outputFile = tempWav
        )
        assertTrue(tempWav.exists())
        assertEquals(4844L, tempWav.length())

        val headerBytes = tempWav.readBytes()
        val riffSize = ByteBuffer.wrap(headerBytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val dataSize = ByteBuffer.wrap(headerBytes, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(4836, riffSize)
        assertEquals(4800, dataSize)
        tempWav.delete()
    }
}
