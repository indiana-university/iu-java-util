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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Implements {@link IuJsonAdapter} for {@link Date}
 *
 * <p>
 * Works in UTC throughout. A date at midnight UTC writes as a date, such as
 * {@code 2026-09-26Z}; any other writes as a date and time, such as
 * {@code 2026-09-26T04:01:30Z}. A date read without a time is midnight, and a
 * date or a date and time read without an offset or zone is in UTC.
 * </p>
 */
class DateJsonAdapter implements IuJsonAdapter<Date> {

	/**
	 * Singleton instance.
	 */
	static final DateJsonAdapter INSTANCE = new DateJsonAdapter();

	private static final DateTimeFormatter DF = DateTimeFormatter.ISO_DATE.withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC);

	private DateJsonAdapter() {
	}

	@Override
	public Date fromJson(JsonValue value) {
		return fromText(TextJsonAdapter.INSTANCE.fromJson(value));
	}

	@Override
	public JsonValue toJson(Date value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return TextJsonAdapter.INSTANCE.toJson(toText(value));
	}

	@Override
	public Date read(JsonParser parser) {
		return fromText(TextJsonAdapter.INSTANCE.read(parser));
	}

	@Override
	public void write(Date value, JsonGenerator generator) {
		if (value == null)
			generator.writeNull();
		else
			generator.write(toText(value));
	}

	private Date fromText(String text) {
		if (text == null)
			return null;
		else
			return Date.from(FormatAdapters.zoned(parse(text)).toInstant());
	}

	/**
	 * Parses an ISO date, or an ISO date and time.
	 *
	 * @param text text
	 * @return parsed date, with or without a time, zone, or offset
	 */
	static TemporalAccessor parse(String text) {
		return (text.indexOf('T') == -1 ? DateTimeFormatter.ISO_DATE : DateTimeFormatter.ISO_DATE_TIME).parse(text);
	}

	private String toText(Date value) {
		final var instant = value.toInstant();
		if (LocalTime.from(instant.atOffset(ZoneOffset.UTC)).equals(LocalTime.MIDNIGHT))
			return DF.format(instant);
		else
			return DTF.format(instant);
	}

}
