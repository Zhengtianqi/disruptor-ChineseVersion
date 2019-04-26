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
 * 等待给定的sequence变为可用
 * Strategy employed for making {@link EventProcessor}s wait on a cursor {@link Sequence}.
 * Disruptor框架中提供了如下几种等待策略：
 * BlockingWaitStrategy：默认的等待策略。利用锁和等待机制的WaitStrategy，CPU消耗少，但是延迟比较高
 * BusySpinWaitStrategy：自旋等待。这种策略会利用CPU资源来避免系统调用带来的延迟抖动，当线程可以绑定到指定CPU(核)的时候可以使用这个策略。
 * LiteBlockingWaitStrategy：实现方法也是阻塞等待
 * SleepingWaitStrategy：是另一种较为平衡CPU消耗与延迟的WaitStrategy，在不同次数的重试后，采用不同的策略选择继续尝试或者让出CPU或者sleep。这种策略延迟不均匀。
 * TimeoutBlockingWaitStrategy：实现方法是阻塞给定的时间，超过时间的话会抛出超时异常。
 * YieldingWaitStrategy：实现方法是先自旋(100次)，不行再临时让出调度(yield)。和SleepingWaitStrategy一样也是一种高性能与CPU资源之间取舍的折中方案，但这个策略不会带来显著的延迟抖动。
 * PhasedBackoffWaitStrategy：实现方法是先自旋(10000次)，不行再临时让出调度(yield)，不行再使用其他的策略进行等待。可以根据具体场景自行设置自旋时间、yield时间和备用等待策略。
 * 
 */
public interface WaitStrategy
{
    /**
     *  等待给定的sequence变为可用
     * @param sequence 等待(申请)的序列值
     * @param cursor RingBuffer中的主序列，也可以认为是事件发布者使用的序列
     * @param dependentSequence 事件处理者使用的序列
     * @param barrier 序列栅栏
     * @return 对事件处理者来说可用的序列值，可能会比申请的序列值大
     * @throws AlertException Disruptor的状态被改变
     * @throws InterruptedException 线程被中断
     * @throws TimeoutException 
     */
    long waitFor(long sequence, Sequence cursor, Sequence dependentSequence, SequenceBarrier barrier)
        throws AlertException, InterruptedException, TimeoutException;

    /**
     * 当发布事件成功后会调用这个方法来通知等待的事件处理者序列{@link EventProcessor}s可用了
     */
    void signalAllWhenBlocking();
}
