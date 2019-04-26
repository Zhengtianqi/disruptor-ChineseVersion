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
package com.lmax.disruptor;

import java.util.concurrent.locks.LockSupport;

import sun.misc.Unsafe;

import com.lmax.disruptor.util.Util;


/**
 * 
 * 适用于多线程publiser。MultiProducerSequencer每次获取序列都是从Sequence中获取的。Sequence中针对value的操作都是原子的。
 * 
 * MultiProducerSequencer内部多了一个availableBuffer，是一个int型的数组，size大小和RingBuffer的Size一样大，
 * 用来追踪Ringbuffer每个槽的状态，构造MultiProducerSequencer的时候会进行初始化，availableBuffer数组中的每个元素会被初始化成-1。
 *    
 */
public final class MultiProducerSequencer extends AbstractSequencer
{
	//获取unsafe
    private static final Unsafe UNSAFE = Util.getUnsafe();
    //获取int[]的偏移量
    private static final long BASE = UNSAFE.arrayBaseOffset(int[].class);
    //获取元素的大小，也就是int的大小4个字节
    private static final long SCALE = UNSAFE.arrayIndexScale(int[].class);
    //gatingSequenceCache是gatingSequence。用来标识事件处理者的序列
    private final Sequence gatingSequenceCache = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    // availableBuffer是用来记录每一个ringbuffer槽的状态。 
    private final int[] availableBuffer;
    private final int indexMask;
    //转了几圈
    private final int indexShift;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize   the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public MultiProducerSequencer(int bufferSize, final WaitStrategy waitStrategy)
    {
    	//初始化父类
        super(bufferSize, waitStrategy);
        //初始化availableBuffer
        availableBuffer = new int[bufferSize];
        indexMask = bufferSize - 1;
        indexShift = Util.log2(bufferSize);
        //这个逻辑是。计算availableBuffer中每个元素的偏移量
        //定位数组每个值的地址就是(index * SCALE) + BASE
        initialiseAvailableBuffer();
    }

    /**
     * 逻辑和前面SingleProducerSequencer内部一样，区别是这里使用了cursor.get()，里面获取的是一个volatile的value值。 
     * @see Sequencer#hasAvailableCapacity(int)
     */
    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        return hasAvailableCapacity(gatingSequences, requiredCapacity, cursor.get());
    }

    private boolean hasAvailableCapacity(Sequence[] gatingSequences, final int requiredCapacity, long cursorValue)
    {
        long wrapPoint = (cursorValue + requiredCapacity) - bufferSize;
        long cachedGatingSequence = gatingSequenceCache.get();

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > cursorValue)
        {
            long minSequence = Util.getMinimumSequence(gatingSequences, cursorValue);
            gatingSequenceCache.set(minSequence);

            if (wrapPoint > minSequence)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * @see Sequencer#claim(long)
     */
    @Override
    public void claim(long sequence)
    {
        cursor.set(sequence);
    }

    /**
     * @see Sequencer#next()
     */
    @Override
    public long next()
    {
        return next(1);
    }

    /**
     *    逻辑还是一样，区别是里面的增加当前序列值是原子操作。
     * @see Sequencer#next(int)
     */
    @Override
    public long next(int n)
    {
        if (n < 1)
        {
            throw new IllegalArgumentException("n must be > 0");
        }

        long current;
        long next;
        //生产者不断尝试地去获取下一个可用的Sequence，确保：下一个可用的seq不会覆盖消费者的最小seq，且下一个可用的seq不会超过当前cursor的值。
        do
        {
        	//获取事件发布者发布序列
            current = cursor.get();
            //新序列位置
            next = current + n;
            //wrap 代表申请的序列绕一圈以后的位置
            long wrapPoint = next - bufferSize;
            //gatingSequenceCache将上次计算出来的gatingSequence记录下来，以备下次循环时判定。获取事件处理者处理到的序列值
            long cachedGatingSequence = gatingSequenceCache.get();
            /** 1.事件发布者要申请的序列值大于事件处理者当前的序列值且事件发布者要申请的序列值减去环的长度要小于事件处理者的序列值。
             * 2.满足(1)，可以申请给定的序列。
             * 3.不满足(1)，就需要查看一下当前事件处理者的最小的序列值(可能有多个事件处理者)。如果最小序列值大于等于
             * 当前事件处理者的最小序列值大了一圈，那就不能申请了序列(申请了就会被覆盖)，
             * */
            if (wrapPoint > cachedGatingSequence || cachedGatingSequence > current)
            {
            	//wrapPoint > cachedGatingSequence 代表绕一圈并且位置大于事件处理者处理到的序列
                //cachedGatingSequence > current 说明事件发布者的位置位于事件处理者的屁股后面

                //获取最小的事件处理者序列
                long gatingSequence = Util.getMinimumSequence(gatingSequences, current);

                //下一个可用的seq不会覆盖消费者的最小seq,且下一个可用的seq不会超过当前cursor的值。
                if (wrapPoint > gatingSequence)
                {
                    LockSupport.parkNanos(1); // TODO, should we spin based on the wait strategy?
                    continue;
                }
                //赋值
                gatingSequenceCache.set(gatingSequence);
                //通过cas修改
            }
            else if (cursor.compareAndSet(current, next))
            {
                break;
            }
        }
        while (true);

        return next;
    }

    /**
     * @see Sequencer#tryNext()
     */
    @Override
    public long tryNext() throws InsufficientCapacityException
    {
        return tryNext(1);
    }

    /**
     * @see Sequencer#tryNext(int)
     */
    @Override
    public long tryNext(int n) throws InsufficientCapacityException
    {
        if (n < 1)
        {
            throw new IllegalArgumentException("n must be > 0");
        }

        long current;
        long next;

        do
        {
            current = cursor.get();
            next = current + n;

            if (!hasAvailableCapacity(gatingSequences, n, current))
            {
                throw InsufficientCapacityException.INSTANCE;
            }
        }
        while (!cursor.compareAndSet(current, next));

        return next;
    }

    /**
     * @see Sequencer#remainingCapacity()
     */
    @Override
    public long remainingCapacity()
    {
        long consumed = Util.getMinimumSequence(gatingSequences, cursor.get());
        long produced = cursor.get();
        return getBufferSize() - (produced - consumed);
    }

    private void initialiseAvailableBuffer()
    {
        for (int i = availableBuffer.length - 1; i != 0; i--)
        {
            setAvailableBufferValue(i, -1);
        }

        setAvailableBufferValue(0, -1);
    }

    /**
     * publish与单生产者区别较大， 方法中会将当前序列值的可用状态记录到availableBuffer里面，
     * 而记录的这个值其实就是sequence除以bufferSize，也就是当前sequence绕buffer的圈数。 
     * 
     * @see Sequencer#publish(long)
     */
    @Override
    public void publish(final long sequence)
    {
    	//这里的操作逻辑大概是修改数组中的序列值
        setAvailable(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * @see Sequencer#publish(long, long)
     */
    @Override
    public void publish(long lo, long hi)
    {
        for (long l = lo; l <= hi; l++)
        {
            setAvailable(l);
        }
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * The below methods work on the availableBuffer flag.
     * <p>
     * The prime reason is to avoid a shared sequence object between publisher threads.
     * (Keeping single pointers tracking start and end would require coordination
     * between the threads).
     * <p>
     * --  Firstly we have the constraint that the delta between the cursor and minimum
     * gating sequence will never be larger than the buffer size (the code in
     * next/tryNext in the Sequence takes care of that).
     * -- Given that; take the sequence value and mask off the lower portion of the
     * sequence as the index into the buffer (indexMask). (aka modulo operator)
     * -- The upper portion of the sequence becomes the value to check for availability.
     * ie: it tells us how many times around the ring buffer we've been (aka division)
     * -- Because we can't wrap without the gating sequences moving forward (i.e. the
     * minimum gating sequence is effectively our last available position in the
     * buffer), when we have new data and successfully claimed a slot we can simply
     * write over the top.
     */
    private void setAvailable(final long sequence)
    {
        setAvailableBufferValue(calculateIndex(sequence), calculateAvailabilityFlag(sequence));
    }

    private void setAvailableBufferValue(int index, int flag)
    {
        long bufferAddress = (index * SCALE) + BASE;
        //修改内存偏移地址为bufferAddress的值，改为flag
        UNSAFE.putOrderedInt(availableBuffer, bufferAddress, flag);
    }

    /**
     * 
     * getHighestPublishedSequence方法基于isAvailable实现
     * 
     * @see Sequencer#isAvailable(long)
     */
    @Override
    public boolean isAvailable(long sequence)
    {
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        long bufferAddress = (index * SCALE) + BASE;
        return UNSAFE.getIntVolatile(availableBuffer, bufferAddress) == flag;
    }

    @Override
    public long getHighestPublishedSequence(long lowerBound, long availableSequence)
    {
        for (long sequence = lowerBound; sequence <= availableSequence; sequence++)
        {
            if (!isAvailable(sequence))
            {
                return sequence - 1;
            }
        }

        return availableSequence;
    }

    //计算数组中的存储的数据
    private int calculateAvailabilityFlag(final long sequence)
    {
        return (int) (sequence >>> indexShift);
    }
    
    //计算数组中位置 sequence&(buffsize-1)
    private int calculateIndex(final long sequence)
    {
        return ((int) sequence) & indexMask;
    }
}
