package com.lucho314.spotter.data.garmin.fit

import java.io.ByteArrayOutputStream

private const val FIT_PROTOCOL_VERSION = 0x20
private const val FIT_HEADER_SIZE = 0x0E
private const val FIT_PROFILE_VERSION = 21217

/**
 * Minimal, hand-written FIT (Flexible and Interoperable data Transfer) binary encoder: only what
 * [com.lucho314.spotter.data.garmin.fit.StrengthActivityFitEncoder] needs (definition + data
 * records, little-endian, one global message per local number). See the FIT SDK's protocol
 * documentation for the wire format this mirrors.
 */
class FitWriter {
    private data class Definition(val globalNum: Int, val fields: List<FitFieldDef>)

    private val definitions = HashMap<Int, Definition>()
    private val records = ByteArrayOutputStream()
    private var finished = false

    fun define(localNum: Int, globalNum: Int, fields: List<FitFieldDef>) {
        require(localNum in 0..15) { "localNum must be 0..15, was $localNum" }
        require(fields.isNotEmpty()) { "a definition needs at least one field" }
        definitions[localNum] = Definition(globalNum, fields)

        records.write(0x40 or localNum)
        records.write(0x00) // reserved
        records.write(0x00) // architecture: little-endian
        writeUInt16LE(records, globalNum)
        records.write(fields.size)
        for (field in fields) {
            records.write(field.num)
            records.write(field.type.size)
            records.write(field.type.id)
        }
    }

    /** [values] must have the same size and order as the fields passed to the matching [define] call. `null` values become their type's "invalid" sentinel. */
    fun write(localNum: Int, values: List<Long?>) {
        val definition = requireNotNull(definitions[localNum]) { "write($localNum): no matching define() call" }
        require(values.size == definition.fields.size) {
            "write($localNum): expected ${definition.fields.size} values, got ${values.size}"
        }
        records.write(localNum and 0x0F)
        values.zip(definition.fields).forEach { (value, field) ->
            writeValue(records, field.type, value ?: field.type.invalid)
        }
    }

    fun finish(): ByteArray {
        check(!finished) { "finish() must be called only once" }
        finished = true
        val dataBytes = records.toByteArray()
        val header = buildHeader(dataBytes.size)
        val withoutCrc = header + dataBytes
        val crc = FitCrc.compute(withoutCrc)
        return withoutCrc + shortLE(crc)
    }

    private fun writeValue(out: ByteArrayOutputStream, type: FitBaseType, value: Long) {
        val maxValue = when (type.size) {
            1 -> 0xFFL
            2 -> 0xFFFFL
            4 -> 0xFFFFFFFFL
            else -> error("unsupported field size ${type.size}")
        }
        require(value in 0..maxValue) { "value $value out of range for $type" }
        for (i in 0 until type.size) {
            out.write(((value shr (8 * i)) and 0xFF).toInt())
        }
    }

    private fun writeUInt16LE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun buildHeader(dataSize: Int): ByteArray {
        val header = ByteArray(FIT_HEADER_SIZE)
        header[0] = FIT_HEADER_SIZE.toByte()
        header[1] = FIT_PROTOCOL_VERSION.toByte()
        shortLE(FIT_PROFILE_VERSION).copyInto(header, 2)
        intLE(dataSize).copyInto(header, 4)
        header[8] = '.'.code.toByte()
        header[9] = 'F'.code.toByte()
        header[10] = 'I'.code.toByte()
        header[11] = 'T'.code.toByte()
        shortLE(FitCrc.compute(header, 0, 12)).copyInto(header, 12)
        return header
    }

    private fun shortLE(value: Int): ByteArray = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    private fun intLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
}
