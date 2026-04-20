// Package pos2scale implements communication protocol for POS2 weight modules
// Protocol: POS2 v1.3 (RS-232C interface)
package pos2scale

import (
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"time"

	"go.bug.st/serial"
)

// Protocol constants
const (
	STX = 0x02
	ACK = 0x06
	NAK = 0x15
	ENQ = 0x05
)

// Command codes
const (
	CmdSwitchMode              = 0x07
	CmdKeyboardEmulation       = 0x08
	CmdKeyboardLock            = 0x09
	CmdGetState                = 0x11
	CmdGetCurrentMode          = 0x12
	CmdSetCommunication        = 0x14
	CmdGetCommunication        = 0x15
	CmdChangeAdminPassword     = 0x16
	CmdOpenCashDrawer          = 0x28
	CmdSetZero                 = 0x30
	CmdSetTare                 = 0x31
	CmdAssignTare              = 0x32
	CmdSetProductType          = 0x33
	CmdGetWeightChannelState   = 0x3A
	CmdGetWeightChannelStateEx = 0x3B
	CmdWriteCalibrationPoint   = 0x70
	CmdReadCalibrationPoint    = 0x71
	CmdStartCalibration        = 0x72
	CmdGetCalibrationState     = 0x73
	CmdAbortCalibration        = 0x74
	CmdGetADCReading           = 0x75
	CmdGetKeyboardState        = 0x90
	CmdGetChannelsCount        = 0xE5
	CmdSelectWeightChannel     = 0xE6
	CmdSetChannelEnabled       = 0xE7
	CmdGetChannelProperties    = 0xE8
	CmdSetChannelProperties    = 0xE9
	CmdGetCurrentChannel       = 0xEA
	CmdRestartChannel          = 0xEF
	CmdReset                   = 0xF0
	CmdGetDeviceType           = 0xFC
)

// Command names
var commandNames = map[byte]string{
	CmdSwitchMode:              "SwitchMode",
	CmdKeyboardEmulation:       "KeyboardEmulation",
	CmdKeyboardLock:            "KeyboardLock",
	CmdGetState:                "GetState",
	CmdGetCurrentMode:          "GetCurrentMode",
	CmdSetCommunication:        "SetCommunication",
	CmdGetCommunication:        "GetCommunication",
	CmdChangeAdminPassword:     "ChangeAdminPassword",
	CmdOpenCashDrawer:          "OpenCashDrawer",
	CmdSetZero:                 "SetZero",
	CmdSetTare:                 "SetTare",
	CmdAssignTare:              "AssignTare",
	CmdSetProductType:          "SetProductType",
	CmdGetWeightChannelState:   "GetWeightChannelState",
	CmdGetWeightChannelStateEx: "GetWeightChannelStateEx",
	CmdWriteCalibrationPoint:   "WriteCalibrationPoint",
	CmdReadCalibrationPoint:    "ReadCalibrationPoint",
	CmdStartCalibration:        "StartCalibration",
	CmdGetCalibrationState:     "GetCalibrationState",
	CmdAbortCalibration:        "AbortCalibration",
	CmdGetADCReading:           "GetADCReading",
	CmdGetKeyboardState:        "GetKeyboardState",
	CmdGetChannelsCount:        "GetChannelsCount",
	CmdSelectWeightChannel:     "SelectWeightChannel",
	CmdSetChannelEnabled:       "SetChannelEnabled",
	CmdGetChannelProperties:    "GetChannelProperties",
	CmdSetChannelProperties:    "SetChannelProperties",
	CmdGetCurrentChannel:       "GetCurrentChannel",
	CmdRestartChannel:          "RestartChannel",
	CmdReset:                   "Reset",
	CmdGetDeviceType:           "GetDeviceType",
}

// getCommandName returns command name by code
func getCommandName(cmd byte) string {
	if name, ok := commandNames[cmd]; ok {
		return name
	}
	return fmt.Sprintf("Unknown(0x%02X)", cmd)
}

// Response codes (some are > 255, stored as uint16)
const (
	RespOK                     = 0x00
	ErrInvalidTare             = 0x17
	ErrUnknownCommand          = 0x0120
	ErrInvalidDataLength       = 0x0121
	ErrInvalidPassword         = 0x0122
	ErrCommandNotImplemented   = 0x0123
	ErrInvalidParameter        = 0x0124
	ErrZeroSettingFailed       = 0x0150
	ErrTareSettingFailed       = 0x0151
	ErrWeightNotStable         = 0x0152
	ErrNVMemoryFailure         = 0x0166
	ErrCommandNotSupported     = 0x0167
	ErrPasswordLimitExceeded   = 0x0170
	ErrCalibrationBlocked      = 0x0180
	ErrKeyboardLocked          = 0x0181
	ErrCannotChangeChannelType = 0x0182
	ErrCannotDisableChannel    = 0x0183
	ErrChannelInvalidAction    = 0x0184
	ErrInvalidChannelNumber    = 0x0185
	ErrNoResponseFromADC       = 0x0186
)

// Mode constants
const (
	ModeNormal      = 0x00
	ModeCalibration = 0x01
)

// Product types
const (
	ProductTypeWeight = 0x00
	ProductTypePiece  = 0x01
)

// RawWeightState represents raw data from device
type RawWeightState struct {
	CommandCode byte
	ErrorCode   byte
	State       uint16
	Weight      int32
	Tare        uint16
	Flags       byte
}

// WeightState represents parsed weight module state
type WeightState struct {
	RawState       uint16
	RawWeight      int32
	RawTare        uint16
	RawFlags       byte
	WeightGrams    int32
	WeightFixed    bool // бит 0 - признак фиксации веса (вес = 0)
	AutoZero       bool // бит 1 - признак работы автонуля
	ChannelEnabled bool // бит 2 - канал включен
	TareSet        bool // бит 3 - признак тары
	Stable         bool // бит 4 - признак успокоения веса
	AutoZeroError  bool // бит 5 - ошибка автонуля при включении
	Overload       bool // бит 6 - перегрузка по весу
	MeasureError   bool // бит 7 - ошибка при получении измерения
	Underload      bool // бит 8 - весы недогружены
	NoResponseADC  bool // бит 9 - нет ответа от АЦП
}

// WeightChannelState represents weight channel state (command 3Ah)
type WeightChannelState struct {
	State       uint16
	Weight      int32
	Tare        uint16
	Flags       byte
	ProductType byte
	Quantity    byte
	Price       uint32
	Cost        uint32
	LastKey     byte
}

// WeightChannelProperties represents weight channel characteristics
type WeightChannelProperties struct {
	Flags                  uint16
	DecimalPointPosition   uint8
	Degree                 int8
	MaxCapacity            uint16
	MinCapacity            uint16
	TareRange              uint16
	Range1                 uint16
	Range2                 uint16
	Range3                 uint16
	Discreteness1          uint8
	Discreteness2          uint8
	Discreteness3          uint8
	Discreteness4          uint8
	CalibrationPointsCount uint8
	IsLoadCell             bool
	IsVibrationFrequency   bool
	IsAbstract             bool
}

// DeviceType represents device type information
type DeviceType struct {
	Type               uint8
	Subtype            uint8
	ProtocolVersion    uint8
	ProtocolSubversion uint8
	Model              uint8
	Language           uint8
	Name               string
}

// WeightScale represents connection to weight module
type WeightScale struct {
	port     serial.Port
	password []byte
	timeout  time.Duration
	baudRate int
	channel  uint8
	logger   *slog.Logger
}

// NewWeightScale creates new weight scale connection
func NewWeightScale(portName string, baud int, logConfig ...LogConfig) (*WeightScale, error) {
	// Setup logger
	var logger *slog.Logger
	var err error

	if len(logConfig) > 0 {
		logger, err = InitLogger(logConfig[0])
	} else {
		logger, err = InitLogger(DefaultLogConfig())
	}

	if err != nil {
		logger = NewNullLogger()
	}

	// Выводим версию модуля и информацию об ОС
	PrintVersion(logger)

	mode := &serial.Mode{
		BaudRate: baud,
		DataBits: 8,
		StopBits: serial.OneStopBit,
		Parity:   serial.NoParity,
	}

	port, err := serial.Open(portName, mode)
	if err != nil {
		logger.Error("failed to open port", "port", portName, "error", err)
		return nil, fmt.Errorf("failed to open port: %w", err)
	}

	ws := &WeightScale{
		port:     port,
		password: []byte("0000"),
		timeout:  100 * time.Millisecond,
		baudRate: baud,
		channel:  0,
		logger:   logger,
	}

	ws.logger.Info("connected", "port", portName, "baud", baud)
	return ws, nil
}

// Close closes the connection
func (ws *WeightScale) Close() error {
	ws.logger.Info("closing connection")
	return ws.port.Close()
}

// SetPassword sets admin password
func (ws *WeightScale) SetPassword(password string) {
	if len(password) != 4 {
		return
	}
	ws.password = []byte(password)
	ws.logger.Debug("password set")
}

// GetLogger returns the logger instance
func (ws *WeightScale) GetLogger() *slog.Logger {
	return ws.logger
}

// calculateLRC computes XOR checksum of data bytes
func calculateLRC(data []byte) byte {
	var lrc byte = 0
	for _, b := range data {
		lrc ^= b
	}
	return lrc
}

// sendENQ sends ENQ and returns response (ACK or NAK)
func (ws *WeightScale) sendENQ() (byte, error) {
	// Send ENQ
	enq := []byte{ENQ}
	ws.logger.Debug("-> " + fmt.Sprintf("% X", enq))

	if _, err := ws.port.Write(enq); err != nil {
		return 0, fmt.Errorf("failed to send ENQ: %w", err)
	}

	// Read response
	response := make([]byte, 1)
	if err := ws.port.SetReadTimeout(ws.timeout); err != nil {
		return 0, err
	}

	n, err := ws.port.Read(response)
	if err != nil || n == 0 {
		return 0, errors.New("no response to ENQ")
	}

	ws.logger.Debug("<- " + fmt.Sprintf("% X", response[:n]))

	return response[0], nil
}

// writeCommand sends command and waits for ACK/NAK
func (ws *WeightScale) writeCommand(message []byte) error {
	maxAttempts := 3
	
	for attempt := 0; attempt < maxAttempts; attempt++ {
		// Send message
		ws.logger.Debug("-> " + fmt.Sprintf("% X", message))
		
		if _, err := ws.port.Write(message); err != nil {
			ws.logger.Error("failed to write", "error", err)
			continue
		}

		// Wait for ACK/NAK
		response := make([]byte, 1)
		if err := ws.port.SetReadTimeout(ws.timeout); err != nil {
			continue
		}

		n, err := ws.port.Read(response)
		if err != nil || n == 0 {
			ws.logger.Debug("no response, attempt %d/%d", attempt+1, maxAttempts)
			continue
		}

		ws.logger.Debug("<- " + fmt.Sprintf("% X", response[:n]))

		if response[0] == ACK {
			return nil
		} else if response[0] == NAK {
			ws.logger.Debug("device returned NAK, retrying...")
			continue
		}
	}

	return errors.New("failed to send command after max attempts")
}

// readResponse reads response message from device and verifies CRC
func (ws *WeightScale) readResponse() ([]byte, error) {
	// Read STX
	stx := make([]byte, 1)
	if _, err := ws.port.Read(stx); err != nil {
		ws.logger.Error("failed to read STX", "error", err)
		return nil, fmt.Errorf("failed to read STX: %w", err)
	}

	ws.logger.Debug("<- " + fmt.Sprintf("% X", stx))

	if stx[0] != STX {
		// Send NAK on invalid STX
		ws.port.Write([]byte{NAK})
		return nil, fmt.Errorf("invalid STX: %02x", stx[0])
	}

	// Read length
	lenBuf := make([]byte, 1)
	if _, err := ws.port.Read(lenBuf); err != nil {
		ws.logger.Error("failed to read length", "error", err)
		ws.port.Write([]byte{NAK})
		return nil, fmt.Errorf("failed to read length: %w", err)
	}
	length := lenBuf[0]

	ws.logger.Debug("<- " + fmt.Sprintf("% X", lenBuf))

	// Read data + LRC
	dataBuf := make([]byte, length+1)
	if _, err := io.ReadFull(ws.port, dataBuf); err != nil {
		ws.logger.Error("failed to read data", "error", err)
		ws.port.Write([]byte{NAK})
		return nil, fmt.Errorf("failed to read data: %w", err)
	}

	// Log data
	if length > 0 {
		ws.logger.Debug("<- " + fmt.Sprintf("% X", dataBuf[:length]))
	}
	ws.logger.Debug("<- " + fmt.Sprintf("% X", dataBuf[length:]))

	// Verify LRC
	expectedLRC := calculateLRC(append([]byte{length}, dataBuf[:length]...))
	receivedLRC := dataBuf[length]

	if expectedLRC != receivedLRC {
		ws.logger.Error("LRC mismatch", "expected", fmt.Sprintf("%02X", expectedLRC), "got", fmt.Sprintf("%02X", receivedLRC))
		// Send NAK on CRC error
		ws.port.Write([]byte{NAK})
		return nil, errors.New("LRC mismatch")
	}

	// Send ACK on successful read
	ack := []byte{ACK}
	ws.logger.Debug("-> " + fmt.Sprintf("% X", ack))
	if _, err := ws.port.Write(ack); err != nil {
		return nil, err
	}

	return dataBuf[:length], nil
}

// sendCommand sends a command and returns response
func (ws *WeightScale) sendCommand(cmd byte, params []byte) ([]byte, error) {
	cmdName := getCommandName(cmd)
	ws.logger.Debug(cmdName)

	// Step 1: Send ENQ and get response
	enqResp, err := ws.sendENQ()
	if err != nil {
		ws.logger.Error("ENQ failed", "error", err)
		return nil, err
	}

	// Step 2: If device returns ACK, it's preparing response
	if enqResp == ACK {
		ws.logger.Debug("device preparing response, waiting...")
		// Wait for device to prepare response
		time.Sleep(50 * time.Millisecond)
		
		// Send ENQ again
		enqResp, err = ws.sendENQ()
		if err != nil {
			ws.logger.Error("second ENQ failed", "error", err)
			return nil, err
		}
	}

	// Step 3: If device returns NAK, it's ready to receive command
	if enqResp == NAK {
		ws.logger.Debug("device ready, sending command")
		
		// Build message
		data := []byte{cmd}
		data = append(data, params...)

		length := byte(len(data))

		message := []byte{STX, length}
		message = append(message, data...)

		lrc := calculateLRC(message[1:]) // exclude STX
		message = append(message, lrc)

		// Send command and wait for ACK
		if err := ws.writeCommand(message); err != nil {
			ws.logger.Error("failed to send command", "error", err)
			return nil, err
		}

		// Read response
		return ws.readResponse()
	}

	// Step 4: Unexpected response
	ws.logger.Error("unexpected ENQ response", "response", fmt.Sprintf("%02X", enqResp))
	return nil, fmt.Errorf("unexpected ENQ response: %02X", enqResp)
}

// sendSimpleCommand sends command without parameters
func (ws *WeightScale) sendSimpleCommand(cmd byte) error {
	_, err := ws.sendCommand(cmd, nil)
	return err
}

// sendCommandWithPassword sends command with admin password
func (ws *WeightScale) sendCommandWithPassword(cmd byte, additionalParams []byte) ([]byte, error) {
	params := append([]byte{}, ws.password...)
	params = append(params, additionalParams...)
	return ws.sendCommand(cmd, params)
}

// getRawState reads raw weight state from device
func (ws *WeightScale) getRawState() (*RawWeightState, error) {
	params := []byte{ws.password[0], ws.password[1], ws.password[2], ws.password[3]}
	response, err := ws.sendCommand(CmdGetState, params)
	if err != nil {
		return nil, err
	}

	if len(response) < 8 {
		return nil, errors.New("response too short")
	}

	raw := &RawWeightState{
		CommandCode: response[0],
		ErrorCode:   response[1],
		State:       binary.LittleEndian.Uint16(response[2:4]),
		Weight:      int32(binary.LittleEndian.Uint32(response[4:8])),
	}

	if len(response) >= 10 {
		raw.Tare = binary.LittleEndian.Uint16(response[8:10])
	}

	if len(response) >= 11 {
		raw.Flags = response[10]
	}

	ws.logger.Debug("state", "state", fmt.Sprintf("0x%04X", raw.State), "weight_g", raw.Weight, "tare", raw.Tare)

	return raw, nil
}

// ParseWeightState parses raw state into WeightState
func ParseWeightState(raw *RawWeightState) *WeightState {
	if raw == nil {
		return nil
	}

	state := &WeightState{
		RawState:    raw.State,
		RawWeight:   raw.Weight,
		RawTare:     raw.Tare,
		RawFlags:    raw.Flags,
		WeightGrams: raw.Weight,
	}

	// Parse flags (2 bytes)
	flagsLow := byte(raw.State & 0xFF)
	flagsHigh := byte((raw.State >> 8) & 0xFF)

	state.WeightFixed = (flagsLow & 0x01) != 0
	state.AutoZero = (flagsLow & 0x02) != 0
	state.ChannelEnabled = (flagsLow & 0x04) != 0
	state.TareSet = (flagsLow & 0x08) != 0
	state.Stable = (flagsLow & 0x10) != 0
	state.AutoZeroError = (flagsLow & 0x20) != 0
	state.Overload = (flagsLow & 0x40) != 0
	state.MeasureError = (flagsLow & 0x80) != 0
	state.Underload = (flagsHigh & 0x01) != 0
	state.NoResponseADC = (flagsHigh & 0x02) != 0

	return state
}

// GetWeightState reads and parses weight state
func (ws *WeightScale) GetWeightState() (*WeightState, error) {
	raw, err := ws.getRawState()
	if err != nil {
		return nil, err
	}

	if raw.CommandCode != CmdGetState {
		return nil, fmt.Errorf("unexpected command response: %02x", raw.CommandCode)
	}

	if raw.ErrorCode != RespOK {
		return nil, ws.getErrorByCode(raw.ErrorCode)
	}

	return ParseWeightState(raw), nil
}

// GetWeight returns weight in kilograms
func (ws *WeightScale) GetWeight() (float64, error) {
	state, err := ws.GetWeightState()
	if err != nil {
		return 0, err
	}

	weightKg := float64(state.WeightGrams) / 1000.0
	ws.logger.Debug("weight", "kg", weightKg, "g", state.WeightGrams)

	return weightKg, nil
}

// GetWeightGrams returns weight in grams
func (ws *WeightScale) GetWeightGrams() (int32, error) {
	state, err := ws.GetWeightState()
	if err != nil {
		return 0, err
	}
	return state.WeightGrams, nil
}

// GetWeightChannelState gets weight channel state (command 3Ah)
func (ws *WeightScale) GetWeightChannelState() (*WeightChannelState, error) {
	params := []byte{ws.password[0], ws.password[1], ws.password[2], ws.password[3]}
	response, err := ws.sendCommand(CmdGetWeightChannelState, params)
	if err != nil {
		return nil, err
	}

	if len(response) < 2 {
		return nil, errors.New("response too short")
	}

	cmdCode := response[0]
	errorCode := response[1]

	if cmdCode != CmdGetWeightChannelState {
		return nil, fmt.Errorf("unexpected command response: %02x", cmdCode)
	}

	if errorCode != RespOK {
		return nil, ws.getErrorByCode(errorCode)
	}

	return parseChannelState(response)
}

// parseChannelState parses channel state response
func parseChannelState(response []byte) (*WeightChannelState, error) {
	if len(response) < 11 {
		return nil, errors.New("response too short")
	}

	state := &WeightChannelState{
		State:  binary.LittleEndian.Uint16(response[2:4]),
		Weight: int32(binary.LittleEndian.Uint32(response[4:8])),
		Tare:   binary.LittleEndian.Uint16(response[8:10]),
		Flags:  response[10],
	}

	if len(response) >= 15 {
		state.ProductType = response[11]
		state.Quantity = response[12]
		state.Price = binary.LittleEndian.Uint32(response[13:16])
	}

	if len(response) >= 19 {
		state.Cost = binary.LittleEndian.Uint32(response[16:20])
	}

	if len(response) >= 20 {
		state.LastKey = response[20]
	}

	return state, nil
}

// GetCurrentMode gets current device mode
func (ws *WeightScale) GetCurrentMode() (byte, error) {
	response, err := ws.sendCommand(CmdGetCurrentMode, nil)
	if err != nil {
		return 0, err
	}

	if len(response) < 3 {
		return 0, errors.New("response too short")
	}

	if response[1] != RespOK {
		return 0, ws.getErrorByCode(response[1])
	}

	mode := response[2]
	modeName := "normal"
	if mode == 1 {
		modeName = "calibration"
	}
	ws.logger.Debug("mode", "mode", mode, "name", modeName)

	return mode, nil
}

// SwitchMode switches device mode (normal/calibration)
func (ws *WeightScale) SwitchMode(mode byte) error {
	modeName := "normal"
	if mode == 1 {
		modeName = "calibration"
	}
	ws.logger.Info("switch mode", "mode", modeName)
	params := []byte{mode}
	_, err := ws.sendCommandWithPassword(CmdSwitchMode, params)
	return err
}

// OpenCashDrawer opens the cash drawer
func (ws *WeightScale) OpenCashDrawer() error {
	_, err := ws.sendCommandWithPassword(CmdOpenCashDrawer, nil)
	return err
}

// SetZero sets current weight as zero
func (ws *WeightScale) SetZero() error {
	_, err := ws.sendCommandWithPassword(CmdSetZero, nil)
	return err
}

// SetTare sets current weight as tare
func (ws *WeightScale) SetTare() error {
	_, err := ws.sendCommandWithPassword(CmdSetTare, nil)
	return err
}

// AssignTare sets specific tare value
func (ws *WeightScale) AssignTare(tareWeight uint16) error {
	tareBytes := make([]byte, 2)
	binary.LittleEndian.PutUint16(tareBytes, tareWeight)
	_, err := ws.sendCommandWithPassword(CmdAssignTare, tareBytes)
	return err
}

// EmulateKeypress emulates key press on scale keyboard
func (ws *WeightScale) EmulateKeypress(keyCode byte, longPress bool) error {
	if longPress {
		keyCode |= 0x80
	}
	params := []byte{keyCode}
	_, err := ws.sendCommandWithPassword(CmdKeyboardEmulation, params)
	return err
}

// LockKeyboard locks physical keyboard on scale
func (ws *WeightScale) LockKeyboard(locked bool) error {
	var value byte = 0
	if locked {
		value = 1
	}
	params := []byte{value}
	_, err := ws.sendCommandWithPassword(CmdKeyboardLock, params)
	return err
}

// GetChannelsCount gets number of weight channels
func (ws *WeightScale) GetChannelsCount() (uint8, error) {
	response, err := ws.sendCommand(CmdGetChannelsCount, nil)
	if err != nil {
		return 0, err
	}

	if len(response) < 3 {
		return 0, errors.New("response too short")
	}

	if response[1] != RespOK {
		return 0, ws.getErrorByCode(response[1])
	}

	count := response[2]
	ws.logger.Debug("channels count", "count", count)

	return count, nil
}

// SelectWeightChannel selects active weight channel
func (ws *WeightScale) SelectWeightChannel(channel uint8) error {
	params := []byte{channel}
	_, err := ws.sendCommandWithPassword(CmdSelectWeightChannel, params)
	if err == nil {
		ws.channel = channel
	}
	return err
}

// GetCurrentChannel gets current active channel
func (ws *WeightScale) GetCurrentChannel() (uint8, error) {
	response, err := ws.sendCommand(CmdGetCurrentChannel, nil)
	if err != nil {
		return 0, err
	}

	if len(response) < 3 {
		return 0, errors.New("response too short")
	}

	if response[1] != RespOK {
		return 0, ws.getErrorByCode(response[1])
	}

	return response[2], nil
}

// GetChannelProperties reads weight channel properties
func (ws *WeightScale) GetChannelProperties(channel uint8) (*WeightChannelProperties, error) {
	params := []byte{channel}
	response, err := ws.sendCommand(CmdGetChannelProperties, params)
	if err != nil {
		return nil, err
	}

	if len(response) < 2 {
		return nil, errors.New("response too short")
	}

	if response[1] != RespOK {
		return nil, ws.getErrorByCode(response[1])
	}

	if len(response) < 28 {
		return nil, errors.New("response incomplete")
	}

	props := &WeightChannelProperties{
		Flags:                  binary.LittleEndian.Uint16(response[2:4]),
		DecimalPointPosition:   response[4],
		Degree:                 int8(response[5]),
		MaxCapacity:            binary.LittleEndian.Uint16(response[6:8]),
		MinCapacity:            binary.LittleEndian.Uint16(response[8:10]),
		TareRange:              binary.LittleEndian.Uint16(response[10:12]),
		Range1:                 binary.LittleEndian.Uint16(response[12:14]),
		Range2:                 binary.LittleEndian.Uint16(response[14:16]),
		Range3:                 binary.LittleEndian.Uint16(response[16:18]),
		Discreteness1:          response[18],
		Discreteness2:          response[19],
		Discreteness3:          response[20],
		Discreteness4:          response[21],
		CalibrationPointsCount: response[22],
	}

	props.IsLoadCell = (props.Flags & 0x03) == 0x00
	props.IsVibrationFrequency = (props.Flags & 0x03) == 0x01
	props.IsAbstract = (props.Flags & 0x03) == 0x02

	ws.logger.Debug("channel properties", "channel", channel, "max_capacity", props.MaxCapacity, "degree", props.Degree)

	return props, nil
}

// SetProductType sets product type, quantity and price
func (ws *WeightScale) SetProductType(productType byte, quantity byte, price uint32) error {
	priceBytes := make([]byte, 3)
	binary.LittleEndian.PutUint32(append(priceBytes, 0), price)

	params := []byte{productType, quantity}
	params = append(params, priceBytes...)

	_, err := ws.sendCommandWithPassword(CmdSetProductType, params)
	return err
}

// GetDeviceType gets device type information
func (ws *WeightScale) GetDeviceType() (*DeviceType, error) {
	response, err := ws.sendCommand(CmdGetDeviceType, nil)
	if err != nil {
		return nil, err
	}

	if len(response) < 9 {
		return nil, errors.New("response too short")
	}

	if response[1] != RespOK {
		return nil, ws.getErrorByCode(response[1])
	}

	device := &DeviceType{
		Type:               response[2],
		Subtype:            response[3],
		ProtocolVersion:    response[4],
		ProtocolSubversion: response[5],
		Model:              response[6],
		Language:           response[7],
	}

	if len(response) > 8 {
		nameBytes := response[8:]
		device.Name = decodeWin1251(nameBytes)
	}

	ws.logger.Info("device info", "type", device.Type, "subtype", device.Subtype,
		"version", fmt.Sprintf("%d.%d", device.ProtocolVersion, device.ProtocolSubversion),
		"model", device.Model, "name", device.Name)

	return device, nil
}

// Reset performs device reset
func (ws *WeightScale) Reset() error {
	return ws.sendSimpleCommand(CmdReset)
}

// GetKeyboardState gets keyboard state
func (ws *WeightScale) GetKeyboardState() (byte, error) {
	params := []byte{ws.password[0], ws.password[1], ws.password[2], ws.password[3]}
	response, err := ws.sendCommand(CmdGetKeyboardState, params)
	if err != nil {
		return 0, err
	}

	if len(response) < 3 {
		return 0, errors.New("response too short")
	}

	if response[1] != RespOK {
		return 0, ws.getErrorByCode(response[1])
	}

	return response[2], nil
}

// GetADCReading gets ADC reading for current channel
func (ws *WeightScale) GetADCReading() (uint32, error) {
	params := []byte{ws.password[0], ws.password[1], ws.password[2], ws.password[3]}
	response, err := ws.sendCommand(CmdGetADCReading, params)
	if err != nil {
		return 0, err
	}

	if len(response) < 6 {
		return 0, errors.New("response too short")
	}

	if response[1] != RespOK {
		return 0, ws.getErrorByCode(response[1])
	}

	adc := binary.LittleEndian.Uint32(response[2:6])
	ws.logger.Debug("ADC reading", "value", adc)

	return adc, nil
}

// WaitForStableWeight waits until weight becomes stable
func (ws *WeightScale) WaitForStableWeight(timeout time.Duration) error {
	deadline := time.Now().Add(timeout)

	for time.Now().Before(deadline) {
		state, err := ws.GetWeightState()
		if err != nil {
			return err
		}

		if state.Stable {
			ws.logger.Debug("weight is stable")
			return nil
		}

		time.Sleep(100 * time.Millisecond)
	}

	ws.logger.Error("timeout waiting for stable weight")
	return errors.New("timeout waiting for stable weight")
}

// getErrorByCode converts error code to error
func (ws *WeightScale) getErrorByCode(code byte) error {
	switch uint16(code) {
	case RespOK:
		return nil
	case ErrInvalidTare:
		return errors.New("invalid tare value")
	case ErrUnknownCommand:
		return errors.New("unknown command")
	case ErrInvalidDataLength:
		return errors.New("invalid data length")
	case ErrInvalidPassword:
		return errors.New("invalid password")
	case ErrCommandNotImplemented:
		return errors.New("command not implemented in this mode")
	case ErrInvalidParameter:
		return errors.New("invalid parameter value")
	case ErrZeroSettingFailed:
		return errors.New("zero setting failed")
	case ErrTareSettingFailed:
		return errors.New("tare setting failed")
	case ErrWeightNotStable:
		return errors.New("weight not stable")
	case ErrNVMemoryFailure:
		return errors.New("non-volatile memory failure")
	case ErrCommandNotSupported:
		return errors.New("command not supported by interface")
	case ErrPasswordLimitExceeded:
		return errors.New("password attempts limit exceeded")
	case ErrCalibrationBlocked:
		return errors.New("calibration mode blocked by switch")
	case ErrKeyboardLocked:
		return errors.New("keyboard is locked")
	case ErrInvalidChannelNumber:
		return errors.New("invalid channel number")
	case ErrNoResponseFromADC:
		return errors.New("no response from ADC")
	default:
		return fmt.Errorf("unknown error code: %02x", code)
	}
}

func decodeWin1251(data []byte) string {
	result := make([]rune, 0, len(data))
	for _, b := range data {
		if b < 0x80 {
			result = append(result, rune(b))
		} else {
			switch b {
			case 0xC0, 0xC1, 0xC2, 0xC3, 0xC4, 0xC5, 0xC6, 0xC7, 0xC8, 0xC9,
				0xCA, 0xCB, 0xCC, 0xCD, 0xCE, 0xCF, 0xD0, 0xD1, 0xD2, 0xD3,
				0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA, 0xDB, 0xDC, 0xDD,
				0xDE, 0xDF:
				ru := []rune{'А', 'Б', 'В', 'Г', 'Д', 'Е', 'Ж', 'З', 'И', 'Й',
					'К', 'Л', 'М', 'Н', 'О', 'П', 'Р', 'С', 'Т', 'У',
					'Ф', 'Х', 'Ц', 'Ч', 'Ш', 'Щ', 'Ъ', 'Ы', 'Ь', 'Э',
					'Ю', 'Я'}
				idx := int(b) - 0xC0
				if idx >= 0 && idx < len(ru) {
					result = append(result, ru[idx])
				} else {
					result = append(result, '?')
				}
			case 0xE0, 0xE1, 0xE2, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9,
				0xEA, 0xEB, 0xEC, 0xED, 0xEE, 0xEF:
				ru := []rune{'а', 'б', 'в', 'г', 'д', 'е', 'ж', 'з', 'и', 'й',
					'к', 'л', 'м', 'н', 'о', 'п'}
				idx := int(b) - 0xE0
				if idx >= 0 && idx < len(ru) {
					result = append(result, ru[idx])
				} else {
					result = append(result, '?')
				}
			default:
				result = append(result, '?')
			}
		}
	}
	return string(result)
}