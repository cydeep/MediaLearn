package com.jadyn.mediakit.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Process
import android.util.Log
import com.jadyn.mediakit.function.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedDeque

/**
 *@version:
 *@FileDescription: 将 PCM 数据编码为 AAC 数据
 *@Author:Jing
 *@Since:2019-05-20
 *@ChangeList:
 */

/**
 * @param format 编码的AAC文件的数据参数:采样率、声道、比特率等
 * @param pcmDataQueue 供给编码器的PCM数据队列
 * */
class AudioEncoder(
        private val isRecording: List<Any>,
        private val format: MediaFormat,
        private val pcmDataQueue: ConcurrentLinkedDeque<ByteArray>,
        private val dataCallback: (ByteBuffer, MediaCodec.BufferInfo) -> Unit,
        private val formatChanged: (MediaFormat) -> Unit = {}) : Runnable {

    private val TAG = "AudioEncoder"
    private var isFormatChanged = false

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        var totalBytes = 0
        var presentationTimeUs = 0L
        var frameCount = 0

        val bufferInfo = MediaCodec.BufferInfo()
        // 循环的拿取PCM数据，编码为AAC数据。
        while (isRecording.isNotEmpty() || pcmDataQueue.isNotEmpty()) {
            Log.d(TAG, "audio encoder $pcmDataQueue")
            val bytes = pcmDataQueue.popSafe()
            bytes?.apply {
                val (id, inputBuffer) = codec.dequeueValidInputBuffer(1000)
                inputBuffer?.let {
                    totalBytes += size
                    it.clear()
                    it.put(this)
                    it.limit(size)
                    // 当输入数据全部处理完，需要向Codec发送end——stream的Flag
                    codec.queueInputBuffer(id, 0, size
                            , frameCount * format.aacPerFrameTime,
                            if (isEmpty()) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    presentationTimeUs = 1000000L * (totalBytes / 2) / format.sampleRate
                }
            }
            codec.handleOutputBuffer(bufferInfo, 1000, {
                // audio format changed
                if (!isFormatChanged) {
                    formatChanged.invoke(codec.outputFormat)
                    isFormatChanged = true
                }
            }, {
                val outputBuffer = codec.getOutputBuffer(it)
                if (bufferInfo.size > 0) {
                    Log.d(TAG, "buffer info size ${bufferInfo.toS()}")
                    Log.d(TAG, "output buffer $outputBuffer")
                    frameCount++
                    dataCallback.invoke(outputBuffer, bufferInfo)
                }
                codec.releaseOutputBuffer(it, false)
            })
        }
        codec.release()
    }

} 