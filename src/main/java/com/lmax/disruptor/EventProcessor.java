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

/**
 * An EventProcessor needs to be an implementation of a runnable that will poll for events from the {@link RingBuffer}
 * using the appropriate wait strategy.  It is unlikely that you will need to implement this interface yourself.
 * Look at using the {@link EventHandler} interface along with the pre-supplied BatchEventProcessor in the first
 * instance.
 * <p>
 * An EventProcessor will generally be associated with a Thread for execution.
 */
/**
 * 介绍: EventProcessor需要是一个Runnable的实现,它将使用适当的等待策略从环形队列轮询事件;
 * 不太可能需要自己实现此接口,在第一个实例中使用{@link EventHandler}接口以及预先提供的BatchEventProcessor。
 * 作用: 事件消费者会等待RingBuffer中的事件变为可用(可处理),然后处理可用的事件;一个事件消费者通常会关联一个线程。
 */
public interface EventProcessor extends Runnable
{
	/**
	 * 获取对此{@link EventProcessor}使用的{@link Sequence}的引用。获取一个消费者使用的序列引用
	 * 
	 * @return 对{@link EventProcessor}的引用
	 */
    Sequence getSequence();

	/**
	 * 发出此消费者应该停止的信号，在下一次干净休息时消耗完后。应该在下一次休息时完成消费时停止
	 * 它将调用{@link SequenceBarrier#alert()}去通知线程检查状态
	 */
    void halt();

    // 判断是否运行
    boolean isRunning();
}
