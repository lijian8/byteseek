/*
 * Copyright Matt Palmer 2009-2011, All rights reserved.
 *
 */

package net.domesdaybook.matcher.singlebyte;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author matt
 */
public class ByteMatcherTest {

    public ByteMatcherTest() {
    }


    /**
     * Tests every possible byte value against every other non-matching
     * byte value.
     */
    @Test
    public void testMatcher() {
        for (int i = 0; i < 256; i++) {
            final byte theByte = (byte) i;
            final ByteMatcher matcher = new ByteMatcher(theByte);
            assertEquals("matches", true, matcher.matches(theByte));
            assertEquals("1 byte matches", 1, matcher.getNumberOfMatchingBytes());
            assertArrayEquals("matching bytes", new byte[] {theByte}, matcher.getMatchingBytes());
            final String regularExpression = String.format("%02x", theByte);
            assertEquals("regular expression", regularExpression, matcher.toRegularExpression(false));
            for (int x = 0; x < 256; x++) {
                if (x != i) {
                    final byte nomatch = (byte) x;
                    assertEquals("no match", false, matcher.matches(nomatch));
                }
            }
        }

    }


    /**
     * Test of matches method, of class ByteMatcher.
     */
    /*
    @Test
    public void testMatches_ByteReader_long() {
        byte expected = (byte) 01;
        ByteMatcher matcher = new ByteMatcher()
    }
    */


 

}