/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.client;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Calendar}; always reads a
 * {@link GregorianCalendar}, so also adapts that type.
 *
 * <p>
 * Writes in the calendar's own time zone, as JSON-B does: at midnight as an ISO
 * date with its offset, such as {@code 2026-09-26-04:00}, and otherwise as an
 * ISO date and time with its zone, such as
 * {@code 2026-09-26T08:01:30-04:00[America/New_York]}. Reads in the zone or
 * offset read, UTC if none, at midnight if no time is read.
 * </p>
 *
 * <p>
 * {@link #LEGACY} converts as before 7.1: written as {@link Date} in UTC, and
 * read in the default time zone.
 * </p>
 */
public class CalendarJsonAdapter implements IuJsonAdapter<Calendar> {

	/**
	 * Singleton instance.
	 */
	static final CalendarJsonAdapter INSTANCE = new CalendarJsonAdapter(false);

	/**
	 * Converts as before 7.1.
	 */
	static final CalendarJsonAdapter LEGACY = new CalendarJsonAdapter(true);

	private final boolean legacy;

	private CalendarJsonAdapter(boolean legacy) {
		this.legacy = legacy;
	}

	@Override
	public Calendar fromJson(JsonValue value) {
		if (legacy) {
			final var date = DateJsonAdapter.INSTANCE.fromJson(value);
			if (date == null)
				return null;
			final var calendar = new GregorianCalendar();
			calendar.setTime(date);
			return calendar;
		}

		final var text = TextJsonAdapter.INSTANCE.fromJson(value);
		if (text == null)
			return null;
		else
			return GregorianCalendar.from(FormatAdapters.zoned(DateJsonAdapter.parse(text)));
	}

	@Override
	public JsonValue toJson(Calendar value) {
		if (value == null)
			return JsonValue.NULL;
		else if (legacy)
			return DateJsonAdapter.INSTANCE.toJson(value.getTime());

		final var zoned = value.toInstant().atZone(value.getTimeZone().toZoneId());
		final var formatter = zoned.toLocalTime().equals(LocalTime.MIDNIGHT) //
				? DateTimeFormatter.ISO_DATE
				: DateTimeFormatter.ISO_DATE_TIME;
		return TextJsonAdapter.INSTANCE.toJson(formatter.format(zoned));
	}

}
