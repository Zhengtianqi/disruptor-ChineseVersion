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
 * <p>如果在没有消耗序列的情况下，无法将值插入RingBuffer，则抛出异常;
 * 在使用{@link RingBuffer#tryNext()}调用声明时特别使用。</p>
 * 
 * <p>为了提高效率，此异常不会有堆栈跟踪</p>
 */
@SuppressWarnings("serial")
public final class InsufficientCapacityException extends Exception
{
    public static final InsufficientCapacityException INSTANCE = new InsufficientCapacityException();

    private InsufficientCapacityException()
    {
        // Singleton
    }

    @Override
    public Throwable fillInStackTrace()
    {
        return this;
    }
}
