package ru.poscenter.jpostest;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

import jpos.JposException;
import jpos.Scale;
import jpos.ScaleConst;

/**
 * Закладка 3: Методы весов, добавленные в UPOS 1.14 (ScaleControl114).
 *
 * Capabilities (1.14):
 *   getCapFreezeValue, getCapReadLiveWeightWithTare, getCapSetPriceCalculationMode,
 *   getCapSetUnitPriceWithWeightUnit, getCapSpecialTare, getCapTarePriority
 *
 * Properties (1.14):
 *   getMinimumWeight
 *
 * Methods (1.14):
 *   doPriceCalculating, freezeValue, readLiveWeightWithTare,
 *   setPriceCalculationMode, setSpecialTare, setTarePriority,
 *   setUnitPriceWithWeightUnit
 */
public class Tab3Scale114Page extends JPanel {

    private final Scale scale;
    private final Tab4EventsPage eventsPage;

    // -- Capabilities --
    private JButton btnGetCaps114;
    private JTextArea taCaps114;

    // -- readLiveWeightWithTare --
    private JTextField tfRlwtTimeout;
    private JButton btnReadLiveWeightWithTare;

    // -- doPriceCalculating --
    private JTextField tfDpcTare, tfDpcUnitPrice, tfDpcTimeout;
    private JButton btnDoPriceCalculating;

    // -- freezeValue --
    private JCheckBox cbFreezeManualTare, cbFreezeWeightedTare, cbFreezePercentTare, cbFreezeUnitPrice;
    private JCheckBox cbFreezeFreeze;
    private JButton btnFreezeValue;

    // -- setPriceCalculationMode --
    private JComboBox<String> cbPriceCalcMode;
    private JButton btnSetPriceCalcMode;

    // -- setSpecialTare --
    private JComboBox<String> cbSpecialTareMode;
    private JTextField tfSpecialTareData;
    private JButton btnSetSpecialTare;

    // -- setTarePriority --
    private JComboBox<String> cbTarePriority;
    private JButton btnSetTarePriority;

    // -- setUnitPriceWithWeightUnit --
    private JTextField tfSpwuUnitPrice, tfSpwuWeightNumerator, tfSpwuWeightDenominator;
    private JComboBox<String> cbSpwuWeightUnit;
    private JButton btnSetUnitPriceWithWeightUnit;

    // -- result --
    private JTextArea taResult;

    public Tab3Scale114Page(Scale scale, Tab4EventsPage eventsPage) {
        this.scale = scale;
        this.eventsPage = eventsPage;
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));

        main.add(buildCapsPanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildReadLiveWeightPanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildDoPriceCalcPanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildFreezeValuePanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildModePanel());
        main.add(Box.createVerticalStrut(6));
        main.add(buildUnitPriceWithWeightUnitPanel());

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

    private JPanel buildCapsPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(new TitledBorder("Capabilities UPOS 1.14"));

        btnGetCaps114 = new JButton("Получить capabilities 1.14");
        btnGetCaps114.addActionListener(e -> doGetCaps114());

        taCaps114 = new JTextArea(4, 60);
        taCaps114.setEditable(false);
        taCaps114.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scroll = new JScrollPane(taCaps114);

        p.add(btnGetCaps114, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildReadLiveWeightPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("readLiveWeightWithTare — чтение живого веса с тарой"));

        p.add(new JLabel("Таймаут (мс):"));
        tfRlwtTimeout = new JTextField("3000", 6);
        p.add(tfRlwtTimeout);

        btnReadLiveWeightWithTare = new JButton("readLiveWeightWithTare");
        btnReadLiveWeightWithTare.setToolTipText(
                "readLiveWeightWithTare(weightData[], tare[], timeout)\n" +
                "Возвращает текущий живой вес и текущую тару одновременно.");
        btnReadLiveWeightWithTare.addActionListener(e -> doReadLiveWeightWithTare());
        p.add(btnReadLiveWeightWithTare);

        return p;
    }

    private JPanel buildDoPriceCalcPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("doPriceCalculating — расчёт цены"));

        p.add(new JLabel("Тара (г):"));
        tfDpcTare = new JTextField("0", 5);
        p.add(tfDpcTare);

        p.add(new JLabel("UnitPrice:"));
        tfDpcUnitPrice = new JTextField("100", 8);
        p.add(tfDpcUnitPrice);

        p.add(new JLabel("Таймаут (мс):"));
        tfDpcTimeout = new JTextField("3000", 6);
        p.add(tfDpcTimeout);

        btnDoPriceCalculating = new JButton("doPriceCalculating");
        btnDoPriceCalculating.setToolTipText(
                "doPriceCalculating(weightValue[], tare[], unitPrice[], unitPriceX[],\n" +
                "  weightUnitX[], weightNumeratorX[], weightDenominatorX[], price[], timeout)\n" +
                "Взвешивает и рассчитывает итоговую цену.");
        btnDoPriceCalculating.addActionListener(e -> doDoPriceCalculating());
        p.add(btnDoPriceCalculating);

        return p;
    }

    private JPanel buildFreezeValuePanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("freezeValue — заморозить значения"));

        cbFreezeManualTare   = new JCheckBox("MANUAL_TARE");
        cbFreezeWeightedTare = new JCheckBox("WEIGHTED_TARE");
        cbFreezePercentTare  = new JCheckBox("PERCENT_TARE");
        cbFreezeUnitPrice    = new JCheckBox("UNITPRICE");
        p.add(cbFreezeManualTare);
        p.add(cbFreezeWeightedTare);
        p.add(cbFreezePercentTare);
        p.add(cbFreezeUnitPrice);

        cbFreezeFreeze = new JCheckBox("freeze=true");
        cbFreezeFreeze.setSelected(true);
        p.add(cbFreezeFreeze);

        btnFreezeValue = new JButton("freezeValue");
        btnFreezeValue.setToolTipText(
                "freezeValue(item, freeze)\n" +
                "item — битовая маска: SFR_MANUAL_TARE|SFR_WEIGHTED_TARE|SFR_PERCENT_TARE|SFR_UNITPRICE\n" +
                "freeze=true — заморозить, false — разморозить.");
        btnFreezeValue.addActionListener(e -> doFreezeValue());
        p.add(btnFreezeValue);

        return p;
    }

    private JPanel buildModePanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("setPriceCalculationMode / setSpecialTare / setTarePriority"));

        // setPriceCalculationMode
        p.add(new JLabel("PriceCalcMode:"));
        cbPriceCalcMode = new JComboBox<>(new String[]{
                "PRICE_LABELING (1)", "SELF_SERVICE (2)", "OPERATOR (3)"
        });
        cbPriceCalcMode.setToolTipText(
                "setPriceCalculationMode(mode)\n1=PRICE_LABELING, 2=SELF_SERVICE, 3=OPERATOR");
        p.add(cbPriceCalcMode);
        btnSetPriceCalcMode = new JButton("set");
        btnSetPriceCalcMode.addActionListener(e -> doSetPriceCalcMode());
        p.add(btnSetPriceCalcMode);

        p.add(Box.createHorizontalStrut(12));

        // setSpecialTare
        p.add(new JLabel("SpecialTare mode:"));
        cbSpecialTareMode = new JComboBox<>(new String[]{
                "DEFAULT (1)", "MANUAL (2)", "PERCENT (3)", "WEIGHTED (4)"
        });
        cbSpecialTareMode.setToolTipText(
                "setSpecialTare(mode, data)\n1=DEFAULT, 2=MANUAL, 3=PERCENT, 4=WEIGHTED");
        p.add(cbSpecialTareMode);
        p.add(new JLabel("data:"));
        tfSpecialTareData = new JTextField("0", 5);
        p.add(tfSpecialTareData);
        btnSetSpecialTare = new JButton("setSpecialTare");
        btnSetSpecialTare.addActionListener(e -> doSetSpecialTare());
        p.add(btnSetSpecialTare);

        p.add(Box.createHorizontalStrut(12));

        // setTarePriority
        p.add(new JLabel("TarePriority:"));
        cbTarePriority = new JComboBox<>(new String[]{"FIRST (1)", "NONE (2)"});
        cbTarePriority.setToolTipText(
                "setTarePriority(priority)\n1=FIRST, 2=NONE");
        p.add(cbTarePriority);
        btnSetTarePriority = new JButton("setTarePriority");
        btnSetTarePriority.addActionListener(e -> doSetTarePriority());
        p.add(btnSetTarePriority);

        return p;
    }

    private JPanel buildUnitPriceWithWeightUnitPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("setUnitPriceWithWeightUnit — цена с указанием единицы веса"));

        p.add(new JLabel("UnitPrice:"));
        tfSpwuUnitPrice = new JTextField("100", 8);
        p.add(tfSpwuUnitPrice);

        p.add(new JLabel("WeightUnit:"));
        cbSpwuWeightUnit = new JComboBox<>(new String[]{
                "GRAM (1)", "KILOGRAM (2)", "OUNCE (3)", "POUND (4)"
        });
        p.add(cbSpwuWeightUnit);

        p.add(new JLabel("Numerator:"));
        tfSpwuWeightNumerator = new JTextField("1", 4);
        p.add(tfSpwuWeightNumerator);

        p.add(new JLabel("Denominator:"));
        tfSpwuWeightDenominator = new JTextField("1", 4);
        p.add(tfSpwuWeightDenominator);

        btnSetUnitPriceWithWeightUnit = new JButton("setUnitPriceWithWeightUnit");
        btnSetUnitPriceWithWeightUnit.setToolTipText(
                "setUnitPriceWithWeightUnit(unitPrice, weightUnit, weightNumerator, weightDenominator)\n" +
                "Устанавливает цену за единицу с указанием единицы и масштаба веса.");
        btnSetUnitPriceWithWeightUnit.addActionListener(e -> doSetUnitPriceWithWeightUnit());
        p.add(btnSetUnitPriceWithWeightUnit);

        return p;
    }

    // ======================= Действия =======================

    private void doGetCaps114() {
        StringBuilder sb = new StringBuilder();
        appendCap(sb, "CapFreezeValue",                () -> scale.getCapFreezeValue());
        appendCap(sb, "CapReadLiveWeightWithTare",     () -> scale.getCapReadLiveWeightWithTare());
        appendCap(sb, "CapSetPriceCalculationMode",    () -> scale.getCapSetPriceCalculationMode());
        appendCap(sb, "CapSetUnitPriceWithWeightUnit", () -> scale.getCapSetUnitPriceWithWeightUnit());
        appendCap(sb, "CapSpecialTare",                () -> scale.getCapSpecialTare());
        appendCap(sb, "CapTarePriority",               () -> scale.getCapTarePriority());
        sb.append("---\n");
        appendCap(sb, "MinimumWeight",                 () -> scale.getMinimumWeight());
        taCaps114.setText(sb.toString());
    }

    private void doReadLiveWeightWithTare() {
        try {
            int timeout = parseIntField(tfRlwtTimeout, 3000);
            int[] weightData = new int[1];
            int[] tare = new int[1];
            scale.readLiveWeightWithTare(weightData, tare, timeout);
            String unit = getWeightUnitText();
            String msg = "readLiveWeightWithTare: weight=" + weightData[0] + " " + unit
                    + ", tare=" + tare[0] + " " + unit;
            showOk(msg);
            eventsPage.log(msg);
        } catch (JposException ex) {
            showError("readLiveWeightWithTare", ex);
        }
    }

    private void doDoPriceCalculating() {
        try {
            int[] weightValue     = new int[1];
            int[] tare            = {parseIntField(tfDpcTare, 0)};
            long[] unitPrice      = {parseLongField(tfDpcUnitPrice, 100)};
            long[] unitPriceX     = new long[1];
            int[] weightUnitX     = new int[1];
            int[] weightNumeratorX   = new int[1];
            int[] weightDenominatorX = new int[1];
            long[] price          = new long[1];
            int timeout           = parseIntField(tfDpcTimeout, 3000);

            scale.doPriceCalculating(
                    weightValue, tare, unitPrice, unitPriceX,
                    weightUnitX, weightNumeratorX, weightDenominatorX,
                    price, timeout);

            String unit = getWeightUnitText();
            String msg = "doPriceCalculating:\n"
                    + "  weightValue=" + weightValue[0] + " " + unit + "\n"
                    + "  tare=" + tare[0] + "\n"
                    + "  unitPrice=" + unitPrice[0] + "\n"
                    + "  price=" + price[0];
            showOk(msg);
            eventsPage.log("doPriceCalculating: weight=" + weightValue[0] + " price=" + price[0]);
        } catch (JposException ex) {
            showError("doPriceCalculating", ex);
        }
    }

    private void doFreezeValue() {
        try {
            int item = 0;
            if (cbFreezeManualTare.isSelected())   item |= ScaleConst.SCAL_SFR_MANUAL_TARE;
            if (cbFreezeWeightedTare.isSelected())  item |= ScaleConst.SCAL_SFR_WEIGHTED_TARE;
            if (cbFreezePercentTare.isSelected())   item |= ScaleConst.SCAL_SFR_PERCENT_TARE;
            if (cbFreezeUnitPrice.isSelected())     item |= ScaleConst.SCAL_SFR_UNITPRICE;
            boolean freeze = cbFreezeFreeze.isSelected();
            scale.freezeValue(item, freeze);
            showOk("freezeValue(item=0x" + Integer.toHexString(item) + ", freeze=" + freeze + ") — OK");
            eventsPage.log("freezeValue(0x" + Integer.toHexString(item) + ", " + freeze + ") — OK");
        } catch (JposException ex) {
            showError("freezeValue", ex);
        }
    }

    private void doSetPriceCalcMode() {
        try {
            int mode = cbPriceCalcMode.getSelectedIndex() + 1;
            scale.setPriceCalculationMode(mode);
            showOk("setPriceCalculationMode(" + mode + ") — OK");
            eventsPage.log("setPriceCalculationMode(" + mode + ") — OK");
        } catch (JposException ex) {
            showError("setPriceCalculationMode", ex);
        }
    }

    private void doSetSpecialTare() {
        try {
            int mode = cbSpecialTareMode.getSelectedIndex() + 1;
            int data = parseIntField(tfSpecialTareData, 0);
            scale.setSpecialTare(mode, data);
            showOk("setSpecialTare(" + mode + ", " + data + ") — OK");
            eventsPage.log("setSpecialTare(" + mode + ", " + data + ") — OK");
        } catch (JposException ex) {
            showError("setSpecialTare", ex);
        }
    }

    private void doSetTarePriority() {
        try {
            int priority = cbTarePriority.getSelectedIndex() + 1;
            scale.setTarePriority(priority);
            showOk("setTarePriority(" + priority + ") — OK");
            eventsPage.log("setTarePriority(" + priority + ") — OK");
        } catch (JposException ex) {
            showError("setTarePriority", ex);
        }
    }

    private void doSetUnitPriceWithWeightUnit() {
        try {
            long unitPrice       = parseLongField(tfSpwuUnitPrice, 100);
            int  weightUnit      = cbSpwuWeightUnit.getSelectedIndex() + 1;
            int  weightNumerator = parseIntField(tfSpwuWeightNumerator, 1);
            int  weightDenominator = parseIntField(tfSpwuWeightDenominator, 1);
            scale.setUnitPriceWithWeightUnit(unitPrice, weightUnit, weightNumerator, weightDenominator);
            showOk("setUnitPriceWithWeightUnit(" + unitPrice + ", " + weightUnit
                    + ", " + weightNumerator + ", " + weightDenominator + ") — OK");
            eventsPage.log("setUnitPriceWithWeightUnit(" + unitPrice + ") — OK");
        } catch (JposException ex) {
            showError("setUnitPriceWithWeightUnit", ex);
        }
    }

    // ======================= Вспомогательные методы =======================

    @FunctionalInterface
    private interface Getter<T> { T get() throws JposException; }

    private <T> void appendCap(StringBuilder sb, String name, Getter<T> getter) {
        sb.append(String.format("%-40s = ", name));
        try { sb.append(getter.get()); } catch (JposException e) { sb.append("[" + e.getMessage() + "]"); }
        sb.append("\n");
    }

    private String getWeightUnitText() {
        try {
            int unit = scale.getWeightUnit();
            switch (unit) {
                case ScaleConst.SCAL_WU_GRAM:     return "г";
                case ScaleConst.SCAL_WU_KILOGRAM: return "кг";
                case ScaleConst.SCAL_WU_OUNCE:    return "oz";
                case ScaleConst.SCAL_WU_POUND:    return "lb";
                default: return String.valueOf(unit);
            }
        } catch (JposException e) { return "?"; }
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
