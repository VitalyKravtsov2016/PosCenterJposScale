package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"time"
	"pos2scale/pos2scale"
)

var (
	portName = flag.String("port", "COM4", "COM port name")
	baudRate = flag.Int("baud", 9600, "Baud rate")
	interval = flag.Duration("interval", 500*time.Millisecond, "Poll interval (e.g., 500ms, 1s)")
	logLevel = flag.String("log-level", "debug", "Log level: debug, info, warn, error")
	logFile  = flag.String("log-file", "pos2scale.log", "Log file path")
)

func main() {
	flag.Parse()

	// Configure logging
	logConfig := pos2scale.LogConfig{
		Filename:   *logFile,
		MaxSize:    10,
		MaxBackups: 7,
		MaxAge:     28,
		Compress:   true,
		Level:      pos2scale.ParseLogLevel(*logLevel),
	}

	scale, err := pos2scale.NewWeightScaleWithEvents(*portName, *baudRate, logConfig)
	if err != nil {
		log.Fatal(err)
	}
	defer scale.Close()

	scale.SetPollInterval(*interval)
	scale.SetPassword("0000")

	fmt.Printf("Connected to %s at %d baud\n", *portName, *baudRate)
	fmt.Printf("Poll interval: %v, Log level: %s, Log file: %s\n", scale.GetPollInterval(), *logLevel, *logFile)
	fmt.Println()
	fmt.Println("Weight scale monitoring started.")
	fmt.Println("Events fire ONLY on changes:")
	fmt.Println("  - WEIGHT when weight changes (only stable weight)")
	fmt.Println("  - STABLE/UNSTABLE on state transition")
	fmt.Println("  - ZERO/NOT ZERO on zero transition")
	fmt.Println("Press Ctrl+C to stop.")
	fmt.Println()

	// Переменная для хранения последнего стабильного веса
	var lastStableWeight float64
	var weightChanged bool

	// Вес изменился - выводим только если вес стабильный
	scale.On(pos2scale.WeightChangedEvent, func(event pos2scale.Event) {
		// Проверяем, что вес стабильный
		if scale.GetCurrentState().Stable {
			// Если вес изменился с момента последнего стабильного
			if event.Weight != lastStableWeight {
				lastStableWeight = event.Weight
				weightChanged = true
				fmt.Printf("📊 %.3f kg\n", event.Weight)
			}
		} else {
			// Нестабильный вес - не выводим
			weightChanged = true
		}
	})

	// Вес стабилизировался - выводим если вес изменился
	scale.On(pos2scale.WeightStableEvent, func(event pos2scale.Event) {
		if weightChanged && event.Weight != lastStableWeight {
			lastStableWeight = event.Weight
			fmt.Printf("✅ %.3f kg\n", event.Weight)
		}
		weightChanged = false
	})

	// Вес стал нулевым
	scale.On(pos2scale.WeightZeroEvent, func(event pos2scale.Event) {
		fmt.Printf("⭕ ZERO\n")
	})

	// Вес перестал быть нулевым
	scale.On(pos2scale.WeightNotZeroEvent, func(event pos2scale.Event) {
		if scale.GetCurrentState().Stable {
			fmt.Printf("🔵 %.3f kg\n", event.Weight)
		}
	})

	// Перегрузка
	scale.On(pos2scale.WeightOverloadEvent, func(event pos2scale.Event) {
		fmt.Printf("⚠️ OVERLOAD!\n")
	})

	scale.On(pos2scale.WeightUnderloadEvent, func(event pos2scale.Event) {
		fmt.Printf("✅ OVERLOAD cleared\n")
	})

	// Потеря/восстановление связи
	scale.On(pos2scale.ConnectionLostEvent, func(event pos2scale.Event) {
		fmt.Printf("🔌 Connection lost: %v\n", event.Error)
	})

	scale.On(pos2scale.ConnectionRestoredEvent, func(event pos2scale.Event) {
		fmt.Printf("🔌 Connection restored\n")
	})

	// Ошибки
	scale.On(pos2scale.ErrorEvent, func(event pos2scale.Event) {
		fmt.Printf("❌ Error: %v\n", event.Error)
	})

	// Запуск мониторинга
	scale.StartPolling()

	// Ожидание Ctrl+C
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt)
	<-c

	fmt.Println("\nShutting down...")
	scale.StopPolling()
}