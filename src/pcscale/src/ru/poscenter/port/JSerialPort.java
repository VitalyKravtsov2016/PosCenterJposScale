package ru.poscenter.port;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Vector;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;

import ru.poscenter.IDevice;
import ru.poscenter.DeviceError;
import ru.poscenter.tools.Tools;
import ru.poscenter.tools.Logger2;
import ru.poscenter.port.SerialPortInterface;

public class JSerialPort implements SerialPortInterface {

    private final Logger logger = LogManager.getLogger(JSerialPort.class);

    private final int bufferSize = 2048;
    private SerialPort port;
    public String appName = JSerialPort.class.getName();
    public String portName = "COM1";
    public int baudRate = 115200;
    public int dataBits = 8;
    public int stopBits = SerialPort.ONE_STOP_BIT;
    public int parity = SerialPort.NO_PARITY;
    public int openTimeout = 1000;
    public long idleTimeoutMS = 0;
    public int idleTimeoutNS = 1;
    private int readTimeout = 1000;

    public JSerialPort() {
    }

    public void setSerialParams(String appName, String portName, int baudrate,
            int dataBits, int stopBits, int parity, int openTimeout) {
        this.appName = appName;
        this.portName = portName;
        this.baudRate = baudrate;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity = parity;
        this.openTimeout = openTimeout;
    }

    public void open() throws Exception {
        open(openTimeout);
    }

    public void open(int openTimeout) throws Exception {
        this.openTimeout = openTimeout;

        if (isOpened()) {
            return;
        }
        logger.debug("open(" + portName + ")");

        long expTime = System.currentTimeMillis() + openTimeout;
        for (;;) {
            if (openPort())  {
                break;
            }
            if (System.currentTimeMillis() > expTime) {
                String errorText = IDevice.TEXT_ERROR_NOTSUCHPORT + ", " + portName;
                throw new DeviceError(IDevice.ERROR_NOSUCHPORT, errorText);
            }
            Thread.sleep(100);
        }
    }

    public boolean openPort() throws Exception {
        try {
            port = SerialPort.getCommPort(portName);
            if (port == null) {
                logger.error("SerialPort.getCommPort returned null");
                return false;
            }
            if (!port.openPort(0, 1024, 1024)){
                port = null;
                return false;
            }
            port.setComPortParameters(baudRate, dataBits, stopBits, parity);
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
            return true;
        } catch (SerialPortInvalidPortException e) {
            return false;
        }
    }

    public void setTimeout(int timeout) throws Exception {
        readTimeout = timeout;
    }

    public void close() {
        logger.debug("close()");
        if (isOpened()) {
            port.closePort();
            port = null;
        }
        logger.debug("close: OK");
    }

    protected void setReceiveTimeout(int timeout) throws Exception {
        if (!isOpened()) {
            return;
        }
        this.readTimeout = timeout;
    }

    public int doReadByte() throws Exception {
        open();

        int result;
        InputStream is;

        is = port.getInputStream();
        if (is == null) {
            return -1;
        }

        long startTime = System.currentTimeMillis();
        for (;;) {
            long currentTime = System.currentTimeMillis();
            if (is.available() > 0) {
                result = is.read();
                if (result >= 0) {
                    return result;
                }
            }
            if ((currentTime - startTime) > readTimeout) {
                throw new DeviceError(IDevice.ERROR_NOLINK, IDevice.TEXT_ERROR_NOLINK);
            }
        }
    }

    public int readByte() throws Exception {
        byte[] data = new byte[1];
        data[0] = (byte) doReadByte();
        Logger2.logRx(logger, data);
        return data[0];
    }

    public void read(JSerialPort.Buffer out, int len, int timeout) throws Exception {
        if (timeout < 100) {
            timeout = 100;
        }
        setTimeout(timeout);
        out.data = readBytes(len);
    }

    public void read(JSerialPort.Buffer out, int timeout) throws Exception {
        read(out, -1, timeout);
    }

    public byte[] readBytes(int len) throws Exception {
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            int b = doReadByte();
            result[i] = (byte) b;
        }
        Logger2.logRx(logger, result);
        return result;
    }

    public void write(ByteBuffer in) throws Exception {
        write(in.array());
    }

    public void write(JSerialPort.Buffer in) throws Exception {
        write(in, false);
    }

    public void write(JSerialPort.Buffer in, boolean flush) throws Exception {
        Logger2.logTx(logger, in.data);

        open();
        OutputStream out = port.getOutputStream();
        out.write(in.data);
        if (flush) {
            out.flush();
        }
    }

    public void writeBytes(byte[] a, boolean flush) throws Exception {
        write(new JSerialPort.Buffer(a), flush);
    }

    public void writeByte(int b, boolean flush) throws Exception {
        write(new JSerialPort.Buffer(new byte[]{(byte) b}), flush);
    }

    public void write(int b) throws Exception {
        writeByte(b, false);
    }

    public void write(byte[] a) throws Exception {
        writeBytes(a, false);
    }

    public boolean isOpened() {
        return port != null;
    }

    public long getIdleTimeoutMS() {
        return idleTimeoutMS;
    }

    public void setIdleTimeoutMS(long timeout) {
        idleTimeoutMS = timeout;
    }

    public int getIdleTimeoutNS() {
        return idleTimeoutNS;
    }

    public void setIdleTimeoutNS(int timeout) {
        idleTimeoutNS = timeout;
    }

    public void setIdleTimeout(long ms, int ns) {
        idleTimeoutMS = ms;
        idleTimeoutNS = ns;
    }

    public class Buffer {

        public byte[] data;

        public Buffer() {
        }

        public Buffer(byte[] bytes) {
            this.data = bytes;
        }
    }

    public class ReceivedData {

        public byte[] rawData;
        public int rawDataLen;
        public String strData;

        public ReceivedData(byte[] rawData, int rawDataLen, String strData) {
            this.rawData = rawData;
            this.rawDataLen = rawDataLen;
            this.strData = strData;
        }
    }

    private void listPortNames() {
        logger.debug("listPortNames");
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            logger.debug("PORT: " + port.getSystemPortName());
        }
    }

    public static Vector<String> getPortList() {
        Vector<String> names = new Vector<String>();
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            names.add(port.getSystemPortName());
        }
        return names;
    }

    public void handleException(Exception e) throws Exception {
        if (e instanceof java.io.IOException) {
            throw new DeviceError(IDevice.ERROR_NOLINK, IDevice.TEXT_ERROR_NOLINK);
        }
        if (e instanceof SerialPortInvalidPortException) {
            throw new DeviceError(IDevice.ERROR_NOSUCHPORT, IDevice.TEXT_ERROR_NOTSUCHPORT);
        }
    }
}
