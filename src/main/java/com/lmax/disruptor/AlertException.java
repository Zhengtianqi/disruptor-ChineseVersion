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
 * Used to alert {@link EventProcessor}s waiting at a {@link SequenceBarrier} of status changes.
 * <p>
 * It does not fill in a stack trace for performance reasons.
 */
@SuppressWarnings("serial")
public final class AlertException extends Exception
{
	/**
	 * 预先分配的异常以避免垃圾生成
	 */
    public static final AlertException INSTANCE = new AlertException();

    /**
	 * 私有构造函数，因此只存在一个实例。
	 */
    private AlertException()
    {
    }

    /**
	 * 由于性能原因，已覆盖，堆栈跟踪未填充导致异常。
	 * 
	 * @return this instance.
	 */
    @Override
    public Throwable fillInStackTrace()
    {
        return this;
    }
}
