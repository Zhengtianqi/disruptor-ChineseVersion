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

import com.lmax.disruptor.util.Util;

/**
 * 缓冲行填充，防止伪共享。真正使用的值是nextValue和cachedValue。
 */
abstract class SingleProducerSequencerPad extends AbstractSequencer
{
    protected long p1, p2, p3, p4, p5, p6, p7;

    SingleProducerSequencerPad(int bufferSize, WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }
}

/**
 * SingleProducerSequencerFields 维护事件生产者发布的序列和消费者处理到的最小序列。
 */
abstract class SingleProducerSequencerFields extends SingleProducerSequencerPad
{
    SingleProducerSequencerFields(int bufferSize, WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }

    // nextValue-生产者申请到的下一个位置序列
    long nextValue = Sequence.INITIAL_VALUE;
    // cachedValue-消费者上次消费到的位置序列
    long cachedValue = Sequence.INITIAL_VALUE;
}

/**
* SingleProducerSequencer内部维护cachedValue(事件消费者序列),nextValue(事件生产者序列)。并且采用padding填充; 这个类是线程不安全的
* <p>
* SingleProducerSequencer对象拥有所有正在访问RingBuffer的消费者gatingSequences列表在调用完{@link Sequencer#publish(long)}之后更新游标
*/

public final class SingleProducerSequencer extends SingleProducerSequencerFields
{
    protected long p1, p2, p3, p4, p5, p6, p7;

	/**
	 * 使用选定的等待策略和buffer大小构造Sequencer。
	 *
	 * @param bufferSize 这个序列的buffer大小
	 * @param waitStrategy 等待策略
	 */
    public SingleProducerSequencer(int bufferSize, WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }

    /**
     * 当前序列的nextValue + requiredCapacity是事件生产者要申请的序列值
     * 
     * @see Sequencer#hasAvailableCapacity(int)
     */
    @Override
    public boolean hasAvailableCapacity(int requiredCapacity)
    {
        return hasAvailableCapacity(requiredCapacity, false);
    }

    private boolean hasAvailableCapacity(int requiredCapacity, boolean doStore)
    {
        long nextValue = this.nextValue;

        long wrapPoint = (nextValue + requiredCapacity) - bufferSize;
        long cachedGatingSequence = this.cachedValue;

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
        {
            if (doStore)
            {
                cursor.setVolatile(nextValue);  // StoreLoad fence
            }

            long minSequence = Util.getMinimumSequence(gatingSequences, nextValue);
            this.cachedValue = minSequence;

            if (wrapPoint > minSequence)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * next方法是真正申请序列的方法, 里面的逻辑和hasAvailableCapacity一样, 只是在不能申请序列的时候会阻塞等待一下, 然后重试
     * 
     * @see Sequencer#next()
     */
    @Override
    public long next()
    {
        return next(1);
    }

    /**
     * 该方法是事件生产者申请序列
     * 
     * @see Sequencer#next(int)
     */
    @Override
    public long next(int n)
    {
    	// 该方法是事件生产者申请序列，n表示此次发布者期望获取多少个序号，通常是1
        if (n < 1 || n > bufferSize)
        {
            throw new IllegalArgumentException("n must be > 0 and < bufferSize");
        }

        // 复制上次成功申请的序列
        long nextValue = this.nextValue;
        // 加上n后，得到本次需要申请的序列
        long nextSequence = nextValue + n;
        // 本次申请的序列减去环形数组的长度，得到绕一圈后的序列
        long wrapPoint = nextSequence - bufferSize;
        // 复制消费者上次消费到的序列位置
        long cachedGatingSequence = this.cachedValue;
        // 如果本次申请的序列，绕一圈后，从消费者后面追上，或者消费者上次消费的序列大于生产者上次申请的序列，则说明发生追尾了，需要进一步处理
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
        {
        	// wrapPoint > cachedGatingSequence 代表绕一圈并且位置大于消费者处理到的序列
        	// cachedGatingSequence > nextValue 说明事件生产者的位置位于消费者的后面
        	// 维护父类中事件生产者的序列
            cursor.setVolatile(nextValue);  // StoreLoad fence

            long minSequence;
            // 如果事件生产者绕一圈以后大于消费者的序列，那么会在此处自旋
            while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue)))
            {
                LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
            }
            
            // 循环退出后，将获取的消费者最小序列，赋值给cachedValue
            this.cachedValue = minSequence;
        }

        // 将成功申请到的nextSequence赋值给nextValue
        this.nextValue = nextSequence;

        return nextSequence;
    }

    /**
     * tryNext方法是next方法的非阻塞版本, 不能申请就抛异常
     * 
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

        if (!hasAvailableCapacity(n, true))
        {
            throw InsufficientCapacityException.INSTANCE;
        }

        long nextSequence = this.nextValue += n;

        return nextSequence;
    }

    /**
     * remainingCapacity方法就是环形队列的容量减去事件生产者与消费者的序列差
     * 
     * @see Sequencer#remainingCapacity()
     */
    @Override
    public long remainingCapacity()
    {
        long nextValue = this.nextValue;

        long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
        long produced = nextValue;
        return getBufferSize() - (produced - consumed);
    }

    /**
     *  claim方法是声明一个序列, 在初始化的时候用
     *  
     * @see Sequencer#claim(long)
     */
    @Override
    public void claim(long sequence)
    {
        this.nextValue = sequence;
    }

    /**
     * 发布一个序列,会先设置内部游标值,然后唤醒等待的消费者
     * 
     * @see Sequencer#publish(long)
     */
    @Override
    public void publish(long sequence)
    {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * @see Sequencer#publish(long, long)
     */
    @Override
    public void publish(long lo, long hi)
    {
        publish(hi);
    }

    /**
     * @see Sequencer#isAvailable(long)
     */
    @Override
    public boolean isAvailable(long sequence)
    {
        return sequence <= cursor.get();
    }

    @Override
    public long getHighestPublishedSequence(long lowerBound, long availableSequence)
    {
        return availableSequence;
    }
}
