/*
 * Copyright 2012 LMAX Ltd.
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
 * Sequencer接口的很多功能是提供给事件发布者用的。通过Sequencer可以得到一个SequenceBarrier，这货是提供给事件处理者用的。
 * Sequencer接口提供了2种实现：SingleProducerSequencer和MultiProducerSequencer。
 * 
 *  用于声明访问数据结构的序列（sequences），他的行踪依赖于Sequences
 *  Sequencer这是Disruptor真正的核心。实现了这个接口的两种生产者（单生产者和多生产者）均实现了所有的并发算法，为了在生产者和消费者之间进行准确快速的数据传递。
 */
public interface Sequencer extends Cursored, Sequenced
{
    /**
     * 游标初始值
     */
    long INITIAL_CURSOR_VALUE = -1L;

    /**
     *  初始化RingBuffer为指定的sequence
     *
     * @param sequence The sequence to initialise too.
     */
    void claim(long sequence);

    /**
     * 消费者调用，判断sequence是否可以消费
     * @param sequence of the buffer to check
     * @return true if the sequence is available for use, false if not
     */
    boolean isAvailable(long sequence);

    /**
     * 将sequence安全地和原子地添加到gating sequences中
     * 
     * @param gatingSequences The sequences to add.
     */
    void addGatingSequences(Sequence... gatingSequences);

    /**
     * 从gating sequences中移除指定的sequence
     *
     * @param sequence to be removed.
     * @return <tt>true</tt> if this sequence was found, <tt>false</tt> otherwise.
     */
    boolean removeGatingSequence(Sequence sequence);

    /**
     * 事件处理者用来追踪ringBuffer中可以用的sequence
     * 
     * @param sequencesToTrack All of the sequences that the newly constructed barrier will wait on.
     * @return A sequence barrier that will track the specified sequences.
     * @see SequenceBarrier
     */
    SequenceBarrier newBarrier(Sequence... sequencesToTrack);

    /**
     * 事件发布者获取gating sequence中最小的sequence的值
     * @return The minimum gating sequence or the cursor sequence if
     * no sequences have been added.
     */
    long getMinimumSequence();

    /**
     *
     * 消费者用来获取从nextSequence到availableSequence之间最大的sequence。如果是多线程生产者判断nextSequence是否可用，否则返回nextSequence-1。单线程直接返回availableSequence
     *
     * @param nextSequence      The sequence to start scanning from.
     * @param availableSequence The sequence to scan to.
     * @return The highest value that can be safely read, will be at least <code>nextSequence - 1</code>.
     */
    long getHighestPublishedSequence(long nextSequence, long availableSequence);
    
    /**
     * 通过给定的数据提供者和控制序列来创建一个EventPoller 
     * @param provider
     * @param gatingSequences
     * @return
     */
    <T> EventPoller<T> newPoller(DataProvider<T> provider, Sequence... gatingSequences);
}	