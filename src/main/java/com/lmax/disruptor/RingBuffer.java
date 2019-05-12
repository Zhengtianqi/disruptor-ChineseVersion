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


import sun.misc.Unsafe;

import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.Util;

/**
 * RingBufferPad用于缓存行填充，消除伪共享
 */
abstract class RingBufferPad
{
    protected long p1, p2, p3, p4, p5, p6, p7;
}

/**
 * 这个类的逻辑比较重要，讲解了event在数组中存储位置
 * 
 * @param <E>
 */
abstract class RingBufferFields<E> extends RingBufferPad
{
	// Buffer数组填充
    private static final int BUFFER_PAD;
    // entry存储位置相对与array起始位置的偏移量, 用于UNSAFE内存操作时进行寻址, 注意这个偏移量加上了用于填充的BUFFER_PAD大小
    private static final long REF_ARRAY_BASE;
    // 对应对象引用占用内存大小, 计算出来的相对位移数, 比如对象引用大小是4byte, 那么REF_ELEMENT_SHIFT=2, 因为2的2次方=4;
    private static final int REF_ELEMENT_SHIFT;
    private static final Unsafe UNSAFE = Util.getUnsafe();

    static
    {
    	// 获取数组中一个元素的大小. Object[]数组的元素是对象引用
    	// Java对象引用的大小取决于你当前的环境，根据不同JVM的设置以及分配给JVM的内存大小，它可能是4个或者8个字节。
    	// 在大于32G的堆中，对像引用的大小总会是8个字节，但是在一个比较小的堆中它就会是4个字节，除非关闭JVM设置：-XX:-UseCompressedOops
        final int scale = UNSAFE.arrayIndexScale(Object[].class);
        if (4 == scale)
        {
            REF_ELEMENT_SHIFT = 2;
        }
        else if (8 == scale)
        {
            REF_ELEMENT_SHIFT = 3;
        }
        else
        {
            throw new IllegalStateException("Unknown pointer size");
        }
        // 填充32或者16
        BUFFER_PAD = 128 / scale;
        // 计算Buffer数组起始基址。用于UNSAFE内存操作时进行寻址，注意这个偏移量加上了用于填充的BUFFER_PAD大小
        REF_ARRAY_BASE = UNSAFE.arrayBaseOffset(Object[].class) + 128;
    }

    private final long indexMask;
    // 保存了RingBuffer每个槽的Event对象
    private final Object[] entries;
    // bufferSize必须是2的幂，indexMask就是bufferSize-1，这样取模更高效(sequence&indexMask)
    protected final int bufferSize;
    // sequencer=SingleProducerSequencer或者MultiProducerSequencer的引用
    protected final Sequencer sequencer;

    RingBufferFields(
        EventFactory<E> eventFactory,
        Sequencer sequencer)
    {
        this.sequencer = sequencer;
        this.bufferSize = sequencer.getBufferSize();

        if (bufferSize < 1)
        {
            throw new IllegalArgumentException("bufferSize must not be less than 1");
        }
        if (Integer.bitCount(bufferSize) != 1)
        {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }

        this.indexMask = bufferSize - 1;
        this.entries = new Object[sequencer.getBufferSize() + 2 * BUFFER_PAD];
        // 最后要填充事件
        fill(eventFactory);
    }

	/**
	 * 初始化时需要传入一个EventFactory, 用来做队列内事件的预填充。
	 * 
	 * @param eventFactory
	 */
    private void fill(EventFactory<E> eventFactory)
    {
        for (int i = 0; i < bufferSize; i++)
        {
            entries[BUFFER_PAD + i] = eventFactory.newInstance();
        }
    }

    @SuppressWarnings("unchecked")
    protected final E elementAt(long sequence)
    {
    	// 从内存中获取sequence对应的entry值
    	// (sequence & indexMask)相当于：sequence%(indexMask+1)，据此计算出sequence对应的entry
    	// 对模值左移REF_ELEMENT_SHIFT，是为了得到entry对应对象应用的实际大小值，REF_ELEMENT_SHIFT是enties[]数组的元素大小值，单位为byte
        return (E) UNSAFE.getObject(entries, REF_ARRAY_BASE + ((sequence & indexMask) << REF_ELEMENT_SHIFT));
    }
}

/**
 * 内部用数组来实现, 同时有保存数组长度的域bufferSize和下标掩码indexMask, 还有一个sequencer
 * Disruptor最主要的组件，负责存储和更新事件对象。
 * 如何往RingBuffer中发布事件: 
 * 1.定义好要生产的数据和相应的事件类(里面存放数据)
 * 2.定于好事件转换器或者直接用RingBuffer进行事件发布。 3.明确发布场景，合理的选择发布模式(单线程还是多线程)
 * 
 * @param <E> 存储的事件以便在事件在交换或者并行协调期间进行共享
 */
public final class RingBuffer<E> extends RingBufferFields<E> implements Cursored, EventSequencer<E>, EventSink<E>
{
    public static final long INITIAL_CURSOR_VALUE = Sequence.INITIAL_VALUE;
    protected long p1, p2, p3, p4, p5, p6, p7;

	/**
	 * 使用完整选项集构造RingBuffer。
	 *
	 * @param eventFactory 用于填充RingBuffer的newInstance entries条目
	 * @param sequencer 用于处理通过RingBuffer移动的事件的顺序
	 * @throws IllegalArgumentException 如果bufferSize小于1或不是2的幂
	 */
    RingBuffer(
        EventFactory<E> eventFactory,
        Sequencer sequencer)
    {
        super(eventFactory, sequencer);
    }

    /**
	 * RingBuffer提供了静态工厂方法分别针对单事件发布者和多事件发布者的情况进行RingBuffer实例创建。
	 *
	 * @param              <E> RingBuffer中的事件类
	 * @param factory      用于在RingBuffer内创建事件
	 * @param bufferSize   在RingBuffer中创建的元素数量
	 * @param waitStrategy 用于确定如何等待新元素变得可用
	 * @return 构造的RingBuffer
	 * @throws IllegalArgumentException 如果bufferSize小于1或不是2的幂
	 * @see MultiProducerSequencer
	 */
    public static <E> RingBuffer<E> createMultiProducer(
        EventFactory<E> factory,
        int bufferSize,
        WaitStrategy waitStrategy)
    {
        MultiProducerSequencer sequencer = new MultiProducerSequencer(bufferSize, waitStrategy);

        return new RingBuffer<E>(factory, sequencer);
    }

    /**
	 * 创建Sequencer对象的过程; 使用默认等待策略{@link BlockingWaitStrategy}创建一个新的多生产者的RingBuffer
	 *
	 * @param <E> RingBuffer中的事件类
	 * @param factory    用于在RingBuffer内创建事件
	 * @param bufferSize 在RingBuffer中创建的元素数量
	 * @return 构造一个RingBuffer
	 * @throws IllegalArgumentException 如果bufferSize小于1或不是2的幂
	 * @see MultiProducerSequencer
	 */
    public static <E> RingBuffer<E> createMultiProducer(EventFactory<E> factory, int bufferSize)
    {
        return createMultiProducer(factory, bufferSize, new BlockingWaitStrategy());
    }

    /**
	 * 使用指定的等待策略创建新的单个生产者的RingBuffer。
	 *
	 * @param <E> RingBuffer中的事件类
	 * @param factory      用于在RingBuffer内创建事件。
	 * @param bufferSize   在RingBuffer中创建的元素数量
	 * @param waitStrategy 等待策略: 用于确定如何等待新元素变得可用。
	 * @return 构建一个RingBuffer
	 * @throws IllegalArgumentException 如果bufferSize小于1或不是2的幂
	 * @see SingleProducerSequencer
	 */
    public static <E> RingBuffer<E> createSingleProducer(
        EventFactory<E> factory,
        int bufferSize,
        WaitStrategy waitStrategy)
    {
    	// 创建Sequencer对象
        SingleProducerSequencer sequencer = new SingleProducerSequencer(bufferSize, waitStrategy);

        return new RingBuffer<E>(factory, sequencer);
    }

    /**
	 * 使用默认等待策略{@link BlockingWaitStrategy}来创建一个新的单一生产者RingBuffer。
	 * 
	 * @param <E> RingBuffer中的事件类
	 * @param factory    用于在RingBuffer内创建事件。
	 * @param bufferSize 在RingBuffer中创建的元素数量
	 * @return 构造一个RingBuffer
	 * @throws IllegalArgumentException 如果bufferSize小于1或不是2的幂
	 * @see MultiProducerSequencer
	 */
    public static <E> RingBuffer<E> createSingleProducer(EventFactory<E> factory, int bufferSize)
    {
        return createSingleProducer(factory, bufferSize, new BlockingWaitStrategy());
    }

	/**
	 * 使用指定的生产者类型(SINGLE或MULTI)创建一个新的RingBuffer
	 *
	 * @param <E> RingBuffer中的事件类
	 * @param producerType 生产者类型使用{@link ProducerType}
	 * @param factory      用于在RingBuffer内创建事件。
	 * @param bufferSize   在RingBuffer中创建的元素数量
	 * @param waitStrategy 等待策略：用于确定如何等待新元素变得可用。
	 * @return 构造一个RingBuffer
	 * @throws IllegalArgumentException 如果bufferSize小于1或不是2的幂
	 */
    public static <E> RingBuffer<E> create(
        ProducerType producerType,
        EventFactory<E> factory,
        int bufferSize,
        WaitStrategy waitStrategy)
    {
        switch (producerType)
        {
            case SINGLE:
                return createSingleProducer(factory, bufferSize, waitStrategy);
            case MULTI:
                return createMultiProducer(factory, bufferSize, waitStrategy);
            default:
                throw new IllegalStateException(producerType.toString());
        }
    }

    /**
     * <p>获取RingBuffer中给定序列的事件</p>
     * <p>此调用有2个用途, 首先在发布到环形缓冲区时使用此调用; 在调用 {@link RingBuffer#next()}之后使用此调用来获取;
     * 在调用{@link RingBuffer#publish(long)}之前预先分配事件以填充数据;</p>
     * 
     * <p>其次，在从RingBuffer消耗数据时使用此调用; 在调用{@link SequenceBarrier#waitFor(long)}之后,
     * 调用此方法的任何值都大于当前使用者序列, 并且小于或等于从{@link SequenceBarrier#waitFor(long)}方法返回的值</p>
     * 
	 * @param sequence 事件的序列
	 * @return 给定序列的事件
	 */
    @Override
    public E get(long sequence)
    {
        return elementAt(sequence);
    }

    /**
     * 
     * 递增并返回环形缓冲区的下一个序列, next系列的方法是通过内部的sequencer来实现的, 调用此方法应确保它们始终发布序列。 例如
     * 
     * <pre>
     * long sequence = ringBuffer.next();
     * try {
     *     Event e = ringBuffer.get(sequence);
     *     // Do some work with the event.
     * } finally {
     *     ringBuffer.publish(sequence);
     * }
     * </pre>
     *
     * @return 下一个发布的序列
     * @see RingBuffer#publish(long)
     * @see RingBuffer#get(long)
     */
    @Override
    public long next()
    {
        return sequencer.next();
    }

	/**
	 * 递增并返回RingBuffer的下n个序列。 调用此方法应确保它们始终发布序列。
	 *
	 * @param n 递增并返回的数量
	 * @return sequence 返回最高位数
	 * @see Sequencer#next(int)
	 */
    @Override
    public long next(int n)
    {
        return sequencer.next(n);
    }

    /**
     * <p>递增并返回RingBuffer的下一个序列; 调用此方法应确保它们始终随后发布序列 例如:</p>
     * 
     * <pre>
     * long sequence = ringBuffer.next();
     * try {
     *     Event e = ringBuffer.get(sequence);
     *     // Do some work with the event.
     * } finally {
     *     ringBuffer.publish(sequence);
     * }
     * </pre>
     * <p>如果RingBuffer中没有可用空间，则此方法不会阻塞，而是抛出{@link InsufficientCapacityException}</p>
     *
     * @return 要发布到的下一个序列
     * @throws InsufficientCapacityException 如果RingBuffer中的必要空间不可用
     * @see RingBuffer#publish(long)
     * @see RingBuffer#get(long)
     */
    @Override
    public long tryNext() throws InsufficientCapacityException
    {
        return sequencer.tryNext();
    }

    /**
	 * 与{@link RingBuffer#tryNext()}功能相同，但允许调用者尝试声明接下来的n个序列。
	 *
	 * @param n 需要序列的数量
	 * @return sequence 所需序列的最高位数
	 * @throws InsufficientCapacityException 如果RingBuffer中的必要空间不可用
	 */
    @Override
    public long tryNext(int n) throws InsufficientCapacityException
    {
        return sequencer.tryNext(n);
    }

    /**
	 * 将游标重置为特定值。这可以在任何时候使用，但值得注意的是，它可能导致数据竞争，并且只应在受控环境中使用。 例如： 在初始化期间
	 *
	 * @param sequence 要充值的序列
	 * @throws IllegalStateException 如果已经指定了gatingSequences
	 */
    @Deprecated
    public void resetTo(long sequence)
    {
        sequencer.claim(sequence);
        sequencer.publish(sequence);
    }

    /**
	 * 将游标设置为特定序列，并返回存储在那里的预分配条目。它可能导致数据竞争，并且只应在受控环境中使用。
	 *
	 * @param sequence 要声明的序列
	 * @return 预分配事件
	 */
    public E claimAndGetPreallocated(long sequence)
    {
        sequencer.claim(sequence);
        return get(sequence);
    }

    /**
	 * 判断某个序列是否已经被事件发布者发布事件。
	 * 
	 * @param sequence 标识entry的顺序。
	 * @return 如果可以读取值true, 反之则false
	 * @deprecated 不要用这个方法，它可能不会做你认为它做的事情
	 */
    @Deprecated
    public boolean isPublished(long sequence)
    {
        return sequencer.isAvailable(sequence);
    }

	/**
	 * 
	 * 追踪序列相关的方法：addGatingSequences，getMinimumGatingSequence，removeGatingSequence
	 * 将指定的gatingSequences添加到Disruptor的实例中。它们将安全地、原子地添加到gatingSequences列表中
	 *
	 * @param gatingSequences 要增加的序列
	 */
    public void addGatingSequences(Sequence... gatingSequences)
    {
        sequencer.addGatingSequences(gatingSequences);
    }

	/**
	 * 从添加到RingBuffer的所有gatingSequences中获取最小序列值。
	 *
	 * @return 如果没有添加序列，则返回最小控序列或光标序列。
	 */
    public long getMinimumGatingSequence()
    {
        return sequencer.getMinimumSequence();
    }

	/**
	 * 从此RingBuffer中删除指定的序列。
	 *
	 * @param sequence 需要删除的序列
	 * @return 如果序列找到返回true，否则false
	 */
    public boolean removeGatingSequence(Sequence sequence)
    {
        return sequencer.removeGatingSequence(sequence);
    }
    
	/**
	 * 创建一个新的SequenceBarrier, 由EventProcessor使用它来跟踪哪些消息可以从RingBuffer中读取, 并给出要跟踪的sequences列表
	 *
	 * @param sequencesToTrack 要追踪的附加序列
	 * @return 序列栏栅, 用于跟踪指定的序列
	 * @see SequenceBarrier
	 */
    public SequenceBarrier newBarrier(Sequence... sequencesToTrack)
    {
        return sequencer.newBarrier(sequencesToTrack);
    }

    /**
	 * 为在提供的sequences上选通的此 RingBuffer创建事件轮询器。
	 *
	 * @param gatingSequences 被封闭的
	 * @return 一个轮询器，它将在此RingBuffer和提供的序列上进行门控
	 */
    public EventPoller<E> newPoller(Sequence... gatingSequences)
    {
        return sequencer.newPoller(this, gatingSequences);
    }

    /**
     * 获取RingBuffer中的当前游标值, 收到的实际值取决于正在使用的{@link Sequencer}的类型
     *
     * @see MultiProducerSequencer
     * @see SingleProducerSequencer
     */
    @Override
    public long getCursor()
    {
        return sequencer.getCursor();
    }

    /**
     * buffer的大小
     */
    public int getBufferSize()
    {
        return bufferSize;
    }

    /**
     * 给定特定要求容量确定是否有可用空间量; 注意, 假设如果此方法不能返回<code>true</code>, 则对{@link RingBuffer#next()}的调用将不会阻止,
     * 设置此RingBuffer以处理多个生产者
     *
     * @param requiredCapacity 检查的能力
     * @return <code>true</code> 如果指定的<code>requiredCapacity</code>可用, 则为true; 否则为false
     */
    public boolean hasAvailableCapacity(int requiredCapacity)
    {
        return sequencer.hasAvailableCapacity(requiredCapacity);
    }


    /**
     * 在发布事件时需要传一个事件转换的接口, 内部用这个接口做一下数据到事件的转换
     * 
     * @see com.lmax.disruptor.EventSink#publishEvent(com.lmax.disruptor.EventTranslator)
     */
    @Override
    public void publishEvent(EventTranslator<E> translator)
    {
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvent(com.lmax.disruptor.EventTranslator)
     */
    @Override
    public boolean tryPublishEvent(EventTranslator<E> translator)
    {
        try
        {
            final long sequence = sequencer.tryNext();
            translateAndPublish(translator, sequence);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvent(com.lmax.disruptor.EventTranslatorOneArg, Object)
     * com.lmax.disruptor.EventSink#publishEvent(com.lmax.disruptor.EventTranslatorOneArg, A)
     */
    @Override
    public <A> void publishEvent(EventTranslatorOneArg<E, A> translator, A arg0)
    {
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence, arg0);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvent(com.lmax.disruptor.EventTranslatorOneArg, Object)
     * com.lmax.disruptor.EventSink#tryPublishEvent(com.lmax.disruptor.EventTranslatorOneArg, A)
     */
    @Override
    public <A> boolean tryPublishEvent(EventTranslatorOneArg<E, A> translator, A arg0)
    {
        try
        {
            final long sequence = sequencer.tryNext();
            translateAndPublish(translator, sequence, arg0);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvent(com.lmax.disruptor.EventTranslatorTwoArg, Object, Object)
     * com.lmax.disruptor.EventSink#publishEvent(com.lmax.disruptor.EventTranslatorTwoArg, A, B)
     */
    @Override
    public <A, B> void publishEvent(EventTranslatorTwoArg<E, A, B> translator, A arg0, B arg1)
    {
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence, arg0, arg1);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvent(com.lmax.disruptor.EventTranslatorTwoArg, Object, Object)
     * com.lmax.disruptor.EventSink#tryPublishEvent(com.lmax.disruptor.EventTranslatorTwoArg, A, B)
     */
    @Override
    public <A, B> boolean tryPublishEvent(EventTranslatorTwoArg<E, A, B> translator, A arg0, B arg1)
    {
        try
        {
            final long sequence = sequencer.tryNext();
            translateAndPublish(translator, sequence, arg0, arg1);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvent(com.lmax.disruptor.EventTranslatorThreeArg, Object, Object, Object)
     * com.lmax.disruptor.EventSink#publishEvent(com.lmax.disruptor.EventTranslatorThreeArg, A, B, C)
     */
    @Override
    public <A, B, C> void publishEvent(EventTranslatorThreeArg<E, A, B, C> translator, A arg0, B arg1, C arg2)
    {
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence, arg0, arg1, arg2);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvent(com.lmax.disruptor.EventTranslatorThreeArg, Object, Object, Object)
     * com.lmax.disruptor.EventSink#tryPublishEvent(com.lmax.disruptor.EventTranslatorThreeArg, A, B, C)
     */
    @Override
    public <A, B, C> boolean tryPublishEvent(EventTranslatorThreeArg<E, A, B, C> translator, A arg0, B arg1, C arg2)
    {
        try
        {
            final long sequence = sequencer.tryNext();
            translateAndPublish(translator, sequence, arg0, arg1, arg2);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvent(com.lmax.disruptor.EventTranslatorVararg, java.lang.Object...)
     */
    @Override
    public void publishEvent(EventTranslatorVararg<E> translator, Object... args)
    {
        final long sequence = sequencer.next();
        translateAndPublish(translator, sequence, args);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvent(com.lmax.disruptor.EventTranslatorVararg, java.lang.Object...)
     */
    @Override
    public boolean tryPublishEvent(EventTranslatorVararg<E> translator, Object... args)
    {
        try
        {
            final long sequence = sequencer.tryNext();
            translateAndPublish(translator, sequence, args);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }


    /**
     * @see com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslator[])
     */
    @Override
    public void publishEvents(EventTranslator<E>[] translators)
    {
        publishEvents(translators, 0, translators.length);
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslator[], int, int)
     */
    @Override
    public void publishEvents(EventTranslator<E>[] translators, int batchStartsAt, int batchSize)
    {
        checkBounds(translators, batchStartsAt, batchSize);
        final long finalSequence = sequencer.next(batchSize);
        translateAndPublishBatch(translators, batchStartsAt, batchSize, finalSequence);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslator[])
     */
    @Override
    public boolean tryPublishEvents(EventTranslator<E>[] translators)
    {
        return tryPublishEvents(translators, 0, translators.length);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslator[], int, int)
     */
    @Override
    public boolean tryPublishEvents(EventTranslator<E>[] translators, int batchStartsAt, int batchSize)
    {
        checkBounds(translators, batchStartsAt, batchSize);
        try
        {
            final long finalSequence = sequencer.tryNext(batchSize);
            translateAndPublishBatch(translators, batchStartsAt, batchSize, finalSequence);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorOneArg, Object[])
     * com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorOneArg, A[])
     */
    @Override
    public <A> void publishEvents(EventTranslatorOneArg<E, A> translator, A[] arg0)
    {
        publishEvents(translator, 0, arg0.length, arg0);
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorOneArg, int, int, Object[])
     * com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorOneArg, int, int, A[])
     */
    @Override
    public <A> void publishEvents(EventTranslatorOneArg<E, A> translator, int batchStartsAt, int batchSize, A[] arg0)
    {
        checkBounds(arg0, batchStartsAt, batchSize);
        final long finalSequence = sequencer.next(batchSize);
        translateAndPublishBatch(translator, arg0, batchStartsAt, batchSize, finalSequence);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorOneArg, Object[])
     * com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorOneArg, A[])
     */
    @Override
    public <A> boolean tryPublishEvents(EventTranslatorOneArg<E, A> translator, A[] arg0)
    {
        return tryPublishEvents(translator, 0, arg0.length, arg0);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorOneArg, int, int, Object[])
     * com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorOneArg, int, int, A[])
     */
    @Override
    public <A> boolean tryPublishEvents(
        EventTranslatorOneArg<E, A> translator, int batchStartsAt, int batchSize, A[] arg0)
    {
        checkBounds(arg0, batchStartsAt, batchSize);
        try
        {
            final long finalSequence = sequencer.tryNext(batchSize);
            translateAndPublishBatch(translator, arg0, batchStartsAt, batchSize, finalSequence);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorTwoArg, Object[], Object[])
     * com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorTwoArg, A[], B[])
     */
    @Override
    public <A, B> void publishEvents(EventTranslatorTwoArg<E, A, B> translator, A[] arg0, B[] arg1)
    {
        publishEvents(translator, 0, arg0.length, arg0, arg1);
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorTwoArg, int, int, Object[], Object[])
     * com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorTwoArg, int, int, A[], B[])
     */
    @Override
    public <A, B> void publishEvents(
        EventTranslatorTwoArg<E, A, B> translator, int batchStartsAt, int batchSize, A[] arg0, B[] arg1)
    {
        checkBounds(arg0, arg1, batchStartsAt, batchSize);
        final long finalSequence = sequencer.next(batchSize);
        translateAndPublishBatch(translator, arg0, arg1, batchStartsAt, batchSize, finalSequence);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorTwoArg, Object[], Object[])
     * com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorTwoArg, A[], B[])
     */
    @Override
    public <A, B> boolean tryPublishEvents(EventTranslatorTwoArg<E, A, B> translator, A[] arg0, B[] arg1)
    {
        return tryPublishEvents(translator, 0, arg0.length, arg0, arg1);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorTwoArg, int, int, Object[], Object[])
     * com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorTwoArg, int, int, A[], B[])
     */
    @Override
    public <A, B> boolean tryPublishEvents(
        EventTranslatorTwoArg<E, A, B> translator, int batchStartsAt, int batchSize, A[] arg0, B[] arg1)
    {
        checkBounds(arg0, arg1, batchStartsAt, batchSize);
        try
        {
            final long finalSequence = sequencer.tryNext(batchSize);
            translateAndPublishBatch(translator, arg0, arg1, batchStartsAt, batchSize, finalSequence);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorThreeArg, Object[], Object[], Object[])
     * com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorThreeArg, A[], B[], C[])
     */
    @Override
    public <A, B, C> void publishEvents(EventTranslatorThreeArg<E, A, B, C> translator, A[] arg0, B[] arg1, C[] arg2)
    {
        publishEvents(translator, 0, arg0.length, arg0, arg1, arg2);
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorThreeArg, int, int, Object[], Object[], Object[])
     * com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorThreeArg, int, int, A[], B[], C[])
     */
    @Override
    public <A, B, C> void publishEvents(
        EventTranslatorThreeArg<E, A, B, C> translator, int batchStartsAt, int batchSize, A[] arg0, B[] arg1, C[] arg2)
    {
        checkBounds(arg0, arg1, arg2, batchStartsAt, batchSize);
        final long finalSequence = sequencer.next(batchSize);
        translateAndPublishBatch(translator, arg0, arg1, arg2, batchStartsAt, batchSize, finalSequence);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorThreeArg, Object[], Object[], Object[])
     * com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorThreeArg, A[], B[], C[])
     */
    @Override
    public <A, B, C> boolean tryPublishEvents(
        EventTranslatorThreeArg<E, A, B, C> translator, A[] arg0, B[] arg1, C[] arg2)
    {
        return tryPublishEvents(translator, 0, arg0.length, arg0, arg1, arg2);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorThreeArg, int, int, Object[], Object[], Object[])
     * com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorThreeArg, int, int, A[], B[], C[])
     */
    @Override
    public <A, B, C> boolean tryPublishEvents(
        EventTranslatorThreeArg<E, A, B, C> translator, int batchStartsAt, int batchSize, A[] arg0, B[] arg1, C[] arg2)
    {
        checkBounds(arg0, arg1, arg2, batchStartsAt, batchSize);
        try
        {
            final long finalSequence = sequencer.tryNext(batchSize);
            translateAndPublishBatch(translator, arg0, arg1, arg2, batchStartsAt, batchSize, finalSequence);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorVararg, java.lang.Object[][])
     */
    @Override
    public void publishEvents(EventTranslatorVararg<E> translator, Object[]... args)
    {
        publishEvents(translator, 0, args.length, args);
    }

    /**
     * @see com.lmax.disruptor.EventSink#publishEvents(com.lmax.disruptor.EventTranslatorVararg, int, int, java.lang.Object[][])
     */
    @Override
    public void publishEvents(EventTranslatorVararg<E> translator, int batchStartsAt, int batchSize, Object[]... args)
    {
        checkBounds(batchStartsAt, batchSize, args);
        final long finalSequence = sequencer.next(batchSize);
        translateAndPublishBatch(translator, batchStartsAt, batchSize, finalSequence, args);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorVararg, java.lang.Object[][])
     */
    @Override
    public boolean tryPublishEvents(EventTranslatorVararg<E> translator, Object[]... args)
    {
        return tryPublishEvents(translator, 0, args.length, args);
    }

    /**
     * @see com.lmax.disruptor.EventSink#tryPublishEvents(com.lmax.disruptor.EventTranslatorVararg, int, int, java.lang.Object[][])
     */
    @Override
    public boolean tryPublishEvents(
        EventTranslatorVararg<E> translator, int batchStartsAt, int batchSize, Object[]... args)
    {
        checkBounds(args, batchStartsAt, batchSize);
        try
        {
            final long finalSequence = sequencer.tryNext(batchSize);
            translateAndPublishBatch(translator, batchStartsAt, batchSize, finalSequence, args);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }

    /**
     * 发布指定的序列; 此操作将此特定消息标记为可供读取
     *
     * @param sequence 要发布的序列
     */
    @Override
    public void publish(long sequence)
    {
        sequencer.publish(sequence);
    }

    /**
     * 发布指定的序列。 此操作将这些特定消息标记为可供读取
     *
     * @param lo 要发布的最低序列的号码
     * @param hi 要发布的最高序列的号码
     * @see Sequencer#next(int)
     */
    @Override
    public void publish(long lo, long hi)
    {
        sequencer.publish(lo, hi);
    }

    /**
     * 获取此RingBuffer的剩余容量
     *
     * @return 剩余的插槽数量
     */
    public long remainingCapacity()
    {
        return sequencer.remainingCapacity();
    }

    private void checkBounds(final EventTranslator<E>[] translators, final int batchStartsAt, final int batchSize)
    {
        checkBatchSizing(batchStartsAt, batchSize);
        batchOverRuns(translators, batchStartsAt, batchSize);
    }

    private void checkBatchSizing(int batchStartsAt, int batchSize)
    {
        if (batchStartsAt < 0 || batchSize < 0)
        {
            throw new IllegalArgumentException("Both batchStartsAt and batchSize must be positive but got: batchStartsAt " + batchStartsAt + " and batchSize " + batchSize);
        }
        else if (batchSize > bufferSize)
        {
            throw new IllegalArgumentException("The ring buffer cannot accommodate " + batchSize + " it only has space for " + bufferSize + " entities.");
        }
    }

    private <A> void checkBounds(final A[] arg0, final int batchStartsAt, final int batchSize)
    {
        checkBatchSizing(batchStartsAt, batchSize);
        batchOverRuns(arg0, batchStartsAt, batchSize);
    }

    private <A, B> void checkBounds(final A[] arg0, final B[] arg1, final int batchStartsAt, final int batchSize)
    {
        checkBatchSizing(batchStartsAt, batchSize);
        batchOverRuns(arg0, batchStartsAt, batchSize);
        batchOverRuns(arg1, batchStartsAt, batchSize);
    }

    private <A, B, C> void checkBounds(
        final A[] arg0, final B[] arg1, final C[] arg2, final int batchStartsAt, final int batchSize)
    {
        checkBatchSizing(batchStartsAt, batchSize);
        batchOverRuns(arg0, batchStartsAt, batchSize);
        batchOverRuns(arg1, batchStartsAt, batchSize);
        batchOverRuns(arg2, batchStartsAt, batchSize);
    }

    private void checkBounds(final int batchStartsAt, final int batchSize, final Object[][] args)
    {
        checkBatchSizing(batchStartsAt, batchSize);
        batchOverRuns(args, batchStartsAt, batchSize);
    }

    private <A> void batchOverRuns(final A[] arg0, final int batchStartsAt, final int batchSize)
    {
        if (batchStartsAt + batchSize > arg0.length)
        {
            throw new IllegalArgumentException(
                "A batchSize of: " + batchSize +
                    " with batchStatsAt of: " + batchStartsAt +
                    " will overrun the available number of arguments: " + (arg0.length - batchStartsAt));
        }
    }

    private void translateAndPublish(EventTranslator<E> translator, long sequence)
    {
        try
        {
            translator.translateTo(get(sequence), sequence);
        }
        finally
        {
            sequencer.publish(sequence);
        }
    }

    private <A> void translateAndPublish(EventTranslatorOneArg<E, A> translator, long sequence, A arg0)
    {
        try
        {
            translator.translateTo(get(sequence), sequence, arg0);
        }
        finally
        {
            sequencer.publish(sequence);
        }
    }

    private <A, B> void translateAndPublish(EventTranslatorTwoArg<E, A, B> translator, long sequence, A arg0, B arg1)
    {
        try
        {
            translator.translateTo(get(sequence), sequence, arg0, arg1);
        }
        finally
        {
            sequencer.publish(sequence);
        }
    }

    private <A, B, C> void translateAndPublish(
        EventTranslatorThreeArg<E, A, B, C> translator, long sequence,
        A arg0, B arg1, C arg2)
    {
        try
        {
            translator.translateTo(get(sequence), sequence, arg0, arg1, arg2);
        }
        finally
        {
            sequencer.publish(sequence);
        }
    }

    private void translateAndPublish(EventTranslatorVararg<E> translator, long sequence, Object... args)
    {
        try
        {
            translator.translateTo(get(sequence), sequence, args);
        }
        finally
        {
            sequencer.publish(sequence);
        }
    }

    private void translateAndPublishBatch(
        final EventTranslator<E>[] translators, int batchStartsAt,
        final int batchSize, final long finalSequence)
    {
        final long initialSequence = finalSequence - (batchSize - 1);
        try
        {
            long sequence = initialSequence;
            final int batchEndsAt = batchStartsAt + batchSize;
            for (int i = batchStartsAt; i < batchEndsAt; i++)
            {
                final EventTranslator<E> translator = translators[i];
                translator.translateTo(get(sequence), sequence++);
            }
        }
        finally
        {
            sequencer.publish(initialSequence, finalSequence);
        }
    }

    private <A> void translateAndPublishBatch(
        final EventTranslatorOneArg<E, A> translator, final A[] arg0,
        int batchStartsAt, final int batchSize, final long finalSequence)
    {
        final long initialSequence = finalSequence - (batchSize - 1);
        try
        {
            long sequence = initialSequence;
            final int batchEndsAt = batchStartsAt + batchSize;
            for (int i = batchStartsAt; i < batchEndsAt; i++)
            {
                translator.translateTo(get(sequence), sequence++, arg0[i]);
            }
        }
        finally
        {
            sequencer.publish(initialSequence, finalSequence);
        }
    }

    private <A, B> void translateAndPublishBatch(
        final EventTranslatorTwoArg<E, A, B> translator, final A[] arg0,
        final B[] arg1, int batchStartsAt, int batchSize,
        final long finalSequence)
    {
        final long initialSequence = finalSequence - (batchSize - 1);
        try
        {
            long sequence = initialSequence;
            final int batchEndsAt = batchStartsAt + batchSize;
            for (int i = batchStartsAt; i < batchEndsAt; i++)
            {
                translator.translateTo(get(sequence), sequence++, arg0[i], arg1[i]);
            }
        }
        finally
        {
            sequencer.publish(initialSequence, finalSequence);
        }
    }

    private <A, B, C> void translateAndPublishBatch(
        final EventTranslatorThreeArg<E, A, B, C> translator,
        final A[] arg0, final B[] arg1, final C[] arg2, int batchStartsAt,
        final int batchSize, final long finalSequence)
    {
        final long initialSequence = finalSequence - (batchSize - 1);
        try
        {
            long sequence = initialSequence;
            final int batchEndsAt = batchStartsAt + batchSize;
            for (int i = batchStartsAt; i < batchEndsAt; i++)
            {
                translator.translateTo(get(sequence), sequence++, arg0[i], arg1[i], arg2[i]);
            }
        }
        finally
        {
            sequencer.publish(initialSequence, finalSequence);
        }
    }

    private void translateAndPublishBatch(
        final EventTranslatorVararg<E> translator, int batchStartsAt,
        final int batchSize, final long finalSequence, final Object[][] args)
    {
        final long initialSequence = finalSequence - (batchSize - 1);
        try
        {
            long sequence = initialSequence;
            final int batchEndsAt = batchStartsAt + batchSize;
            for (int i = batchStartsAt; i < batchEndsAt; i++)
            {
                translator.translateTo(get(sequence), sequence++, args[i]);
            }
        }
        finally
        {
            sequencer.publish(initialSequence, finalSequence);
        }
    }

    @Override
    public String toString()
    {
        return "RingBuffer{" +
            "bufferSize=" + bufferSize +
            ", sequencer=" + sequencer +
            "}";
    }
}
