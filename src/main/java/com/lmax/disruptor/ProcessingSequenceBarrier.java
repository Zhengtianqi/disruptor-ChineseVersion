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
 * 
 * ProcessingSequenceBarrier获取可用序号
 * 
 * ProcessingSequenceBarrier的实例是由框架控制的，分别：
 *    @{link Disruptor}类的createEventProcessors方法内
 *    @{link RingBuffer}类的newBarrier方法
 *    @{link AbstractSequencer}类的newBarrier方法
 * 
 */
final class ProcessingSequenceBarrier implements SequenceBarrier
{
	//等待策略
    private final WaitStrategy waitStrategy;
    //当消费者之前没有依赖关系的时候，那么dependentSequence=cursorSequence
    //存在依赖关系的时候，dependentSequence 里存放的是一组依赖的Sequence，get方法得到的是最小的序列值
    //所谓的依赖关系是有两个消费者A、B，其中B需要在A之后进行消费，这A的序列就是B需要依赖的序列，因为B的消费速度不能超过A。
    private final Sequence dependentSequence;
    //判断是否执行shutdown
    private volatile boolean alerted = false;
    //cursorSequence 代表的是写指针。代表事件发布者发布到那个位置
    private final Sequence cursorSequence;
    //sequencer=SingleProducerSequencer or MultiProducerSequencer的引用
    private final Sequencer sequencer;
    /**
     * 
     * @param sequencer 生产者序号控制器
     * @param waitStrategy 等待策略
     * @param cursorSequence 生产者序号
     * @param dependentSequences 依赖的Sequence
     */
    ProcessingSequenceBarrier(
        final Sequencer sequencer,
        final WaitStrategy waitStrategy,
        final Sequence cursorSequence,
        final Sequence[] dependentSequences)
    {
        this.sequencer = sequencer;
        this.waitStrategy = waitStrategy;
        this.cursorSequence = cursorSequence;
        // 如果事件处理器不依赖于任何前置处理器，那么dependentSequence也指向生产者的序号。
        if (0 == dependentSequences.length)
        {
            dependentSequence = cursorSequence;
        }
        else
        {
        	// 如果有多个前置处理器，则对其进行封装，实现了组合模式
            dependentSequence = new FixedSequenceGroup(dependentSequences);
        }
    }
    /**
     * 该方法不保证总是返回未处理的序号；如果有更多的可处理序号时，返回的序号也可能是超过指定序号的。
     */
    @Override
    public long waitFor(final long sequence)
        throws AlertException, InterruptedException, TimeoutException
    {
    	// 首先检查有无通知
        checkAlert();
        // 通过WaitStrategy等待策略来获取可处理事件序号
        //eg. 1、YieldingWaitStrategy在自旋100次尝试后，会直接返回dependentSequence的最小seq，这时并不保证返回值>=given sequence
        //    2、BlockingWaitStrategy则会阻塞等待given sequence可用为止，可用并不是说availableSequence == given sequence，而应当是指 >=
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);
        if (availableSequence < sequence)
        {
        	// 这个方法不保证总是返回可处理的序号
            return availableSequence;
        }
        //如果是单线程生产者直接返回availableSequence
        // 再通过生产者序号控制器返回最大的可处理序号
        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }
    
    /**
     * 获取当前序列
     */
    @Override
    public long getCursor()
    {
        return dependentSequence.get();
    }

    /**
     * 判断是否中断
     */
    @Override
    public boolean isAlerted()
    {
        return alerted;
    }

    /**
     * 中断
     */
    @Override
    public void alert()
    {
    	//设置通知标记
        alerted = true;
        //如果有线程以阻塞的方式等待序列，将其唤醒。
        waitStrategy.signalAllWhenBlocking();
    }
    
    /**
     * 清除中断
     */
    @Override
    public void clearAlert()
    {
        alerted = false;
    }

    /**
     * 检查是否中断
     */
    @Override
    public void checkAlert() throws AlertException
    {
        if (alerted)
        {
            throw AlertException.INSTANCE;
        }
    }
}