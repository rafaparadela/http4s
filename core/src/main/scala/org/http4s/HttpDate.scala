package org.http4s

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.http4s.internal.parboiled2.{Parser, ParserInput}
import org.http4s.parser.AdditionalRules
import org.http4s.util.{Renderable, Writer}

/**
 * An HTTP-date value represents time as an instance of Coordinated Universal
 * Time (UTC). It expresses time at a resolution of one second.  By using it
 * over java.time.Instant in the model, we assure that if two headers render
 * equally, their values are equal.
 *
 * @see https://tools.ietf.org/html/rfc7231#page65
 */
class HttpDate private (val epochSecond: Long)
    extends Renderable
    with Ordered[HttpDate] {
  def compare(that: HttpDate): Int =
    this.epochSecond.compare(that.epochSecond)

  def toInstant: Instant =
    Instant.ofEpochSecond(epochSecond)

  def render(writer: Writer): writer.type =
    writer << toInstant

  override def equals(o: Any): Boolean =
    o match {
      case that: HttpDate => this.epochSecond == that.epochSecond
      case _ => false
    }

  override def hashCode(): Int =
    epochSecond.##
}

object HttpDate {
  private val dateFormat =
    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
      .withLocale(Locale.US)
      .withZone(ZoneId.of("GMT"))

  /**
   * Constructs an `HttpDate` from the current time. Starting on January 1,n
   * 10000, this will throw an exception. The author intends to leave this
   * problem for future generations.
   */
  def now: HttpDate = 
    unsafeFromInstant(Instant.now)

  /** The `HttpDate` equal to `Thu, Jan 01 1970 00:00:00 GMT` */
  val Epoch: HttpDate =
    unsafeFromEpochSecond(0)

  private val MinEpochSecond = -62135596800L

  /** The earliest value reprsentable by RFC1123, `Mon, 01 Jan 0001 00:00:00 GMT`. */
  val MinValue = HttpDate.unsafeFromEpochSecond(MinEpochSecond)

  private val MaxEpochSecond = 253402300799L

  /** The latest value reprsentable by RFC1123, `Fri, 31 Dec 9999 23:59:59 GMT`. */
  val MaxValue = HttpDate.unsafeFromEpochSecond(MaxEpochSecond)

  /** Parses a date according to RFC7321, Section 7.1.1.1 */
  def fromString(s: String): ParseResult[HttpDate] =
    AdditionalRules.httpDate(s)

  /** Like `fromString`, but throws on invalid input */
  def unsafeFromString(s: String): HttpDate =
    fromString(s).fold(throw _, identity)

  /** Constructs a date from the seconds since the [[Epoch]]. If out of range,
   *  returns a ParseFailure. */
  def fromEpochSecond(epochSecond: Long): ParseResult[HttpDate] =
    if (epochSecond < MinEpochSecond || epochSecond > MaxEpochSecond)
      ParseResult.fail("Invalid HTTP date", s"${epochSecond} out of range for HTTP date. Must be between ${MinEpochSecond} and ${MaxEpochSecond}, inclusive")
    else
      ParseResult.success(new HttpDate(epochSecond))

  /** Like `fromEpochSecond`, but throws any parse failures */
  def unsafeFromEpochSecond(epochSecond: Long): HttpDate =
    fromEpochSecond(epochSecond).fold(throw _, identity)

  /** Constructs a date from an instant, truncating to the most recent second. If
   *  out of range, returns a ParseFailure. */
  def fromInstant(instant: Instant): ParseResult[HttpDate] =
    fromEpochSecond(instant.toEpochMilli / 1000)

  /** Like `fromInstant`, but throws any parse failures */
  def unsafeFromInstant(instant: Instant): HttpDate =
    unsafeFromEpochSecond(instant.toEpochMilli / 1000)

  /** Constructs a date from an zoned date-time, truncating to the most recent
   *  second. If out of range, returns a ParseFailure. */
  def fromZonedDateTime(dateTime: ZonedDateTime): ParseResult[HttpDate] =
    fromInstant(dateTime.toInstant)

  /** Like `fromZonedDateTime`, but throws any parse failures */
  def unsafeFromZonedDateTime(dateTime: ZonedDateTime): HttpDate =
    unsafeFromInstant(dateTime.toInstant)
}
