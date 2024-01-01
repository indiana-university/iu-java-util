package edu.iu.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class IuXidTest {

	@Test
	public void testIds() {
		final var xid = new IuXid();
		assertNotEquals(xid, null);
		assertNotEquals(xid, new IuXid());
		assertEquals(xid, xid);
		assertEquals(01_733_7_0, xid.getFormatId());
		final var bqid = IdGenerator.encodeId(xid.getBranchQualifier());
		final var gtid = IdGenerator.encodeId(xid.getGlobalTransactionId());
		IdGenerator.verifyId(bqid, 1000L);
		IdGenerator.verifyId(gtid, 1000L);
		assertEquals("iuxid-63224+" + gtid + "+" + bqid, xid.toString());
	}

	@Test
	public void testBranchIds() {
		final var xid = new IuXid();
		final var bxid = new IuXid(xid);
		assertEquals(01_733_7_0, bxid.getFormatId());
		assertEquals(xid.getGlobalTransactionId(), bxid.getGlobalTransactionId());
		assertNotEquals(xid.getBranchQualifier(), bxid.getBranchQualifier());
		assertNotEquals(xid, bxid);
		assertNotEquals(xid.hashCode(), bxid.hashCode());
		final var bqid = IdGenerator.encodeId(bxid.getBranchQualifier());
		final var gtid = IdGenerator.encodeId(bxid.getGlobalTransactionId());
		IdGenerator.verifyId(bqid, 1000L);
		IdGenerator.verifyId(gtid, 1000L);
		assertEquals("iuxid-63224+" + gtid + "+" + bqid, bxid.toString());
	}

}
