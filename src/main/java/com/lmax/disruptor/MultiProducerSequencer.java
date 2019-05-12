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
 * <p>适用于跨多个生产者线程排序; MultiProducerSequencer每次获取序列都是从{@link Sequence}中获取的,Sequence中针对value的操作都是原子的</p>
 * 
 * 关于{@link Sequencer#getCursor()}的注意事项: 使用此序列器, 在调用{@link Sequencer#next()}之后更新游标值,
 * 以确定可读取的最高可用序列, 然后应该使用{@link Sequencer#getHighestPublishedSequence(long, long)}
 * 
 *	MultiProducerSequencer内部多了一个availableBuffer，是一个int型的数组，size大小和RingBuffer的Size一样大，
 *	用来追踪Ringbuffer每个槽的状态，构造MultiProducerSequencer的时候会进行初始化，availableBuffer数组中的每个元素会被初始化成-1。
 */
public final class MultiProducerSequencer extends AbstractSequencer
{
	// 获取unsafe
    private static final Unsafe UNSAFE = Util.getUnsafe();
    // 获取int[]的偏移量
    private static final long BASE = UNSAFE.arrayBaseOffset(int[].class);
    // 获取元素的大小，也就是int的大小4个字节
    private static final long SCALE = UNSAFE.arrayIndexScale(int[].class);
    
    // gatingSequenceCache是gatingSequence。用来标识事件处理者的序列
    private final Sequence gatingSequenceCache = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    // availableBuffer是用来记录每一个ringbuffer槽的状态。
    private final int[] availableBuffer;
    private final int indexMask;
    // 转了几圈
    private final int indexShift;

	/**
	 *	用选定的等待策略和缓冲区大小构造一个序列器
	 *
	 * @param bufferSize   将要排序的缓冲区大小
	 * @param waitStrategy 等待序列的等待策略
	 */
    public MultiProducerSequencer(int bufferSize, final WaitStrategy waitStrategy)
    {
    	// 初始化父类
        super(bufferSize, waitStrategy);
        // 初始化availableBuffer
        availableBuffer = new int[bufferSize];
        indexMask = bufferSize - 1;
        indexShift = Util.log2(bufferSize);
        // 这个逻辑是: 计算availableBuffer中每个元素的偏移量; 定位数组每个值的地址就是(index * SCALE) + BASE
        initialiseAvailableBuffer();
    }

    /**
     * 逻辑和前面SingleProducerSequencer内部一样, 区别是这里使用了cursor.get(), 里面获取的是一个volatile的value值
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
     * @see Sequencer#next(int)
     */
    @Override
    public long next(int n)
    {
        if (n < 1 || n > bufferSize)
        {
            throw new IllegalArgumentException("n must be > 0 and < bufferSize");
        }

        long current;
        long next;
        // 生产者不断尝试地去获取下一个可用的Sequence; 确保: 下一个可用的seq不会覆盖消费者的最小seq，且下一个可用的seq不会超过当前cursor的值
        do
        {
        	// 获取事件生产者生产序列
            current = cursor.get();
            // 新序列位置
            next = current + n;
            
            // wrapPoint代表申请的序列绕一圈以后的位置
            long wrapPoint = next - bufferSize;
            // gatingSequenceCache将上次计算出来的gatingSequence记录下来, 以备下次循环时判定;获取事件处理者处理到的序列值
            long cachedGatingSequence = gatingSequenceCache.get();
			/**
			 * 事件发布者要申请的序列值大于事件处理者当前的序列值且事件发布者要申请的序列值减去环的长度要小于事件处理者的序列值
			 * wrapPoint > cachedGatingSequence 代表绕一圈并且位置大于事件消费者处理到的序列
			 * cachedGatingSequence > current 说明事件生产者的位置位于事件消费者的后面
			 */
            if (wrapPoint > cachedGatingSequence || cachedGatingSequence > current)
            {
            	// 获取最小的事件处理者序列
                long gatingSequence = Util.getMinimumSequence(gatingSequences, current);

                // 下一个可用的seq不会覆盖消费者的最小seq,且下一个可用的seq不会超过当前cursor的值
                if (wrapPoint > gatingSequence)
                {
                    LockSupport.parkNanos(1); // TODO, should we spin based on the wait strategy?
                    continue;
                }
                
                // 赋值
                gatingSequenceCache.set(gatingSequence);
            }
            // 通过cas修改
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
	 * publish与单生产者区别较大,  方法中会将当前序列值的可用状态记录到availableBuffer里面,
	 * 而记录的这个值其实就是sequence除以bufferSize,也就是当前sequence绕buffer的圈数
	 * 
	 * @see Sequencer#publish(long)
	 */
    @Override
    public void publish(final long sequence)
    {
    	// 这里的操作逻辑是修改数组中的序列值
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
	 * 以下方法适用于availableBuffer标志
	 * <p>
	 * 主要原因是避免发布者线程之间的共享序列对象(保持单指针跟踪开始和结束将需要线程之间的协调)
	 * <p>
	 * -- 首先，我们有一个约束，即游标和最小门控序列之间的增量永远不会大于缓冲区大小(Sequence中的next/tryNext中的代码负责处理) --
	 * 鉴于; 取序列值并掩盖掉这些序列的下半部分作为缓冲区的索引（indexMask）。（又名模运算符） -- 序列的上半部分成为检查可用性的值。
	 * 即:它告诉我们RingBuffer周围有多少次（也就是划分）
	 * --因为我们无法在没有门控序列向前移动的情况下进行换行（即最小门控序列实际上是我们在缓冲区中的最后一个可用位置），
	 * 当我们有新数据并成功声明一个时隙时，我们可以简单地在顶部写入。
	 */
    private void setAvailable(final long sequence)
    {
        setAvailableBufferValue(calculateIndex(sequence), calculateAvailabilityFlag(sequence));
    }

    private void setAvailableBufferValue(int index, int flag)
    {
        long bufferAddress = (index * SCALE) + BASE;
        UNSAFE.putOrderedInt(availableBuffer, bufferAddress, flag);
    }

    /**
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

    // 计算数组中的存储的数据
    private int calculateAvailabilityFlag(final long sequence)
    {
        return (int) (sequence >>> indexShift);
    }

    // 计算数组中位置 sequence&(buffsize-1)
    private int calculateIndex(final long sequence)
    {
        return ((int) sequence) & indexMask;
    }
}
