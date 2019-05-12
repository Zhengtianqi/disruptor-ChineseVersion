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
 * 在{@link EventHandler}中实现此接口，以便在{@link BatchEventProcessor}的线程启动和关闭时得到通知。
 */
public interface LifecycleAware
{
	/**
	 * 在第一个事件可用之前，在线程启动时调用一次。
	 */
    void onStart();

	/**
	 * <p>在线程关闭之前调用一次</p>
	 * 
	 * <p>在调用此方法之前，序列事件处理已经停止。此消息后不会处理任何事件。</p>
	 */
    void onShutdown();
}
