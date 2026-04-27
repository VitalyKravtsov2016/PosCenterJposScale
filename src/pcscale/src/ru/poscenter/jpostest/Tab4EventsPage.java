package ru.poscenter.jpostest;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import jpos.events.*;

/**
 * Закладка 4: события JPOS (DataEvent, ErrorEvent, StatusUpdateEvent, DirectIOEvent).
 *
 * Реализует слушатели всех четырёх типов событий.
 * Другие закладки добавляют/удаляют слушателей при открытии/закрытии устройства.
 */
public class Tab4EventsPage extends JPanel
        implements DataListener, ErrorListener, StatusUpdateListener, DirectIOListener {

    private final JTextArea logArea;
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    public Tab4EventsPage() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(250, 250, 250));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Журнал событий"));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnClear = new JButton("Очистить");
        btnClear.addActionListener(e -> logArea.setText(""));
        topPanel.add(btnClear);
        JLabel hint = new JLabel("Здесь отображаются все события JPOS-драйвера весов.");
        hint.setForeground(Color.DARK_GRAY);
        topPanel.add(hint);

        add(topPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /** Добавить строку в журнал (потокобезопасно). */
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + sdf.format(new Date()) + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ---- DataListener ----
    @Override
    public void dataOccurred(DataEvent e) {
        log("DataEvent: Status=" + e.getStatus());
    }

    // ---- ErrorListener ----
    @Override
    public void errorOccurred(ErrorEvent e) {
        log("ErrorEvent: ErrorCode=" + e.getErrorCode()
                + " ErrorCodeExtended=" + e.getErrorCodeExtended()
                + " ErrorLocus=" + getLocusText(e.getErrorLocus())
                + " ErrorResponse=" + e.getErrorResponse());
    }

    // ---- StatusUpdateListener ----
    @Override
    public void statusUpdateOccurred(StatusUpdateEvent e) {
        log("StatusUpdateEvent: Status=" + e.getStatus() + " (" + getStatusText(e.getStatus()) + ")");
    }

    // ---- DirectIOListener ----
    @Override
    public void directIOOccurred(DirectIOEvent e) {
        log("DirectIOEvent: EventNumber=" + e.getEventNumber()
                + " Data=" + e.getData()
                + " Object=" + e.getObject());
    }

    // ---- helpers ----
    private static String getLocusText(int locus) {
        switch (locus) {
            case jpos.JposConst.JPOS_EL_OUTPUT:     return "OUTPUT";
            case jpos.JposConst.JPOS_EL_INPUT:      return "INPUT";
            case jpos.JposConst.JPOS_EL_INPUT_DATA: return "INPUT_DATA";
            default: return String.valueOf(locus);
        }
    }

    private static String getStatusText(int status) {
        switch (status) {
            case jpos.ScaleConst.SCAL_SUE_STABLE_WEIGHT:     return "STABLE_WEIGHT";
            case jpos.ScaleConst.SCAL_SUE_WEIGHT_UNSTABLE:   return "WEIGHT_UNSTABLE";
            case jpos.ScaleConst.SCAL_SUE_WEIGHT_ZERO:       return "WEIGHT_ZERO";
            case jpos.ScaleConst.SCAL_SUE_WEIGHT_OVERWEIGHT: return "WEIGHT_OVERWEIGHT";
            case jpos.ScaleConst.SCAL_SUE_NOT_READY:         return "NOT_READY";
            case jpos.ScaleConst.SCAL_SUE_WEIGHT_UNDER_ZERO: return "WEIGHT_UNDER_ZERO";
            case jpos.JposConst.JPOS_PS_ONLINE:              return "POWER_ONLINE";
            case jpos.JposConst.JPOS_PS_OFF:                 return "POWER_OFF";
            case jpos.JposConst.JPOS_PS_OFFLINE:             return "POWER_OFFLINE";
            case jpos.JposConst.JPOS_PS_OFF_OFFLINE:         return "POWER_OFF_OFFLINE";
            case jpos.JposConst.JPOS_PS_UNKNOWN:             return "POWER_UNKNOWN";
            default: return "?";
        }
    }
}
