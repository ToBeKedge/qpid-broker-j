/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.qpid.bytebuffer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.InvalidMarkException;
import java.nio.charset.StandardCharsets;

import com.google.common.io.ByteStreams;
import org.junit.Assert;
import org.mockito.internal.util.Primitives;

import org.apache.qpid.test.utils.QpidTestCase;

public class SlicedQpidByteBufferTest extends QpidTestCase
{

    private static final int BUFFER_SIZE = 10;
    private QpidByteBuffer _slicedBuffer;
    private QpidByteBuffer _parent;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _parent = QpidByteBuffer.allocateDirect(BUFFER_SIZE);
    }

    @Override
    public void tearDown() throws Exception
    {
        super.tearDown();
        _parent.dispose();
        if (_slicedBuffer != null)
        {
            _slicedBuffer.dispose();
        }
    }

    public void testPutGetByIndex() throws Exception
    {
        testPutGetByIndex(double.class, 1.0);
        testPutGetByIndex(float.class, 1.0f);
        testPutGetByIndex(long.class, 1L);
        testPutGetByIndex(int.class, 1);
        testPutGetByIndex(char.class, 'A');
        testPutGetByIndex(short.class, (short)1);
        testPutGetByIndex(byte.class, (byte)1);
    }

    public void testPutGet() throws Exception
    {
        testPutGet(double.class, false, 1.0);
        testPutGet(float.class, false, 1.0f);
        testPutGet(long.class, false, 1L);
        testPutGet(int.class, false, 1);
        testPutGet(char.class, false, 'A');
        testPutGet(short.class, false, (short)1);
        testPutGet(byte.class, false, (byte)1);

        testPutGet(int.class, true, 1L);
        testPutGet(short.class, true, 1);
        testPutGet(byte.class, true, (short)1);
    }

    public void testMarkReset() throws Exception
    {
        _slicedBuffer = createSlice();

        _slicedBuffer.mark();
        _slicedBuffer.position(_slicedBuffer.position() + 1);
        assertEquals("Unexpected position after move", 1, _slicedBuffer.position());

        _slicedBuffer.reset();
        assertEquals("Unexpected position after reset", 0, _slicedBuffer.position());
    }

    public void testInvalidMark() throws Exception
    {
        _slicedBuffer = createSlice();

        try
        {
            _slicedBuffer.reset();
            fail("Exception not thrown");
        }
        catch (InvalidMarkException e)
        {
            // pass
        }


        _slicedBuffer.position(_slicedBuffer.capacity());
        _slicedBuffer.mark();

        _slicedBuffer.position(0); // Makes mark invalid
        try
        {
            _slicedBuffer.reset();
            fail("Exception not thrown");
        }
        catch (InvalidMarkException e)
        {
            // pass
        }

        _slicedBuffer.position(1);
        _slicedBuffer.mark();
        _slicedBuffer.clear(); // Marks mark invalid
        try
        {
            _slicedBuffer.reset();
            fail("Exception not thrown");
        }
        catch (InvalidMarkException e)
        {
            // pass
        }

        _slicedBuffer.position(1);
        _slicedBuffer.mark();
        _slicedBuffer.rewind(); // Marks mark invalid
        try
        {
            _slicedBuffer.reset();
            fail("Exception not thrown");
        }
        catch (InvalidMarkException e)
        {
            // pass
        }

        _slicedBuffer.position(2);
        _slicedBuffer.mark();
        _slicedBuffer.limit(1);
        try
        {
            _slicedBuffer.reset();
            fail("Exception not thrown");
        }
        catch (InvalidMarkException e)
        {
            // pass
        }

        _slicedBuffer.position(1);
        _slicedBuffer.mark();
        _slicedBuffer.flip(); // Marks mark invalid
        try
        {
            _slicedBuffer.reset();
            fail("Exception not thrown");
        }
        catch (InvalidMarkException e)
        {
            // pass
        }
    }

    public void testPosition() throws Exception
    {
        _slicedBuffer = createSlice();

        assertEquals("Unexpected position for new slice", 0, _slicedBuffer.position());

        _slicedBuffer.position(1);
        assertEquals("Unexpected position after advance", 1, _slicedBuffer.position());

        final int oldLimit = _slicedBuffer.limit();
        _slicedBuffer.limit(oldLimit - 1);
        try
        {
            _slicedBuffer.position(oldLimit);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }
    }

    public void testLimit()
    {
        _slicedBuffer = createSlice();
        assertEquals("Unexpected initial limit for new slice", _slicedBuffer.capacity(), _slicedBuffer.limit());

        int originalLimit = _slicedBuffer.limit();
        _slicedBuffer.limit(originalLimit - 1);
        assertEquals("Unexpected new limit", originalLimit - 1, _slicedBuffer.limit());

        try
        {
            _slicedBuffer.limit(originalLimit + 1);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }

        _slicedBuffer.position(_slicedBuffer.limit());
        _slicedBuffer.limit(originalLimit / 2);
        assertEquals("Unexpected position after imposing limit before position", _slicedBuffer.limit(), _slicedBuffer.position());
    }

    public void testFlip() throws Exception
    {
        _slicedBuffer = createSlice();

        _slicedBuffer.putInt(Integer.MAX_VALUE);
        _slicedBuffer.flip();
        int rv = _slicedBuffer.getInt();
        assertEquals("Unexpected read value after flip", Integer.MAX_VALUE, rv);
    }

    public void testRemaining() throws Exception
    {
        _slicedBuffer = createSlice();

        assertEquals("Unexpected remaining for new slice", _slicedBuffer.limit(), _slicedBuffer.remaining());
        assertTrue("Unexpected hasRemaining for new slice", _slicedBuffer.hasRemaining());

        _slicedBuffer.position(_slicedBuffer.limit());
        assertEquals("Unexpected remaining for slice at limit", 0, _slicedBuffer.remaining());
        assertFalse("Unexpected hasRemaining for slice at limit", _slicedBuffer.hasRemaining());
    }

    public void testBulkPutGet() throws Exception
    {
        _slicedBuffer = createSlice();

        final byte[] source = getTestBytes(_slicedBuffer.remaining());

        QpidByteBuffer rv = _slicedBuffer.put(source, 0, source.length);
        assertEquals("Unexpected builder return value", _slicedBuffer, rv);

        _slicedBuffer.flip();
        byte[] target = new byte[_slicedBuffer.remaining()];
        rv = _slicedBuffer.get(target, 0, target.length);
        assertEquals("Unexpected builder return value", _slicedBuffer, rv);

        Assert.assertArrayEquals("Unexpected bulk put/get result", source, target);


        _slicedBuffer.clear();
        _slicedBuffer.position(1);

        try
        {
            _slicedBuffer.put(source, 0, source.length);
            fail("Exception not thrown");
        }
        catch (BufferOverflowException e)
        {
            // pass
        }

        assertEquals("Position should be unchanged after failed put", 1, _slicedBuffer.position());

        try
        {
            _slicedBuffer.get(target, 0, target.length);
            fail("Exception not thrown");
        }
        catch (BufferUnderflowException e)
        {
            // pass
        }

        assertEquals("Position should be unchanged after failed get", 1, _slicedBuffer.position());


    }

    public void testByteBufferPutGet()
    {
        _slicedBuffer = createSlice();
        final byte[] source = getTestBytes(_slicedBuffer.remaining());

        ByteBuffer sourceByteBuffer = ByteBuffer.wrap(source);

        QpidByteBuffer rv = _slicedBuffer.put(sourceByteBuffer);
        assertEquals("Unexpected builder return value", _slicedBuffer, rv);

        assertEquals("Unexpected position", _slicedBuffer.capacity(), _slicedBuffer.position());
        assertEquals("Unexpected remaining", 0, _slicedBuffer.remaining());

        assertEquals("Unexpected remaining in source ByteBuffer", 0, sourceByteBuffer.remaining());

        _slicedBuffer.flip();

        ByteBuffer destinationByteBuffer =  ByteBuffer.allocate(source.length);
        _slicedBuffer.get(destinationByteBuffer);


        assertEquals("Unexpected remaining", 0, _slicedBuffer.remaining());

        assertEquals("Unexpected remaining in destination ByteBuffer", 0, destinationByteBuffer.remaining());
        assertEquals("Unexpected position in destination ByteBuffer", source.length, destinationByteBuffer.position());

        Assert.assertArrayEquals("Unexpected ByteBuffer put/get result", source, destinationByteBuffer.array());

        _slicedBuffer.clear();
        _slicedBuffer.position(1);

        sourceByteBuffer.clear();
        try
        {
            _slicedBuffer.put(sourceByteBuffer);
            fail("Exception should be thrown");
        }
        catch(BufferOverflowException e)
        {
            // pass
        }

        assertEquals("Position should not be changed after failed put", 1, _slicedBuffer.position());
        assertEquals("Source position should not changed after failed put", source.length, sourceByteBuffer.remaining());

        _slicedBuffer.clear();
        destinationByteBuffer.position(1);

        try
        {
            _slicedBuffer.get(destinationByteBuffer);
            fail("Exception should be thrown");
        }
        catch(BufferUnderflowException e )
        {
            // pass
        }
    }

    public void testQpidByteBufferPutGet()
    {
        _slicedBuffer = createSlice();
        final byte[] source = getTestBytes(_slicedBuffer.remaining());

        QpidByteBuffer sourceQpidByteBuffer = QpidByteBuffer.wrap(source);

        QpidByteBuffer rv = _slicedBuffer.put(sourceQpidByteBuffer);
        assertEquals("Unexpected builder return value", _slicedBuffer, rv);

        assertEquals("Unexpected position", _slicedBuffer.capacity(), _slicedBuffer.position());
        assertEquals("Unexpected remaining", 0, _slicedBuffer.remaining());

        assertEquals("Unexpected remaining in source QpidByteBuffer", 0, sourceQpidByteBuffer.remaining());

        _slicedBuffer.flip();

        ByteBuffer destinationByteBuffer =  ByteBuffer.allocate(source.length);
        _slicedBuffer.get(destinationByteBuffer);

        assertEquals("Unexpected remaining", 0, _slicedBuffer.remaining());

        assertEquals("Unexpected remaining in destination ByteBuffer", 0, destinationByteBuffer.remaining());
        assertEquals("Unexpected position in destination ByteBuffer", source.length, destinationByteBuffer.position());

        Assert.assertArrayEquals("Unexpected ByteBuffer put/get result", source, destinationByteBuffer.array());

        _slicedBuffer.clear();
        _slicedBuffer.position(1);

        sourceQpidByteBuffer.clear();
        try
        {
            _slicedBuffer.put(sourceQpidByteBuffer);
            fail("Exception should be thrown");
        }
        catch(BufferOverflowException e)
        {
            // pass
        }

        assertEquals("Position should not be changed after failed put", 1, _slicedBuffer.position());
        assertEquals("Source position should not changed after failed put", source.length, sourceQpidByteBuffer.remaining());
    }

    public void testDuplicate()
    {
        _slicedBuffer = createSlice();
        _slicedBuffer.position(1);
        int originalLimit = _slicedBuffer.limit();
        _slicedBuffer.limit(originalLimit - 1);

        QpidByteBuffer duplicate = _slicedBuffer.duplicate();
        try
        {
            assertEquals("Unexpected position", _slicedBuffer.position(), duplicate.position() );
            assertEquals("Unexpected limit", _slicedBuffer.limit(), duplicate.limit() );
            assertEquals("Unexpected capacity", _slicedBuffer.capacity(), duplicate.capacity() );

            duplicate.position(2);
            duplicate.limit(originalLimit - 2);

            assertEquals("Unexpected position in the original", 1, _slicedBuffer.position());
            assertEquals("Unexpected limit in the original", originalLimit -1, _slicedBuffer.limit());
        }
        finally
        {
            duplicate.dispose();
        }
    }

    public void testCopyToByteBuffer()
    {
        _slicedBuffer = createSlice();
        byte[] source = getTestBytes(_slicedBuffer.remaining());
        _slicedBuffer.put(source);
        _slicedBuffer.flip();

        int originalRemaining = _slicedBuffer.remaining();
        ByteBuffer destination =  ByteBuffer.allocate(source.length);
        _slicedBuffer.copyTo(destination);

        assertEquals("Unexpected remaining in original QBB", originalRemaining, _slicedBuffer.remaining());
        assertEquals("Unexpected remaining in destination", 0, destination.remaining());

        Assert.assertArrayEquals("Unexpected copyTo result", source, destination.array());
    }

    public void testCopyToArray()
    {
        _slicedBuffer = createSlice();
        byte[] source = getTestBytes(_slicedBuffer.remaining());
        _slicedBuffer.put(source);
        _slicedBuffer.flip();

        int originalRemaining = _slicedBuffer.remaining();
        byte[] destination = new byte[source.length];
        _slicedBuffer.copyTo(destination);

        assertEquals("Unexpected remaining in original QBB", originalRemaining, _slicedBuffer.remaining());

        Assert.assertArrayEquals("Unexpected copyTo result", source, destination);
    }

    public void testPutCopyOf()
    {
        _slicedBuffer = createSlice();
        byte[] source = getTestBytes(_slicedBuffer.remaining());

        QpidByteBuffer sourceQpidByteBuffer =  QpidByteBuffer.wrap(source);
        _slicedBuffer.putCopyOf(sourceQpidByteBuffer);

        assertEquals("Copied buffer should not be changed", source.length, sourceQpidByteBuffer.remaining());
        assertEquals("Buffer should be full", 0, _slicedBuffer.remaining());
        _slicedBuffer.flip();

        byte[] destination = new byte[source.length];
        _slicedBuffer.get(destination);

        Assert.assertArrayEquals("Unexpected putCopyOf result", source, destination);
    }

    public void testCompact()
    {
        _slicedBuffer = createSlice();
        byte[] source = getTestBytes(_slicedBuffer.remaining());
        _slicedBuffer.put(source);

        _slicedBuffer.position(1);
        _slicedBuffer.limit(_slicedBuffer.limit() - 1);

        int remaining =  _slicedBuffer.remaining();
        _slicedBuffer.compact();

        assertEquals("Unexpected position", remaining, _slicedBuffer.position());
        assertEquals("Unexpected limit", _slicedBuffer.capacity(), _slicedBuffer.limit());

        _slicedBuffer.flip();


        byte[] destination =  new byte[_slicedBuffer.remaining()];
        _slicedBuffer.get(destination);

        byte[] expected =  new byte[source.length - 2];
        System.arraycopy(source, 1, expected, 0, expected.length);

        Assert.assertArrayEquals("Unexpected compact result", expected, destination);
    }

    public void testSlice()
    {
        _slicedBuffer = createSlice();
        byte[] source = getTestBytes(_slicedBuffer.remaining());
        _slicedBuffer.put(source);

        _slicedBuffer.position(1);
        _slicedBuffer.limit(_slicedBuffer.limit() - 1);

        int remaining = _slicedBuffer.remaining();
        QpidByteBuffer newSlice = _slicedBuffer.slice();
        try
        {
            assertEquals("Unexpected position in original", 1, _slicedBuffer.position());
            assertEquals("Unexpected limit in original", source.length - 1, _slicedBuffer.limit());
            assertEquals("Unexpected position", 0, newSlice.position());
            assertEquals("Unexpected limit", remaining, newSlice.limit());
            assertEquals("Unexpected capacity", remaining, newSlice.capacity());

            byte[] destination =  new byte[newSlice.remaining()];
            newSlice.get(destination);

            byte[] expected =  new byte[source.length - 2];
            System.arraycopy(source, 1, expected, 0, expected.length);
            Assert.assertArrayEquals("Unexpected slice result", expected, destination);
        }
        finally
        {
            newSlice.dispose();
        }
    }

    public void testView()
    {
        _slicedBuffer = createSlice();
        byte[] source = getTestBytes(_slicedBuffer.remaining());
        _slicedBuffer.put(source);

        _slicedBuffer.position(1);
        _slicedBuffer.limit(_slicedBuffer.limit() - 1);

        QpidByteBuffer view = _slicedBuffer.view(0, _slicedBuffer.remaining());
        try
        {
            assertEquals("Unexpected position in original", 1, _slicedBuffer.position());
            assertEquals("Unexpected limit in original", source.length - 1, _slicedBuffer.limit());

            assertEquals("Unexpected position", 0, view.position());
            assertEquals("Unexpected limit", _slicedBuffer.remaining(), view.limit());
            assertEquals("Unexpected capacity", _slicedBuffer.remaining(), view.capacity());

            byte[] destination =  new byte[view.remaining()];
            view.get(destination);

            byte[] expected =  new byte[source.length - 2];
            System.arraycopy(source, 1, expected, 0, expected.length);
            Assert.assertArrayEquals("Unexpected view result", expected, destination);
        }
        finally
        {
            view.dispose();
        }

        view = _slicedBuffer.view(1, _slicedBuffer.remaining() - 2);
        try
        {
            assertEquals("Unexpected position in original", 1, _slicedBuffer.position());
            assertEquals("Unexpected limit in original", source.length - 1, _slicedBuffer.limit());

            assertEquals("Unexpected position", 0, view.position());
            assertEquals("Unexpected limit", _slicedBuffer.remaining() - 2, view.limit());
            assertEquals("Unexpected capacity", _slicedBuffer.remaining() - 2, view.capacity());

            byte[] destination =  new byte[view.remaining()];
            view.get(destination);

            byte[] expected =  new byte[source.length - 4];
            System.arraycopy(source, 2, expected, 0, expected.length);
            Assert.assertArrayEquals("Unexpected view result", expected, destination);
        }
        finally
        {
            view.dispose();
        }
    }

    public void testAsInputStream() throws Exception
    {
        _slicedBuffer = createSlice();
        byte[] source = getTestBytes(_slicedBuffer.remaining());
        _slicedBuffer.put(source);

        _slicedBuffer.position(1);
        _slicedBuffer.limit(_slicedBuffer.limit() - 1);

        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        try(InputStream is = _slicedBuffer.asInputStream())
        {
            ByteStreams.copy(is, destination);
        }

        byte[] expected =  new byte[source.length - 2];
        System.arraycopy(source, 1, expected, 0, expected.length);
        Assert.assertArrayEquals("Unexpected view result", expected, destination.toByteArray());
    }

    public void testAsByteBuffer() throws Exception
    {
        _slicedBuffer = createSlice();
        byte[] source = getTestBytes(_slicedBuffer.remaining());
        _slicedBuffer.put(source);

        _slicedBuffer.position(1);
        _slicedBuffer.limit(_slicedBuffer.limit() - 1);


        ByteBuffer buffer = _slicedBuffer.asByteBuffer();
        assertEquals("Unexpected capacity", _slicedBuffer.capacity(), buffer.capacity());
        assertEquals("Unexpected position", _slicedBuffer.position(), buffer.position());
        assertEquals("Unexpected limit", _slicedBuffer.limit(), buffer.limit());

        buffer.clear();

        byte[] target = new byte[source.length];
        buffer.get(target);
        Assert.assertArrayEquals("Unexpected asByteBuffer result", source, target);
    }

    public void testDecode()
    {
        _slicedBuffer = createSlice();
        final String input = "ABC";
        _slicedBuffer.put(input.getBytes());
        _slicedBuffer.flip();

        final CharBuffer charBuffer = _slicedBuffer.decode(StandardCharsets.US_ASCII);
        final char[] destination = new char[charBuffer.remaining()];
        charBuffer.get(destination);
        Assert.assertArrayEquals("Unexpected char buffer", input.toCharArray(), destination);
    }

    private byte[] getTestBytes(final int length)
    {
        final byte[] source = new byte[length];
        for (int i = 0; i < source.length; i++)
        {
            source[i] = (byte) ('A' + i);
        }
        return source;
    }

    private QpidByteBuffer createSlice()
    {
        _parent.position(1);
        _parent.limit(_parent.capacity() - 1);

        return _parent.slice();
    }

    private void testPutGet(final Class<?> primitiveTargetClass, final boolean unsigned, final Object value) throws Exception
    {
        int size = sizeof(primitiveTargetClass);

        _parent.position(1);
        _parent.limit(size + 1);

        _slicedBuffer = _parent.slice();
        _parent.limit(_parent.capacity());

        String methodSuffix = getMethodSuffix(primitiveTargetClass, unsigned);
        Method put = _slicedBuffer.getClass().getMethod("put" + methodSuffix, Primitives.primitiveTypeOf(value.getClass()));
        Method get = _slicedBuffer.getClass().getMethod("get" + methodSuffix);


        _slicedBuffer.mark();
        QpidByteBuffer rv = (QpidByteBuffer) put.invoke(_slicedBuffer, value);
        assertEquals("Unexpected builder return value for type " + methodSuffix, _slicedBuffer, rv);

        assertEquals("Unexpected position for type " + methodSuffix, size, _slicedBuffer.position());

        try
        {
            invokeMethod(put, value);
            fail("BufferOverflowException should be thrown for put with insufficient room for " + methodSuffix);
        }
        catch (BufferOverflowException e)
        {
            // pass
        }

        _slicedBuffer.reset();

        assertEquals("Unexpected position after reset", 0, _slicedBuffer.position());

        Object retrievedValue = get.invoke(_slicedBuffer);
        assertEquals("Unexpected value retrieved from get method for " + methodSuffix, value, retrievedValue);
        try
        {
            invokeMethod(get);
            fail("BufferUnderflowException not thrown for get with insufficient room for " + methodSuffix);
        }
        catch (BufferUnderflowException ite)
        {
           // pass
        }
    }

    private void testPutGetByIndex(final Class<?> primitiveTargetClass, Object value) throws Exception
    {
        int size = sizeof(primitiveTargetClass);

        _parent.position(1);
        _parent.limit(size + 1);

        _slicedBuffer = _parent.slice();
        _parent.limit(_parent.capacity());

        String methodSuffix = getMethodSuffix(primitiveTargetClass, false);
        Method put = _slicedBuffer.getClass().getMethod("put" + methodSuffix, int.class, primitiveTargetClass);
        Method get = _slicedBuffer.getClass().getMethod("get" + methodSuffix, int.class);

        QpidByteBuffer rv = (QpidByteBuffer) put.invoke(_slicedBuffer, 0, value);
        assertEquals("Unexpected builder return value for type " + methodSuffix, _slicedBuffer, rv);

        Object retrievedValue = get.invoke(_slicedBuffer, 0);
        assertEquals("Unexpected value retrieved from index get method for " + methodSuffix, value, retrievedValue);

        try
        {
            invokeMethod(put, 1, value);
            fail("IndexOutOfBoundsException not thrown for  indexed " + methodSuffix + " put");
        }
        catch (IndexOutOfBoundsException ite)
        {
            // pass
        }

        try
        {
            invokeMethod(put, -1, value);
            fail("IndexOutOfBoundsException not thrown for indexed " + methodSuffix + " put with negative index");
        }
        catch (IndexOutOfBoundsException ite)
        {
            // pass
        }

        try
        {
            invokeMethod(get, 1);
            fail("IndexOutOfBoundsException not thrown for indexed " + methodSuffix + " get");
        }
        catch (IndexOutOfBoundsException ite)
        {
            // pass
        }

        try
        {
            invokeMethod(get, -1);
            fail("IndexOutOfBoundsException not thrown for indexed " + methodSuffix + " get with negative index");
        }
        catch (IndexOutOfBoundsException ite)
        {
            // pass
        }
    }

    private void invokeMethod(final Method method, final Object... value)
            throws Exception
    {
        try
        {
            method.invoke(_slicedBuffer, value);
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof Exception)
            {
                throw (Exception)cause;
            }
            fail(String.format("Unexpected throwable on method %s invocation: %s", method.getName(), cause));
        }
    }


    private String getMethodSuffix(final Class<?> target, final boolean unsigned)
    {
        StringBuilder name = new StringBuilder();
        if (unsigned)
        {
            name.append("Unsigned");
        }
        if ((!target.isAssignableFrom(byte.class) || unsigned))
        {
            String simpleName = target.getSimpleName();
            name.append(simpleName.substring(0, 1).toUpperCase()).append(simpleName.substring(1));
        }

        return name.toString();
    }

    private int sizeof(final Class<?> type)
    {
        if (type.isAssignableFrom(double.class))
        {
            return 8;
        }
        else if (type.isAssignableFrom(float.class))
        {
            return 4;
        }
        else if (type.isAssignableFrom(long.class))
        {
            return 8;
        }
        else if (type.isAssignableFrom(int.class))
        {
            return 4;
        }
        else if (type.isAssignableFrom(short.class))
        {
            return 2;
        }
        else if (type.isAssignableFrom(char.class))
        {
            return 2;
        }
        else if (type.isAssignableFrom(byte.class))
        {
            return 1;
        }
        else
        {
            throw new UnsupportedOperationException("Unexpected type " + type);
        }
   }
}
