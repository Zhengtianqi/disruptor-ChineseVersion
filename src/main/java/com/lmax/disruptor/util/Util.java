/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor.util;

import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

/**
 * Disruptor使用的公共方法
 */
public final class Util
{
	/**
	 * 计算下一个2的幂,大于或等于x。
	 * 
	 * @param x 值
	 * @return 从x开始的下一个2的幂
	 */
    public static int ceilingNextPowerOfTwo(final int x)
    {
        return 1 << (32 - Integer.numberOfLeadingZeros(x - 1));
    }

	/**
	 * 从{@link com.lmax.disruptor.Sequence}数组中获取最小序列。
	 * 
	 * @param sequences 要比较的序列
	 * @return t找到的最小序列,当数组为空时返回Long.MAX_VALUE
	 */
    public static long getMinimumSequence(final Sequence[] sequences)
    {
        return getMinimumSequence(sequences, Long.MAX_VALUE);
    }

	/**
	 * 从{@link com.lmax.disruptor.Sequence}数组中获取最小序列。
	 * 
	 * @param sequences 要比较的序列
	 * @param minimum   初始默认最小值. 如果数组为空,则返回该值。
	 * @return sequence 需要的值{@code sequences}和 {@code minimum}; {@code minimum}
	 *         如果{@code sequences}为空。
	 */
    public static long getMinimumSequence(final Sequence[] sequences, long minimum)
    {
        for (int i = 0, n = sequences.length; i < n; i++)
        {
            long value = sequences[i].get();
            minimum = Math.min(minimum, value);
        }

        return minimum;
    }

	/**
	 * 获取传递的{@link EventProcessor}的{@link Sequence}数组
	 * 
	 * @param processors 哪个得到序列
	 * @return he array of {@link Sequence}数据。
	 */
    public static Sequence[] getSequencesFor(final EventProcessor... processors)
    {
        Sequence[] sequences = new Sequence[processors.length];
        for (int i = 0; i < sequences.length; i++)
        {
            sequences[i] = processors[i].getSequence();
        }

        return sequences;
    }

    private static final Unsafe THE_UNSAFE;

    static
    {
        try
        {
            final PrivilegedExceptionAction<Unsafe> action = new PrivilegedExceptionAction<Unsafe>()
            {
                public Unsafe run() throws Exception
                {
                    Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafe.setAccessible(true);
                    return (Unsafe) theUnsafe.get(null);
                }
            };

            THE_UNSAFE = AccessController.doPrivileged(action);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Unable to load unsafe", e);
        }
    }

	/**
	 * 获取Unsafe实例的句柄,用于访问低级并发和内存构造。
	 * 
	 * @return The Unsafe
	 */
    public static Unsafe getUnsafe()
    {
        return THE_UNSAFE;
    }

	/**
	 * 计算提供的整数的log2,取整数,小数点后省略。
	 * 
	 * @param i 计算log2的值
	 * @return log2的值,取整数去小数
	 */
    public static int log2(int i)
    {
        int r = 0;
        while ((i >>= 1) != 0)
        {
            ++r;
        }
        return r;
    }

    public static long awaitNanos(Object mutex, long timeoutNanos) throws InterruptedException
    {
        long millis = timeoutNanos / 1_000_000;
        long nanos = timeoutNanos % 1_000_000;

        long t0 = System.nanoTime();
        mutex.wait(millis, (int) nanos);
        long t1 = System.nanoTime();

        return timeoutNanos - (t1 - t0);
    }
}
