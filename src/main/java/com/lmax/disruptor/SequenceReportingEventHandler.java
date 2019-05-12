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
 * 由{@link BatchEventProcessor}用于设置回调，允许{@link EventHandler}在完成消费事件时,
 * 通知EventHandler#onEvent(T，long，boolean)调用之后发生这种情况
 * <p>
 * 通常，这将在处理程序执行某种批处理操作(例如写入IO设备)时使用;
 * 在操作完成后，实现应该调用{@link Sequence#set}来更新序列，并允许依赖于此处理程序的其他进程进行
 *
 * @param <T> 事件实现存储数据以便在事件的交换或并行协调期间进行共享
 */

public interface SequenceReportingEventHandler<T>
    extends EventHandler<T>
{
	/**
	 * 调用 {@link BatchEventProcessor}设置回调
	 *
	 * @param sequenceCallback 回调，通知{@link BatchEventProcessor}序列已经进展
	 */
    void setSequenceCallback(Sequence sequenceCallback);
}
