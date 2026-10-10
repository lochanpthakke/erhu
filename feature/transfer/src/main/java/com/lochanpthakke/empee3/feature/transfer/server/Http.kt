package com.lochanpthakke.empee3.feature.transfer.server

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.util.Locale

/** HTTP status codes used by the transfer server. */
object HttpStatus {
    const val OK = 200
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val METHOD_NOT_ALLOWED = 405
    const val REQUEST_TIMEOUT = 408
    const val CONFLICT = 409
    const val LENGTH_REQUIRED = 411
    const val PAYLOAD_TOO_LARGE = 413
    const val UNSUPPORTED_MEDIA_TYPE = 415
    const val MISDIRECTED_REQUEST = 421
    const val TOO_MANY_REQUESTS = 429
    const val HEADERS_TOO_LARGE = 431
    const val INTERNAL_ERROR = 500
    const val SERVICE_UNAVAILABLE = 503
    const val INSUFFICIENT_STORAGE = 507
}

/** A request rejected before routing (malformed, too large, unsupported). */
class HttpException(
    val status: Int,
    message: String,
) : Exception(message)

/** Request line and headers; the body stays in the socket stream and is read by the route. */
class HttpRequest(
    val method: String,
    val path: String,
    private val headers: Map<String, String>,
) {
    fun header(name: String): String? = headers[name.lowercase(Locale.ROOT)]

    /** Content-Length, or null when absent. Malformed or negative values are rejected while parsing. */
    val contentLength: Long? get() = header("content-length")?.toLong()
}

/**
 * Minimal HTTP/1.1 request parsing for the transfer page: one request per connection, a bounded
 * head, Content-Length bodies only (no chunked requests), no keep-alive.
 */
object HttpParser {
    private const val MAX_HEAD_BYTES = 16 * 1024
    private const val MAX_HEADERS = 64
    private const val REQUEST_LINE_PARTS = 3

    /** Progress through "

", the end of the head. */
    private const val NOTHING = 0
    private const val AFTER_CR = 1
    private const val AFTER_CRLF = 2
    private const val AFTER_CRLF_CR = 3
    private const val BLANK_LINE = 4
    private val METHOD = Regex("^[A-Z]{3,7}$")

    /** Every malformed part is its own rejection, hence many throws. */
    @Suppress("ThrowsCount")
    @Throws(HttpException::class, java.io.IOException::class)
    fun readHead(input: InputStream): HttpRequest {
        val head = readUntilBlankLine(input)
        val lines = head.split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size != REQUEST_LINE_PARTS ||
            !METHOD.matches(parts[0]) ||
            !parts[2].startsWith("HTTP/1.")
        ) {
            throw HttpException(HttpStatus.BAD_REQUEST, "Bad request line")
        }
        val target = parts[1]
        if (!target.startsWith("/")) throw HttpException(HttpStatus.BAD_REQUEST, "Bad request target")
        val path = target.substringBefore('?').substringBefore('#')
        val headers = HashMap<String, String>()
        val headerLines = lines.drop(1).filter { it.isNotEmpty() }
        if (headerLines.size > MAX_HEADERS) throw HttpException(HttpStatus.HEADERS_TOO_LARGE, "Too many headers")
        for (line in headerLines) {
            val colon = line.indexOf(':')
            if (colon <= 0) throw HttpException(HttpStatus.BAD_REQUEST, "Bad header")
            val name = line.substring(0, colon).trim().lowercase(Locale.ROOT)
            val value = line.substring(colon + 1).trim()
            if (name == "content-length" &&
                headers.containsKey(name)
            ) {
                throw HttpException(HttpStatus.BAD_REQUEST, "Repeated Content-Length")
            }
            headers[name] = value
        }
        headers["content-length"]?.let {
            if (it.toLongOrNull()?.takeIf { n -> n >= 0 } ==
                null
            ) {
                throw HttpException(HttpStatus.BAD_REQUEST, "Bad Content-Length")
            }
        }
        if (headers["transfer-encoding"] != null) throw HttpException(HttpStatus.LENGTH_REQUIRED, "Send a Content-Length body")
        return HttpRequest(parts[0], decodePath(path), headers)
    }

    @Suppress("SwallowedException") // the bad encoding is reported as a 400, its details do not matter
    private fun decodePath(path: String): String =
        try {
            URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            throw HttpException(HttpStatus.BAD_REQUEST, "Bad path encoding: ${e.message}")
        }

    private fun readUntilBlankLine(input: InputStream): String {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (true) {
            val b = input.read()
            if (b == -1) throw HttpException(HttpStatus.BAD_REQUEST, "Incomplete request")
            buffer.write(b)
            if (buffer.size() > MAX_HEAD_BYTES) throw HttpException(HttpStatus.HEADERS_TOO_LARGE, "Request head too large")
            matched =
                when {
                    (matched == NOTHING || matched == AFTER_CRLF) && b == '\r'.code -> matched + 1
                    (matched == AFTER_CR || matched == AFTER_CRLF_CR) && b == '\n'.code -> matched + 1
                    b == '\r'.code -> 1
                    else -> 0
                }
            if (matched == BLANK_LINE) return buffer.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r\n\r\n")
        }
    }
}

/** A complete response (always small: pages, JSON results). Every response closes the connection. */
class HttpResponse(
    val status: Int,
    val contentType: String,
    val body: ByteArray,
) {
    fun writeTo(out: OutputStream) {
        val head =
            buildString {
                append("HTTP/1.1 ")
                    .append(status)
                    .append(' ')
                    .append(reason(status))
                    .append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Cache-Control: no-store\r\n")
                append("X-Content-Type-Options: nosniff\r\n")
                append("Referrer-Policy: no-referrer\r\n")
                append("X-Frame-Options: DENY\r\n")
                append("Content-Security-Policy: ").append(CSP).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    companion object {
        /** Everything the page needs is inline; it may only talk to this phone. */
        private const val CSP =
            "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; " +
                "img-src 'self' data:; form-action 'none'; frame-ancestors 'none'; base-uri 'none'"

        fun json(
            status: Int,
            vararg fields: Pair<String, Any?>,
        ): HttpResponse = HttpResponse(status, "application/json; charset=utf-8", Json.obj(*fields).toByteArray(Charsets.UTF_8))

        fun error(
            status: Int,
            message: String,
        ): HttpResponse = json(status, "status" to "error", "message" to message)

        @Suppress("CyclomaticComplexMethod")
        private fun reason(status: Int): String =
            when (status) {
                HttpStatus.OK -> "OK"
                HttpStatus.BAD_REQUEST -> "Bad Request"
                HttpStatus.UNAUTHORIZED -> "Unauthorized"
                HttpStatus.FORBIDDEN -> "Forbidden"
                HttpStatus.NOT_FOUND -> "Not Found"
                HttpStatus.METHOD_NOT_ALLOWED -> "Method Not Allowed"
                HttpStatus.REQUEST_TIMEOUT -> "Request Timeout"
                HttpStatus.CONFLICT -> "Conflict"
                HttpStatus.LENGTH_REQUIRED -> "Length Required"
                HttpStatus.PAYLOAD_TOO_LARGE -> "Payload Too Large"
                HttpStatus.UNSUPPORTED_MEDIA_TYPE -> "Unsupported Media Type"
                HttpStatus.MISDIRECTED_REQUEST -> "Misdirected Request"
                HttpStatus.TOO_MANY_REQUESTS -> "Too Many Requests"
                HttpStatus.HEADERS_TOO_LARGE -> "Request Header Fields Too Large"
                HttpStatus.SERVICE_UNAVAILABLE -> "Service Unavailable"
                HttpStatus.INSUFFICIENT_STORAGE -> "Insufficient Storage"
                else -> "Error"
            }
    }
}

/** Just enough JSON for flat result objects. */
internal object Json {
    fun obj(vararg fields: Pair<String, Any?>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (key, value) -> "${string(key)}:${value(value)}" }

    private fun value(v: Any?): String =
        when (v) {
            null -> "null"
            is Number, is Boolean -> v.toString()
            else -> string(v.toString())
        }

    @Suppress("MagicNumber")
    fun string(s: String): String =
        buildString {
            append('"')
            for (c in s) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '<' -> append("\\u003c")
                    c.code < 0x20 -> append(String.format(Locale.ROOT, "\\u%04x", c.code))
                    else -> append(c)
                }
            }
            append('"')
        }
}
