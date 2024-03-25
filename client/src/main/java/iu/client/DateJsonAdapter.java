package iu.client;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Date}
 */
class DateJsonAdapter implements IuJsonAdapter<Date> {

	/**
	 * Singleton instance.
	 */
	static final DateJsonAdapter INSTANCE = new DateJsonAdapter();

	private final ZoneId UTC = ZoneId.of("UTC");
	private final DateTimeFormatter DF = DateTimeFormatter.ISO_DATE.withZone(UTC);
	private final DateTimeFormatter DTF = DateTimeFormatter.ISO_DATE_TIME.withZone(UTC);

	private DateJsonAdapter() {
	}

	@Override
	public Date fromJson(JsonValue value) {
		final var text = TextJsonAdapter.INSTANCE.fromJson(value);
		if (text == null)
			return null;

		final Instant instant;
		if (text.indexOf('T') == -1)
			instant = LocalDate.parse(text, DateTimeFormatter.ISO_DATE).atStartOfDay().atZone(ZoneId.systemDefault())
					.toInstant();
		else
			instant = ZonedDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME).toInstant();

		return Date.from(instant);
	}

	@Override
	public JsonValue toJson(Date value) {
		if (value == null)
			return JsonValue.NULL;

		final var instant = value.toInstant();

		final String text;
		if (LocalTime.from(instant.atZone(ZoneId.systemDefault())).equals(LocalTime.MIDNIGHT))
			text = DF.format(instant);
		else
			text = DTF.format(instant);

		return TextJsonAdapter.INSTANCE.toJson(text);
	}

}
