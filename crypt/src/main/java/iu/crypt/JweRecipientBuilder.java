package iu.crypt;

import java.util.Objects;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;

import edu.iu.IdGenerator;
import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryptionHeader.Encryption;
import edu.iu.crypt.WebEncryptionRecipient.Builder;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Builds JWE recipients for {@link JweBuilder}
 */
class JweRecipientBuilder extends JoseBuilder<JweRecipientBuilder> implements Builder<JweRecipientBuilder> {

	private final JweBuilder jweBuilder;

	/**
	 * Constructor
	 * 
	 * @param algorithm  algorithm
	 * @param encryption encryption
	 * @param deflate    deflate
	 * @param jweBuilder JWE builder
	 */
	JweRecipientBuilder(Algorithm algorithm, Encryption encryption, boolean deflate, JweBuilder jweBuilder) {
		super(algorithm, encryption, deflate);
		this.jweBuilder = jweBuilder;
	}

	@Override
	public JweBuilder then() {
		return jweBuilder;
	}

	@Override
	protected JweRecipientBuilder next() {
		return this;
	}

	byte[] agreedUponKey() {
//		3.   When Direct Key Agreement or Key Agreement with Key Wrapping are
//      employed, use the key agreement algorithm to compute the value
//      of the agreed upon key.  When Direct Key Agreement is employed,
//      let the CEK be the agreed upon key.  When Key Agreement with Key
//      Wrapping is employed, the agreed upon key will be used to wrap
//      the CEK.
		final var algorithm = algorithm();
		final var encryption = encryption();
		
		final var epk = new JwkBuilder().algorithm(algorithm).ephemeral().build();
		final var serializedEpk = IuJson.object();
		epk.wellKnown().serializeTo(serializedEpk);
		crit("epk", serializedEpk.build());

		final var uinfo = EncodingUtils.base64Url(IuCrypt.sha256(epk.getPublicKey().getEncoded()));
		crit("apu", uinfo);

		final var pub = key().getPublicKey();
		final var vinfo = EncodingUtils.base64Url(IuCrypt.sha256(pub.getEncoded()));
		crit("apv", vinfo);

		final var z = IuException.unchecked(() -> {
			final var ka = KeyAgreement.getInstance(algorithm().algorithm);
			ka.init(epk.getPrivateKey());
			ka.doPhase(key().getPublicKey(), true);
			return ka.generateSecret();
		});

		final int keyDataLen;
		final String algId;
		if (algorithm.equals(Algorithm.ECDH_ES)) {
			keyDataLen = encryption.size;
			algId = encryption.size + encryption.enc;
		} else {
			keyDataLen = encryption.size;
			algId = algorithm.size + algorithm.alg;
		}

//		Key derivation is performed using the Concat KDF, as defined in
//		   Section 5.8.1 of [NIST.800-56A], where the Digest Method is SHA-256.
//		   The Concat KDF parameters are set as follows:
//
//		   Z
//		      This is set to the representation of the shared secret Z as an
//		      octet sequence.
//
//		   keydatalen
//		      This is set to the number of bits in the desired output key.  For
//		      "ECDH-ES", this is length of the key used by the "enc" algorithm.
//		      For "ECDH-ES+A128KW", "ECDH-ES+A192KW", and "ECDH-ES+A256KW", this
//		      is 128, 192, and 256, respectively.
//
//		   AlgorithmID
//		      The AlgorithmID value is of the form Datalen || Data, where Data
//		      is a variable-length string of zero or more octets, and Datalen is
//		      a fixed-length, big-endian 32-bit counter that indicates the
//		      length (in octets) of Data.  In the Direct Key Agreement case,
//		      Data is set to the octets of the ASCII representation of the "enc"
//		      Header Parameter value.  In the Key Agreement with Key Wrapping
//		      case, Data is set to the octets of the ASCII representation of the
//		      "alg" (algorithm) Header Parameter value.
//
//		   PartyUInfo
//		      The PartyUInfo value is of the form Datalen || Data, where Data is
//		      a variable-length string of zero or more octets, and Datalen is a
//		      fixed-length, big-endian 32-bit counter that indicates the length
//		      (in octets) of Data.  If an "apu" (agreement PartyUInfo) Header
//		      Parameter is present, Data is set to the result of base64url
//		      decoding the "apu" value and Datalen is set to the number of
//		      octets in Data.  Otherwise, Datalen is set to 0 and Data is set to
//		      the empty octet sequence.
//
//		   PartyVInfo
//		      The PartyVInfo value is of the form Datalen || Data, where Data is
//		      a variable-length string of zero or more octets, and Datalen is a
//		      fixed-length, big-endian 32-bit counter that indicates the length
//		      (in octets) of Data.  If an "apv" (agreement PartyVInfo) Header
//		      Parameter is present, Data is set to the result of base64url
//		      decoding the "apv" value and Datalen is set to the number of
//		      octets in Data.  Otherwise, Datalen is set to 0 and Data is set to
//		      the empty octet sequence.
//
//		   SuppPubInfo
//		      This is set to the keydatalen represented as a 32-bit big-endian
//		      integer.
//
//		   SuppPrivInfo
//		      This is set to the empty octet sequence.
//
//		   Applications need to specify how the "apu" and "apv" Header
//		   Parameters are used for that application.  The "apu" and "apv" values
//		   MUST be distinct, when used.  Applications wishing to conform to
//		   [NIST.800-56A] need to provide values that meet the requirements of
//		   that document, e.g., by using values that identify the producer and
//		   consumer.  Alternatively, applications MAY conduct key derivation in
//		   a manner similar to "Diffie-Hellman Key Agreement Method" [RFC2631]:
//		   in that case, the "apu" parameter MAY either be omitted or represent
//		   a random 512-bit value (analogous to PartyAInfo in Ephemeral-Static
//		   mode in RFC 2631) and the "apv" parameter SHOULD NOT be present.
//
//		   See Appendix C for an example key agreement computation using this
//		   method.
	}

	/**
	 * Generates the encrypted key and creates the recipient.
	 * 
	 * @param cek        ephemeral content encryption key, null if not ephemeral
	 * 
	 * @param encryption partially initialized JWE
	 * @return recipient
	 */
	JweRecipient build(byte[] cek) {
//
//   4.   When Key Wrapping, Key Encryption, or Key Agreement with Key
//        Wrapping are employed, encrypt the CEK to the recipient and let
//        the result be the JWE Encrypted Key.
//
//   5.   When Direct Key Agreement or Direct Encryption are employed, let
//        the JWE Encrypted Key be the empty octet sequence.
//
//
//
//
//Jones & Hildebrand           Standards Track                   [Page 15]
//
//RFC 7516                JSON Web Encryption (JWE)               May 2015
//
//
//   6.   When Direct Encryption is employed, let the CEK be the shared
//        symmetric key.
//
//   7.   Compute the encoded key value BASE64URL(JWE Encrypted Key).
		throw new UnsupportedOperationException("TODO: encryptedKey");
//		return new JweRecipient(encryption, new Jose(this), encryptedKey);
	}

}
