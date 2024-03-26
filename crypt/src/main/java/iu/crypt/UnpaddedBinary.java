package iu.crypt;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Encodes {@link byte[]} values for inclusion in JWS and JWE serialized forms
 * as unpadded Base64 URL encoded strings.
 */
class UnpaddedBinary implements IuJsonAdapter<byte[]> {

	/**
	 * Singleton.
	 */
	static final UnpaddedBinary JSON = new UnpaddedBinary();

	@Override
	public byte[] fromJson(JsonValue jsonValue) {
		return EncodingUtils.base64Url(IuJsonAdapter.<String>basic().fromJson(jsonValue));
	}

	@Override
	public JsonValue toJson(byte[] javaValue) {
		return IuJsonAdapter.basic().toJson(EncodingUtils.base64Url(javaValue));
	}

	private UnpaddedBinary() {
	}

}