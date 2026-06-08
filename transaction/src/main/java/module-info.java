/**
 * Virtual <a href="https://pubs.opengroup.org/onlinepubs/009680699/toc.pdf">X/A
 * Open compliant</a> <a href=
 * "https://jakarta.ee/specifications/transactions/2.0/jakarta-transactions-spec-2.0">Jakarta
 * Transactions</a> implementation.
 */
module iu.util.transaction {
	exports edu.iu.transaction;

	requires iu.util;
	requires transitive jakarta.transaction;	
	requires transitive java.transaction.xa;
}
