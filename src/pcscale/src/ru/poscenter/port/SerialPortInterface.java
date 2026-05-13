package ru.poscenter.port;

/**
 * @author V.Kravtsov
 */
public interface SerialPortInterface {

    // Parity Values

    static final public int NO_PARITY = 0;
    static final public int ODD_PARITY = 1;
    static final public int EVEN_PARITY = 2;
    static final public int MARK_PARITY = 3;
    static final public int SPACE_PARITY = 4;

    // Number of Stop Bits
    static final public int ONE_STOP_BIT = 1;
    static final public int ONE_POINT_FIVE_STOP_BITS = 2;
    static final public int TWO_STOP_BITS = 3;

    void open() throws Exception;

    void open(int openTimeout) throws Exception;

    void close();

    boolean isOpened();

    int readByte() throws Exception;

    byte[] readBytes(int len) throws Exception;

    void write(byte[] b) throws Exception;

    void write(int b) throws Exception;

    void setTimeout(int timeout) throws Exception;

    public static final byte STX = 0x02;
    public static final byte ETX = 0x03;
    public static final byte EOT = 0x04;
    public static final byte ENQ = 0x05;
    public static final byte ACK = 0x06;
    public static final byte DLE = 0x10;
    public static final byte NAK = 0x15;

}
