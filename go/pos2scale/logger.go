// pos2scale/logger.go
package pos2scale

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"gopkg.in/natefinch/lumberjack.v2"
)

// Version модуля
const Version = "1.0.0"

// LogLevel represents logging level
type LogLevel string

const (
	LogLevelDebug LogLevel = "debug"
	LogLevelInfo  LogLevel = "info"
	LogLevelWarn  LogLevel = "warn"
	LogLevelError LogLevel = "error"
)

// LogConfig contains logging configuration
type LogConfig struct {
	Filename   string   // path to log file
	MaxSize    int      // maximum file size in MB
	MaxBackups int      // number of backups
	MaxAge     int      // days to keep
	Compress   bool     // compress old files
	Level      LogLevel // log level
}

// DefaultLogConfig returns default configuration
func DefaultLogConfig() LogConfig {
	return LogConfig{
		Filename:   "pos2scale.log",
		MaxSize:    10,
		MaxBackups: 7,
		MaxAge:     28,
		Compress:   true,
		Level:      LogLevelInfo,
	}
}

// ParseLogLevel parses log level from string
func ParseLogLevel(level string) LogLevel {
	switch strings.ToLower(level) {
	case "debug":
		return LogLevelDebug
	case "info":
		return LogLevelInfo
	case "warn", "warning":
		return LogLevelWarn
	case "error":
		return LogLevelError
	default:
		return LogLevelInfo
	}
}

// ToSlogLevel converts LogLevel to slog.Level
func (l LogLevel) ToSlogLevel() slog.Level {
	switch l {
	case LogLevelDebug:
		return slog.LevelDebug
	case LogLevelInfo:
		return slog.LevelInfo
	case LogLevelWarn:
		return slog.LevelWarn
	case LogLevelError:
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}

// customHandler - собственный обработчик для формата лога
type customHandler struct {
	writer io.Writer
	level  slog.Level
}

func (h *customHandler) Enabled(_ context.Context, level slog.Level) bool {
	return level >= h.level
}

func (h *customHandler) Handle(_ context.Context, r slog.Record) error {
	// Формат: 17.04.2026 11:58:34.117 DEBUG -> 02 05 11 30 30 30 30 14
	timestamp := r.Time.Format("02.01.2006 15:04:05.000")
	
	// Уровень логирования
	level := strings.ToUpper(r.Level.String())
	
	// Сообщение
	msg := r.Message
	
	// Если есть дополнительные атрибуты, добавляем их в конец
	var attrs string
	r.Attrs(func(a slog.Attr) bool {
		attrs += fmt.Sprintf(" %s=%v", a.Key, a.Value)
		return true
	})
	
	// Запись в лог
	_, err := fmt.Fprintf(h.writer, "%s %s %s%s\n", timestamp, level, msg, attrs)
	return err
}

func (h *customHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	return h
}

func (h *customHandler) WithGroup(name string) slog.Handler {
	return h
}

// InitLogger initializes logger with file rotation
func InitLogger(config LogConfig) (*slog.Logger, error) {
	// Create log directory if not exists
	dir := filepath.Dir(config.Filename)
	if dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return nil, err
		}
	}

	// Configure lumberjack for file rotation
	lumberjackLogger := &lumberjack.Logger{
		Filename:   config.Filename,
		MaxSize:    config.MaxSize,
		MaxBackups: config.MaxBackups,
		MaxAge:     config.MaxAge,
		Compress:   config.Compress,
	}

	// Create custom handler
	handler := &customHandler{
		writer: lumberjackLogger,
		level:  config.Level.ToSlogLevel(),
	}

	return slog.New(handler), nil
}

// NewNullLogger returns a logger that discards all output
func NewNullLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

// PrintVersion prints module version and OS info to log
func PrintVersion(logger *slog.Logger) {
	logger.Info("-----------------------------------------------")
	logger.Info("pos2scale Go module", "version", Version)
	logger.Info("OS", "os", runtime.GOOS, "arch", runtime.GOARCH)
	logger.Info("-----------------------------------------------")
}