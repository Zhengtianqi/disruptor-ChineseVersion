package com.lmax.disruptor;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 使用标准JDK日志记录将异常记录为{@link Level} .INFO的异常处理程序的便捷实现
 */
public final class IgnoreExceptionHandler implements ExceptionHandler<Object>
{
    private static final Logger LOGGER = Logger.getLogger(IgnoreExceptionHandler.class.getName());
    private final Logger logger;

    public IgnoreExceptionHandler()
    {
        this.logger = LOGGER;
    }

    public IgnoreExceptionHandler(final Logger logger)
    {
        this.logger = logger;
    }

    @Override
    public void handleEventException(final Throwable ex, final long sequence, final Object event)
    {
        logger.log(Level.INFO, "Exception processing: " + sequence + " " + event, ex);
    }

    @Override
    public void handleOnStartException(final Throwable ex)
    {
        logger.log(Level.INFO, "Exception during onStart()", ex);
    }

    @Override
    public void handleOnShutdownException(final Throwable ex)
    {
        logger.log(Level.INFO, "Exception during onShutdown()", ex);
    }
}
