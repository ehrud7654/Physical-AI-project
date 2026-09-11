package com.ssafy.arpose

import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * rec 폴더를 계약 아카이브(파일명 shape)로 묶는다 — `{sessionId}.zip` (STORE).
 * 엔트리: meta.json · summary.json · frames.csv · pose.csv · imu.csv · gripper.csv · frames 이하 jpg
 *
 * S15P21A103-161. 지금은 **파일명·구조 우선** — 내부 컬럼/스키마 완벽화는 후속.
 * 현재 녹화 산출물(episode.json·summary.txt·poses.csv…)을 계약 이름으로 매핑한다.
 */
object EpisodePackager {
    private const val TAG = "PACKAGE"

    /** 계약 아카이브 zip 생성. 이미 있으면 재사용. 실패 시 null. */
    fun buildContractZip(dir: File, sessionId: String, outDir: File): File? {
        val sid = sessionId.ifBlank { readSessionId(dir) ?: return null }
        val zip = File(outDir, "$sid.zip")
        if (zip.exists() && zip.length() > 0) return zip
        val tmp = File(outDir, "$sid.zip.part")
        return try {
            ZipOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { zos ->
                zos.setMethod(ZipOutputStream.STORED)   // 압축률 0
                readBytes(dir, "episode.json")?.let { store(zos, "meta.json", it) }
                readBytes(dir, "summary.txt")?.let { store(zos, "summary.json", it) }
                readBytes(dir, "poses.csv")?.let { store(zos, "pose.csv", it) }
                readBytes(dir, "imu.csv")?.let { store(zos, "imu.csv", it) }
                store(zos, "gripper.csv", gripperCsv(dir))
                store(zos, "frames.csv", framesCsv(dir))
                File(dir, "frames").listFiles { f -> f.name.endsWith(".jpg") }
                    ?.sortedBy { it.name }
                    ?.forEach { store(zos, "frames/${it.name}", it.readBytes()) }
            }
            if (zip.exists()) zip.delete()
            tmp.renameTo(zip)
            zip
        } catch (e: Exception) {
            Log.e(TAG, "패키징 실패 ${dir.name}", e); tmp.delete(); null
        }
    }

    fun readSessionId(dir: File): String? =
        File(dir, "session_id.txt").let { if (it.exists()) it.readText().trim() else null }

    private fun readBytes(dir: File, name: String): ByteArray? =
        File(dir, name).let { if (it.exists()) it.readBytes() else null }

    /** poses.csv(index,timestamp_ns,…,image) → frames.csv(frame_index,t_ns,file_name) */
    private fun framesCsv(dir: File): ByteArray {
        val sb = StringBuilder("frame_index,t_ns,file_name\n")
        File(dir, "poses.csv").takeIf { it.exists() }?.useLines { lines ->
            lines.drop(1).forEach { line ->
                val c = line.split(",")
                if (c.size >= 13 && c[12].trim().isNotEmpty()) sb.append("${c[0]},${c[1]},${c[12].trim()}\n")
            }
        }
        return sb.toString().toByteArray()
    }

    /** 후처리 gripper.csv 있으면 그대로, 없으면 헤더만(온디바이스/PC 후처리가 채움). */
    private fun gripperCsv(dir: File): ByteArray =
        readBytes(dir, "gripper.csv") ?: "t_ns,openness,tracking_state\n".toByteArray()

    private fun store(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
        }
        zos.putNextEntry(entry); zos.write(bytes); zos.closeEntry()
    }
}
