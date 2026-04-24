package ru.poscenter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import jpos.JposException;
import jpos.Scale;
import jpos.events.DataEvent;
import jpos.events.DataListener;
import jpos.events.DirectIOEvent;
import jpos.events.DirectIOListener;
import jpos.events.ErrorEvent;
import jpos.events.ErrorListener;
import jpos.events.OutputCompleteEvent;
import jpos.events.OutputCompleteListener;
import jpos.events.StatusUpdateEvent;
import jpos.events.StatusUpdateListener;

public class JPOSScaleTester extends JFrame implements DataListener, DirectIOListener,
        ErrorListener, StatusUpdateListener, OutputCompleteListener {

    private final Scale scale = new Scale();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    private final JTextField logicalNameField = new JTextField("Scale");
    private final JTextField timeoutField = new JTextField("1000");
    private final JTextArea commonPropsArea = new JTextArea();
    private final JTextArea scalePropsArea = new JTextArea();
    private final JTextArea eventsArea = new JTextArea();

    private final JTextField tareField = new JTextField("0");
    private final JTextField unitPriceField = new JTextField("0");
    private final JTextField displayTextField = new JTextField("TEST");
    private final JTextField freezeItemField = new JTextField("1");
    private final JCheckBox freezeEnabledBox = new JCheckBox("Freeze", true);
    private final JComboBox<String> priceModeBox = new JComboBox<>(new String[]{"SCAL_PCM_OPERATOR (3)", "SCAL_PCM_SELF_SERVICE (2)", "SCAL_PCM_PRICE_LABELING (1)"});
    private final JComboBox<String> specialTareModeBox = new JComboBox<>(new String[]{"SCAL_SST_DEFAULT (1)", "SCAL_SST_MANUAL (2)", "SCAL_SST_PERCENT (3)", "SCAL_SST_WEIGHTED (4)"});
    private final JTextField specialTareDataField = new JTextField("0");
    private final JComboBox<String> tarePriorityBox = new JComboBox<>(new String[]{"SCAL_STP_FIRST (1)", "SCAL_STP_NONE (2)"});
    private final JComboBox<String> weightUnitBox = new JComboBox<>(new String[]{"SCAL_WU_GRAM (1)", "SCAL_WU_KILOGRAM (2)", "SCAL_WU_OUNCE (3)", "SCAL_WU_POUND (4)"});
    private final JTextField weightNumeratorField = new JTextField("1");
    private final JTextField weightDenominatorField = new JTextField("1");
    private final JCheckBox autoRefreshProps = new JCheckBox("Auto refresh properties", true);
    private final JComboBox<String> statusNotifyBox = new JComboBox<>(new String[]{"1 - Disabled", "2 - Enabled"});
    private final JCheckBox zeroValidBox = new JCheckBox("Zero valid", true);
    private final JCheckBox asyncModeBox = new JCheckBox("Async mode", false);

    private int dataCount;
    private int statusCount;
    private int errorCount;
    private int directIoCount;
    private int outputCompleteCount;

    public JPOSScaleTester() {
        super("JPOS 1.14 Scale GUI Tester");
        initComponents();
        registerListeners();
    }

    private void initComponents() {
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 760));
        setLayout(new BorderLayout(8, 8));

        JPanel topPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        JPanel row1 = new JPanel(new GridLayout(1, 4, 6, 6));
        row1.add(new JLabel("Logical name"));
        row1.add(logicalNameField);
        row1.add(new JLabel("Timeout (ms)"));
        row1.add(timeoutField);

        JPanel row2 = new JPanel(new GridLayout(1, 5, 6, 6));
        JButton refreshButton = button("Refresh all", this::refreshAll);
        row2.add(refreshButton);
        row2.add(autoRefreshProps);
        row2.add(button("Clear events", this::clearEventLog));
        row2.add(button("Clear counters", this::clearCounters));
        row2.add(button("Exit", this::exitApp));

        topPanel.add(row1);
        topPanel.add(row2);
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        add(topPanel, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Common", buildCommonTab());
        tabs.add("Scale", buildScaleTab());
        tabs.add("Events", buildEventsTab());
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel buildCommonTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel actions = new JPanel(new GridLayout(0, 3, 6, 6));
        actions.setBorder(BorderFactory.createTitledBorder("POSCommon methods"));
        actions.add(button("open()", () -> safeCall(() -> scale.open(logicalNameField.getText().trim()), "open")));
        actions.add(button("claim()", () -> safeCall(() -> scale.claim(parseTimeout()), "claim")));
        actions.add(button("release()", () -> safeCall(scale::release, "release")));
        actions.add(button("close()", () -> safeCall(scale::close, "close")));
        actions.add(button("setDeviceEnabled(true)", () -> safeCall(() -> scale.setDeviceEnabled(true), "enable")));
        actions.add(button("setDeviceEnabled(false)", () -> safeCall(() -> scale.setDeviceEnabled(false), "disable")));
        actions.add(button("checkHealth()", () -> safeCall(() -> scale.checkHealth(1), "checkHealth")));
        actions.add(button("clearInput()", () -> safeCall(scale::clearInput, "clearInput")));
        actions.add(button("retrieveStatistics()", this::retrieveStatistics));
        actions.add(button("resetStatistics(*)", () -> safeCall(() -> scale.resetStatistics("*"), "resetStatistics")));
        actions.add(button("updateStatistics()", () -> safeCall(() -> scale.updateStatistics(""), "updateStatistics")));

        commonPropsArea.setEditable(false);
        commonPropsArea.setBorder(BorderFactory.createTitledBorder("Common properties"));

        panel.add(actions, BorderLayout.NORTH);
        panel.add(new JScrollPane(commonPropsArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildScaleTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel methods = new JPanel(new GridLayout(0, 3, 6, 6));
        methods.setBorder(BorderFactory.createTitledBorder("Scale methods"));
        methods.add(button("readWeight()", this::readWeightSync));
        methods.add(button("zeroScale()", () -> safeCall(scale::zeroScale, "zeroScale")));
        methods.add(button("setTareWeight()", this::setTareWeight));
        methods.add(button("setUnitPrice()", this::setUnitPrice));
        methods.add(button("displayText()", this::displayText));
        methods.add(button("doPriceCalculating()", this::doPriceCalculating));
        methods.add(button("readLiveWeightWithTare()", this::readLiveWeightWithTare));
        methods.add(button("freezeValue()", this::freezeValue));
        methods.add(button("setPriceCalculationMode()", this::setPriceCalculationMode));
        methods.add(button("setSpecialTare()", this::setSpecialTare));
        methods.add(button("setTarePrioity()", this::setTarePriority));
        methods.add(button("setUnitPriceWithWeightUnit()", this::setUnitPriceWithWeightUnit));

        JPanel propsControl = new JPanel(new GridLayout(0, 3, 6, 6));
        propsControl.setBorder(BorderFactory.createTitledBorder("Scale properties setup"));
        propsControl.add(new JLabel("Tare"));
        propsControl.add(tareField);
        propsControl.add(button("Apply tare", this::setTareWeight));
        propsControl.add(new JLabel("Unit price"));
        propsControl.add(unitPriceField);
        propsControl.add(button("Apply price", this::setUnitPrice));
        propsControl.add(new JLabel("Display text"));
        propsControl.add(displayTextField);
        propsControl.add(button("Send text", this::displayText));
        propsControl.add(new JLabel("Status notify"));
        propsControl.add(statusNotifyBox);
        propsControl.add(button("Apply statusNotify", this::setStatusNotify));
        propsControl.add(zeroValidBox);
        propsControl.add(asyncModeBox);
        propsControl.add(button("Apply flags", this::applyScaleFlags));

        JPanel methods114Control = new JPanel(new GridLayout(0, 3, 6, 6));
        methods114Control.setBorder(BorderFactory.createTitledBorder("JPOS 1.14 controls"));
        methods114Control.add(new JLabel("Freeze item bitmask"));
        methods114Control.add(freezeItemField);
        methods114Control.add(freezeEnabledBox);
        methods114Control.add(new JLabel("Price calculation mode"));
        methods114Control.add(priceModeBox);
        methods114Control.add(button("Apply mode", this::setPriceCalculationMode));
        methods114Control.add(new JLabel("Special tare mode"));
        methods114Control.add(specialTareModeBox);
        methods114Control.add(new JLabel("Special tare data"));
        methods114Control.add(new JLabel("Value"));
        methods114Control.add(specialTareDataField);
        methods114Control.add(button("Apply special tare", this::setSpecialTare));
        methods114Control.add(new JLabel("Tare priority"));
        methods114Control.add(tarePriorityBox);
        methods114Control.add(button("Apply tare priority", this::setTarePriority));
        methods114Control.add(new JLabel("Weight unit"));
        methods114Control.add(weightUnitBox);
        methods114Control.add(new JLabel("Weight numerator"));
        methods114Control.add(new JLabel("Numerator"));
        methods114Control.add(weightNumeratorField);
        methods114Control.add(new JLabel("Weight denominator"));
        methods114Control.add(new JLabel("Denominator"));
        methods114Control.add(weightDenominatorField);
        methods114Control.add(button("Apply unit+ratio", this::setUnitPriceWithWeightUnit));

        scalePropsArea.setEditable(false);
        scalePropsArea.setBorder(BorderFactory.createTitledBorder("Scale properties"));

        controls.add(methods);
        controls.add(propsControl);
        controls.add(methods114Control);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, controls, new JScrollPane(scalePropsArea));
        split.setResizeWeight(0.58);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildEventsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new GridLayout(1, 4, 6, 6));
        top.add(button("Enable DataEvents", () -> safeCall(() -> scale.setDataEventEnabled(true), "setDataEventEnabled(true)")));
        top.add(button("Disable DataEvents", () -> safeCall(() -> scale.setDataEventEnabled(false), "setDataEventEnabled(false)")));
        top.add(button("Freeze events", () -> safeCall(() -> scale.setFreezeEvents(true), "setFreezeEvents(true)")));
        top.add(button("Unfreeze events", () -> safeCall(() -> scale.setFreezeEvents(false), "setFreezeEvents(false)")));

        eventsArea.setEditable(false);
        eventsArea.setLineWrap(true);
        eventsArea.setWrapStyleWord(true);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(eventsArea), BorderLayout.CENTER);
        return panel;
    }

    private void registerListeners() {
        scale.addDataListener(this);
        scale.addDirectIOListener(this);
        scale.addErrorListener(this);
        scale.addStatusUpdateListener(this);
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    private int parseTimeout() {
        return Integer.parseInt(timeoutField.getText().trim());
    }

    private void safeCall(JposRunnable runnable, String title) {
        try {
            runnable.run();
            log("OK", title + " succeeded");
            refreshAll();
        } catch (Exception ex) {
            showError(title, ex);
        }
    }

    private void refreshAll() {
        refreshCommonProperties();
        refreshScaleProperties();
    }

    private void refreshCommonProperties() {
        StringBuilder sb = new StringBuilder();
        sb.append("claimed=").append(getBool("getClaimed")).append('\n');
        sb.append("deviceEnabled=").append(getBool("getDeviceEnabled")).append('\n');
        sb.append("state=").append(getInt("getState")).append('\n');
        sb.append("powerState=").append(getInt("getPowerState")).append('\n');
        sb.append("powerNotify=").append(getInt("getPowerNotify")).append('\n');
        sb.append("dataEventEnabled=").append(getBool("getDataEventEnabled")).append('\n');
        sb.append("freezeEvents=").append(getBool("getFreezeEvents")).append('\n');
        sb.append("autoDisable=").append(getBool("getAutoDisable")).append('\n');
        sb.append("checkHealthText=").append(getText(() -> scale.getCheckHealthText())).append('\n');
        sb.append("deviceServiceDescription=").append(getText(() -> scale.getDeviceServiceDescription())).append('\n');
        sb.append("physicalDeviceName=").append(getText(() -> scale.getPhysicalDeviceName())).append('\n');
        sb.append("physicalDeviceDescription=").append(getText(() -> scale.getPhysicalDeviceDescription())).append('\n');
        commonPropsArea.setText(sb.toString());
    }

    private void refreshScaleProperties() {
        StringBuilder sb = new StringBuilder();
        sb.append("maximumWeight=").append(getInt("getMaximumWeight")).append('\n');
        sb.append("minimumWeight=").append(getIntReflective("getMinimumWeight")).append('\n');
        sb.append("weightUnit=").append(getInt("getWeightUnit")).append('\n');
        sb.append("scaleLiveWeight=").append(getIntReflective("getScaleLiveWeight")).append('\n');
        sb.append("tareWeight=").append(getInt("getTareWeight")).append('\n');
        sb.append("unitPrice=").append(getLong("getUnitPrice")).append('\n');
        sb.append("salesPrice=").append(getLongReflective("getSalesPrice")).append('\n');
        sb.append("statusNotify=").append(getInt("getStatusNotify")).append('\n');
        sb.append("zeroValid=").append(getBool("getZeroValid")).append('\n');
        sb.append("asyncMode=").append(getBool("getAsyncMode")).append('\n');
        sb.append("dataCount=").append(getInt("getDataCount")).append('\n');
        sb.append("capTareWeight=").append(getBool("getCapTareWeight")).append('\n');
        sb.append("capZeroScale=").append(getBool("getCapZeroScale")).append('\n');
        sb.append("capDisplayText=").append(getBool("getCapDisplayText")).append('\n');
        sb.append("capFreezeValue=").append(getBoolReflective("getCapFreezeValue")).append('\n');
        sb.append("capReadLiveWeightWithTare=").append(getBoolReflective("getCapReadLiveWeightWithTare")).append('\n');
        sb.append("capSetPriceCalculationMode=").append(getBoolReflective("getCapSetPriceCalculationMode")).append('\n');
        sb.append("capSetUnitPriceWithWeightUnit=").append(getBoolReflective("getCapSetUnitPriceWithWeightUnit")).append('\n');
        sb.append("capSpecialTare=").append(getBoolReflective("getCapSpecialTare")).append('\n');
        sb.append("capTarePriority=").append(getBoolReflective("getCapTarePriority")).append('\n');
        scalePropsArea.setText(sb.toString());
    }

    private void readWeightSync() {
        safeCall(() -> {
            int[] value = new int[1];
            scale.readWeight(value, parseTimeout());
            log("SCALE", "readWeight -> " + value[0]);
        }, "readWeight");
    }

    private void setTareWeight() {
        safeCall(() -> scale.setTareWeight(Integer.parseInt(tareField.getText().trim())), "setTareWeight");
    }

    private void setUnitPrice() {
        safeCall(() -> scale.setUnitPrice(Long.parseLong(unitPriceField.getText().trim())), "setUnitPrice");
    }

    private void displayText() {
        safeCall(() -> scale.displayText(displayTextField.getText()), "displayText");
    }

    private void setStatusNotify() {
        int value = statusNotifyBox.getSelectedIndex() == 0 ? 1 : 2;
        safeCall(() -> scale.setStatusNotify(value), "setStatusNotify");
    }

    private void applyScaleFlags() {
        safeCall(() -> {
            scale.setZeroValid(zeroValidBox.isSelected());
            scale.setAsyncMode(asyncModeBox.isSelected());
        }, "set flags");
    }

    private void retrieveStatistics() {
        safeCall(() -> {
            String[] buf = new String[1];
            scale.retrieveStatistics(buf);
            log("COMMON", "retrieveStatistics -> " + (buf[0] == null ? "null" : buf[0]));
        }, "retrieveStatistics");
    }

    private void doPriceCalculating() {
        safeCall(() -> {
            int[] weight = new int[1];
            int[] tare = new int[1];
            long[] uPrice = new long[1];
            long[] uPriceX = new long[1];
            int[] wUnitX = new int[1];
            int[] wNumX = new int[1];
            int[] wDenX = new int[1];
            long[] price = new long[1];
            invokeReflective("doPriceCalculating",
                new Class<?>[]{int[].class, int[].class, long[].class, long[].class, int[].class, int[].class, int[].class, long[].class, int.class},
                new Object[]{weight, tare, uPrice, uPriceX, wUnitX, wNumX, wDenX, price, parseTimeout()});
            log("SCALE114", "doPriceCalculating -> weight=" + weight[0] + ", tare=" + tare[0] + ", unitPrice=" + uPrice[0] + ", price=" + price[0]);
        }, "doPriceCalculating");
    }

    private void readLiveWeightWithTare() {
        safeCall(() -> {
            int[] weight = new int[1];
            int[] tare = new int[1];
            invokeReflective("readLiveWeightWithTare",
                new Class<?>[]{int[].class, int[].class, int.class},
                new Object[]{weight, tare, parseTimeout()});
            log("SCALE114", "readLiveWeightWithTare -> weight=" + weight[0] + ", tare=" + tare[0]);
        }, "readLiveWeightWithTare");
    }

    private void freezeValue() {
        safeCall(() -> invokeReflective("freezeValue",
                new Class<?>[]{int.class, boolean.class},
                new Object[]{parseIntField(freezeItemField), freezeEnabledBox.isSelected()}), "freezeValue");
    }

    private void setPriceCalculationMode() {
        safeCall(() -> invokeReflective("setPriceCalculationMode",
                new Class<?>[]{int.class},
                new Object[]{priceModeByIndex()}), "setPriceCalculationMode");
    }

    private void setSpecialTare() {
        safeCall(() -> invokeReflective("setSpecialTare",
                new Class<?>[]{int.class, int.class},
                new Object[]{specialTareModeByIndex(), parseIntField(specialTareDataField)}), "setSpecialTare");
    }

    private void setTarePriority() {
        safeCall(() -> invokeReflective("setTarePrioity",
                new Class<?>[]{int.class},
                new Object[]{tarePriorityByIndex()}), "setTarePrioity");
    }

    private void setUnitPriceWithWeightUnit() {
        safeCall(() -> invokeReflective("setUnitPriceWithWeightUnit",
                new Class<?>[]{long.class, int.class, int.class, int.class},
                new Object[]{parseLongField(unitPriceField), weightUnitByIndex(), parseIntField(weightNumeratorField), parseIntField(weightDenominatorField)}), "setUnitPriceWithWeightUnit");
    }

    private void clearEventLog() {
        eventsArea.setText("");
    }

    private void clearCounters() {
        dataCount = 0;
        statusCount = 0;
        errorCount = 0;
        directIoCount = 0;
        outputCompleteCount = 0;
        log("EVENTS", "Counters reset");
    }

    private void exitApp() {
        safeCall(() -> {
            if (scale.getDeviceEnabled()) {
                scale.setDeviceEnabled(false);
            }
            if (scale.getClaimed()) {
                scale.release();
            }
            if (scale.getState() != 1) {
                scale.close();
            }
            dispose();
        }, "exit");
    }

    private void showError(String action, Exception ex) {
        String message = ex.getMessage();
        if (ex instanceof JposException) {
            JposException je = (JposException) ex;
            message = "JposException: code=" + je.getErrorCode()
                    + ", ext=" + je.getErrorCodeExtended()
                    + ", message=" + je.getMessage();
        }
        log("ERR", action + " failed: " + message);
        JOptionPane.showMessageDialog(this, message, action, JOptionPane.ERROR_MESSAGE);
    }

    private void log(String kind, String text) {
        String line = String.format("[%s] %s %s%n", dateFormat.format(new Date()), kind, text);
        SwingUtilities.invokeLater(() -> {
            eventsArea.append(line);
            eventsArea.setCaretPosition(eventsArea.getDocument().getLength());
            if (autoRefreshProps.isSelected()) {
                refreshAll();
            }
        });
    }

    @Override
    public void dataOccurred(DataEvent event) {
        dataCount++;
        log("DataEvent", "status=" + event.getStatus() + " total=" + dataCount);
    }

    @Override
    public void directIOOccurred(DirectIOEvent event) {
        directIoCount++;
        log("DirectIOEvent", "eventNumber=" + event.getEventNumber() + ", data=" + event.getData() + ", total=" + directIoCount);
    }

    @Override
    public void errorOccurred(ErrorEvent event) {
        errorCount++;
        log("ErrorEvent", "code=" + event.getErrorCode() + ", ext=" + event.getErrorCodeExtended() + ", total=" + errorCount);
    }

    @Override
    public void outputCompleteOccurred(OutputCompleteEvent event) {
        outputCompleteCount++;
        log("OutputCompleteEvent", "outputId=" + event.getOutputID() + ", total=" + outputCompleteCount);
    }

    @Override
    public void statusUpdateOccurred(StatusUpdateEvent event) {
        statusCount++;
        log("StatusUpdateEvent", "status=" + event.getStatus() + ", total=" + statusCount);
    }

    private boolean getBool(String method) {
        try {
            return (Boolean) Scale.class.getMethod(method).invoke(scale);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean getBoolReflective(String method) {
        try {
            return (Boolean) scale.getClass().getMethod(method).invoke(scale);
        } catch (Exception ex) {
            return false;
        }
    }

    private int getInt(String method) {
        try {
            return (Integer) Scale.class.getMethod(method).invoke(scale);
        } catch (Exception ex) {
            return -1;
        }
    }

    private int getIntReflective(String method) {
        try {
            return (Integer) scale.getClass().getMethod(method).invoke(scale);
        } catch (Exception ex) {
            return -1;
        }
    }

    private long getLong(String method) {
        try {
            return (Long) Scale.class.getMethod(method).invoke(scale);
        } catch (Exception ex) {
            return -1L;
        }
    }

    private long getLongReflective(String method) {
        try {
            return (Long) scale.getClass().getMethod(method).invoke(scale);
        } catch (Exception ex) {
            return -1L;
        }
    }

    private String getText(TextSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            return "<n/a>";
        }
    }

    private void invokeReflective(String methodName, Class<?>[] signature, Object[] args) throws Exception {
        Method m = scale.getClass().getMethod(methodName, signature);
        m.invoke(scale, args);
    }

    private int parseIntField(JTextField field) {
        return Integer.parseInt(field.getText().trim());
    }

    private long parseLongField(JTextField field) {
        return Long.parseLong(field.getText().trim());
    }

    private int priceModeByIndex() {
        switch (priceModeBox.getSelectedIndex()) {
            case 1:
                return 2;
            case 2:
                return 1;
            case 0:
            default:
                return 3;
        }
    }

    private int specialTareModeByIndex() {
        return specialTareModeBox.getSelectedIndex() + 1;
    }

    private int tarePriorityByIndex() {
        return tarePriorityBox.getSelectedIndex() == 0 ? 1 : 2;
    }

    private int weightUnitByIndex() {
        return weightUnitBox.getSelectedIndex() + 1;
    }

    @FunctionalInterface
    private interface JposRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface TextSupplier {
        String get() throws Exception;
    }

    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(JPOSScaleTester.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(JPOSScaleTester.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(JPOSScaleTester.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(JPOSScaleTester.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(() -> new JPOSScaleTester().setVisible(true));
    }
}
