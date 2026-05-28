package ru.poscenter.tools;

/**
 *
 * @author V.Kravtsov
 */
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.util.logging.SimpleFormatter;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.Format;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.poscenter.port.JSerialPort;

public class LoggerAdapter {

    private final Logger logger;
    private boolean enabled = false;

    private LoggerAdapter(String className) {
        logger = LogManager.getLogger(className);
    }

    public static synchronized LoggerAdapter getLogger(java.lang.Class c) {
        return getLogger(c.getName());
    }

    public static synchronized LoggerAdapter getLogger(String className) {
        return new LoggerAdapter(className);
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean getEnabled() {
        return enabled;
    }
    
    public synchronized void fatal(String text, Throwable e) {
        if (text == null) {
            return;
        }
        if (e == null) {
            return;
        }

        if (enabled) {
            logger.fatal(text, e);
        }
    }

    public synchronized void error(String text, Throwable e) {
        if (text == null) {
            return;
        }
        if (e == null) {
            return;
        }

        if (enabled) {
            logger.error(text, e);
        }
    }

    public synchronized void error(Throwable e) {
        if (e == null) {
            return;
        }

        if (enabled) {
            logger.error(e);
        }
    }

    public synchronized void error(String text) {
        if (text == null) {
            return;
        }

        if (enabled) {
            logger.error(text);
        }
    }

    public synchronized void debug(String text) {
        if (text == null) {
            return;
        }

        if (enabled) {
            logger.debug(text);
        }
    }

    public synchronized void warn(String text) {
        if (enabled) {
            logger.warn(text);
        }
    }
    
    public synchronized void info(String text) {
        if (enabled) {
            logger.info(text);
        }
    }
    
    public synchronized void debug(String text, Throwable e) {
        if (text == null) {
            return;
        }
        if (e == null) {
            return;
        }

        if (enabled) {
            logger.debug(text, e);
        }
    }
}
