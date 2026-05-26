/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.poscenter.jpos;

import jpos.JposConst;
import jpos.JposException;
import jpos.ScaleConst;

/**
 *
 * @author V.Kravtsov
 */
public class JposUtils {

    public static String getCapPowerReportingText(int value) {
        switch (value) {
            case JposConst.JPOS_PR_NONE:
                return "JPOS_PR_NONE";
            case JposConst.JPOS_PR_STANDARD:
                return "JPOS_PR_STANDARD";
            case JposConst.JPOS_PR_ADVANCED:
                return "JPOS_PR_ADVANCED";
            default:
                return String.valueOf(value);
        }
    }

    public static String getStateText(int value) {
        switch (value) {
            case JposConst.JPOS_S_CLOSED:
                return "JPOS_S_CLOSED";
            case JposConst.JPOS_S_IDLE:
                return "JPOS_S_IDLE";
            case JposConst.JPOS_S_BUSY:
                return "JPOS_S_BUSY";
            case JposConst.JPOS_S_ERROR:
                return "JPOS_S_ERROR";
            default:
                return String.valueOf(value);
        }
    }

    /** Имя константы JposConst для ErrorCode. */
    public static String getErrorCodeText(int errorCode) {
        switch (errorCode) {
            case JposConst.JPOS_SUCCESS:
                return "JPOS_SUCCESS";
            case JposConst.JPOS_E_CLOSED:
                return "JPOS_E_CLOSED";
            case JposConst.JPOS_E_CLAIMED:
                return "JPOS_E_CLAIMED";
            case JposConst.JPOS_E_NOTCLAIMED:
                return "JPOS_E_NOTCLAIMED";
            case JposConst.JPOS_E_NOSERVICE:
                return "JPOS_E_NOSERVICE";
            case JposConst.JPOS_E_DISABLED:
                return "JPOS_E_DISABLED";
            case JposConst.JPOS_E_ILLEGAL:
                return "JPOS_E_ILLEGAL";
            case JposConst.JPOS_E_NOHARDWARE:
                return "JPOS_E_NOHARDWARE";
            case JposConst.JPOS_E_OFFLINE:
                return "JPOS_E_OFFLINE";
            case JposConst.JPOS_E_NOEXIST:
                return "JPOS_E_NOEXIST";
            case JposConst.JPOS_E_EXISTS:
                return "JPOS_E_EXISTS";
            case JposConst.JPOS_E_FAILURE:
                return "JPOS_E_FAILURE";
            case JposConst.JPOS_E_TIMEOUT:
                return "JPOS_E_TIMEOUT";
            case JposConst.JPOS_E_BUSY:
                return "JPOS_E_BUSY";
            case JposConst.JPOS_E_EXTENDED:
                return "JPOS_E_EXTENDED";
            default:
                return "?";
        }
    }

    /** Имя константы ScaleConst для ErrorCodeExtended (категория Scale). */
    public static String getScaleErrorExtendedText(int errorCodeExtended) {
        switch (errorCodeExtended) {
            case ScaleConst.JPOS_ESCAL_OVERWEIGHT:
                return "JPOS_ESCAL_OVERWEIGHT";
            case ScaleConst.JPOS_ESCAL_UNDER_ZERO:
                return "JPOS_ESCAL_UNDER_ZERO";
            case ScaleConst.JPOS_ESCAL_SAME_WEIGHT:
                return "JPOS_ESCAL_SAME_WEIGHT";
            default:
                return "?";
        }
    }

    /**
     * Текст ошибки JposException для отображения в UI: коды с именами констант,
     * при JPOS_E_EXTENDED — также ErrorCodeExtended.
     */
    public static String formatJposException(JposException ex) {
        StringBuilder sb = new StringBuilder();
        int code = ex.getErrorCode();
        sb.append("ErrorCode=").append(code).append(" (").append(getErrorCodeText(code)).append(")");
        int ext = ex.getErrorCodeExtended();
        if (code == JposConst.JPOS_E_EXTENDED || ext != 0) {
            sb.append("\nErrorCodeExtended=").append(ext)
                    .append(" (").append(getScaleErrorExtendedText(ext)).append(")");
        }
        String message = ex.getMessage();
        if (message != null && !message.isEmpty()) {
            sb.append("\n").append(message);
        }
        return sb.toString();
    }

}
