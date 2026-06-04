/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.poscenter.jpos;

import jpos.JposConst;
import jpos.JposException;

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

    /**
     * Имя константы JposConst для ErrorCode.
     */
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
                return String.valueOf(errorCode);
        }
    }

    public static String getStatusUpdateEventText(int value) {
        switch (value) {
            case JposConst.JPOS_SUE_POWER_ONLINE:
                return "JPOS_SUE_POWER_ONLINE";

            case JposConst.JPOS_SUE_POWER_OFF:
                return "JPOS_SUE_POWER_OFF";

            case JposConst.JPOS_SUE_POWER_OFFLINE:
                return "JPOS_SUE_POWER_OFFLINE";

            case JposConst.JPOS_SUE_POWER_OFF_OFFLINE:
                return "JPOS_SUE_POWER_OFF_OFFLINE";

            case JposConst.JPOS_SUE_UF_PROGRESS:
                return "JPOS_SUE_UF_PROGRESS";

            case JposConst.JPOS_SUE_UF_COMPLETE:
                return "JPOS_SUE_UF_COMPLETE";

            case JposConst.JPOS_SUE_UF_FAILED_DEV_OK:
                return "JPOS_SUE_UF_FAILED_DEV_OK";

            case JposConst.JPOS_SUE_UF_FAILED_DEV_UNRECOVERABLE:
                return "JPOS_SUE_UF_FAILED_DEV_UNRECOVERABLE";

            case JposConst.JPOS_SUE_UF_FAILED_DEV_NEEDS_FIRMWARE:
                return "JPOS_SUE_UF_FAILED_DEV_NEEDS_FIRMWARE";

            case JposConst.JPOS_SUE_UF_FAILED_DEV_UNKNOWN:
                return "JPOS_SUE_UF_FAILED_DEV_UNKNOWN";

            case JposConst.JPOS_SUE_UF_COMPLETE_DEV_NOT_RESTORED:
                return "JPOS_SUE_UF_COMPLETE_DEV_NOT_RESTORED";
            default:
                return String.valueOf(value);
        }
    }

    public static String getErrorLocusText(int value) {
        switch (value) {
            case JposConst.JPOS_EL_OUTPUT:
                return "JPOS_EL_OUTPUT";

            case JposConst.JPOS_EL_INPUT:
                return "JPOS_EL_INPUT";

            case JposConst.JPOS_EL_INPUT_DATA:
                return "JPOS_EL_INPUT_DATA";
            default:
                return String.valueOf(value);
        }
    }

    public static String getErrorResponseText(int value) {
        switch (value) {
            case JposConst.JPOS_ER_RETRY:
                return "JPOS_ER_RETRY";

            case JposConst.JPOS_ER_CLEAR:
                return "JPOS_ER_CLEAR";

            case JposConst.JPOS_ER_CONTINUEINPUT:
                return "JPOS_ER_CONTINUEINPUT";
            default:
                return String.valueOf(value);
        }
    }

}
