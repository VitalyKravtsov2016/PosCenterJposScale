package ru.poscenter.jpostest;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;

import jpos.JposConst;
import jpos.JposException;
import jpos.Scale;

/**
 * Закладка 1: Общие методы UPOS.
 *
 * Группа "Открытие/Закрытие":  open, close, claim, release
 * Группа "Состояние устройства": getState, getDeviceEnabled/setDeviceEnabled,
 *   getFreezeEvents/setFreezeEvents, getClaimed
 * Группа "Информация":  getCheckHealthText, getDeviceControlDescription,
 *   getDeviceControlVersion, getDeviceServiceDescription, getDeviceServiceVersion,
 *   getPhysicalDeviceName, getPhysicalDeviceDescription
 * Группа "Операции":  checkHealth, directIO
 */
public class Tab1UposPage extends JPanel {

    private final Scale scale;
    private final Tab4EventsPage eventsPage;

    // -- Open/Close controls --
    private JTextField tfLogicalName;
    private JTextField tfClaimTimeout;
    private JButton btnOpen, btnClose, btnClaim, btnRelease;

    // -- State controls --
    private JLabel lblState;
    private JCheckBox cbDeviceEnabled;
    private JCheckBox cbFreezeEvents;
    private JLabel lblClaimed;
    private JButton btnRefreshState;

    // -- Info controls --
    private JTextArea taInfo;
    private JButton btnGetInfo;

    // -- Operations controls --
    private JComboBox<String> cbHealthLevel;
    private JTextField tfDioCommand, tfDioData, tfDioObject;
    private JButton btnCheckHealth, btnDirectIO;

    // -- Result --
    private JTextArea taResult;

    public Tab1UposPage(Scale scale, Tab4EventsPage eventsPage) {
        this.scale = scale;
        this.eventsPage = eventsPage;
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));

        main.add(buildOpenClosePanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildStatePanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildInfoPanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildOperationsPanel());

        JScrollPane scroll = new JScrollPane(main);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        taResult = new JTextArea(4, 60);
        taResult.setEditable(false);
        taResult.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane resultScroll = new JScrollPane(taResult);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Результат"));

        add(scroll, BorderLayout.CENTER);
        add(resultScroll, BorderLayout.SOUTH);
    }

    private JPanel buildOpenClosePanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Открытие / Закрытие"));

        p.add(new JLabel("Логическое имя:"));
        tfLogicalName = new JTextField("Scale", 12);
        p.add(tfLogicalName);

        btnOpen = new JButton("open");
        btnOpen.setToolTipText("Scale.open(logicalDeviceName)");
        btnOpen.addActionListener(e -> doOpen());
        p.add(btnOpen);

        btnClose = new JButton("close");
        btnClose.setToolTipText("Scale.close()");
        btnClose.addActionListener(e -> doClose());
        p.add(btnClose);

        p.add(new JLabel("  Таймаут claim (мс):"));
        tfClaimTimeout = new JTextField("5000", 6);
        p.add(tfClaimTimeout);

        btnClaim = new JButton("claim");
        btnClaim.setToolTipText("Scale.claim(timeout)");
        btnClaim.addActionListener(e -> doClaim());
        p.add(btnClaim);

        btnRelease = new JButton("release");
        btnRelease.setToolTipText("Scale.release()");
        btnRelease.addActionListener(e -> doRelease());
        p.add(btnRelease);

        return p;
    }

    private JPanel buildStatePanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Состояние устройства"));

        p.add(new JLabel("State:"));
        lblState = new JLabel("CLOSED");
        lblState.setForeground(Color.BLUE);
        p.add(lblState);

        p.add(new JLabel("  Claimed:"));
        lblClaimed = new JLabel("false");
        p.add(lblClaimed);

        cbDeviceEnabled = new JCheckBox("DeviceEnabled");
        cbDeviceEnabled.setToolTipText("setDeviceEnabled / getDeviceEnabled");
        cbDeviceEnabled.addItemListener(e -> doSetDeviceEnabled(cbDeviceEnabled.isSelected()));
        p.add(cbDeviceEnabled);

        cbFreezeEvents = new JCheckBox("FreezeEvents");
        cbFreezeEvents.setToolTipText("setFreezeEvents / getFreezeEvents");
        cbFreezeEvents.addItemListener(e -> doSetFreezeEvents(cbFreezeEvents.isSelected()));
        p.add(cbFreezeEvents);

        btnRefreshState = new JButton("Обновить");
        btnRefreshState.addActionListener(e -> refreshState());
        p.add(btnRefreshState);

        return p;
    }

    private JPanel buildInfoPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(new TitledBorder("Информация о драйвере"));

        btnGetInfo = new JButton("Получить информацию");
        btnGetInfo.addActionListener(e -> doGetInfo());

        taInfo = new JTextArea(5, 60);
        taInfo.setEditable(false);
        taInfo.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scroll = new JScrollPane(taInfo);

        p.add(btnGetInfo, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildOperationsPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Операции"));

        // checkHealth
        p.add(new JLabel("checkHealth:"));
        cbHealthLevel = new JComboBox<>(new String[]{"INTERNAL (1)", "EXTERNAL (2)", "INTERACTIVE (3)"});
        cbHealthLevel.setToolTipText("checkHealth(level): 1=Internal, 2=External, 3=Interactive");
        p.add(cbHealthLevel);
        btnCheckHealth = new JButton("checkHealth");
        btnCheckHealth.addActionListener(e -> doCheckHealth());
        p.add(btnCheckHealth);

        p.add(Box.createHorizontalStrut(12));

        // directIO
        p.add(new JLabel("directIO cmd:"));
        tfDioCommand = new JTextField("1", 4);
        tfDioCommand.setToolTipText("команда");
        p.add(tfDioCommand);

        p.add(new JLabel("data:"));
        tfDioData = new JTextField("0", 4);
        tfDioData.setToolTipText("целочисленные данные");
        p.add(tfDioData);

        p.add(new JLabel("obj:"));
        tfDioObject = new JTextField("", 8);
        tfDioObject.setToolTipText("строковый объект");
        p.add(tfDioObject);

        btnDirectIO = new JButton("directIO");
        btnDirectIO.addActionListener(e -> doDirectIO());
        p.add(btnDirectIO);

        return p;
    }

    // ======================= Действия =======================

    private void doOpen() {
        try {
            String name = tfLogicalName.getText().trim();
            scale.open(name);
            // Регистрируем слушателей событий
            scale.addDataListener(eventsPage);
            scale.addErrorListener(eventsPage);
            scale.addStatusUpdateListener(eventsPage);
            scale.addDirectIOListener(eventsPage);
            showOk("open(\"" + name + "\") — OK");
            eventsPage.log("open(\"" + name + "\") — OK");
            refreshState();
        } catch (JposException ex) {
            showError("open", ex);
        }
    }

    private void doClose() {
        try {
            scale.removeDataListener(eventsPage);
            scale.removeErrorListener(eventsPage);
            scale.removeStatusUpdateListener(eventsPage);
            scale.removeDirectIOListener(eventsPage);
            scale.close();
            showOk("close() — OK");
            eventsPage.log("close() — OK");
            refreshState();
        } catch (JposException ex) {
            showError("close", ex);
        }
    }

    private void doClaim() {
        try {
            int timeout = parseIntField(tfClaimTimeout, 5000);
            scale.claim(timeout);
            showOk("claim(" + timeout + ") — OK");
            eventsPage.log("claim(" + timeout + ") — OK");
            refreshState();
        } catch (JposException ex) {
            showError("claim", ex);
        }
    }

    private void doRelease() {
        try {
            scale.release();
            showOk("release() — OK");
            eventsPage.log("release() — OK");
            refreshState();
        } catch (JposException ex) {
            showError("release", ex);
        }
    }

    private void doSetDeviceEnabled(boolean enabled) {
        try {
            scale.setDeviceEnabled(enabled);
            showOk("setDeviceEnabled(" + enabled + ") — OK");
            eventsPage.log("setDeviceEnabled(" + enabled + ") — OK");
            refreshState();
        } catch (JposException ex) {
            showError("setDeviceEnabled", ex);
            // Revert checkbox to actual state
            try { cbDeviceEnabled.setSelected(scale.getDeviceEnabled()); } catch (Exception ignored) {}
        }
    }

    private void doSetFreezeEvents(boolean freeze) {
        try {
            scale.setFreezeEvents(freeze);
            showOk("setFreezeEvents(" + freeze + ") — OK");
            eventsPage.log("setFreezeEvents(" + freeze + ") — OK");
        } catch (JposException ex) {
            showError("setFreezeEvents", ex);
            try { cbFreezeEvents.setSelected(scale.getFreezeEvents()); } catch (Exception ignored) {}
        }
    }

    private void doGetInfo() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("DeviceControlDescription : ").append(scale.getDeviceControlDescription()).append("\n");
            sb.append("DeviceControlVersion     : ").append(scale.getDeviceControlVersion()).append("\n");
            try { sb.append("DeviceServiceDescription : ").append(scale.getDeviceServiceDescription()).append("\n"); } catch (JposException e) { sb.append("DeviceServiceDescription : [").append(e.getMessage()).append("]\n"); }
            try { sb.append("DeviceServiceVersion     : ").append(scale.getDeviceServiceVersion()).append("\n"); } catch (JposException e) { sb.append("DeviceServiceVersion     : [").append(e.getMessage()).append("]\n"); }
            try { sb.append("PhysicalDeviceName       : ").append(scale.getPhysicalDeviceName()).append("\n"); } catch (JposException e) { sb.append("PhysicalDeviceName       : [").append(e.getMessage()).append("]\n"); }
            try { sb.append("PhysicalDeviceDescription: ").append(scale.getPhysicalDeviceDescription()).append("\n"); } catch (JposException e) { sb.append("PhysicalDeviceDescription: [").append(e.getMessage()).append("]\n"); }
            try { sb.append("CheckHealthText          : ").append(scale.getCheckHealthText()).append("\n"); } catch (JposException e) { sb.append("CheckHealthText          : [").append(e.getMessage()).append("]\n"); }
            taInfo.setText(sb.toString());
        } catch (Exception ex) {
            taInfo.setText("Ошибка: " + ex.getMessage());
        }
    }

    private void doCheckHealth() {
        try {
            int level = cbHealthLevel.getSelectedIndex() + 1;
            scale.checkHealth(level);
            String checkText = "";
            try { checkText = scale.getCheckHealthText(); } catch (Exception ignored) {}
            showOk("checkHealth(" + level + ") — OK\nCheckHealthText: " + checkText);
            eventsPage.log("checkHealth(" + level + ") — OK");
        } catch (JposException ex) {
            showError("checkHealth", ex);
        }
    }

    private void doDirectIO() {
        try {
            int cmd  = parseIntField(tfDioCommand, 1);
            int data = parseIntField(tfDioData, 0);
            int[] dataArr = new int[]{data};
            String obj = tfDioObject.getText();
            scale.directIO(cmd, dataArr, obj.isEmpty() ? null : obj);
            showOk("directIO(" + cmd + ", " + dataArr[0] + ", \"" + obj + "\") — OK");
            eventsPage.log("directIO(" + cmd + ") — OK");
        } catch (JposException ex) {
            showError("directIO", ex);
        }
    }

    // ======================= Вспомогательные методы =======================

    public void refreshState() {
        try {
            int state = scale.getState();
            lblState.setText(stateText(state));
        } catch (Exception e) {
            lblState.setText("?");
        }
        try {
            lblClaimed.setText(String.valueOf(scale.getClaimed()));
        } catch (Exception e) {
            lblClaimed.setText("?");
        }
        try {
            boolean enabled = scale.getDeviceEnabled();
            if (cbDeviceEnabled.isSelected() != enabled) {
                cbDeviceEnabled.setSelected(enabled);
            }
        } catch (Exception ignored) {}
        try {
            boolean freeze = scale.getFreezeEvents();
            if (cbFreezeEvents.isSelected() != freeze) {
                cbFreezeEvents.setSelected(freeze);
            }
        } catch (Exception ignored) {}
    }

    private static String stateText(int state) {
        switch (state) {
            case JposConst.JPOS_S_CLOSED: return "CLOSED";
            case JposConst.JPOS_S_IDLE:   return "IDLE";
            case JposConst.JPOS_S_BUSY:   return "BUSY";
            case JposConst.JPOS_S_ERROR:  return "ERROR";
            default: return String.valueOf(state);
        }
    }

    private void showOk(String text) {
        taResult.setForeground(new Color(0, 128, 0));
        taResult.setText(text);
    }

    private void showError(String method, JposException ex) {
        String msg = method + "() ОШИБКА: ErrorCode=" + ex.getErrorCode()
                + " (" + ex.getMessage() + ")";
        taResult.setForeground(Color.RED);
        taResult.setText(msg);
        eventsPage.log(msg);
    }

    private static int parseIntField(JTextField field, int defaultValue) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
