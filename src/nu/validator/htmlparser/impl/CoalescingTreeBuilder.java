/*
 * Copyright (c) 2008-2009 Mozilla Foundation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL 
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package nu.validator.htmlparser.impl;

import nu.validator.htmlparser.annotation.NoLength;

import org.xml.sax.SAXException;

public abstract class CoalescingTreeBuilder<T> extends TreeBuilder<T> {

    private char[] charBuffer;

    private int charBufferLen = 0;
    
    protected final void accumulateCharacters(@NoLength char[] buf, int start,
            int length) throws SAXException {
            int newLen = charBufferLen + length;
            if (newLen > charBuffer.length) {
                char[] newBuf = new char[newLen];
                System.arraycopy(charBuffer, 0, newBuf, 0, charBufferLen);
                Portability.releaseArray(charBuffer);
                charBuffer = newBuf;
            }
            System.arraycopy(buf, start, charBuffer, charBufferLen, length);
            charBufferLen = newLen;
    }

    protected final void flushCharacters() throws SAXException {
        if (charBufferLen > 0) {
            appendCharacters(currentNode(), charBuffer, 0,
                    charBufferLen);
            charBufferLen = 0;
        }
    }
    
    /**
     * @see nu.validator.htmlparser.impl.TreeBuilder#appendCharacters(java.lang.Object, char[], int, int)
     */
    @Override protected final void appendCharacters(T parent, char[] buf, int start,
            int length) throws SAXException {
        appendCharacters(parent, new String(buf, start, length));
    }

    protected abstract void appendCharacters(T parent, String text) throws SAXException;

    /**
     * @see nu.validator.htmlparser.impl.TreeBuilder#appendComment(java.lang.Object, char[], int, int)
     */
    @Override final protected void appendComment(T parent, char[] buf, int start,
            int length) throws SAXException {
        appendComment(parent, new String(buf, start, length));
    }

    protected abstract void appendComment(T parent, String comment) throws SAXException;
    
    /**
     * @see nu.validator.htmlparser.impl.TreeBuilder#appendCommentToDocument(char[], int, int)
     */
    @Override protected final void appendCommentToDocument(char[] buf, int start,
            int length) throws SAXException {
        // TODO Auto-generated method stub
        appendCommentToDocument(new String(buf, start, length));
    }

    protected abstract void appendCommentToDocument(String comment) throws SAXException;
    
    /**
     * @see nu.validator.htmlparser.impl.TreeBuilder#insertFosterParentedCharacter(char[], int, java.lang.Object, java.lang.Object)
     */
    @Override protected final void insertFosterParentedCharacter(char[] buf, int start,
            T table, T stackParent) throws SAXException {
        insertFosterParentedCharacter(new String(buf, start, 1), table, stackParent);
    }
    
    /**
     * @see nu.validator.htmlparser.impl.TreeBuilder#endCoalescing()
     */
    @Override void endCoalescing() throws SAXException {
        charBuffer = null;
    }

    /**
     * @see nu.validator.htmlparser.impl.TreeBuilder#startCoalescing()
     */
    @Override void startCoalescing() throws SAXException {
        charBufferLen = 0;
        charBuffer = new char[1024];
    }

    protected abstract void insertFosterParentedCharacter(String text, T table, T stackParent) throws SAXException;
}
