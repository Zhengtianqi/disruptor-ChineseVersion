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

import java.util.Arrays;

import com.lmax.disruptor.util.Util;

/**
 * 在单个序列后面隐藏一组序列
 */
public final class FixedSequenceGroup extends Sequence
{
    private final Sequence[] sequences;

    /**
     * 构造函数
     * @param sequences 要在此序列组下跟踪的序列列表
     */
    public FixedSequenceGroup(Sequence[] sequences)
    {
        this.sequences = Arrays.copyOf(sequences, sequences.length);
    }

    /**
     * 获取组的最小序列值
     * @return 组的最小序列值
     */
    @Override
    public long get()
    {
        return Util.getMinimumSequence(sequences);
    }

    @Override
    public String toString()
    {
        return Arrays.toString(sequences);
    }

    /**
     * 不支持
     */
    @Override
    public void set(long value)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持
     */
    @Override
    public boolean compareAndSet(long expectedValue, long newValue)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持
     */
    @Override
    public long incrementAndGet()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持
     */
    @Override
    public long addAndGet(long increment)
    {
        throw new UnsupportedOperationException();
    }
}
