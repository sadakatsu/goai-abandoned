package com.sadakatsu.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Time {
    private static final Logger LOGGER = LoggerFactory.getLogger(Time.class);
    
    public static void time( NiladicProcedure procedure, String format, Object...args ) throws Exception {
        if (LOGGER.isDebugEnabled()) {
            boolean returned = false;
            long start = System.nanoTime();
            try {
                procedure.run();
                returned = true;
            } catch (Exception e) {
                throw e;
            } finally {
                long end = System.nanoTime();
                String message = buildMessage(format, args, returned);
                log(start, end, message);
            }
        } else {
            procedure.run();
        }
    }
    
    private static String buildMessage( String format, Object[] args, boolean returned ) {
        String label = String.format(format, args);
        StringBuilder messageBuilder = new StringBuilder(label);
        if (returned) {
            messageBuilder.append(" returned");
        } else {
            messageBuilder.append(" threw an Exception");
        }
        return messageBuilder.toString();
    }
    
    private static void log( long start, long end, String message ) {
        LOGGER.debug("{} in {} ms", message, (end - start) / 1e6);
    }
    
    public static <T> T time( NiladicFunction<T> function, String format, Object...args ) throws Exception {
        T result;
        
        if (LOGGER.isDebugEnabled()) {
            boolean returned = false;
            long start = System.nanoTime();
            try {
                result = function.run();
                returned = true;
            } catch (Exception e) {
                throw e;
            } finally {
                long end = System.nanoTime();
                String message = buildMessage(format, args, returned);
                log(start, end, message);
            }
        } else {
            result = function.run();
        }
        
        return result;
    }
}
