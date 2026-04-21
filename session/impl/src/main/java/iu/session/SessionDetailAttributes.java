package iu.session;

import java.util.Map;

import jakarta.json.JsonValue;

/**
 * Captures attributes and type name for tokenizing session details.
 */
public interface SessionDetailAttributes {

	/**
	 * Gets the session detail interface class name.
	 * 
	 * @return class name
	 */
	String getClassName();

	/**
	 * Gets a mapping of attribute values.
	 * 
	 * @return attribute values by name
	 */
	Map<String, JsonValue> getAttributes();

}
