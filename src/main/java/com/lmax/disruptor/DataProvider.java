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
 * DataProvider 提供了根据序列获取对应的对象
 * 有两个地方调用。这个Event对象需要被生产者获取往里面填充数据。第二个是在消费时，获取这个Event对象用于消费
 * @param <T>
 */
public interface DataProvider<T>
{
    T get(long sequence);
}
