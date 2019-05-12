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
 * 回调接口，用于处理{@link RingBuffer}中可用的工作单元
 *
 * @param <T> 事件存储
 * @see WorkerPool
 */
public interface WorkHandler<T>
{
	/**
	 * 回调以指示需要处理的工作单元
	 *
	 * @param event 发布到{@link RingBuffer}
	 * @throws Exception 如果{@link WorkHandler}希望在链上进一步处理异常。
	 */
    void onEvent(T event) throws Exception;
}
