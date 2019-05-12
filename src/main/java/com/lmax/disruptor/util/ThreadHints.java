/*  Copyright 2016 Gil Tene
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

package com.lmax.disruptor.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;


import static java.lang.invoke.MethodType.methodType;

/**
 * 捕获提示：用于运行时提高代码性能的提示。 在{@link java.lang.Thread}中实现或预期编译,在某些Java
 * SE版本中,但是先前的版本没有。
 */
public final class ThreadHints
{
    private static final MethodHandle ON_SPIN_WAIT_METHOD_HANDLE;

    static
    {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodHandle methodHandle = null;
        try
        {
            methodHandle = lookup.findStatic(Thread.class, "onSpinWait", methodType(void.class));
        }
        catch (final Exception ignore)
        {
        }

        ON_SPIN_WAIT_METHOD_HANDLE = methodHandle;
    }

    private ThreadHints()
    {
    }

	/**
	 * 表示调用者暂时无法进展,直到其他活动发生一个或多个操作为止。 通过在spin-wait循环结构的每次迭代中调用此方法,
	 * 调用线程表明,运行时他在忙等待busy-waiting。 运行时可以采取措施来提高调用自旋等待循环结构的性能。
	 */
    public static void onSpinWait()
    {
		// 调用java.lang.Thread.onSpinWait()在JAVA SE版本上支持它
		// 这应该优化为无任何内容或内联java.lang.Thread.onSpinWait()
        if (null != ON_SPIN_WAIT_METHOD_HANDLE)
        {
            try
            {
                ON_SPIN_WAIT_METHOD_HANDLE.invokeExact();
            }
            catch (final Throwable ignore)
            {
            }
        }
    }
}