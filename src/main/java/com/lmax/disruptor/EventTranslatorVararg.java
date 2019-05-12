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
 * 实现将另一个数据表示转换为{@link RingBuffer}声明的事件
 * 
 * @param <T> 事件实现存储数据以便在事件的交换或并行协调期间进行共享
 * @see EventTranslator
 */
public interface EventTranslatorVararg<T>
{
    /**
     * 将数据表示转换为在给定事件中设置的字段
     *
     * @param event    应该将数据翻译成什么事件
     * @param sequence 事件指定的序列
     * @param args     用户参数数组
     */
    void translateTo(T event, long sequence, Object... args);
}
