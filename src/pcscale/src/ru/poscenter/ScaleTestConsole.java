package ru.poscenter;

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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class ScaleTestConsole implements DataListener, DirectIOListener,
        ErrorListener, StatusUpdateListener {

    // Scale Status Constants
    private static final int SCAL_SUE_WEIGHT_STABLE = 11;
    private static final int SCAL_SUE_WEIGHT_UNSTABLE = 12;
    private static final int SCAL_SUE_WEIGHT_ZERO = 13;
    private static final int SCAL_SUE_WEIGHT_OVERWEIGHT = 14;
    private static final int SCAL_SUE_NOT_READY = 15;
    private static final int SCAL_SUE_READY = 16;
    private static final int SCAL_SUE_WEIGHT_UNDER_ZERO = 17;

    // Status Notify Constants
    private static final int SCAL_SN_DISABLED = 1;
    private static final int SCAL_SN_ENABLED = 2;

    private Scale scale;
    private Scanner scanner;
    private boolean eventLoggingEnabled = true;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    // Event counters
    private int dataEventCount = 0;
    private int directIOEventCount = 0;
    private int errorEventCount = 0;
    private int outputCompleteEventCount = 0;
    private int statusUpdateEventCount = 0;

    public ScaleTestConsole() {
        scale = new Scale();
        scanner = new Scanner(System.in);

        // Register event listeners (only those that exist in your JPOS version)
        scale.addDataListener(this);
        scale.addDirectIOListener(this);
        scale.addErrorListener(this);
        scale.addStatusUpdateListener(this);

        // OutputCompleteListener might not exist in older version
        // scale.addOutputCompleteListener(this);
    }

    public static void main(String[] args) {
        ScaleTestConsole test = new ScaleTestConsole();
        test.run();
    }

    public void run() {
        System.out.println("=== JPOS Scale Driver Test Console (with Event Support) ===");
        System.out.println("Events will be displayed in real-time as they occur");
        printHelp();

        String command;
        while (true) {
            System.out.print("\n> ");
            command = scanner.nextLine().trim().toLowerCase();

            try {
                switch (command) {
                    case "help":
                    case "?":
                        printHelp();
                        break;
                    case "open":
                        openDevice();
                        break;
                    case "claim":
                        claimDevice();
                        break;
                    case "release":
                        releaseDevice();
                        break;
                    case "close":
                        closeDevice();
                        break;
                    case "enable":
                        enableDevice(true);
                        break;
                    case "disable":
                        enableDevice(false);
                        break;
                    case "zero":
                        zeroScale();
                        break;
                    case "read":
                        readWeight();
                        break;
                    case "readasync":
                        readWeightAsync();
                        break;
                    case "tare":
                        setTareWeight();
                        break;
                    case "price":
                        setUnitPrice();
                        break;
                    case "display":
                        displayText();
                        break;
                    case "statusnotify":
                        setStatusNotify();
                        break;
                    case "zerovalid":
                        setZeroValid();
                        break;
                    case "async":
                        setAsyncMode();
                        break;
                    case "info":
                        showInfo();
                        break;
                    case "events":
                        toggleEventLogging();
                        break;
                    case "stats":
                        showEventStats();
                        break;
                    case "clearstats":
                        clearEventStats();
                        break;
                    case "wait":
                        waitForEvents();
                        break;
                    case "exit":
                    case "quit":
                        System.out.println("Exiting...");
                        cleanup();
                        return;
                    default:
                        if (!command.isEmpty()) {
                            System.out.println("Unknown command. Type 'help' for available commands.");
                        }
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void printHelp() {
        System.out.println("\n=== Commands ===");
        System.out.println("open          - Open the scale device");
        System.out.println("claim         - Claim exclusive access");
        System.out.println("release       - Release exclusive access");
        System.out.println("close         - Close the device");
        System.out.println("enable        - Enable the device");
        System.out.println("disable       - Disable the device");
        System.out.println("zero          - Zero the scale");
        System.out.println("read          - Read current weight (synchronous)");
        System.out.println("readasync     - Read weight asynchronously (events will fire)");
        System.out.println("tare          - Set tare weight");
        System.out.println("price         - Set unit price");
        System.out.println("display       - Display text on scale");
        System.out.println("statusnotify  - Set status notification (1=Disabled, 2=Enabled)");
        System.out.println("zerovalid     - Set zero valid flag (true/false)");
        System.out.println("async         - Set async mode (true/false)");
        System.out.println("info          - Show device information");
        System.out.println("events        - Toggle event logging (currently: " + eventLoggingEnabled + ")");
        System.out.println("stats         - Show event statistics");
        System.out.println("clearstats    - Clear event statistics");
        System.out.println("wait          - Wait for events (sleep 10 seconds)");
        System.out.println("exit/quit     - Exit the program");
        System.out.println("help/?        - Show this help");

        System.out.println("\n=== Status Codes ===");
        System.out.println("11 - Weight Stable");
        System.out.println("12 - Weight Unstable");
        System.out.println("13 - Weight Zero");
        System.out.println("14 - Overweight");
        System.out.println("15 - Not Ready");
        System.out.println("16 - Ready");
        System.out.println("17 - Under Zero");
    }

    // ==================== Event Handlers ====================
    @Override
    public void dataOccurred(DataEvent event) {
        dataEventCount++;
        if (eventLoggingEnabled) {
            String timestamp = dateFormat.format(new Date());
            System.out.println("\n┌─────────────────────────────────────────");
            System.out.println("│ [" + timestamp + "] 📊 DATA EVENT #" + dataEventCount);
            try {
                // getStatus() might not exist, use event.getStatus()
                System.out.println("│ Event Status: " + event.getStatus());
                System.out.println("│ Device State: " + getStateString());
            } catch (Exception e) {
                System.out.println("│ Error getting details: " + e.getMessage());
            }
            System.out.println("└─────────────────────────────────────────");
            System.out.print("> ");
        }
    }

    @Override
    public void directIOOccurred(DirectIOEvent event) {
        directIOEventCount++;
        if (eventLoggingEnabled) {
            String timestamp = dateFormat.format(new Date());
            System.out.println("\n┌─────────────────────────────────────────");
            System.out.println("│ [" + timestamp + "] 🔌 DIRECT IO EVENT #" + directIOEventCount);
            System.out.println("│ Event Number: " + event.getEventNumber());
            System.out.println("│ Data: " + event.getData());
            System.out.println("│ Object: " + event.getObject());
            System.out.println("└─────────────────────────────────────────");
            System.out.print("> ");
        }
    }

    @Override
    public void errorOccurred(ErrorEvent event) {
        errorEventCount++;
        String timestamp = dateFormat.format(new Date());
        System.err.println("\n┌─────────────────────────────────────────");
        System.err.println("│ [" + timestamp + "] ❌ ERROR EVENT #" + errorEventCount);
        System.err.println("│ Error Code: " + event.getErrorCode());
        System.err.println("│ Error Code Extended: " + event.getErrorCodeExtended());
        System.err.println("│ Error Locus: " + event.getErrorLocus());
        System.err.println("│ Error Response: " + event.getErrorResponse());
        System.err.println("└─────────────────────────────────────────");
        System.out.print("> ");
    }

    // OutputCompleteEvent handler - commented out if not available
    /*
     @Override
     public void outputCompleteOccurred(OutputCompleteEvent event) {
     outputCompleteEventCount++;
     if (eventLoggingEnabled) {
     String timestamp = dateFormat.format(new Date());
     System.out.println("\n┌─────────────────────────────────────────");
     System.out.println("│ [" + timestamp + "] ✅ OUTPUT COMPLETE EVENT #" + outputCompleteEventCount);
     System.out.println("│ Output ID: " + event.getOutputID());
     System.out.println("└─────────────────────────────────────────");
     System.out.print("> ");
     }
     }
     */
    @Override
    public void statusUpdateOccurred(StatusUpdateEvent event) {
        statusUpdateEventCount++;
        if (eventLoggingEnabled) {
            String timestamp = dateFormat.format(new Date());
            System.out.println("\n┌─────────────────────────────────────────");
            System.out.println("│ [" + timestamp + "] 📡 STATUS UPDATE EVENT #" + statusUpdateEventCount);
            System.out.println("│ Status: " + event.getStatus() + " (" + getStatusDescription(event.getStatus()) + ")");
            System.out.println("└─────────────────────────────────────────");
            System.out.print("> ");
        }
    }

    private String getStatusDescription(int status) {
        switch (status) {
            case SCAL_SUE_WEIGHT_STABLE:
                return "Weight Stable";
            case SCAL_SUE_WEIGHT_UNSTABLE:
                return "Weight Unstable";
            case SCAL_SUE_WEIGHT_ZERO:
                return "Weight Zero";
            case SCAL_SUE_WEIGHT_OVERWEIGHT:
                return "Overweight";
            case SCAL_SUE_NOT_READY:
                return "Not Ready";
            case SCAL_SUE_READY:
                return "Ready";
            case SCAL_SUE_WEIGHT_UNDER_ZERO:
                return "Under Zero";
            default:
                return "Unknown status";
        }
    }

    // ==================== Device Operations ====================
    private void openDevice() {
        try {
            System.out.print("Enter logical device name [default: Scale]: ");
            String logicalName = scanner.nextLine().trim();
            if (logicalName.isEmpty()) {
                logicalName = "Scale";
            }

            scale.open(logicalName);
            System.out.println("✓ Device opened successfully. Logical name: " + logicalName);
            System.out.println("  State: " + getStateString());
        } catch (JposException e) {
            System.err.println("✗ Failed to open device: " + e.getMessage());
            if (e.getErrorCodeExtended() != 0) {
                System.err.println("  Extended error code: " + e.getErrorCodeExtended());
            }
        }
    }

    private void claimDevice() {
        try {
            System.out.print("Enter timeout (ms) [default: 1000]: ");
            String input = scanner.nextLine().trim();
            int timeout = input.isEmpty() ? 1000 : Integer.parseInt(input);

            scale.claim(timeout);
            System.out.println("✓ Device claimed successfully");
            System.out.println("  State: " + getStateString());
        } catch (JposException e) {
            System.err.println("✗ Failed to claim device: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("✗ Invalid timeout value");
        }
    }

    private void releaseDevice() {
        try {
            scale.release();
            System.out.println("✓ Device released successfully");
            System.out.println("  State: " + getStateString());
        } catch (JposException e) {
            System.err.println("✗ Failed to release device: " + e.getMessage());
        }
    }

    private void closeDevice() {
        try {
            scale.close();
            System.out.println("✓ Device closed successfully");
            System.out.println("  State: " + getStateString());
        } catch (JposException e) {
            System.err.println("✗ Failed to close device: " + e.getMessage());
        }
    }

    private void enableDevice(boolean enable) {
        try {
            scale.setDeviceEnabled(enable);
            System.out.println("✓ Device " + (enable ? "enabled" : "disabled") + " successfully");
            System.out.println("  State: " + getStateString());

            if (enable) {
                displayCurrentProperties();
            }
        } catch (JposException e) {
            System.err.println("✗ Failed to " + (enable ? "enable" : "disable") + " device: " + e.getMessage());
        }
    }

    private void zeroScale() {
        try {
            System.out.println("  Zeroing scale...");
            scale.zeroScale();
            System.out.println("✓ Scale zeroed successfully");
        } catch (JposException e) {
            System.err.println("✗ Failed to zero scale: " + e.getMessage());
        }
    }

    private void readWeight() {
        try {
            System.out.print("Enter timeout (ms) [default: 1000]: ");
            String input = scanner.nextLine().trim();
            int timeout = input.isEmpty() ? 1000 : Integer.parseInt(input);

            int[] weightData = new int[1];
            scale.readWeight(weightData, timeout);

            int weightUnit = scale.getWeightUnit();
            String unitName = getWeightUnitString(weightUnit);

            System.out.println("✓ Weight read successfully");
            System.out.println("  Weight: " + weightData[0]);
            System.out.println("  Unit: " + weightUnit + " (" + unitName + ")");
        } catch (JposException e) {
            System.err.println("✗ Failed to read weight: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("✗ Invalid timeout value");
        }
    }

    private void readWeightAsync() {
        try {
            System.out.print("Enter timeout (ms) [default: 1000]: ");
            String input = scanner.nextLine().trim();
            int timeout = input.isEmpty() ? 1000 : Integer.parseInt(input);

            System.out.println("⏳ Asynchronous read initiated... Waiting for DataEvent...");
            int[] weightData = new int[1];
            scale.readWeight(weightData, timeout);

            System.out.println("  Async read request sent, DataEvent should fire shortly");
        } catch (JposException e) {
            System.err.println("✗ Failed to read weight: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("✗ Invalid timeout value");
        }
    }

    private void setTareWeight() {
        try {
            System.out.print("Enter tare weight: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                System.err.println("✗ Tare weight is required");
                return;
            }

            int tareWeight = Integer.parseInt(input);
            scale.setTareWeight(tareWeight);
            System.out.println("✓ Tare weight set to: " + tareWeight);
            System.out.println("  Current tare weight: " + scale.getTareWeight());
        } catch (JposException e) {
            System.err.println("✗ Failed to set tare weight: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("✗ Invalid tare weight value");
        }
    }

    private void setUnitPrice() {
        try {
            System.out.print("Enter unit price: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                System.err.println("✗ Unit price is required");
                return;
            }

            long unitPrice = Long.parseLong(input);
            scale.setUnitPrice(unitPrice);
            System.out.println("✓ Unit price set to: " + unitPrice);
            System.out.println("  Current unit price: " + scale.getUnitPrice());
        } catch (JposException e) {
            System.err.println("✗ Failed to set unit price: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("✗ Invalid unit price value");
        }
    }

    private void displayText() {
        try {
            System.out.print("Enter text to display: ");
            String text = scanner.nextLine();
            if (text.isEmpty()) {
                System.err.println("✗ Display text is required");
                return;
            }

            scale.displayText(text);
            System.out.println("✓ Text displayed: \"" + text + "\"");
        } catch (JposException e) {
            System.err.println("✗ Failed to display text: " + e.getMessage());
        }
    }

    private void setStatusNotify() {
        try {
            System.out.println("Status notify options:");
            System.out.println("  1 - Disabled (SCAL_SN_DISABLED)");
            System.out.println("  2 - Enabled (SCAL_SN_ENABLED)");
            System.out.print("Enter choice [1/2]: ");

            String input = scanner.nextLine().trim();
            int value;

            switch (input) {
                case "1":
                    value = SCAL_SN_DISABLED;
                    break;
                case "2":
                    value = SCAL_SN_ENABLED;
                    break;
                default:
                    System.err.println("✗ Invalid choice");
                    return;
            }

            scale.setStatusNotify(value);
            System.out.println("✓ Status notify set to: " + (value == SCAL_SN_DISABLED ? "Disabled" : "Enabled"));
        } catch (JposException e) {
            System.err.println("✗ Failed to set status notify: " + e.getMessage());
        }
    }

    private void setZeroValid() {
        try {
            System.out.print("Set zero valid (true/false) [default: true]: ");
            String input = scanner.nextLine().trim().toLowerCase();
            boolean value = input.isEmpty() || input.equals("true");

            scale.setZeroValid(value);
            System.out.println("✓ Zero valid set to: " + value);
            System.out.println("  Current zero valid: " + scale.getZeroValid());
        } catch (JposException e) {
            System.err.println("✗ Failed to set zero valid: " + e.getMessage());
        }
    }

    private void setAsyncMode() {
        try {
            System.out.print("Set async mode (true/false) [default: false]: ");
            String input = scanner.nextLine().trim().toLowerCase();
            boolean value = !input.isEmpty() && input.equals("true");

            scale.setAsyncMode(value);
            System.out.println("✓ Async mode set to: " + value);
            System.out.println("  Current async mode: " + scale.getAsyncMode());
        } catch (JposException e) {
            System.err.println("✗ Failed to set async mode: " + e.getMessage());
        }
    }

    // ==================== Event Management ====================
    private void toggleEventLogging() {
        eventLoggingEnabled = !eventLoggingEnabled;
        System.out.println("Event logging " + (eventLoggingEnabled ? "enabled" : "disabled"));
    }

    private void showEventStats() {
        System.out.println("\n=== Event Statistics ===");
        System.out.println("📊 Data Events:          " + dataEventCount);
        System.out.println("🔌 DirectIO Events:      " + directIOEventCount);
        System.out.println("❌ Error Events:         " + errorEventCount);
        System.out.println("📡 StatusUpdate Events:  " + statusUpdateEventCount);
        System.out.println("─────────────────────────────");
        System.out.println("Total Events:            " + (dataEventCount + directIOEventCount
                + errorEventCount + statusUpdateEventCount));
    }

    private void clearEventStats() {
        dataEventCount = 0;
        directIOEventCount = 0;
        errorEventCount = 0;
        outputCompleteEventCount = 0;
        statusUpdateEventCount = 0;
        System.out.println("✓ Event statistics cleared");
    }

    private void waitForEvents() {
        System.out.println("Waiting for events for 10 seconds...");
        System.out.println("(Place weight on scale, remove weight, etc.)");
        try {
            for (int i = 10; i > 0; i--) {
                System.out.print("\r  " + i + " seconds remaining...");
                Thread.sleep(1000);
            }
            System.out.println("\n✓ Wait period completed");
            showEventStats();
        } catch (InterruptedException e) {
            System.err.println("Wait interrupted");
        }
    }

    // ==================== Helper Methods ====================
    private void showInfo() {
        try {
            System.out.println("\n=== Device Information ===");
            System.out.println("State: " + getStateString());
            System.out.println("Check Health: " + scale.getCheckHealthText());

            System.out.println("\nCapabilities:");
            // These methods return boolean in older JPOS versions
            //System.out.println("  - Price Calculation: " + (scale.getCapPriceCalculation() ? "Yes" : "No"));
            System.out.println("  - Tare Weight: " + (scale.getCapTareWeight() ? "Yes" : "No"));
            System.out.println("  - Zero Scale: " + (scale.getCapZeroScale() ? "Yes" : "No"));
            System.out.println("  - Display Text: " + (scale.getCapDisplayText() ? "Yes" : "No"));

            if (scale.getDeviceEnabled()) {
                displayCurrentProperties();
            }
            showEventStats();
        } catch (JposException e) {
            System.err.println("✗ Failed to get device info: " + e.getMessage());
        }
    }

    private void displayCurrentProperties() {
        try {
            System.out.println("\nCurrent Properties:");
            System.out.println("  - Async Mode: " + scale.getAsyncMode());
            System.out.println("  - Status Notify: " + scale.getStatusNotify());
            System.out.println("  - Tare Weight: " + scale.getTareWeight());
            System.out.println("  - Unit Price: " + scale.getUnitPrice());
            System.out.println("  - Zero Valid: " + scale.getZeroValid());
            System.out.println("  - Weight Unit: " + scale.getWeightUnit() + " ("
                    + getWeightUnitString(scale.getWeightUnit()) + ")");
            System.out.println("  - Maximum Weight: " + scale.getMaximumWeight());
        } catch (JposException e) {
            System.err.println("  Failed to get some properties: " + e.getMessage());
        }
    }

    private String getWeightUnitString(int unit) {
        switch (unit) {
            case 1:
                return "Grams";
            case 2:
                return "Kilograms";
            case 3:
                return "Ounces";
            case 4:
                return "Pounds";
            default:
                return "Unknown";
        }
    }

    private String getStateString() {
        int state = scale.getState();
        switch (state) {
            case 0:
                return "Closed (JPOS_S_CLOSED)";
            case 1:
                return "Opened (JPOS_S_OPENED)";
            case 2:
                return "Claimed (JPOS_S_CLAIMED)";
            case 3:
                return "Enabled (JPOS_S_ENABLED)";
            default:
                return "Unknown state: " + state;
        }
    }

    private void cleanup() {
        try {
            if (scale.getDeviceEnabled()) {
                scale.setDeviceEnabled(false);
            }
            if (scale.getState() >= 2) {
                scale.release();
            }
            if (scale.getState() >= 1) {
                scale.close();
            }
            System.out.println("✓ Cleanup completed");
        } catch (JposException e) {
            System.err.println("⚠ Cleanup warning: " + e.getMessage());
        }
    }
}
