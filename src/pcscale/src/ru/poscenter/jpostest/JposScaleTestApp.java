package ru.poscenter.jpostest;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import jpos.Scale;

/**
 * Графический тест JPOS-драйвера весов по стандарту UPOS 1.14.
 *
 * Закладка 1: Общие методы UPOS (open, close, claim, release, enable, checkHealth, ...)
 * Закладка 2: Методы весов (readWeight, zeroScale, displayText, clearInput, ...)
 * Закладка 3: Методы весов UPOS 1.14 (doPriceCalculating, freezeValue, readLiveWeightWithTare, ...)
 * Закладка 4: События (DataEvent, ErrorEvent, StatusUpdateEvent, DirectIOEvent)
 */
public class JposScaleTestApp extends JFrame {

    private final Scale scale = new Scale();
    private final JTabbedPane tabbedPane = new JTabbedPane();

    private Tab4EventsPage eventsPage;
    private Tab1UposPage uposPage;
    private Tab2ScalePage scalePage;
    private Tab3Scale114Page scale114Page;

    public JposScaleTestApp() {
        setTitle("JPOS Scale Test — UPOS 1.14");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(720, 560);
        setMinimumSize(new Dimension(640, 480));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });

        eventsPage   = new Tab4EventsPage();
        uposPage     = new Tab1UposPage(scale, eventsPage);
        scalePage    = new Tab2ScalePage(scale, eventsPage);
        scale114Page = new Tab3Scale114Page(scale, eventsPage);

        tabbedPane.addTab("1. Общие UPOS",  uposPage);
        tabbedPane.addTab("2. Весы",        scalePage);
        tabbedPane.addTab("3. Весы 1.14",   scale114Page);
        tabbedPane.addTab("4. События",     eventsPage);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClose = new JButton("Закрыть");
        btnClose.addActionListener(e -> onClose());
        bottomPanel.add(btnClose);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(tabbedPane, BorderLayout.CENTER);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        // Center on screen
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
    }

    private void onClose() {
        try {
            if (scale.getState() != jpos.JposConst.JPOS_S_CLOSED) {
                scale.close();
            }
        } catch (Exception ex) {
            // ignore on close
        }
        dispose();
        System.exit(0);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            // use default L&F
        }
        SwingUtilities.invokeLater(() -> new JposScaleTestApp().setVisible(true));
    }
}
