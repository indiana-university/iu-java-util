package edu.iu.client;

/**
 * Enumerates property name formats for JSON conversion.
 */
public enum IuJsonPropertyNameFormat {

	/**
	 * Uses Java properties names as-is.
	 * 
	 * <p>
	 * Consistent with <a href=
	 * "https://jakarta.ee/specifications/jsonb/3.0/apidocs/jakarta.json.bind/jakarta/json/bind/config/propertynamingstrategy">JSON-B
	 * PropertyNamingStrategy</a>
	 * </p>
	 */
	IDENTITY,

	/**
	 * Converts all letters to lower case, with an underline preceding each that was
	 * originally upper case.
	 * 
	 * <p>
	 * Consistent with <a href=
	 * "https://jakarta.ee/specifications/jsonb/3.0/apidocs/jakarta.json.bind/jakarta/json/bind/config/propertynamingstrategy">JSON-B
	 * PropertyNamingStrategy</a>
	 * </p>
	 */
	LOWER_CASE_WITH_UNDERSCORES,

	/**
	 * Converts all letters to upper case, with an underline preceding each that was
	 * originally upper case.
	 */
	UPPER_CASE_WITH_UNDERSCORES;

}
