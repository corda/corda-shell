package net.corda.tools.shell

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.google.common.io.Closeables
import net.corda.client.jackson.internal.readValueAs
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowTimeWindow
import net.corda.core.internal.copyTo
import org.crsh.command.InvocationContext
import rx.Observable
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.io.path.inputStream

fun List<String>?.flattenInput(): String =
    if (this == null || isEmpty()) {
        ""
    } else {
        this.joinToString(" ").trim { it <= ' ' }
    }

//region Extra serializers
//
// These serializers are used to enable the user to specify objects that aren't natural data containers in the shell,
// and for the shell to print things out that otherwise wouldn't be usefully printable.

object ObservableSerializer : JsonSerializer<Observable<*>>() {
    override fun serialize(value: Observable<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString("(observable)")
    }
}

/**
 * String value deserialized to [UniqueIdentifier].
 * Any string value used as [UniqueIdentifier.externalId].
 * If string contains underscore(i.e. externalId_uuid) then split with it.
 *      Index 0 as [UniqueIdentifier.externalId]
 *      Index 1 as [UniqueIdentifier.id]
 * */
object UniqueIdentifierDeserializer : JsonDeserializer<UniqueIdentifier>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UniqueIdentifier {
        //Check if externalId and UUID may be separated by underscore.
        if (p.text.contains("_")) {
            val ids = p.text.split("_")
            //Create UUID object from string.
            val uuid: UUID = UUID.fromString(ids[1])
            //Create UniqueIdentifier object using externalId and UUID.
            return UniqueIdentifier(ids[0], uuid)
        }
        //Any other string used as externalId.
        return UniqueIdentifier.fromString(p.text)
    }
}

// An InputStream found in a response triggers a request to the user to provide somewhere to save it.
object InputStreamSerializer : JsonSerializer<InputStream>() {
    var invokeContext: InvocationContext<*>? = null

    override fun serialize(value: InputStream, gen: JsonGenerator, serializers: SerializerProvider) {

        value.use {
            val toPath = invokeContext!!.readLine("Path to save stream to (enter to ignore): ", true)
            if (toPath == null || toPath.isBlank()) {
                gen.writeString("<not saved>")
            } else {
                val path = Paths.get(toPath)
                it.copyTo(path)
                gen.writeString("<saved to: ${path.toAbsolutePath()}>")
            }
        }
    }
}

// A file name is deserialized to an InputStream if found.
object InputStreamDeserializer : JsonDeserializer<InputStream>() {
    // Keep track of them so we can close them later.
    private val streams = Collections.synchronizedSet(HashSet<InputStream>())

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): InputStream {
        val stream = object : BufferedInputStream(Paths.get(p.text).inputStream()) {
            override fun close() {
                super.close()
                streams.remove(this)
            }
        }
        streams += stream
        return stream
    }

    fun closeAll() {
        // Clone the set with toList() here so each closed stream can be removed from the set inside close().
        streams.toList().forEach { Closeables.closeQuietly(it) }
    }
}

@JsonDeserialize(using = InputDurationDeserializer::class)
interface InputDurationMixin

/**
 * java.time.Duration deserialization to be used to parse shell input
 * Can parse the human readable format like '10 MINUTES' as well as the
 * ISO like PT10M
 */
object InputDurationDeserializer : JsonDeserializer<Duration>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Duration {
        return try {
            parse(parser.text.trim())
        } catch (e: IllegalArgumentException) {
            throw JsonParseException(parser, "Invalid java.time.Duration format ${parser.text}: ${e.message}", e)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun parse(value: String): Duration {
        if (value.contains(',') || value.contains(' ')) {
            value.split(",", " ").map { it.trim() }.let { (sizeString, unitString) ->
                val size = sizeString.toLong()
                val unit = ChronoUnit.valueOf(unitString)
                return Duration.of(size, unit)
            }
        } else {
            return Duration.parse(value)
        }
    }
}

@JsonDeserialize(using = InputTimeWindowDeserializer::class)
interface InputTimeWindowMixin

/**
 * TimeWindow deserialization to be used to parse shell input
 * Can parse the human readable format like (The UTC specifier can be omitted)
 * {fromTime: "2007-12-04T10:15:30", untilTime: "2007-12-05T10:15:30Z"}
 */
object InputTimeWindowDeserializer : JsonDeserializer<TimeWindow>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): TimeWindow {
        return parser.readValueAs<TimeWindowJson>().run {
            when {
                fromTime != null && untilTime != null -> TimeWindow.between(fromTime!!.toInstant(), untilTime!!.toInstant())
                fromTime != null -> TimeWindow.fromOnly(fromTime!!.toInstant())
                untilTime != null -> TimeWindow.untilOnly(untilTime!!.toInstant())
                else -> throw JsonParseException(parser, "Neither fromTime nor untilTime exists for TimeWindow")
            }
        }
    }

    private fun String.toInstant(): Instant {
        val value = trim()
        if (value.endsWith("Z")) {
            return Instant.parse(value)
        }
        return try {
            LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
        } catch (e: DateTimeParseException) {
            ZonedDateTime.parse(value).toInstant()
        }
    }

    private data class TimeWindowJson constructor(var fromTime: String?, var untilTime: String?) {
        constructor() : this(null, null)
    }
}

@JsonDeserialize(using = InputFlowTimeWindowDeserializer::class)
interface InputFlowTimeWindowMixin

object InputFlowTimeWindowDeserializer : JsonDeserializer<FlowTimeWindow>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): FlowTimeWindow {
        return parser.readValueAs<TimeWindowJson>().run {
            when {
                fromTime != null && untilTime != null -> FlowTimeWindow.between(fromTime!!.toInstant(), untilTime!!.toInstant())
                fromTime != null -> FlowTimeWindow.fromOnly(fromTime!!.toInstant())
                untilTime != null -> FlowTimeWindow.untilOnly(untilTime!!.toInstant())
                else -> throw JsonParseException(parser, "Neither fromTime nor untilTime exists for FlowTimeWindow")
            }
        }
    }

    private fun String.toInstant(): Instant {
        val value = trim()
        if (value.endsWith("Z")) {
            return Instant.parse(value)
        }
        return try {
            LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
        } catch (e: DateTimeParseException) {
            ZonedDateTime.parse(value).toInstant()
        }
    }

    private data class TimeWindowJson constructor(var fromTime: String?, var untilTime: String?) {
        constructor() : this(null, null)
    }
}

//endregion
