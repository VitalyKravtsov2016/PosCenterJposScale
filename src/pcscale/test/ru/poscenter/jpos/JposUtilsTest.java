package ru.poscenter.jpos;

import ru.poscenter.jpos.JposUtils;
import static org.junit.Assert.*;

import org.junit.Test;

import jpos.JposConst;
import jpos.JposException;
import jpos.ScaleConst;

public class JposUtilsTest {

    @Test
    public void testGetCapPowerReportingText() {
        assertEquals("JPOS_PR_NONE", JposUtils.getCapPowerReportingText(JposConst.JPOS_PR_NONE));
        assertEquals("JPOS_PR_STANDARD", JposUtils.getCapPowerReportingText(JposConst.JPOS_PR_STANDARD));
        assertEquals("JPOS_PR_ADVANCED", JposUtils.getCapPowerReportingText(JposConst.JPOS_PR_ADVANCED));
        assertEquals("123", JposUtils.getCapPowerReportingText(123));
    }

    @Test
    public void testGetStateText() {
        assertEquals("JPOS_S_CLOSED", JposUtils.getStateText(JposConst.JPOS_S_CLOSED));
        assertEquals("JPOS_S_IDLE", JposUtils.getStateText(JposConst.JPOS_S_IDLE));
        assertEquals("JPOS_S_BUSY", JposUtils.getStateText(JposConst.JPOS_S_BUSY));
        assertEquals("JPOS_S_ERROR", JposUtils.getStateText(JposConst.JPOS_S_ERROR));
        assertEquals("321", JposUtils.getStateText(321));
    }

    @Test
    public void testGetErrorCodeText() {
        assertEquals("JPOS_E_CLOSED", JposUtils.getErrorCodeText(JposConst.JPOS_E_CLOSED));
        assertEquals("JPOS_E_EXTENDED", JposUtils.getErrorCodeText(JposConst.JPOS_E_EXTENDED));
        assertEquals("?", JposUtils.getErrorCodeText(99999));
    }

    @Test
    public void testGetScaleErrorExtendedText() {
        assertEquals("JPOS_ESCAL_OVERWEIGHT",
                JposUtils.getScaleErrorExtendedText(ScaleConst.JPOS_ESCAL_OVERWEIGHT));
        assertEquals("JPOS_ESCAL_UNDER_ZERO",
                JposUtils.getScaleErrorExtendedText(ScaleConst.JPOS_ESCAL_UNDER_ZERO));
        assertEquals("?", JposUtils.getScaleErrorExtendedText(0));
    }

    @Test
    public void testFormatJposExceptionExtended() {
        JposException ex = new JposException(
                JposConst.JPOS_E_EXTENDED,
                ScaleConst.JPOS_ESCAL_OVERWEIGHT,
                "Weight exceeds maximum");
        String text = JposUtils.formatJposException(ex);
        assertTrue(text.contains("JPOS_E_EXTENDED"));
        assertTrue(text.contains("JPOS_ESCAL_OVERWEIGHT"));
        assertTrue(text.contains("Weight exceeds maximum"));
    }
}

