/**
 * Provides unit testing support.
 * 
 * <p>
 * Supports the use of:
 * </p>
 * 
 * <ul>
 * <li>JUnit Juptier Engine</li>
 * <li>Mockito</li>
 * </ul>
 * 
 * @see edu.iu.test.IuTest
 */
module iu.util.test {
	exports edu.iu.test;
	
	requires org.mockito;
	requires org.junit.jupiter.api;
	requires org.junit.platform.launcher;
}