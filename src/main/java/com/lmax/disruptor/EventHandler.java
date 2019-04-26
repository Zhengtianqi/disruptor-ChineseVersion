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
 * 回调接口，用于处理{@link RingBuffer}中可用的事件
 * 
 * @param <T> 事件实现存储数据以便在事件的交换或并行协调期间进行共享。
 * @see 处理从handler传播出去的异常：BatchEventProcessor#setExceptionHandler(ExceptionHandler) 
 */
public interface EventHandler<T>
{
    /**
     * Called when a publisher has published an event to the {@link RingBuffer}.  The {@link BatchEventProcessor} will
     * read messages from the {@link RingBuffer} in batches, where a batch is all of the events available to be
     * processed without having to wait for any new event to arrive.  This can be useful for event handlers that need
     * to do slower operations like I/O as they can group together the data from multiple events into a single
     * operation.  Implementations should ensure that the operation is always performed when endOfBatch is true as
     * the time between that message an the next one is inderminate.
     *
     * @param event      发布到{@link RingBuffer}
     * @param sequence   正在处理的事件
     * @param endOfBatch 用于指示是否来自{@link RingBuffer}的批处理中的最后一个时间的标志	
     * @throws Exception 如果事件处理程序希望在链上进一步处理异常。
     */
    void onEvent(T event, long sequence, boolean endOfBatch) throws Exception;
}
