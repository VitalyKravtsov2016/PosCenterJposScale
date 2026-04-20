// test.go
package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"strconv"
	"strings"
	"time"
	"pos2scale/pos2scale"
)

// CLI flags
var (
	portName    = flag.String("port", "", "COM port name (e.g., COM3, /dev/ttyUSB0)")
	baudRate    = flag.Int("baud", 9600, "Baud rate (2400, 4800, 9600, 19200, 38400, 57600, 115200)")
	password    = flag.String("password", "0000", "Admin password (4 digits)")
	timeout     = flag.Duration("timeout", 2*time.Second, "Communication timeout")
	command     = flag.String("cmd", "", "Command name (see --list)")
	listCmds    = flag.Bool("list", false, "List all available commands")
	params      = flag.String("params", "", "Command parameters")
	interactive = flag.Bool("interactive", false, "Interactive mode")
	verbose     = flag.Bool("v", false, "Verbose output")
	logLevel    = flag.String("log-level", "debug", "Log level: debug, info, warn, error")
	logFile     = flag.String("log-file", "pos2scale.log", "Log file path")
)

// Command definition with handler function
type CommandDef struct {
	Name        string
	Description string
	Handler     func(scale *pos2scale.WeightScale, args []string) error
}

var commands = map[string]CommandDef{
	// Basic commands
	"get-state": {
		Name:        "get-state",
		Description: "Get current weight and state",
		Handler:     cmdGetState,
	},
	"get-mode": {
		Name:        "get-mode",
		Description: "Get current mode (normal/calibration)",
		Handler:     cmdGetMode,
	},
	"switch-mode": {
		Name:        "switch-mode",
		Description: "Switch mode (0=normal,1=calibration)",
		Handler:     cmdSwitchMode,
	},
	"get-channel-state": {
		Name:        "get-channel-state",
		Description: "Get weight channel state",
		Handler:     cmdGetChannelState,
	},
	"set-zero": {
		Name:        "set-zero",
		Description: "Set zero",
		Handler:     cmdSetZero,
	},
	"set-tare": {
		Name:        "set-tare",
		Description: "Set tare from current weight",
		Handler:     cmdSetTare,
	},
	"assign-tare": {
		Name:        "assign-tare",
		Description: "Assign specific tare value (0-65535)",
		Handler:     cmdAssignTare,
	},
	"open-drawer": {
		Name:        "open-drawer",
		Description: "Open cash drawer",
		Handler:     cmdOpenDrawer,
	},
	"reset": {
		Name:        "reset",
		Description: "Device reset",
		Handler:     cmdReset,
	},
	// Product commands
	"set-product": {
		Name:        "set-product",
		Description: "Set product type, quantity and price",
		Handler:     cmdSetProduct,
	},
	// Keyboard commands
	"keyboard-emul": {
		Name:        "keyboard-emul",
		Description: "Emulate key press",
		Handler:     cmdKeyboardEmul,
	},
	"keyboard-lock": {
		Name:        "keyboard-lock",
		Description: "Lock/unlock physical keyboard (0=unlock,1=lock)",
		Handler:     cmdKeyboardLock,
	},
	"get-keyboard": {
		Name:        "get-keyboard",
		Description: "Get keyboard state",
		Handler:     cmdGetKeyboard,
	},
	// Channel commands
	"get-channels": {
		Name:        "get-channels",
		Description: "Get number of weight channels",
		Handler:     cmdGetChannels,
	},
	"get-current-channel": {
		Name:        "get-current-channel",
		Description: "Get current active channel",
		Handler:     cmdGetCurrentChannel,
	},
	"select-channel": {
		Name:        "select-channel",
		Description: "Select active channel",
		Handler:     cmdSelectChannel,
	},
	"get-channel-props": {
		Name:        "get-channel-props",
		Description: "Get channel properties",
		Handler:     cmdGetChannelProps,
	},
	"restart-channel": {
		Name:        "restart-channel",
		Description: "Restart current channel",
		Handler:     cmdRestartChannel,
	},
	// Communication commands
	"get-comm": {
		Name:        "get-comm",
		Description: "Get communication parameters",
		Handler:     cmdGetComm,
	},
	"set-comm": {
		Name:        "set-comm",
		Description: "Set communication parameters",
		Handler:     cmdSetComm,
	},
	"change-password": {
		Name:        "change-password",
		Description: "Change admin password",
		Handler:     cmdChangePassword,
	},
	// Calibration commands
	"get-adc": {
		Name:        "get-adc",
		Description: "Get ADC reading",
		Handler:     cmdGetADC,
	},
	"start-calibration": {
		Name:        "start-calibration",
		Description: "Start calibration process",
		Handler:     cmdStartCalibration,
	},
	"get-cal-state": {
		Name:        "get-cal-state",
		Description: "Get calibration state",
		Handler:     cmdGetCalState,
	},
	"abort-calibration": {
		Name:        "abort-calibration",
		Description: "Abort calibration",
		Handler:     cmdAbortCalibration,
	},
	// Device info
	"get-device-type": {
		Name:        "get-device-type",
		Description: "Get device type and version",
		Handler:     cmdGetDeviceType,
	},
	// Weight reading
	"weight": {
		Name:        "weight",
		Description: "Read current weight in kg",
		Handler:     cmdWeight,
	},
	"wait-weight": {
		Name:        "wait-weight",
		Description: "Wait for stable weight and read",
		Handler:     cmdWaitWeight,
	},
}

func main() {
	flag.Parse()

	if *listCmds {
		listAllCommands()
		return
	}

	if *interactive {
		runInteractive()
		return
	}

	if *command == "" {
		fmt.Println("Error: command required. Use --list to see available commands")
		flag.Usage()
		os.Exit(1)
	}

	if *portName == "" {
		fmt.Println("Error: port required. Use --port COM3")
		os.Exit(1)
	}

	executeCommand()
}

func listAllCommands() {
	fmt.Println("Available commands:")
	fmt.Println()

	cmdGroups := map[string]string{
		"get-state":          "Basic commands:",
		"get-mode":           "Basic commands:",
		"switch-mode":        "Basic commands:",
		"get-channel-state":  "Basic commands:",
		"set-zero":           "Basic commands:",
		"set-tare":           "Basic commands:",
		"assign-tare":        "Basic commands:",
		"open-drawer":        "Basic commands:",
		"reset":              "Basic commands:",
		"set-product":        "Product commands:",
		"keyboard-emul":      "Keyboard commands:",
		"keyboard-lock":      "Keyboard commands:",
		"get-keyboard":       "Keyboard commands:",
		"get-channels":       "Channel commands:",
		"get-current-channel": "Channel commands:",
		"select-channel":     "Channel commands:",
		"get-channel-props":  "Channel commands:",
		"restart-channel":    "Channel commands:",
		"get-comm":           "Communication commands:",
		"set-comm":           "Communication commands:",
		"change-password":    "Communication commands:",
		"get-adc":            "Calibration commands:",
		"start-calibration":  "Calibration commands:",
		"get-cal-state":      "Calibration commands:",
		"abort-calibration":  "Calibration commands:",
		"get-device-type":    "Device info:",
		"weight":             "Basic commands:",
		"wait-weight":        "Basic commands:",
	}

	for _, group := range []string{"Basic commands:", "Product commands:", "Keyboard commands:",
		"Channel commands:", "Communication commands:", "Calibration commands:", "Device info:"} {
		fmt.Printf("\n%s\n", group)
		for name, def := range commands {
			if cmdGroups[name] == group {
				fmt.Printf("  %-20s - %s\n", name, def.Description)
			}
		}
	}
	fmt.Println()
}

func executeCommand() {
	// Configure logging
	logConfig := pos2scale.LogConfig{
		Filename:   *logFile,
		MaxSize:    10,
		MaxBackups: 7,
		MaxAge:     28,
		Compress:   true,
		Level:      pos2scale.ParseLogLevel(*logLevel),
	}

	// Create weight scale instance with logger
	scale, err := pos2scale.NewWeightScale(*portName, *baudRate, logConfig)
	if err != nil {
		log.Fatalf("Failed to connect: %v", err)
	}
	defer scale.Close()

	// Set password
	scale.SetPassword(*password)

	// Parse command arguments
	args := flag.Args()

	if *verbose {
		fmt.Printf("Connected to %s at %d baud\n", *portName, *baudRate)
		fmt.Printf("Log level: %s, Log file: %s\n", *logLevel, *logFile)
		fmt.Printf("Command: %s\n", *command)
		fmt.Printf("Args: %v\n", args)
		fmt.Println()
	}

	// Execute command
	cmdDef, exists := commands[*command]
	if !exists {
		log.Fatalf("Unknown command: %s", *command)
	}

	if err := cmdDef.Handler(scale, args); err != nil {
		log.Fatalf("Command failed: %v", err)
	}
}

// Command handlers

func cmdGetState(scale *pos2scale.WeightScale, args []string) error {
	state, err := scale.GetWeightState()
	if err != nil {
		return err
	}

	fmt.Printf("RawState: 0x%04X\n", state.RawState)
	fmt.Printf("Weight (grams): %d\n", state.WeightGrams)
	fmt.Printf("RawTare: %d\n", state.RawTare)
	fmt.Printf("RawFlags: 0x%02X\n", state.RawFlags)
	fmt.Printf("  WeightFixed (Zero): %v\n", state.WeightFixed)
	fmt.Printf("  Stable: %v\n", state.Stable)
	fmt.Printf("  Overload: %v\n", state.Overload)
	fmt.Printf("  Underload: %v\n", state.Underload)
	fmt.Printf("  AutoZero: %v\n", state.AutoZero)
	fmt.Printf("  ChannelEnabled: %v\n", state.ChannelEnabled)
	fmt.Printf("  TareSet: %v\n", state.TareSet)
	fmt.Printf("  AutoZeroError: %v\n", state.AutoZeroError)
	fmt.Printf("  MeasureError: %v\n", state.MeasureError)
	fmt.Printf("  NoResponseADC: %v\n", state.NoResponseADC)

	return nil
}

func cmdWeight(scale *pos2scale.WeightScale, args []string) error {
	weight, err := scale.GetWeight()
	if err != nil {
		return err
	}

	fmt.Printf("%.3f\n", weight)
	return nil
}

func cmdWaitWeight(scale *pos2scale.WeightScale, args []string) error {
	waitTimeout := 5 * time.Second
	if len(args) > 0 {
		if secs, err := strconv.Atoi(args[0]); err == nil {
			waitTimeout = time.Duration(secs) * time.Second
		}
	}

	if err := scale.WaitForStableWeight(waitTimeout); err != nil {
		return err
	}

	weight, err := scale.GetWeight()
	if err != nil {
		return err
	}

	fmt.Printf("%.3f\n", weight)
	return nil
}

func cmdGetMode(scale *pos2scale.WeightScale, args []string) error {
	mode, err := scale.GetCurrentMode()
	if err != nil {
		return err
	}

	modeName := "normal"
	if mode == 1 {
		modeName = "calibration"
	}

	fmt.Printf("Mode: %d (%s)\n", mode, modeName)
	return nil
}

func cmdSwitchMode(scale *pos2scale.WeightScale, args []string) error {
	if len(args) < 1 {
		return fmt.Errorf("mode required (0=normal,1=calibration)")
	}

	mode, err := strconv.ParseUint(args[0], 10, 8)
	if err != nil {
		return fmt.Errorf("invalid mode: %v", err)
	}

	return scale.SwitchMode(byte(mode))
}

func cmdGetChannelState(scale *pos2scale.WeightScale, args []string) error {
	state, err := scale.GetWeightChannelState()
	if err != nil {
		return err
	}

	fmt.Printf("Weight (raw): %d\n", state.Weight)
	fmt.Printf("Tare: %d\n", state.Tare)
	fmt.Printf("Flags: 0x%02X\n", state.Flags)
	fmt.Printf("ProductType: %d\n", state.ProductType)
	fmt.Printf("Quantity: %d\n", state.Quantity)
	fmt.Printf("Price: %d\n", state.Price)
	fmt.Printf("Cost: %d\n", state.Cost)
	fmt.Printf("LastKey: 0x%02X\n", state.LastKey)

	return nil
}

func cmdSetZero(scale *pos2scale.WeightScale, args []string) error {
	return scale.SetZero()
}

func cmdSetTare(scale *pos2scale.WeightScale, args []string) error {
	return scale.SetTare()
}

func cmdAssignTare(scale *pos2scale.WeightScale, args []string) error {
	if len(args) < 1 {
		return fmt.Errorf("tare value required")
	}

	tare, err := strconv.ParseUint(args[0], 10, 16)
	if err != nil {
		return fmt.Errorf("invalid tare: %v", err)
	}

	return scale.AssignTare(uint16(tare))
}

func cmdOpenDrawer(scale *pos2scale.WeightScale, args []string) error {
	return scale.OpenCashDrawer()
}

func cmdReset(scale *pos2scale.WeightScale, args []string) error {
	return scale.Reset()
}

func cmdSetProduct(scale *pos2scale.WeightScale, args []string) error {
	if len(args) < 3 {
		return fmt.Errorf("type, quantity and price required")
	}

	prodType, err := strconv.ParseUint(args[0], 10, 8)
	if err != nil {
		return fmt.Errorf("invalid type: %v", err)
	}

	qty, err := strconv.ParseUint(args[1], 10, 8)
	if err != nil {
		return fmt.Errorf("invalid quantity: %v", err)
	}

	price, err := strconv.ParseUint(args[2], 10, 24)
	if err != nil {
		return fmt.Errorf("invalid price: %v", err)
	}

	return scale.SetProductType(byte(prodType), byte(qty), uint32(price))
}

func cmdKeyboardEmul(scale *pos2scale.WeightScale, args []string) error {
	if len(args) < 1 {
		return fmt.Errorf("key code required")
	}

	code, err := strconv.ParseUint(args[0], 0, 8)
	if err != nil {
		return fmt.Errorf("invalid key code: %v", err)
	}

	longPress := false
	if len(args) > 1 && args[1] == "long" {
		longPress = true
	}

	return scale.EmulateKeypress(byte(code), longPress)
}

func cmdKeyboardLock(scale *pos2scale.WeightScale, args []string) error {
	if len(args) < 1 {
		return fmt.Errorf("value required (0=unlock,1=lock)")
	}

	val, err := strconv.ParseUint(args[0], 10, 8)
	if err != nil {
		return fmt.Errorf("invalid value: %v", err)
	}

	return scale.LockKeyboard(val == 1)
}

func cmdGetKeyboard(scale *pos2scale.WeightScale, args []string) error {
	state, err := scale.GetKeyboardState()
	if err != nil {
		return err
	}

	fmt.Printf("Keyboard state: 0x%02X\n", state)
	return nil
}

func cmdGetChannels(scale *pos2scale.WeightScale, args []string) error {
	count, err := scale.GetChannelsCount()
	if err != nil {
		return err
	}

	fmt.Printf("Channels count: %d\n", count)
	return nil
}

func cmdGetCurrentChannel(scale *pos2scale.WeightScale, args []string) error {
	channel, err := scale.GetCurrentChannel()
	if err != nil {
		return err
	}

	fmt.Printf("Current channel: %d\n", channel)
	return nil
}

func cmdSelectChannel(scale *pos2scale.WeightScale, args []string) error {
	if len(args) < 1 {
		return fmt.Errorf("channel number required")
	}

	channel, err := strconv.ParseUint(args[0], 10, 8)
	if err != nil {
		return fmt.Errorf("invalid channel: %v", err)
	}

	return scale.SelectWeightChannel(byte(channel))
}

func cmdGetChannelProps(scale *pos2scale.WeightScale, args []string) error {
	if len(args) < 1 {
		return fmt.Errorf("channel number required")
	}

	channel, err := strconv.ParseUint(args[0], 10, 8)
	if err != nil {
		return fmt.Errorf("invalid channel: %v", err)
	}

	props, err := scale.GetChannelProperties(byte(channel))
	if err != nil {
		return err
	}

	fmt.Printf("Channel %d properties:\n", channel)
	fmt.Printf("  Flags: 0x%04X\n", props.Flags)
	fmt.Printf("  Degree: %d\n", props.Degree)
	fmt.Printf("  Max capacity (НПВ): %d\n", props.MaxCapacity)
	fmt.Printf("  Min capacity (НмПВ): %d\n", props.MinCapacity)
	fmt.Printf("  Tare range (ТАРА): %d\n", props.TareRange)
	fmt.Printf("  Range1: %d\n", props.Range1)
	fmt.Printf("  Range2: %d\n", props.Range2)
	fmt.Printf("  Range3: %d\n", props.Range3)
	fmt.Printf("  Discreteness1: %d\n", props.Discreteness1)
	fmt.Printf("  Discreteness2: %d\n", props.Discreteness2)
	fmt.Printf("  Discreteness3: %d\n", props.Discreteness3)
	fmt.Printf("  Discreteness4: %d\n", props.Discreteness4)
	fmt.Printf("  Calibration points: %d\n", props.CalibrationPointsCount)
	fmt.Printf("  Type: ")
	if props.IsLoadCell {
		fmt.Printf("load cell\n")
	} else if props.IsVibrationFrequency {
		fmt.Printf("vibration frequency\n")
	} else if props.IsAbstract {
		fmt.Printf("abstract\n")
	} else {
		fmt.Printf("unknown\n")
	}

	return nil
}

func cmdRestartChannel(scale *pos2scale.WeightScale, args []string) error {
	return fmt.Errorf("not yet implemented")
}

func cmdGetComm(scale *pos2scale.WeightScale, args []string) error {
	return fmt.Errorf("not yet implemented")
}

func cmdSetComm(scale *pos2scale.WeightScale, args []string) error {
	return fmt.Errorf("not yet implemented")
}

func cmdChangePassword(scale *pos2scale.WeightScale, args []string) error {
	if len(args) < 1 {
		return fmt.Errorf("new password required (4 digits)")
	}

	if len(args[0]) != 4 {
		return fmt.Errorf("password must be 4 digits")
	}

	scale.SetPassword(args[0])
	fmt.Printf("Password changed to %s (note: may need to be sent to device)\n", args[0])

	return nil
}

func cmdGetADC(scale *pos2scale.WeightScale, args []string) error {
	adc, err := scale.GetADCReading()
	if err != nil {
		return err
	}

	fmt.Printf("ADC reading: %d (0x%08X)\n", adc, adc)
	return nil
}

func cmdStartCalibration(scale *pos2scale.WeightScale, args []string) error {
	return fmt.Errorf("not yet implemented")
}

func cmdGetCalState(scale *pos2scale.WeightScale, args []string) error {
	return fmt.Errorf("not yet implemented")
}

func cmdAbortCalibration(scale *pos2scale.WeightScale, args []string) error {
	return fmt.Errorf("not yet implemented")
}

func cmdGetDeviceType(scale *pos2scale.WeightScale, args []string) error {
	device, err := scale.GetDeviceType()
	if err != nil {
		return err
	}

	fmt.Printf("Device type: %d\n", device.Type)
	fmt.Printf("Subtype: %d\n", device.Subtype)
	fmt.Printf("Protocol version: %d.%d\n", device.ProtocolVersion, device.ProtocolSubversion)
	fmt.Printf("Model: %d\n", device.Model)
	fmt.Printf("Language: %d\n", device.Language)
	if device.Name != "" {
		fmt.Printf("Name: %s\n", device.Name)
	}

	return nil
}

// Interactive mode
func runInteractive() {
	fmt.Println("Weight Scale CLI - Interactive Mode")
	fmt.Println("Type 'help' for commands, 'exit' to quit")
	fmt.Println()

	// Configure logging
	logConfig := pos2scale.LogConfig{
		Filename:   *logFile,
		MaxSize:    10,
		MaxBackups: 7,
		MaxAge:     28,
		Compress:   true,
		Level:      pos2scale.ParseLogLevel(*logLevel),
	}

	scale, err := pos2scale.NewWeightScale(*portName, *baudRate, logConfig)
	if err != nil {
		log.Fatalf("Failed to connect: %v", err)
	}
	defer scale.Close()

	scale.SetPassword(*password)

	fmt.Printf("Connected to %s at %d baud\n", *portName, *baudRate)
	fmt.Printf("Log level: %s, Log file: %s\n", *logLevel, *logFile)
	fmt.Println()

	for {
		fmt.Print("> ")
		var input string
		fmt.Scanln(&input)

		input = strings.TrimSpace(input)
		if input == "" {
			continue
		}

		if input == "exit" || input == "quit" {
			break
		}

		if input == "help" {
			listAllCommands()
			continue
		}

		parts := strings.Fields(input)
		cmdName := parts[0]

		cmdDef, exists := commands[cmdName]
		if !exists {
			fmt.Printf("Unknown command: %s\n", cmdName)
			continue
		}

		if err := cmdDef.Handler(scale, parts[1:]); err != nil {
			fmt.Printf("Error: %v\n", err)
		}
		fmt.Println()
	}
}