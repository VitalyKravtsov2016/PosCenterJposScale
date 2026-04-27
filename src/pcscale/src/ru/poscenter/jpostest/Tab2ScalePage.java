package ru.poscenter.jpostest;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

import jpos.JposConst;
import jpos.JposException;
import jpos.Scale;
import jpos.ScaleConst;

/**
 * Закладка 2: Методы весов UPOS (ScaleControl12 – ScaleControl113).
 *
 * Capabilities:
 *   getCapDisplay, getCapDisplayText, getCapPriceCalculating, getCapPowerReporting,
 *   getCapTareWeight, getCapZeroScale, getCapStatisticsReporting, getCapUpdateStatistics,
 *   getCapCompareFirmwareVersion, getCapStatusUpdate, getCapUpdateFirmware
 *
 * Properties:
 *   getMaximumWeight, getWeightUnit, getScaleLiveWeight, getSalesPrice,
 *   getTareWeight/setTareWeight, getUnitPrice/setUnitPrice,
 *   getAsyncMode/setAsyncMode, getAutoDisable/setAutoDisable,
 *   getDataCount, getDataEventEnabled/setDataEventEnabled,
 *   getMaxDisplayTextChars, getPowerNotify/setPowerNotify, getPowerState,
 *   getStatusNotify/setStatusNotify, getZeroValid/setZeroValid
 *
 * Methods:
 *   readWeight, zeroScale, displayText, clearInput,
 *   resetStatistics, retrieveStatistics, updateStatistics,
 *   compareFirmwareVersion, updateFirmware
 */
public class Tab2ScalePage extends JPanel {

    private final Scale scale;
    private final Tab4EventsPage eventsPage;

    // -- readWeight --
    private JTextField tfReadTimeout;
    private JLabel lblWeight;
    private JButton btnReadWeight;

    // -- zeroScale --
    private JButton btnZeroScale;

    // -- displayText --
    private JTextField tfDisplayText;
    private JButton btnDisplayText;

    // -- clearInput --
    private JButton btnClearInput;

    // -- TareWeight --
    private JTextField tfTareWeight;
    private JButton btnGetTare, btnSetTare;

    // -- UnitPrice --
    private JTextField tfUnitPrice;
    private JButton btnGetUnitPrice, btnSetUnitPrice;

    // -- Properties checkboxes --
    private JCheckBox cbAsyncMode, cbAutoDisable, cbDataEventEnabled;

    // -- StatusNotify --
    private JComboBox<String> cbStatusNotify;
    private JButton btnSetStatusNotify;

    // -- PowerNotify --
    private JComboBox<String> cbPowerNotify;
    private JButton btnSetPowerNotify;

    // -- ZeroValid (1.13) --
    private JCheckBox cbZeroValid;

    // -- Capabilities & Properties info --
    private JButton btnGetCapProps;
    private JTextArea taCapProps;

    // -- Statistics --
    private JButton btnResetStatistics, btnRetrieveStatistics;

    // -- compareFirmwareVersion --
    private JTextField tfFirmwareFile;
    private JButton btnCompareFirmware;

    // -- result --
    private JTextArea taResult;

    public Tab2ScalePage(Scale scale, Tab4EventsPage eventsPage) {
        this.scale = scale;
        this.eventsPage = eventsPage;
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));

        main.add(buildWeightPanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildTareUnitPanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildPropertiesPanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildStatisticsPanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildCapabilitiesPanel());

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

    private JPanel buildWeightPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Взвешивание"));

        p.add(new JLabel("Таймаут (мс):"));
        tfReadTimeout = new JTextField("3000", 6);
        p.add(tfReadTimeout);

        btnReadWeight = new JButton("readWeight");
        btnReadWeight.setToolTipText("readWeight(weightData[], timeout)");
        btnReadWeight.addActionListener(e -> doReadWeight());
        p.add(btnReadWeight);

        p.add(new JLabel("  Вес:"));
        lblWeight = new JLabel("—");
        lblWeight.setFont(new Font("Monospaced", Font.BOLD, 14));
        lblWeight.setForeground(Color.DARK_GRAY);
        p.add(lblWeight);

        p.add(Box.createHorizontalStrut(12));

        btnZeroScale = new JButton("zeroScale");
        btnZeroScale.setToolTipText("zeroScale() — тарировка нуля");
        btnZeroScale.addActionListener(e -> doZeroScale());
        p.add(btnZeroScale);

        p.add(Box.createHorizontalStrut(12));

        btnClearInput = new JButton("clearInput");
        btnClearInput.setToolTipText("clearInput() — сбросить входной буфер");
        btnClearInput.addActionListener(e -> doClearInput());
        p.add(btnClearInput);

        p.add(Box.createHorizontalStrut(12));

        p.add(new JLabel("displayText:"));
        tfDisplayText = new JTextField("Hello", 10);
        p.add(tfDisplayText);
        btnDisplayText = new JButton("displayText");
        btnDisplayText.setToolTipText("displayText(data)");
        btnDisplayText.addActionListener(e -> doDisplayText());
        p.add(btnDisplayText);

        return p;
    }

    private JPanel buildTareUnitPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Тара и цена"));

        p.add(new JLabel("TareWeight:"));
        tfTareWeight = new JTextField("0", 6);
        tfTareWeight.setToolTipText("Вес тары в единицах WeightUnit");
        p.add(tfTareWeight);
        btnGetTare = new JButton("get");
        btnGetTare.addActionListener(e -> doGetTareWeight());
        btnSetTare = new JButton("set");
        btnSetTare.addActionListener(e -> doSetTareWeight());
        p.add(btnGetTare);
        p.add(btnSetTare);

        p.add(Box.createHorizontalStrut(12));

        p.add(new JLabel("UnitPrice (cents):"));
        tfUnitPrice = new JTextField("0", 8);
        tfUnitPrice.setToolTipText("Цена за единицу в центах/копейках");
        p.add(tfUnitPrice);
        btnGetUnitPrice = new JButton("get");
        btnGetUnitPrice.addActionListener(e -> doGetUnitPrice());
        btnSetUnitPrice = new JButton("set");
        btnSetUnitPrice.addActionListener(e -> doSetUnitPrice());
        p.add(btnGetUnitPrice);
        p.add(btnSetUnitPrice);

        return p;
    }

    private JPanel buildPropertiesPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Свойства"));

        cbAsyncMode = new JCheckBox("AsyncMode");
        cbAsyncMode.setToolTipText("setAsyncMode / getAsyncMode");
        cbAsyncMode.addItemListener(e -> doSetAsyncMode(cbAsyncMode.isSelected()));
        p.add(cbAsyncMode);

        cbAutoDisable = new JCheckBox("AutoDisable");
        cbAutoDisable.setToolTipText("setAutoDisable / getAutoDisable");
        cbAutoDisable.addItemListener(e -> doSetAutoDisable(cbAutoDisable.isSelected()));
        p.add(cbAutoDisable);

        cbDataEventEnabled = new JCheckBox("DataEventEnabled");
        cbDataEventEnabled.setToolTipText("setDataEventEnabled / getDataEventEnabled");
        cbDataEventEnabled.addItemListener(e -> doSetDataEventEnabled(cbDataEventEnabled.isSelected()));
        p.add(cbDataEventEnabled);

        cbZeroValid = new JCheckBox("ZeroValid (1.13)");
        cbZeroValid.setToolTipText("setZeroValid / getZeroValid — добавлено в UPOS 1.13");
        cbZeroValid.addItemListener(e -> doSetZeroValid(cbZeroValid.isSelected()));
        p.add(cbZeroValid);

        p.add(Box.createHorizontalStrut(8));

        p.add(new JLabel("StatusNotify:"));
        cbStatusNotify = new JComboBox<>(new String[]{"DISABLED (1)", "ENABLED (2)"});
        cbStatusNotify.setToolTipText("setStatusNotify: 1=DISABLED, 2=ENABLED");
        p.add(cbStatusNotify);
        btnSetStatusNotify = new JButton("set");
        btnSetStatusNotify.addActionListener(e -> doSetStatusNotify());
        p.add(btnSetStatusNotify);

        p.add(Box.createHorizontalStrut(8));

        p.add(new JLabel("PowerNotify:"));
        cbPowerNotify = new JComboBox<>(new String[]{"DISABLED (0)", "ENABLED (1)"});
        cbPowerNotify.setToolTipText("setPowerNotify: 0=DISABLED, 1=ENABLED");
        p.add(cbPowerNotify);
        btnSetPowerNotify = new JButton("set");
        btnSetPowerNotify.addActionListener(e -> doSetPowerNotify());
        p.add(btnSetPowerNotify);

        return p;
    }

    private JPanel buildStatisticsPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Статистика (UPOS 1.8) и прошивка (UPOS 1.9)"));

        btnResetStatistics = new JButton("resetStatistics");
        btnResetStatistics.setToolTipText("resetStatistics(\"\")");
        btnResetStatistics.addActionListener(e -> doResetStatistics());
        p.add(btnResetStatistics);

        btnRetrieveStatistics = new JButton("retrieveStatistics");
        btnRetrieveStatistics.setToolTipText("retrieveStatistics(result[])");
        btnRetrieveStatistics.addActionListener(e -> doRetrieveStatistics());
        p.add(btnRetrieveStatistics);

        p.add(Box.createHorizontalStrut(12));

        p.add(new JLabel("Firmware file:"));
        tfFirmwareFile = new JTextField("", 14);
        p.add(tfFirmwareFile);
        btnCompareFirmware = new JButton("compareFirmwareVersion");
        btnCompareFirmware.setToolTipText("compareFirmwareVersion(fileName, result[])");
        btnCompareFirmware.addActionListener(e -> doCompareFirmware());
        p.add(btnCompareFirmware);

        return p;
    }

    private JPanel buildCapabilitiesPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(new TitledBorder("Capabilities и Properties (чтение)"));

        btnGetCapProps = new JButton("Получить capabilities и свойства");
        btnGetCapProps.addActionListener(e -> doGetCapProps());

        taCapProps = new JTextArea(6, 60);
        taCapProps.setEditable(false);
        taCapProps.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scroll = new JScrollPane(taCapProps);

        p.add(btnGetCapProps, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // ======================= Действия =======================

    private void doReadWeight() {
        try {
            int timeout = parseIntField(tfReadTimeout, 3000);
            int[] w = new int[1];
            scale.readWeight(w, timeout);
            String unit = getWeightUnitText();
            lblWeight.setText(w[0] + " " + unit);
            showOk("readWeight(" + timeout + ") — Вес: " + w[0] + " " + unit);
            eventsPage.log("readWeight: " + w[0] + " " + unit);
        } catch (JposException ex) {
            showError("readWeight", ex);
        }
    }

    private void doZeroScale() {
        try {
            scale.zeroScale();
            showOk("zeroScale() — OK");
            eventsPage.log("zeroScale() — OK");
        } catch (JposException ex) {
            showError("zeroScale", ex);
        }
    }

    private void doClearInput() {
        try {
            scale.clearInput();
            showOk("clearInput() — OK");
            eventsPage.log("clearInput() — OK");
        } catch (JposException ex) {
            showError("clearInput", ex);
        }
    }

    private void doDisplayText() {
        try {
            String text = tfDisplayText.getText();
            scale.displayText(text);
            showOk("displayText(\"" + text + "\") — OK");
            eventsPage.log("displayText(\"" + text + "\") — OK");
        } catch (JposException ex) {
            showError("displayText", ex);
        }
    }

    private void doGetTareWeight() {
        try {
            int tare = scale.getTareWeight();
            tfTareWeight.setText(String.valueOf(tare));
            showOk("getTareWeight() = " + tare);
        } catch (JposException ex) {
            showError("getTareWeight", ex);
        }
    }

    private void doSetTareWeight() {
        try {
            int tare = parseIntField(tfTareWeight, 0);
            scale.setTareWeight(tare);
            showOk("setTareWeight(" + tare + ") — OK");
            eventsPage.log("setTareWeight(" + tare + ") — OK");
        } catch (JposException ex) {
            showError("setTareWeight", ex);
        }
    }

    private void doGetUnitPrice() {
        try {
            long price = scale.getUnitPrice();
            tfUnitPrice.setText(String.valueOf(price));
            showOk("getUnitPrice() = " + price);
        } catch (JposException ex) {
            showError("getUnitPrice", ex);
        }
    }

    private void doSetUnitPrice() {
        try {
            long price = parseLongField(tfUnitPrice, 0);
            scale.setUnitPrice(price);
            showOk("setUnitPrice(" + price + ") — OK");
            eventsPage.log("setUnitPrice(" + price + ") — OK");
        } catch (JposException ex) {
            showError("setUnitPrice", ex);
        }
    }

    private void doSetAsyncMode(boolean value) {
        try {
            scale.setAsyncMode(value);
            showOk("setAsyncMode(" + value + ") — OK");
            eventsPage.log("setAsyncMode(" + value + ") — OK");
        } catch (JposException ex) {
            showError("setAsyncMode", ex);
            try { cbAsyncMode.setSelected(scale.getAsyncMode()); } catch (Exception ignored) {}
        }
    }

    private void doSetAutoDisable(boolean value) {
        try {
            scale.setAutoDisable(value);
            showOk("setAutoDisable(" + value + ") — OK");
            eventsPage.log("setAutoDisable(" + value + ") — OK");
        } catch (JposException ex) {
            showError("setAutoDisable", ex);
            try { cbAutoDisable.setSelected(scale.getAutoDisable()); } catch (Exception ignored) {}
        }
    }

    private void doSetDataEventEnabled(boolean value) {
        try {
            scale.setDataEventEnabled(value);
            showOk("setDataEventEnabled(" + value + ") — OK");
            eventsPage.log("setDataEventEnabled(" + value + ") — OK");
        } catch (JposException ex) {
            showError("setDataEventEnabled", ex);
            try { cbDataEventEnabled.setSelected(scale.getDataEventEnabled()); } catch (Exception ignored) {}
        }
    }

    private void doSetZeroValid(boolean value) {
        try {
            scale.setZeroValid(value);
            showOk("setZeroValid(" + value + ") — OK (UPOS 1.13)");
            eventsPage.log("setZeroValid(" + value + ") — OK");
        } catch (JposException ex) {
            showError("setZeroValid", ex);
            try { cbZeroValid.setSelected(scale.getZeroValid()); } catch (Exception ignored) {}
        }
    }

    private void doSetStatusNotify() {
        try {
            int sn = cbStatusNotify.getSelectedIndex() + 1;
            scale.setStatusNotify(sn);
            showOk("setStatusNotify(" + sn + ") — OK");
            eventsPage.log("setStatusNotify(" + sn + ") — OK");
        } catch (JposException ex) {
            showError("setStatusNotify", ex);
        }
    }

    private void doSetPowerNotify() {
        try {
            int pn = cbPowerNotify.getSelectedIndex();
            scale.setPowerNotify(pn);
            showOk("setPowerNotify(" + pn + ") — OK");
            eventsPage.log("setPowerNotify(" + pn + ") — OK");
        } catch (JposException ex) {
            showError("setPowerNotify", ex);
        }
    }

    private void doResetStatistics() {
        try {
            scale.resetStatistics("");
            showOk("resetStatistics(\"\") — OK");
            eventsPage.log("resetStatistics() — OK");
        } catch (JposException ex) {
            showError("resetStatistics", ex);
        }
    }

    private void doRetrieveStatistics() {
        try {
            String[] buf = new String[1];
            scale.retrieveStatistics(buf);
            showOk("retrieveStatistics() — OK:\n" + buf[0]);
            eventsPage.log("retrieveStatistics() — OK");
        } catch (JposException ex) {
            showError("retrieveStatistics", ex);
        }
    }

    private void doCompareFirmware() {
        try {
            String file = tfFirmwareFile.getText().trim();
            int[] result = new int[1];
            scale.compareFirmwareVersion(file, result);
            String resultText = getFirmwareCompareText(result[0]);
            showOk("compareFirmwareVersion(\"" + file + "\") = " + result[0] + " (" + resultText + ")");
            eventsPage.log("compareFirmwareVersion: " + resultText);
        } catch (JposException ex) {
            showError("compareFirmwareVersion", ex);
        }
    }

    private void doGetCapProps() {
        StringBuilder sb = new StringBuilder();
        appendCap(sb, "CapDisplay",                  () -> scale.getCapDisplay());
        appendCap(sb, "CapDisplayText",              () -> scale.getCapDisplayText());
        appendCap(sb, "CapPriceCalculating",         () -> scale.getCapPriceCalculating());
        appendCap(sb, "CapPowerReporting",           () -> scale.getCapPowerReporting());
        appendCap(sb, "CapTareWeight",               () -> scale.getCapTareWeight());
        appendCap(sb, "CapZeroScale",                () -> scale.getCapZeroScale());
        appendCap(sb, "CapStatisticsReporting",      () -> scale.getCapStatisticsReporting());
        appendCap(sb, "CapUpdateStatistics",         () -> scale.getCapUpdateStatistics());
        appendCap(sb, "CapCompareFirmwareVersion",   () -> scale.getCapCompareFirmwareVersion());
        appendCap(sb, "CapStatusUpdate",             () -> scale.getCapStatusUpdate());
        appendCap(sb, "CapUpdateFirmware",           () -> scale.getCapUpdateFirmware());
        sb.append("---\n");
        appendProp(sb, "MaximumWeight",        () -> scale.getMaximumWeight());
        appendProp(sb, "WeightUnit",           () -> getWeightUnitText());
        appendProp(sb, "ScaleLiveWeight",      () -> scale.getScaleLiveWeight());
        appendProp(sb, "SalesPrice",           () -> scale.getSalesPrice());
        appendProp(sb, "TareWeight",           () -> scale.getTareWeight());
        appendProp(sb, "UnitPrice",            () -> scale.getUnitPrice());
        appendProp(sb, "DataCount",            () -> scale.getDataCount());
        appendProp(sb, "MaxDisplayTextChars",  () -> scale.getMaxDisplayTextChars());
        appendProp(sb, "PowerState",           () -> getPowerStateText());
        appendProp(sb, "PowerNotify",          () -> scale.getPowerNotify());
        appendProp(sb, "StatusNotify",         () -> scale.getStatusNotify());
        appendProp(sb, "AsyncMode",            () -> scale.getAsyncMode());
        appendProp(sb, "AutoDisable",          () -> scale.getAutoDisable());
        appendProp(sb, "DataEventEnabled",     () -> scale.getDataEventEnabled());
        appendProp(sb, "ZeroValid (1.13)",     () -> scale.getZeroValid());
        taCapProps.setText(sb.toString());
    }

    // ======================= Вспомогательные методы =======================

    @FunctionalInterface
    private interface Getter<T> { T get() throws JposException; }

    private <T> void appendCap(StringBuilder sb, String name, Getter<T> getter) {
        sb.append(String.format("%-40s = ", name));
        try { sb.append(getter.get()); } catch (JposException e) { sb.append("[" + e.getMessage() + "]"); }
        sb.append("\n");
    }

    private <T> void appendProp(StringBuilder sb, String name, Getter<T> getter) {
        sb.append(String.format("%-40s = ", name));
        try { sb.append(getter.get()); } catch (JposException e) { sb.append("[" + e.getMessage() + "]"); }
        sb.append("\n");
    }

    private String getWeightUnitText() {
        try {
            int unit = scale.getWeightUnit();
            switch (unit) {
                case ScaleConst.SCAL_WU_GRAM:     return "г (GRAM)";
                case ScaleConst.SCAL_WU_KILOGRAM: return "кг (KILOGRAM)";
                case ScaleConst.SCAL_WU_OUNCE:    return "oz (OUNCE)";
                case ScaleConst.SCAL_WU_POUND:    return "lb (POUND)";
                default: return String.valueOf(unit);
            }
        } catch (JposException e) {
            return "?";
        }
    }

    private String getPowerStateText() {
        try {
            int ps = scale.getPowerState();
            switch (ps) {
                case JposConst.JPOS_PS_ONLINE:      return "ONLINE";
                case JposConst.JPOS_PS_OFF:         return "OFF";
                case JposConst.JPOS_PS_OFFLINE:     return "OFFLINE";
                case JposConst.JPOS_PS_OFF_OFFLINE: return "OFF_OFFLINE";
                case JposConst.JPOS_PS_UNKNOWN:     return "UNKNOWN";
                default: return String.valueOf(ps);
            }
        } catch (JposException e) {
            return "?";
        }
    }

    private static String getFirmwareCompareText(int result) {
        switch (result) {
            case JposConst.JPOS_CFV_FIRMWARE_OLDER:     return "OLDER";
            case JposConst.JPOS_CFV_FIRMWARE_SAME:      return "SAME";
            case JposConst.JPOS_CFV_FIRMWARE_NEWER:     return "NEWER";
            case JposConst.JPOS_CFV_FIRMWARE_DIFFERENT: return "DIFFERENT";
            case JposConst.JPOS_CFV_FIRMWARE_UNKNOWN:   return "UNKNOWN";
            default: return String.valueOf(result);
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
        try { return Integer.parseInt(field.getText().trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private static long parseLongField(JTextField field, long defaultValue) {
        try { return Long.parseLong(field.getText().trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
