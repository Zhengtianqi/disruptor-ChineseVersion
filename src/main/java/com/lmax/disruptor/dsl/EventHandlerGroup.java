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
package com.lmax.disruptor.dsl;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WorkHandler;

import java.util.Arrays;

/**
 * 创建消费者组。一组{@link EventProcessor},用作与{@link Disruptor}的一部分。
 *
 * @param <T> 消费者使用的entry类型
 */
public class EventHandlerGroup<T>
{
    private final Disruptor<T> disruptor;
    private final ConsumerRepository<T> consumerRepository;
    private final Sequence[] sequences;

    EventHandlerGroup(
        final Disruptor<T> disruptor,
        final ConsumerRepository<T> consumerRepository,
        final Sequence[] sequences)
    {
        this.disruptor = disruptor;
        this.consumerRepository = consumerRepository;
        this.sequences = Arrays.copyOf(sequences, sequences.length);
    }

	/**
	 * 创建一个消费者组，将该组中的使用者与<code>otherHandlerGroup</code>组合在一起。
	 * 
	 * @param otherHandlerGroup 要组合的消费者
	 * @return 一个新的EventHandlerGroup结合了现有的消费者
	 */
    public EventHandlerGroup<T> and(final EventHandlerGroup<T> otherHandlerGroup)
    {
        final Sequence[] combinedSequences = new Sequence[this.sequences.length + otherHandlerGroup.sequences.length];
        System.arraycopy(this.sequences, 0, combinedSequences, 0, this.sequences.length);
        System.arraycopy(
            otherHandlerGroup.sequences, 0,
            combinedSequences, this.sequences.length, otherHandlerGroup.sequences.length);
        return new EventHandlerGroup<>(disruptor, consumerRepository, combinedSequences);
    }

	/**
	 * 创建一个消费者组,将此组中的消费者结合起来
	 * 
	 * @param processors 整合processors
	 * @return 将已经存在的和新加入的processors整合成一个EventHandlerGroup
	 */
    public EventHandlerGroup<T> and(final EventProcessor... processors)
    {
        Sequence[] combinedSequences = new Sequence[sequences.length + processors.length];

        for (int i = 0; i < processors.length; i++)
        {
            consumerRepository.add(processors[i]);
            combinedSequences[i] = processors[i].getSequence();
        }
        System.arraycopy(sequences, 0, combinedSequences, processors.length, sequences.length);

        return new EventHandlerGroup<>(disruptor, consumerRepository, combinedSequences);
    }

    /**
	 * <p>
	 * 设置批处理以使用RingBuffer中的事件,在每个{@link EventProcessor}之后处理事件
	 * </p>
	 * <p>
	 * 例如 <code>A</code> 必须处理事件早于 <code>B</code>:
	 * </p>
	 * 
	 * <pre>
	 * <code>dw.handleEventsWith(A).then(B);</code>
	 * </pre>
	 * 
	 * @param handlers 将处理事件的批处理
	 * @return 一个{@link EventHandlerGroup},可用于在创建的消费者上设置消费者栏栅
	 */
    @SafeVarargs
    public final EventHandlerGroup<T> then(final EventHandler<? super T>... handlers)
    {
        return handleEventsWith(handlers);
    }

	/**
	 * <p>
	 * 设置自定义消费者以处理来自RingBuffer的事件。 当调用{@link Disruptor#start()}时,Disruptor将自动启动这些处理器
	 * <p>
	 * 例如 <code>A</code> 必须处理事件早于 <code>B</code>:
	 * </p>
	 * 
	 * <pre>
	 * <code>dw.handleEventsWith(A).then(B);</code>
	 * </pre>
	 * 
	 * @param eventProcessorFactories 用于创建处理事件的消费者
	 * @return 一个{@link EventHandlerGroup},可用于链接依赖项。
	 */
    @SafeVarargs
    public final EventHandlerGroup<T> then(final EventProcessorFactory<T>... eventProcessorFactories)
    {
        return handleEventsWith(eventProcessorFactories);
    }

	/**
	 * <p>
	 * 设置工作池来处理RingBuffer中的事件 工作池只处理事件,在此组中的每个{@link EventProcessor}处理完该事件之后。
	 * 每个事件都将由其中一个消费者实例处理
	 * </p>
	 * <p>
	 * 例如： <code>A</code> 处理的事件必须优先于处理工厂 <code>B, C</code>:
	 * </p>
	 * 
	 * <pre>
	 * <code>dw.handleEventsWith(A).thenHandleEventsWithWorkerPool(B, C);</code>
	 * </pre>
	 *
	 * @param handlers 消费者,每个消费者实例将在工作池中提供额外的线程。
	 * @return a {@link EventHandlerGroup} that can be used to set up a event
	 *         processor barrier over the created event processors.
	 */
    @SafeVarargs
    public final EventHandlerGroup<T> thenHandleEventsWithWorkerPool(final WorkHandler<? super T>... handlers)
    {
        return handleEventsWithWorkerPool(handlers);
    }

    /**
	 * <p>
	 * 设置批处理程序以处理来自RingBuffer的事件
	 * </p>
	 * <p>
	 * 例如：<code>A</code>必须处理事件优先于<code>B</code>:
	 * </p>
	 * 
	 * <pre>
	 * <code>dw.after(A).handleEventsWith(B);</code>
	 * </pre>
	 *
	 * @param handlers 将处理事件的批处理程序。
	 * @return 一个{@link EventHandlerGroup},可用于在创建的消费者上设置事件处理栏栅
	 */
    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWith(final EventHandler<? super T>... handlers)
    {
        return disruptor.createEventProcessors(sequences, handlers);
    }

    /**
	 * <p>
	 * 设置自定义消费者以处理来RingBuffer的事件 当调用{@link Disruptor#start()}时,Disruptor将自动启动这些消费者
	 * </p>
	 *
	 * <p>
	 * 例如：<code>A</code>必须处理事件优先于<code>B</code>:
	 * </p>
	 * 
	 * <pre>
	 * <code>dw.after(A).handleEventsWith(B);</code>
	 * </pre>
	 *
	 * @param eventProcessorFactories 消费者工厂,用于创建将处理事件的消费者
	 * @return 一个{@link EventHandlerGroup},可用于链接依赖项。
	 */
    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWith(final EventProcessorFactory<T>... eventProcessorFactories)
    {
        return disruptor.createEventProcessors(sequences, eventProcessorFactories);
    }

    /**
	 * <p>
	 * 设置工作池以处理来自RingBuffer的事件。 工作池将仅在该组中的每个{@link EventProcessor}处理完事件后处理事件。
	 * 每个事件都将由其中一个消费者实例处理
	 * </p>
	 *
	 * <p>
	 * 例如：<code>A</code> 必须在处理工作池之前处理<code>B, C</code>:
	 * </p>
	 * 
	 * <pre>
	 * <code>dw.after(A).handleEventsWithWorkerPool(B, C);</code>
	 * </pre>
	 *
	 * @param handlers 将要处理事件的消费者,每个消费者实例将在工作池中提供额外的线程。
	 * @return 一个{@link EventHandlerGroup},可用于在创建的消费者上设置事件处理栏栅
	 */
    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWithWorkerPool(final WorkHandler<? super T>... handlers)
    {
        return disruptor.createWorkerPool(sequences, handlers);
    }

    /**
	 * 为该组中的处理器创建依赖关系栏栅。
	 * 这里允许自定义的消费者依赖于--》由disruptor创建的{@link com.lmax.disruptor.BatchEventProcessor}
	 *
	 * @return {@link SequenceBarrier} 包括该组中的所有处理器
	 */
    public SequenceBarrier asSequenceBarrier()
    {
        return disruptor.getRingBuffer().newBarrier(sequences);
    }
}
