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
 * 在发布事件时需要传一个事件转换的接口，内部用这个接口做一下数据到事件的转换。
 * 具体的生产者可以实现这个接口，将需要发布的数据放到这个事件里面，一般是设置到事件的某个域上。
 * <p>
 * 发布到RingBuffer时，请提供EventTranslator。
 * RingBuffer将按序列选择下一个可用事件，并在发布序列更新之前将其提供给EventTranslator（应更新事件）。
 * </p>
 * 
 * @param <T> event 在事件的交换或并行协调期间存储用于共享的数据的实现
 * 
 */
public interface EventTranslator<T>
{
	/**
	 * 将数据表示转换为在给定事件中设置的字段
	 *
	 * @param event    应该转换的数据
	 * @param sequence 分配给事件的
	 */
    void translateTo(T event, long sequence);
}
