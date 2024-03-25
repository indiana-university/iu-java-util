package iu.client;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Date}
 */
class TimeZoneJsonAdapter implements IuJsonAdapter<TimeZone> {

	/**
	 * Singleton instance.
	 */
	static final TimeZoneJsonAdapter INSTANCE = new TimeZoneJsonAdapter();

	private TimeZoneJsonAdapter() {
	}

	@Override
	public TimeZone fromJson(JsonValue value) {
		final var text = TextJsonAdapter.INSTANCE.fromJson(value);
		if (text == null)
			return null;
		else {
			final var id = ZoneId.of(text);
			final var now = LocalDateTime.now().atZone(id);
			return new SimpleTimeZone(now.getOffset().getTotalSeconds() * 1000, id.getId());
		}
	}

	@Override
	public JsonValue toJson(TimeZone value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return TextJsonAdapter.INSTANCE.toJson(value.getID());
	}

}
