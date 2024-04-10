package iu.auth.saml;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Logger;

import org.opensaml.saml.common.assertion.AssertionValidationException;
import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.common.assertion.ValidationResult;
import org.opensaml.saml.saml2.assertion.impl.BearerSubjectConfirmationValidator;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import net.shibboleth.shared.primitive.StringSupport;

/**
 * Validates a bearer subject confirmation. Support to validate subject details
 * return as SAML response as successful authentication
 */
public class IuSubjectConfirmationValidator extends BearerSubjectConfirmationValidator {

	private static final Logger LOG = Logger.getLogger(IuSubjectConfirmationValidator.class.getName());

	private final List<InetAddress> allowedRanges;
	private final boolean failOnAddressMismatch;

	/**
	 * constructor
	 * 
	 * @param allowedRanges         allowed IP address list
	 * @param failOnAddressMismatch determine whether to fail on address mismatch or
	 *                              not, true if required, false if not
	 */
	public IuSubjectConfirmationValidator(List<InetAddress> allowedRanges, boolean failOnAddressMismatch) {
		this.allowedRanges = allowedRanges;
		this.failOnAddressMismatch = failOnAddressMismatch;
	}

	@Override
	protected ValidationResult validateAddress(SubjectConfirmationData confirmationData, Assertion assertion,
			ValidationContext context, boolean required) throws AssertionValidationException {

		final String address = StringSupport.trimOrNull(confirmationData.getAddress());
		if (address == null) {
			if (required) {
				context.getValidationFailureMessages().add(
						"SubjectConfirmationData/@Address was missing and was required");
				return ValidationResult.INVALID;
			}
			return ValidationResult.VALID;
		}

		InetAddress[] confirmingAddresses; try { confirmingAddresses =
				InetAddress.getAllByName(address); } catch (UnknownHostException e) {
					context.getValidationFailureMessages().add(String.format(
							"Subject confirmation address '%s' is not resolvable hostname or IP address"
							, address)); return ValidationResult.INDETERMINATE; }

		// Assume that subject confirmation with a private IP resides on the same //
		//network as the IDP. Since we don't have a way to confirm which NAT'd address
		// the subject is calling from, this assumption will have to be good enough.
		for (InetAddress confirmingAddress : confirmingAddresses) { if
			(confirmingAddress.isSiteLocalAddress()) { LOG.fine("Allowing private IP " +
					confirmingAddress); return ValidationResult.VALID; } if (allowedRanges !=
					null) for (InetAddress range : allowedRanges) if
			(isInetAddressInRange(confirmingAddress, range)) {
						LOG.fine("Allowing whitelisted IP " + confirmingAddress + "; range = " +
								range); return ValidationResult.VALID; } }

		ValidationResult result = super.validateAddress(confirmationData, assertion, context, required);
		 
		
		if (!ValidationResult.VALID.equals(result)) {
			LOG.info(() ->
					"IP address mismatch in SAML subject confirmation; remote address = " +
					address + "; allowed ranges = " + allowedRanges); if (!failOnAddressMismatch)
			return ValidationResult.VALID; 
		} 
		
		return result;
	}




	private boolean isInetAddressInRange(InetAddress address, InetAddress range) {
		/*byte[] hostaddr = address.getAddress();
		int lastSlash = range.lastIndexOf('/');

		byte[] rangeaddr = range.getAddress();
		int maskbits = range.getAddress().length * 8;
		if (lastSlash == -1) {
		rangeaddr = range.getAddress();
		maskbits = rangeaddr.length * 8;
	} else {
		rangeaddr = range.substring(0, lastSlash)).getAddress();
		maskbits = Integer.parseInt(range.substring(lastSlash + 1));
	}

		for (int i = 0; i < range.getAddress().length; i++) {
			if (maskbits >= 8) {
				maskbits -= 8;
				if (hostaddr[i] != rangeaddr[i])
					return false;
			} else if (maskbits > 0) {
				int mask = ~((1 << (8 - maskbits)) - 1);
				return (hostaddr[i] & mask) == (rangeaddr[i] & mask);
			} else
				break;
		}*/

		return true;
	}
}