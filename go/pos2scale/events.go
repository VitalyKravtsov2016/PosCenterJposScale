// pos2scale/events.go
package pos2scale

import (
	"context"
	"fmt"
	"log/slog"
	"sync"
	"time"
)

// EventType represents type of event
type EventType int

const (
	WeightChangedEvent EventType = iota
	WeightStableEvent
	WeightUnstableEvent
	WeightZeroEvent
	WeightNotZeroEvent
	WeightOverloadEvent
	WeightUnderloadEvent
	TareChangedEvent
	ZeroChangedEvent
	ErrorEvent
	ConnectionLostEvent
	ConnectionRestoredEvent
	KeyboardEvent
	ChannelEnabledEvent
	AutoZeroEvent
)

// String returns string representation of event type
func (e EventType) String() string {
	switch e {
	case WeightChangedEvent:
		return "WeightChanged"
	case WeightStableEvent:
		return "WeightStable"
	case WeightUnstableEvent:
		return "WeightUnstable"
	case WeightZeroEvent:
		return "WeightZero"
	case WeightNotZeroEvent:
		return "WeightNotZero"
	case WeightOverloadEvent:
		return "WeightOverload"
	case WeightUnderloadEvent:
		return "WeightUnderload"
	case TareChangedEvent:
		return "TareChanged"
	case ZeroChangedEvent:
		return "ZeroChanged"
	case ErrorEvent:
		return "Error"
	case ConnectionLostEvent:
		return "ConnectionLost"
	case ConnectionRestoredEvent:
		return "ConnectionRestored"
	case KeyboardEvent:
		return "Keyboard"
	case ChannelEnabledEvent:
		return "ChannelEnabled"
	case AutoZeroEvent:
		return "AutoZero"
	default:
		return "Unknown"
	}
}

// Event represents a weight scale event
type Event struct {
	Type      EventType
	Weight    float64
	WeightRaw int32
	OldWeight float64
	Delta     float64
	Tare      uint16
	KeyCode   byte
	Error     error
	Timestamp time.Time
	Enabled   bool
	AutoZero  bool
}

// EventHandler is a function that handles events
type EventHandler func(Event)

// SubscriptionID is a unique identifier for a subscription
type SubscriptionID uint64

// EventEmitter manages event subscriptions and notifications
type EventEmitter struct {
	handlers map[EventType]map[SubscriptionID]EventHandler
	nextID   SubscriptionID
	mu       sync.RWMutex
	logger   *slog.Logger
}

// NewEventEmitter creates a new event emitter
func NewEventEmitter(logger *slog.Logger) *EventEmitter {
	return &EventEmitter{
		handlers: make(map[EventType]map[SubscriptionID]EventHandler),
		nextID:   1,
		logger:   logger,
	}
}

// On subscribes to an event and returns subscription ID
func (e *EventEmitter) On(eventType EventType, handler EventHandler) SubscriptionID {
	e.mu.Lock()
	defer e.mu.Unlock()

	if e.handlers[eventType] == nil {
		e.handlers[eventType] = make(map[SubscriptionID]EventHandler)
	}

	id := e.nextID
	e.nextID++
	e.handlers[eventType][id] = handler

	if e.logger != nil {
		e.logger.Debug("event subscription added", "event", eventType.String(), "id", id)
	}

	return id
}

// Off unsubscribes from an event
func (e *EventEmitter) Off(eventType EventType, id SubscriptionID) {
	e.mu.Lock()
	defer e.mu.Unlock()

	if handlers, ok := e.handlers[eventType]; ok {
		delete(handlers, id)
		if e.logger != nil {
			e.logger.Debug("event subscription removed", "event", eventType.String(), "id", id)
		}
	}
}

// emit sends an event to all subscribers
func (e *EventEmitter) emit(event Event) {
	e.mu.RLock()
	handlers := make([]EventHandler, 0, len(e.handlers[event.Type]))
	for _, handler := range e.handlers[event.Type] {
		handlers = append(handlers, handler)
	}
	e.mu.RUnlock()

	if len(handlers) > 0 && e.logger != nil {
		e.logger.Debug("emitting event", "event", event.Type.String(), "weight", event.Weight)
	}

	for _, handler := range handlers {
		go handler(event)
	}
}

// WeightScaleState represents current state of the scale
type WeightScaleState struct {
	Weight         float64
	WeightRaw      int32
	Tare           uint16
	Stable         bool
	Zero           bool
	Overload       bool
	Underload      bool
	ConnectionOK   bool
	ChannelEnabled bool
	AutoZero       bool
	TareSet        bool
	LastUpdate     time.Time
}

// WeightScaleWithEvents extends WeightScale with event support
type WeightScaleWithEvents struct {
	*WeightScale
	*EventEmitter

	pollInterval time.Duration
	stopChan     chan struct{}
	running      bool
	mu           sync.Mutex

	state   WeightScaleState
	stateMu sync.RWMutex
}

// NewWeightScaleWithEvents creates a new event-enabled weight scale
func NewWeightScaleWithEvents(portName string, baud int, logConfig ...LogConfig) (*WeightScaleWithEvents, error) {
	scale, err := NewWeightScale(portName, baud, logConfig...)
	if err != nil {
		return nil, err
	}

	ws := &WeightScaleWithEvents{
		WeightScale:  scale,
		EventEmitter: NewEventEmitter(scale.GetLogger()),
		stopChan:     make(chan struct{}),
		pollInterval: 500 * time.Millisecond,
		state: WeightScaleState{
			Stable:         false,
			Zero:           false,
			Overload:       false,
			Underload:      false,
			ConnectionOK:   true,
			ChannelEnabled: true,
			AutoZero:       false,
			TareSet:        false,
		},
	}

	return ws, nil
}

// SetPollInterval sets the polling interval for weight updates
func (ws *WeightScaleWithEvents) SetPollInterval(interval time.Duration) {
	ws.pollInterval = interval
	ws.WeightScale.logger.Debug("poll interval set", "interval", interval)
}

// GetPollInterval returns current polling interval
func (ws *WeightScaleWithEvents) GetPollInterval() time.Duration {
	return ws.pollInterval
}

// StartPolling starts continuous weight monitoring
func (ws *WeightScaleWithEvents) StartPolling() {
	ws.mu.Lock()
	defer ws.mu.Unlock()

	if ws.running {
		return
	}

	ws.running = true
	ws.stopChan = make(chan struct{})
	go ws.pollingLoop()
	ws.WeightScale.logger.Info("polling started", "interval", ws.pollInterval)
}

// StopPolling stops continuous weight monitoring
func (ws *WeightScaleWithEvents) StopPolling() {
	ws.mu.Lock()
	defer ws.mu.Unlock()

	if !ws.running {
		return
	}

	close(ws.stopChan)
	ws.running = false
	ws.WeightScale.logger.Info("polling stopped")
}

// IsPolling returns true if polling is active
func (ws *WeightScaleWithEvents) IsPolling() bool {
	ws.mu.Lock()
	defer ws.mu.Unlock()
	return ws.running
}

// pollingLoop is the main polling goroutine
func (ws *WeightScaleWithEvents) pollingLoop() {
	ticker := time.NewTicker(ws.pollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ws.stopChan:
			return
		case <-ticker.C:
			ws.update()
		}
	}
}

// update reads current state and emits events ONLY on changes
func (ws *WeightScaleWithEvents) update() {
	rawState, err := ws.GetWeightState()

	if err != nil {
		ws.stateMu.Lock()
		wasConnected := ws.state.ConnectionOK
		ws.state.ConnectionOK = false
		ws.stateMu.Unlock()

		if wasConnected {
			ws.WeightScale.logger.Warn("connection lost", "error", err)
			ws.emit(Event{
				Type:      ConnectionLostEvent,
				Error:     err,
				Timestamp: time.Now(),
			})
		}

		ws.emit(Event{
			Type:      ErrorEvent,
			Error:     err,
			Timestamp: time.Now(),
		})
		return
	}

	ws.stateMu.Lock()
	oldState := ws.state
	ws.stateMu.Unlock()

	weightKg := float64(rawState.WeightGrams) / 1000.0

	newState := WeightScaleState{
		Weight:         weightKg,
		WeightRaw:      rawState.WeightGrams,
		Tare:           rawState.RawTare,
		Stable:         rawState.Stable,
		Zero:           rawState.WeightFixed,
		Overload:       rawState.Overload,
		Underload:      rawState.Underload,
		ConnectionOK:   true,
		ChannelEnabled: rawState.ChannelEnabled,
		AutoZero:       rawState.AutoZero,
		TareSet:        rawState.TareSet,
		LastUpdate:     time.Now(),
	}

	// Убрано чтение Mode и Channel
	// newState.Mode, _ = ws.GetCurrentMode()
	// newState.Channel, _ = ws.GetCurrentChannel()

	if !oldState.ConnectionOK && newState.ConnectionOK {
		ws.WeightScale.logger.Info("connection restored")
		ws.emit(Event{
			Type:      ConnectionRestoredEvent,
			Timestamp: time.Now(),
		})
	}

	if newState.Weight != oldState.Weight {
		delta := newState.Weight - oldState.Weight
		ws.WeightScale.logger.Debug("weight changed", "old", oldState.Weight, "new", newState.Weight, "delta", delta)
		ws.emit(Event{
			Type:      WeightChangedEvent,
			Weight:    newState.Weight,
			WeightRaw: newState.WeightRaw,
			OldWeight: oldState.Weight,
			Delta:     delta,
			Tare:      newState.Tare,
			Timestamp: time.Now(),
		})
	}

	if newState.Stable != oldState.Stable {
		if newState.Stable {
			ws.WeightScale.logger.Info("weight stable", "weight", newState.Weight)
			ws.emit(Event{
				Type:      WeightStableEvent,
				Weight:    newState.Weight,
				WeightRaw: newState.WeightRaw,
				Timestamp: time.Now(),
			})
		} else {
			ws.WeightScale.logger.Debug("weight unstable")
			ws.emit(Event{
				Type:      WeightUnstableEvent,
				Weight:    newState.Weight,
				WeightRaw: newState.WeightRaw,
				Timestamp: time.Now(),
			})
		}
	}

	if newState.Zero != oldState.Zero {
		if newState.Zero {
			ws.WeightScale.logger.Info("weight zero")
			ws.emit(Event{
				Type:      WeightZeroEvent,
				Weight:    0,
				Timestamp: time.Now(),
			})
		} else {
			ws.WeightScale.logger.Debug("weight not zero", "weight", newState.Weight)
			ws.emit(Event{
				Type:      WeightNotZeroEvent,
				Weight:    newState.Weight,
				Timestamp: time.Now(),
			})
		}
	}

	if newState.Overload != oldState.Overload {
		if newState.Overload {
			ws.WeightScale.logger.Warn("overload detected")
			ws.emit(Event{
				Type:      WeightOverloadEvent,
				Weight:    newState.Weight,
				Timestamp: time.Now(),
			})
		} else {
			ws.WeightScale.logger.Info("overload cleared")
			ws.emit(Event{
				Type:      WeightUnderloadEvent,
				Weight:    newState.Weight,
				Timestamp: time.Now(),
			})
		}
	}

	if newState.Underload != oldState.Underload && newState.Underload {
		ws.WeightScale.logger.Warn("underload detected")
		ws.emit(Event{
			Type:      ErrorEvent,
			Error:     fmt.Errorf("underload detected"),
			Timestamp: time.Now(),
		})
	}

	if newState.ChannelEnabled != oldState.ChannelEnabled {
		ws.WeightScale.logger.Info("channel enabled changed", "enabled", newState.ChannelEnabled)
		ws.emit(Event{
			Type:      ChannelEnabledEvent,
			Enabled:   newState.ChannelEnabled,
			Timestamp: time.Now(),
		})
	}

	if newState.AutoZero != oldState.AutoZero {
		ws.WeightScale.logger.Debug("auto zero changed", "enabled", newState.AutoZero)
		ws.emit(Event{
			Type:      AutoZeroEvent,
			AutoZero:  newState.AutoZero,
			Timestamp: time.Now(),
		})
	}

	ws.stateMu.Lock()
	ws.state = newState
	ws.stateMu.Unlock()
}

// GetCurrentState returns the current state of the scale
func (ws *WeightScaleWithEvents) GetCurrentState() WeightScaleState {
	ws.stateMu.RLock()
	defer ws.stateMu.RUnlock()
	return ws.state
}

// GetLastWeight returns the last known weight
func (ws *WeightScaleWithEvents) GetLastWeight() float64 {
	ws.stateMu.RLock()
	defer ws.stateMu.RUnlock()
	return ws.state.Weight
}

// GetLastWeightRaw returns the last known raw weight (in grams)
func (ws *WeightScaleWithEvents) GetLastWeightRaw() int32 {
	ws.stateMu.RLock()
	defer ws.stateMu.RUnlock()
	return ws.state.WeightRaw
}

// WaitForStable waits until weight becomes stable
func (ws *WeightScaleWithEvents) WaitForStable(timeout time.Duration) (float64, error) {
	if ws.GetCurrentState().Stable {
		state := ws.GetCurrentState()
		return state.Weight, nil
	}

	resultChan := make(chan float64)

	id := ws.On(WeightStableEvent, func(event Event) {
		resultChan <- event.Weight
	})
	defer ws.Off(WeightStableEvent, id)

	select {
	case weight := <-resultChan:
		return weight, nil
	case <-time.After(timeout):
		return 0, context.DeadlineExceeded
	}
}

// WaitForWeightChange waits until weight changes by at least delta
func (ws *WeightScaleWithEvents) WaitForWeightChange(delta float64, timeout time.Duration) (float64, error) {
	resultChan := make(chan float64)

	id := ws.On(WeightChangedEvent, func(event Event) {
		if abs(event.Delta) >= delta {
			resultChan <- event.Weight
		}
	})
	defer ws.Off(WeightChangedEvent, id)

	select {
	case weight := <-resultChan:
		return weight, nil
	case <-time.After(timeout):
		return 0, context.DeadlineExceeded
	}
}

// WaitForZero waits until weight becomes zero
func (ws *WeightScaleWithEvents) WaitForZero(timeout time.Duration) error {
	if ws.GetCurrentState().Zero {
		return nil
	}

	resultChan := make(chan struct{})

	id := ws.On(WeightZeroEvent, func(event Event) {
		resultChan <- struct{}{}
	})
	defer ws.Off(WeightZeroEvent, id)

	select {
	case <-resultChan:
		return nil
	case <-time.After(timeout):
		return context.DeadlineExceeded
	}
}

// SetZeroWithEvent sets zero and emits event
func (ws *WeightScaleWithEvents) SetZeroWithEvent() error {
	err := ws.SetZero()
	if err == nil {
		ws.WeightScale.logger.Info("zero set via event")
		ws.emit(Event{
			Type:      ZeroChangedEvent,
			Timestamp: time.Now(),
		})
	}
	return err
}

// SetTareWithEvent sets tare and emits event
func (ws *WeightScaleWithEvents) SetTareWithEvent() error {
	err := ws.SetTare()
	if err == nil {
		state := ws.GetCurrentState()
		ws.WeightScale.logger.Info("tare set via event", "tare", state.Tare)
		ws.emit(Event{
			Type:      TareChangedEvent,
			Tare:      state.Tare,
			Timestamp: time.Now(),
		})
	}
	return err
}

// EmulateKeypressWithEvent emulates key press and emits event
func (ws *WeightScaleWithEvents) EmulateKeypressWithEvent(keyCode byte, longPress bool) error {
	err := ws.EmulateKeypress(keyCode, longPress)
	if err == nil {
		ws.WeightScale.logger.Debug("keypress via event", "code", fmt.Sprintf("0x%02X", keyCode), "long", longPress)
		ws.emit(Event{
			Type:      KeyboardEvent,
			KeyCode:   keyCode,
			Timestamp: time.Now(),
		})
	}
	return err
}

// Close shuts down the event system
func (ws *WeightScaleWithEvents) Close() error {
	ws.StopPolling()
	return ws.WeightScale.Close()
}

func abs(x float64) float64 {
	if x < 0 {
		return -x
	}
	return x
}