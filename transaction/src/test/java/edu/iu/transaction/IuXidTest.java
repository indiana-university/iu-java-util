package edu.iu.transaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;

@SuppressWarnings("javadoc")
public class IuXidTest {

	@Test
	public void testIds() {
		final var xid = new IuXid();
		assertNotEquals(xid, null);
		assertNotEquals(xid, new IuXid());
		assertEquals(xid, xid);
		assertEquals(01_733_7_1, xid.getFormatId());
		final var bqid = IuText.base64Url(xid.getBranchQualifier());
		final var gtid = IuText.base64Url(xid.getGlobalTransactionId());
		IdGenerator.verifyId(bqid, 1000L);
		IdGenerator.verifyId(gtid, 1000L);
		assertEquals("iuxid-63225+" + gtid + "+" + bqid, xid.toString());
	}

	@Test
	public void testBranchIds() {
		final var xid = new IuXid();
		final var bxid = new IuXid(xid);
		assertEquals(63225, bxid.getFormatId());
		assertArrayEquals(xid.getGlobalTransactionId(), bxid.getGlobalTransactionId());
		assertNotEquals(xid.getBranchQualifier(), bxid.getBranchQualifier());
		assertNotEquals(xid, bxid);
		assertNotEquals(xid.hashCode(), bxid.hashCode());
		final var bqid = IuText.base64Url(bxid.getBranchQualifier());
		final var gtid = IuText.base64Url(bxid.getGlobalTransactionId());
		IdGenerator.verifyId(bqid, 1000L);
		IdGenerator.verifyId(gtid, 1000L);
		assertEquals("iuxid-63225+" + gtid + "+" + bqid, bxid.toString());
	}

}
