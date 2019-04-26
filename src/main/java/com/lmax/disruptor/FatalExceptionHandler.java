package com.lmax.disruptor;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SEVERE(严重)的异常处理程序的便捷实现。便于使用标准JDK日志，实现异常处理，记录为{@link Level}
 * 将其重新包装在{@link RuntimeException}
 */
public final class FatalExceptionHandler implements ExceptionHandler<Object>
{
    private static final Logger LOGGER = Logger.getLogger(FatalExceptionHandler.class.getName());
    private final Logger logger;

    public FatalExceptionHandler()
    {
        this.logger = LOGGER;
    }

    public FatalExceptionHandler(final Logger logger)
    {
        this.logger = logger;
    }

    @Override
    public void handleEventException(final Throwable ex, final long sequence, final Object event)
    {
        logger.log(Level.SEVERE, "Exception processing: " + sequence + " " + event, ex);

        throw new RuntimeException(ex);
    }

    @Override
    public void handleOnStartException(final Throwable ex)
    {
        logger.log(Level.SEVERE, "Exception during onStart()", ex);
    }

    @Override
    public void handleOnShutdownException(final Throwable ex)
    {
        logger.log(Level.SEVERE, "Exception during onShutdown()", ex);
    }
}
