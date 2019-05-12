package com.lmax.disruptor.dsl;

import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;

/**
 * 工厂接口: 可以在链中包含自定义的消费者
 * 
 * <pre>
 * <code>
 * disruptor.handleEventsWith(handler).then((ringBuffer, barrierSequences) -&gt; new CustomEventProcessor(ringBuffer, barrierSequences));
 * </code>
 * </pre>
 */
public interface EventProcessorFactory<T>
{
	/**
	 * 创建一个新的事件消费者,他在<code>barrierSequences</code>上建立
	 *
	 * @param ringBuffer       接收事件的RingBuffer
	 * @param barrierSequences 要打开的序列
	 * @return 消费者,在处理事件之前打开<code>barrierSequences</code>
	 */
    EventProcessor createEventProcessor(RingBuffer<T> ringBuffer, Sequence[] barrierSequences);
}
