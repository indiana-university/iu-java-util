package iu.auth.util;

import edu.iu.IuObject;

/**
 * Cache key binding for initialized JWT verification resources by kid and alg
 * claim values.
 */
class AlgorithmKey {
	private final String kid;
	private final String alg;

	/**
	 * Constructor.
	 * 
	 * @param kid kid claim value
	 * @param alg alg claim value
	 */
	AlgorithmKey(String kid, String alg) {
		this.kid = kid;
		this.alg = alg;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(alg, kid);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		AlgorithmKey other = (AlgorithmKey) obj;
		return IuObject.equals(alg, other.alg) //
				&& IuObject.equals(kid, other.kid);
	}

}
