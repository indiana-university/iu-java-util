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
package edu.iu.oidc;

/**
 * The {@code address} claim, which is a structured value rather than a scalar.
 *
 * <p>
 * Every member is optional and answers {@code null} when the source doesn't
 * know it, so an implementation declares only what it holds. A source that
 * knows an address as one block of text implements {@link #getFormatted()}
 * alone; one that holds it in parts implements the parts and may leave the
 * formatted rendering to a consumer.
 * </p>
 *
 * @see <a href=
 *      "https://openid.net/specs/openid-connect-core-1_0.html#AddressClaim">OpenID
 *      Connect Core 1.0 &sect;5.1.1</a>
 */
public interface IuOidcAddress {

	/**
	 * Gets the full mailing address, as it would be laid out on a label.
	 *
	 * <p>
	 * May carry newlines, either as a carriage return / line feed pair or as a bare
	 * line feed.
	 * </p>
	 *
	 * @return {@code formatted} claim; null if not known
	 */
	default String getFormatted() {
		return null;
	}

	/**
	 * Gets the street address, which is the house number, street name, and unit
	 * together, and may itself carry newlines.
	 *
	 * @return {@code street_address} claim; null if not known
	 */
	default String getStreetAddress() {
		return null;
	}

	/**
	 * Gets the city or locality.
	 *
	 * @return {@code locality} claim; null if not known
	 */
	default String getLocality() {
		return null;
	}

	/**
	 * Gets the state, province, prefecture, or region.
	 *
	 * @return {@code region} claim; null if not known
	 */
	default String getRegion() {
		return null;
	}

	/**
	 * Gets the zip or postal code.
	 *
	 * @return {@code postal_code} claim; null if not known
	 */
	default String getPostalCode() {
		return null;
	}

	/**
	 * Gets the country name.
	 *
	 * @return {@code country} claim; null if not known
	 */
	default String getCountry() {
		return null;
	}

}
