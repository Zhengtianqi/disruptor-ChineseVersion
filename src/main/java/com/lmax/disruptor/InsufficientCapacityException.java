package com.lmax.disruptor;

/**
 * <p>
 * 如果在没有包装消耗序列的情况下，无法将值插入RingBuffer，则抛出异常。
 * 在使用{@link RingBuffer#tryNext()}调用声明时特别使用。
 * </p>
 * 
 * <p>
 * 为了提高效率，此异常不会有堆栈跟踪</p>
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
    public synchronized Throwable fillInStackTrace()
    {
        return this;
    }
}
