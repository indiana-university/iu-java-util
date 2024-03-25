package iu.client;

import java.util.Calendar;
import java.util.Date;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Calendar}
 */
public class CalendarJsonAdapter implements IuJsonAdapter<Calendar> {

	/**
	 * Singleton instance.
	 */
	static final CalendarJsonAdapter INSTANCE = new CalendarJsonAdapter();

	private CalendarJsonAdapter() {
	}

	@Override
	public Calendar fromJson(JsonValue value) {
		final var date = IuJsonAdapter.of(Date.class).fromJson(value);
		if (date == null)
			return null;
		else {
			final var cal = Calendar.getInstance();
			cal.setTime(date);
			return cal;
		}
	}

	@Override
	public JsonValue toJson(Calendar value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return IuJsonAdapter.of(Date.class).toJson(value.getTime());
	}

}
