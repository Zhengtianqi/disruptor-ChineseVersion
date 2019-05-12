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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * event模式单线程处理。用于处理来自{@link RingBuffer}的消费者的批处理语义，并将可用事件委派给{@link EventHandler}。
 * 
 * 1. BatchEventProcessor内部会记录自己的序列、运行状态。 
 * 2. BatchEventProcessor需要外部提供数据提供者(其实就是队列-RingBuffer)、序列栅栏、异常处理器。 
 * 3. BatchEventProcessor其实是将事件委托给内部的EventHandler来处理的。
 * <p>
 * 如果{@link EventHandler}也实现了{@link LifecycleAware}，它将在线程启动后和线程关闭之前立即得到通知。
 * </p>
 * 
 * @param <T> event 在交换或并行协调事件期间存储数据以进行共享
 */
public final class BatchEventProcessor<T>
    implements EventProcessor
{
    private static final int IDLE = 0;
    private static final int HALTED = IDLE + 1;
    private static final int RUNNING = HALTED + 1;

    // 表示当前事件处理器的运行状态
    private final AtomicInteger running = new AtomicInteger(IDLE);
    // 异常处理器
    private ExceptionHandler<? super T> exceptionHandler = new FatalExceptionHandler();
    // 数据提供者(RingBuffer)
    private final DataProvider<T> dataProvider;
    // 序列栅栏
    private final SequenceBarrier sequenceBarrier;
    // 处理事件的回调接口
    private final EventHandler<? super T> eventHandler;
    // 事件处理器使用的序列
    private final Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    // 超时处理器
    private final TimeoutHandler timeoutHandler;
    // 每次循环取得一批可用事件后，在实际处理前调用
    private final BatchStartAware batchStartAware;

    /**
	 * 构造一个{@link EventProcessor}，它将在{@link EventHandler#onEvent(Object, long, boolean)}
	 * 方法返回时更新其sequence来自动跟踪进度。
	 *
	 * @param dataProvider    发布的事件
	 * @param sequenceBarrier 序列栏栅
	 * @param eventHandler    调度事件的委托。
	 */
    public BatchEventProcessor(
        final DataProvider<T> dataProvider,
        final SequenceBarrier sequenceBarrier,
        final EventHandler<? super T> eventHandler)
    {
        this.dataProvider = dataProvider;
        this.sequenceBarrier = sequenceBarrier;
        this.eventHandler = eventHandler;

        if (eventHandler instanceof SequenceReportingEventHandler)
        {
            ((SequenceReportingEventHandler<?>) eventHandler).setSequenceCallback(sequence);
        }

        batchStartAware =
            (eventHandler instanceof BatchStartAware) ? (BatchStartAware) eventHandler : null;
        timeoutHandler =
            (eventHandler instanceof TimeoutHandler) ? (TimeoutHandler) eventHandler : null;
    }

    @Override
    public Sequence getSequence()
    {
        return sequence;
    }

    @Override
    public void halt()
    {
        running.set(HALTED);
        sequenceBarrier.alert();
    }

    @Override
    public boolean isRunning()
    {
        return running.get() != IDLE;
    }

	/**
	 * 设置一个新的{@link ExceptionHandler}来处理从{@link BatchEventProcessor}传播的异常
	 * 
	 * @param exceptionHandler 替换现有的exceptionHandler
	 */
    public void setExceptionHandler(final ExceptionHandler<? super T> exceptionHandler)
    {
        if (null == exceptionHandler)
        {
            throw new NullPointerException();
        }

        this.exceptionHandler = exceptionHandler;
    }

	/**
	 * 在halt()之后让另一个线程重新运行此方法是可以的。
	 * 
	 * @throws IllegalStateException 如果此对象实例已在线程中运行
	 */
    @Override
    public void run()
    {
    	// 启动任务,状态设置与检测
        if (running.compareAndSet(IDLE, RUNNING))
        {
        	// 先清除序列栅栏的通知状态
            sequenceBarrier.clearAlert();
            // 如果eventHandler实现了LifecycleAware，这里会对其进行一个启动通知。
            notifyStart();
            try
            {
            	// 判断任务是否启动
                if (running.get() == RUNNING)
                {
                	// 处理事件
                    processEvents();
                }
            }
            finally
            {
            	// 判断一下消费者是否实现了LifecycleAware ,如果实现了这个接口那么此时会发送一个停止通知
                notifyShutdown();
                // 重新设置状态
                running.set(IDLE);
            }
        }
        else
        {
    		// 此时运行状态可能已更改为“已停止“; 然而，Java没有比较精确的改变，这是唯一正确的方法; 线程已经启动
            if (running.get() == RUNNING)
            {
                throw new IllegalStateException("Thread is already running");
            }
            else
            {
            	// 这里就是 notifyStart();notifyShutdown();
                earlyExit();
            }
        }
    }

    private void processEvents()
    {
    	// 定义一个event
        T event = null;
        // 获取要申请的序列
        long nextSequence = sequence.get() + 1L;
        // 循环处理事件。除非超时或者中断
        while (true)
        {
            try
            {
            	// 根据等待策略来等待可用的序列值
                final long availableSequence = sequenceBarrier.waitFor(nextSequence);
                if (batchStartAware != null && availableSequence >= nextSequence)
                {
                    batchStartAware.onBatchStart(availableSequence - nextSequence + 1);
                }

                // 根据可用的序列值获取事件。批量处理nextSequence到availableSequence之间的事件
                while (nextSequence <= availableSequence)
                {
                	// 获取事件
                    event = dataProvider.get(nextSequence);
                    // 触发事件
                    eventHandler.onEvent(event, nextSequence, nextSequence == availableSequence);
                    nextSequence++;
                }
                // 设置事件处理者处理到的序列值。事件发布者会根据availableSequence判断是否发布事件
                sequence.set(availableSequence);
            }
            catch (final TimeoutException e)
            {
            	// 超时异常
                notifyTimeout(sequence.get());
            }
            catch (final AlertException ex)
            {
            	// 中断异常
                if (running.get() != RUNNING)
                {
                    break;
                }
            }
            catch (final Throwable ex)
            {
            	// 这里可能用户消费者事件出错。如果自己实现了ExceptionHandler那么就不会影响继续消费
                exceptionHandler.handleEventException(ex, nextSequence, event);
                // 如果出现异常则设置为nextSequence
                sequence.set(nextSequence);
                nextSequence++;
            }
        }
    }

    private void earlyExit()
    {
        notifyStart();
        notifyShutdown();
    }

    private void notifyTimeout(final long availableSequence)
    {
        try
        {
            if (timeoutHandler != null)
            {
                timeoutHandler.onTimeout(availableSequence);
            }
        }
        catch (Throwable e)
        {
            exceptionHandler.handleEventException(e, availableSequence, null);
        }
    }

	/**
	 * processor启动时通知EventHandler
	 */
    private void notifyStart()
    {
        if (eventHandler instanceof LifecycleAware)
        {
            try
            {
                ((LifecycleAware) eventHandler).onStart();
            }
            catch (final Throwable ex)
            {
                exceptionHandler.handleOnStartException(ex);
            }
        }
    }

	/**
	 * processor关闭之前立即通知EventHandler
	 */
    private void notifyShutdown()
    {
        if (eventHandler instanceof LifecycleAware)
        {
            try
            {
                ((LifecycleAware) eventHandler).onShutdown();
            }
            catch (final Throwable ex)
            {
                exceptionHandler.handleOnShutdownException(ex);
            }
        }
    }
}