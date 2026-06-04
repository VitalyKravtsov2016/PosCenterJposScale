/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.poscenter.jpos;

import java.util.List;
import java.util.ArrayList;

import jpos.JposConst;
import jpos.ScaleConst;
import jpos.JposException;
import jpos.events.DataEvent;
import jpos.events.JposEvent;
import jpos.events.DirectIOEvent;
import jpos.events.ErrorEvent;
import jpos.events.OutputCompleteEvent;
import jpos.events.StatusUpdateEvent;

import ru.poscenter.jpos.JposUtils;
import ru.poscenter.tools.LoggerAdapter;

/**
 *
 * @author User
 */
public class JposScaleUtils {

    public static String getWeightUnitText(int weightUnit) {
        switch (weightUnit) {
            case ScaleConst.SCAL_WU_GRAM:
                return "SCAL_WU_GRAM";
            case ScaleConst.SCAL_WU_KILOGRAM:
                return "SCAL_WU_KILOGRAM";
            case ScaleConst.SCAL_WU_OUNCE:
                return "SCAL_WU_OUNCE";
            case ScaleConst.SCAL_WU_POUND:
                return "SCAL_WU_POUND";
            default:
                return String.valueOf(weightUnit);
        }
    }

    public static String getStatusNotifyText(int statusNotify) {
        switch (statusNotify) {
            case ScaleConst.SCAL_SN_DISABLED:
                return "SCAL_SN_DISABLED";
            case ScaleConst.SCAL_SN_ENABLED:
                return "SCAL_SN_ENABLED";
            default:
                return String.valueOf(statusNotify);
        }
    }

    public static List<String> getFreezeValueNames(int value) {
        List<String> activeBits = new ArrayList<>();

        if ((value & ScaleConst.SCAL_SFR_MANUAL_TARE) != 0) {
            activeBits.add("SCAL_SFR_MANUAL_TARE");
        }
        if ((value & ScaleConst.SCAL_SFR_WEIGHTED_TARE) != 0) {
            activeBits.add("SCAL_SFR_WEIGHTED_TARE");
        }
        if ((value & ScaleConst.SCAL_SFR_PERCENT_TARE) != 0) {
            activeBits.add("SCAL_SFR_PERCENT_TARE");
        }
        if ((value & ScaleConst.SCAL_SFR_UNITPRICE) != 0) {
            activeBits.add("SCAL_SFR_UNITPRICE");
        }

        return activeBits;
    }

    public static String getFreezeValueText(int value) {
        List<String> activeBits = getFreezeValueNames(value);
        return String.join(",", activeBits);
    }

    public static String getPriceCalculationModeText(int mode) {
        switch (mode) {
            case ScaleConst.SCAL_PCM_PRICE_LABELING:
                return "SCAL_PCM_PRICE_LABELING";
            case ScaleConst.SCAL_PCM_SELF_SERVICE:
                return "SCAL_PCM_SELF_SERVICE";
            case ScaleConst.SCAL_PCM_OPERATOR:
                return "SCAL_PCM_OPERATOR";
            default:
                return String.valueOf(mode);
        }
    }

    public static String getSpecialTareModeText(int mode) {
        switch (mode) {
            case ScaleConst.SCAL_SST_DEFAULT:
                return "SCAL_SST_DEFAULT";
            case ScaleConst.SCAL_SST_MANUAL:
                return "SCAL_SST_MANUAL";
            case ScaleConst.SCAL_SST_PERCENT:
                return "SCAL_SST_PERCENT";
            case ScaleConst.SCAL_SST_WEIGHTED:
                return "SCAL_SST_WEIGHTED";
            default:
                return String.valueOf(mode);
        }
    }

    public static String getTarePriorityText(int mode) {
        switch (mode) {
            case ScaleConst.SCAL_STP_FIRST:
                return "SCAL_STP_FIRST";
            case ScaleConst.SCAL_STP_NONE:
                return "SCAL_STP_NONE";
            default:
                return String.valueOf(mode);
        }
    }

    public static boolean isScaleSUE(int event) {
        return (event >= ScaleConst.SCAL_SUE_STABLE_WEIGHT)
                && (event <= ScaleConst.SCAL_SUE_WEIGHT_UNDER_ZERO);

    }

    public static String getStatusUpdateEventText(int event) {
        switch (event) {
            case ScaleConst.SCAL_SUE_STABLE_WEIGHT:
                return "SCAL_SUE_STABLE_WEIGHT";

            case ScaleConst.SCAL_SUE_WEIGHT_UNSTABLE:
                return "SCAL_SUE_WEIGHT_UNSTABLE";

            case ScaleConst.SCAL_SUE_WEIGHT_ZERO:
                return "SCAL_SUE_WEIGHT_ZERO";

            case ScaleConst.SCAL_SUE_WEIGHT_OVERWEIGHT:
                return "SCAL_SUE_WEIGHT_OVERWEIGHT";

            case ScaleConst.SCAL_SUE_NOT_READY:
                return "SCAL_SUE_NOT_READY";

            case ScaleConst.SCAL_SUE_WEIGHT_UNDER_ZERO:
                return "SCAL_SUE_WEIGHT_UNDER_ZERO";

            default:
                return JposUtils.getStatusUpdateEventText(event);
        }
    }

    public static String getErrorCodeExtendedText(int errorCodeExtended) {
        switch (errorCodeExtended) {
            case ScaleConst.JPOS_ESCAL_OVERWEIGHT:
                return "JPOS_ESCAL_OVERWEIGHT";
            case ScaleConst.JPOS_ESCAL_UNDER_ZERO:
                return "JPOS_ESCAL_UNDER_ZERO";
            case ScaleConst.JPOS_ESCAL_SAME_WEIGHT:
                return "JPOS_ESCAL_SAME_WEIGHT";
            default:
                return String.valueOf(errorCodeExtended);
        }
    }

    /**
     * Текст ошибки JposException для отображения в UI: коды с именами констант,
     * при JPOS_E_EXTENDED — также ErrorCodeExtended.
     */
    public static String formatJposException(JposException ex) {
        StringBuilder sb = new StringBuilder();
        int code = ex.getErrorCode();
        sb.append("ErrorCode=").append(code).append(" (").append(JposUtils.getErrorCodeText(code)).append(")");
        int ext = ex.getErrorCodeExtended();
        if (code == JposConst.JPOS_E_EXTENDED || ext != 0) {
            sb.append("\nErrorCodeExtended=").append(ext)
                    .append(" (").append(getErrorCodeExtendedText(ext)).append(")");
        }
        String message = ex.getMessage();
        if (message != null && !message.isEmpty()) {
            sb.append("\n").append(message);
        }
        return sb.toString();
    }

    public static String getEventText(JposEvent event)
    {
        if (event instanceof StatusUpdateEvent) 
        {
            StatusUpdateEvent statusUpdateEvent = (StatusUpdateEvent)event;
            return getStatusUpdateEventText(statusUpdateEvent.getStatus());
        } else if (event instanceof DataEvent) 
        {
            DataEvent dataEvent = (DataEvent) event;
            return "Weight=" + dataEvent.getStatus();
        } else if (event instanceof ErrorEvent) 
        {
            ErrorEvent errorEvent = (ErrorEvent) event;
            return String.format(
                    "ErrorCode=%s,ErrorCodeExtended=%s,ErrorLocus=%s,ErrorResponse=%s", 
                    JposUtils.getErrorCodeText(errorEvent.getErrorCode()),
                    getErrorCodeExtendedText(errorEvent.getErrorCodeExtended()),
                    JposUtils.getErrorLocusText(errorEvent.getErrorLocus()),
                    JposUtils.getErrorResponseText(errorEvent.getErrorResponse()));
        } else if (event instanceof DirectIOEvent) 
        {
            DirectIOEvent directIOEvent = (DirectIOEvent) event;
            return String.format("EventNumber=%d,Data=%d,Object=%s", 
                    directIOEvent.getEventNumber(), 
                    directIOEvent.getData(),
                    String.valueOf(directIOEvent.getObject()));
        } else if (event instanceof OutputCompleteEvent) 
        {
            OutputCompleteEvent outputCompleteEvent = (OutputCompleteEvent) event;
            return String.format("OutputID=%d", outputCompleteEvent.getOutputID());
        }
        return String.valueOf(event);
    }
}
