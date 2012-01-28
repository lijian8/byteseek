/*
 * Copyright Matt Palmer 2009-2011, All rights reserved.
 *
 * This code is licensed under a standard 3-clause BSD license:
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 * 
 *  * The names of its contributors may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.domesdaybook.matcher.bytes;

import net.domesdaybook.bytes.ByteUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *
 * @author matt
 */
public final class SimpleByteMatcherFactory implements ByteMatcherFactory {

    private static final String ILLEGAL_ARGUMENTS = "Null or empty Byte set passed in to ByteSetMatcher.";
    private static final int BINARY_SEARCH_THRESHOLD = 16;

    
    /**
     * Builds an optimal matcher from a set of bytes.
     *
     * <p>If the set is a single, non-inverted byte, then a {@link OneByteMatcher}
     * is returned. If the values lie in a contiguous range, then a
     * {@link ByteRangeMatcher} is returned.  If the number of bytes in the
     * set are below a threshold value (16), then a {@link SetBinarySearchMatcher}
     * is returned, otherwise a {@link SetBitsetMatcher} is returned.
     *
     * @param setValues The set of byte values to match.
     * @param matchInverse   Whether the set values are inverted or not
     * @return A ByteMatcher which is optimal for that set of bytes.
     * @throws {@link IllegalArgumentException} if the set is null or empty.
     */
    @Override
    public ByteMatcher create(Set<Byte> bytes, boolean matchInverse) {
        if (bytes == null || bytes.isEmpty()) {
            throw new IllegalArgumentException(ILLEGAL_ARGUMENTS);
        }
        // Produce the (possibly inverted) set of bytes:
        final  Set<Byte> values = matchInverse? ByteUtilities.invertedSet(bytes) : bytes;

        // See if some obvious byte matchers apply:
        ByteMatcher result = getSimpleCases(values);
        if (result == null) {
            
            // Now check to see if any of the invertible matchers 
            // match our set of bytes:
            result = getInvertibleCases(values, false);
            if (result == null) {

                // They didn't match the set of bytes, but since we have invertible
                // matchers, does the inverse set match any of them?
                final Set<Byte> invertedValues = matchInverse? bytes : ByteUtilities.invertedSet(bytes);
                result = getInvertibleCases(invertedValues, true);
                if (result == null) {

                    // Fall back on a standard set, defined as passed in.
                    result = new SetBitsetMatcher(bytes, matchInverse);
                }
            }
        }
        return result;
    }

    
    private ByteMatcher getInvertibleCases(Set<Byte> bytes, boolean isInverted) {
        ByteMatcher result = getBitmaskMatchers(bytes, isInverted);
        if (result == null) {
            result = getRangeMatchers(bytes, isInverted);
            if (result == null) {
                 result = getBinarySearchMatcher(bytes, isInverted);
            }
        }
        return result;
    }


    private ByteMatcher getSimpleCases(Set<Byte> values) {
        ByteMatcher result = null;
        switch (values.size()) {
            case 0: {
                // matches no bytes at all - AnyBitmaskMatcher with a mask of zero never matches anything.
                // Or: should throw exception - matcher can never match anything.
                result = new AnyBitmaskMatcher((byte) 0, false);
                break;
            }

            case 1: {
                for (Byte byteToMatch : values) {
                    result = new OneByteMatcher(byteToMatch);
                    break;
                }
                break;
            }

            case 2: {
                result = getCaseInsensitiveMatcher(values);
                break;
            }

            case 255: {
                for (byte byteValue = Byte.MIN_VALUE; byteValue < Byte.MAX_VALUE; byteValue++) {
                    if (!values.contains(byteValue)) {
                        result = new InvertedByteMatcher(byteValue);
                        break;
                    }
                }
                break;
            }
            
            case 256: {
                result = new AnyByteMatcher();
                break;
            }

            default: {
                result = null;
            }
        }
       return result;
    }


    private ByteMatcher getBitmaskMatchers(Set<Byte> values, boolean isInverted) {
        ByteMatcher result = null;
        // Determine if the bytes in the set can be matched by a bitmask:
        Byte bitmask = ByteUtilities.getAllBitMaskForBytes(values);
        if (bitmask != null) {
             result = new AllBitmaskMatcher(bitmask, isInverted);
        } else {
             bitmask = ByteUtilities.getAnyBitMaskForBytes(values);
             if (bitmask != null) {
                result = new AnyBitmaskMatcher(bitmask, isInverted);
             }
        }
        return result;
    }


    private ByteMatcher getRangeMatchers(Set<Byte> values, boolean isInverted) {
        ByteMatcher result = null;
        // Determine if all the values lie in a single range:
        List<Integer> byteValues = getSortedByteValues(values);
        int lastValuePosition = byteValues.size() - 1;
        int firstValue = byteValues.get(0);
        int lastValue = byteValues.get(lastValuePosition);
        if (lastValue - firstValue == lastValuePosition) {  // values lie in a contiguous range
            result = new ByteRangeMatcher(firstValue, lastValue, isInverted);
        }
        return result;
    }

    
    private ByteMatcher getBinarySearchMatcher(Set<Byte> values, boolean isInverted) {
        ByteMatcher result = null;
        // if there aren't very many values, use a BinarySearchMatcher:
        if (values.size() < BINARY_SEARCH_THRESHOLD) { // small number of bytes in set - use binary searcher:
            result = new SetBinarySearchMatcher(values, isInverted);
        }
        return result;
    }


    private ByteMatcher getCaseInsensitiveMatcher(Set<Byte> values) {
        ByteMatcher result = null;
        Character caseChar = getCaseChar(values);
        if (caseChar != null) {
            result = new CaseInsensitiveByteMatcher(caseChar);
        }
        return result;
    }

    
    private Character getCaseChar(Set<Byte> values) {
        Character result = null;
        final int size = values.size();
        if (size == 2) {
            Iterator<Byte> iterator = values.iterator();
            int val1 = iterator.next() & 0xFF;
            int val2 = iterator.next() & 0xFF;
            if (isSameCharDifferentCase(val1, val2)) {
                result = Character.valueOf((char) val1);
            }
        }
        return result;
    }

    
    private boolean isSameCharDifferentCase(int val1, int val2) {
        boolean result = false;
        if (isAlphaChar(val1) && isAlphaChar(val2)) {
            result = Math.abs(val1-val2) == 32;
        }
        return result;
    }

    
    private boolean isAlphaChar(int val) {
        return ((val >= 65 && val <= 90) || (val >= 97 && val <= 122));
    }

    private static List<Integer> getSortedByteValues(Set<Byte> byteSet) {
        final List<Integer> sortedByteValues = new ArrayList<Integer>();
        for (Byte b : byteSet) {
            sortedByteValues.add(Integer.valueOf(b.byteValue() & 0xFF));
        }
        Collections.sort(sortedByteValues);
        return sortedByteValues;
    }

}