/*
 * Copyright © 2024 Indiana University
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
