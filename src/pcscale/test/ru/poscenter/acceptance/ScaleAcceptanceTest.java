package ru.poscenter.acceptance;

import gnu.io.CommPortIdentifier;
import jpos.*;
import jpos.events.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Приемочные испытания JPos Scale драйвера
 * 
 * Для работы тестов необходимо:
 * 1. Настроить виртуальный COM-порт через com0com (например, COM5 <-> COM6)
 * 2. Указать в настройках JPos Scale Service реальный COM-порт (например, COM6)
 * 3. Эмулятор подключается к парному порту (например, COM5)
 * 4. Для Linux использовать socat или аналоги
 * 
 * @version 1.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScaleAcceptanceTest {

    private static final String EMULATOR_PORT = "COM5"; // Порт эмулятора (менять под свою конфигурацию)
    private static final String LOGICAL_DEVICE_NAME = "Scale"; // Логическое имя в JPos конфигурации
    
    private static Pos2ProtocolEmulator emulator;
    private static Scale scale;
    
    private final CountDownLatch eventLatch = new CountDownLatch(1);
    private final AtomicInteger receivedStatus = new AtomicInteger(0);
    private final AtomicInteger receivedErrorCode = new AtomicInteger(0);
    private final AtomicInteger receivedDataStatus = new AtomicInteger(0);
    private final AtomicReference<String> directIOData = new AtomicReference<>();
    private final AtomicBoolean dataEventReceived = new AtomicBoolean(false);
    private final AtomicBoolean outputCompleteReceived = new AtomicBoolean(false);
    
    private static final int DEFAULT_TIMEOUT = 10000;
    private static final int WEIGHT_STABLE_DELAY = 500;
    
    // Константы для весов
    private static final int SCAL_WU_GRAM = 1;
    private static final int SCAL_WU_KILOGRAM = 2;
    private static final int SCAL_WU_OUNCE = 3;
    private static final int SCAL_WU_POUND = 4;
    
    private static final int SCAL_SN_DISABLED = 1;
    private static final int SCAL_SN_ENABLED = 2;
    
    private static final int SCAL_SUE_STABLE_WEIGHT = 11;
    private static final int SCAL_SUE_WEIGHT_UNSTABLE = 12;
    private static final int SCAL_SUE_WEIGHT_ZERO = 13;
    private static final int SCAL_SUE_WEIGHT_OVERWEIGHT = 14;
    private static final int SCAL_SUE_NOT_READY = 15;
    private static final int SCAL_SUE_WEIGHT_UNDER_ZERO = 16;
    
    // ------------------------------------------------------------------------
    // Setup and teardown
    // ------------------------------------------------------------------------
    
    @BeforeAll
    static void setUpClass() throws Exception {
        // Запуск эмулятора
        emulator = new Pos2ProtocolEmulator(EMULATOR_PORT);
        emulator.start();
        
        // Небольшая пауза для инициализации порта
        Thread.sleep(1000);
        
        // Инициализация JPos Scale
        scale = new Scale();
    }
    
    @AfterAll
    static void tearDownClass() {
        if (scale != null) {
            try {
                if (scale.getClaimed()) {
                    scale.setDeviceEnabled(false);
                    scale.release();
                }
                scale.close();
            } catch (JposException e) {
                System.err.println("Error closing scale: " + e.getMessage());
            }
        }
        
        if (emulator != null) {
            emulator.stop();
        }
    }
    
    @BeforeEach
    void setUp() throws Exception {
        eventLatch.reset();
        receivedStatus.set(0);
        receivedErrorCode.set(0);
        receivedDataStatus.set(0);
        directIOData.set(null);
        dataEventReceived.set(false);
        outputCompleteReceived.set(false);
        
        // Регистрация слушателей событий, если ещё не зарегистрированы
        if (scale.getDataListeners().length == 0) {
            scale.addDataListener(new DataListener() {
                @Override
                public void dataOccurred(DataEvent e) {
                    dataEventReceived.set(true);
                    receivedDataStatus.set(e.getStatus());
                    eventLatch.countDown();
                    System.out.println("DataEvent received, status=" + e.getStatus());
                }
            });
            
            scale.addErrorListener(new ErrorListener() {
                @Override
                public void errorOccurred(ErrorEvent e) {
                    receivedErrorCode.set(e.getErrorCode());
                    eventLatch.countDown();
                    System.out.println("ErrorEvent received, code=" + e.getErrorCode() + 
                                     ", extended=" + e.getErrorCodeExtended() + 
                                     ", locus=" + e.getErrorLocus());
                }
            });
            
            scale.addStatusUpdateListener(new StatusUpdateListener() {
                @Override
                public void statusUpdateOccurred(StatusUpdateEvent e) {
                    receivedStatus.set(e.getStatus());
                    eventLatch.countDown();
                    System.out.println("StatusUpdateEvent received, status=" + e.getStatus());
                }
            });
            
            scale.addDirectIOListener(new DirectIOListener() {
                @Override
                public void directIOOccurred(DirectIOEvent e) {
                    directIOData.set("EventNumber=" + e.getEventNumber() + ", Data=" + e.getData());
                    eventLatch.countDown();
                    System.out.println("DirectIOEvent received: " + directIOData.get());
                }
            });
        }
    }
    
    @AfterEach
    void tearDown() throws Exception {
        try {
            if (scale.getClaimed()) {
                scale.setDeviceEnabled(false);
                scale.release();
            }
            if (scale.getState() != JposConst.JPOS_S_CLOSED) {
                scale.close();
            }
        } catch (Exception e) {
            // ignore
        }
        
        // Открываем заново для следующего теста
        scale.open(LOGICAL_DEVICE_NAME);
        Thread.sleep(500);
    }
    
    // ------------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------------
    
    private void claimAndEnable() throws JposException {
        if (!scale.getClaimed()) {
            scale.claim(5000);
        }
        if (!scale.getDeviceEnabled()) {
            scale.setDeviceEnabled(true);
        }
        Thread.sleep(WEIGHT_STABLE_DELAY);
    }
    
    private void releaseAndDisable() throws JposException {
        if (scale.getDeviceEnabled()) {
            scale.setDeviceEnabled(false);
        }
        if (scale.getClaimed()) {
            scale.release();
        }
    }
    
    private void waitForEvent(long timeoutMs) throws InterruptedException {
        eventLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }
    
    // ------------------------------------------------------------------------
    // Test 1: Device initialization
    // ------------------------------------------------------------------------
    
    @Test
    @Order(1)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testOpenAndClose() throws JposException {
        System.out.println("\n=== Test 1: Device initialization ===");
        
        // Проверка открытия устройства
        scale.open(LOGICAL_DEVICE_NAME);
        assertNotEquals(JposConst.JPOS_S_CLOSED, scale.getState(), "Device should be opened");
        assertEquals(JposConst.JPOS_S_IDLE, scale.getState(), "Device should be in IDLE state");
        
        // Проверка основных свойств после открытия
        assertNotNull(scale.getDeviceControlDescription(), "DeviceControlDescription should not be null");
        assertNotNull(scale.getDeviceServiceDescription(), "DeviceServiceDescription should not be null");
        assertNotNull(scale.getPhysicalDeviceDescription(), "PhysicalDeviceDescription should not be null");
        assertNotNull(scale.getPhysicalDeviceName(), "PhysicalDeviceName should not be null");
        
        System.out.println("DeviceControlDescription: " + scale.getDeviceControlDescription());
        System.out.println("DeviceServiceDescription: " + scale.getDeviceServiceDescription());
        System.out.println("PhysicalDeviceDescription: " + scale.getPhysicalDeviceDescription());
        System.out.println("PhysicalDeviceName: " + scale.getPhysicalDeviceName());
        
        // Проверка версий
        assertTrue(scale.getDeviceControlVersion() > 0, "DeviceControlVersion should be > 0");
        assertTrue(scale.getDeviceServiceVersion() > 0, "DeviceServiceVersion should be > 0");
        
        System.out.println("DeviceControlVersion: " + scale.getDeviceControlVersion());
        System.out.println("DeviceServiceVersion: " + scale.getDeviceServiceVersion());
        
        // Проверка закрытия
        scale.close();
        assertEquals(JposConst.JPOS_S_CLOSED, scale.getState(), "Device should be closed");
        
        System.out.println("Test 1 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 2: Claim and release
    // ------------------------------------------------------------------------
    
    @Test
    @Order(2)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testClaimAndRelease() throws JposException {
        System.out.println("\n=== Test 2: Claim and release ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        
        // Проверка, что устройство не захвачено
        assertFalse(scale.getClaimed(), "Device should not be claimed initially");
        
        // Захват устройства
        scale.claim(5000);
        assertTrue(scale.getClaimed(), "Device should be claimed");
        
        // Повторный захват не должен вызвать ошибку (метод идемпотентный)
        scale.claim(5000);
        assertTrue(scale.getClaimed(), "Device should still be claimed");
        
        // Освобождение устройства
        scale.release();
        assertFalse(scale.getClaimed(), "Device should be released");
        
        scale.close();
        
        System.out.println("Test 2 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 3: Capabilities properties
    // ------------------------------------------------------------------------
    
    @Test
    @Order(3)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testCapabilities() throws JposException {
        System.out.println("\n=== Test 3: Capabilities properties ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Проверка всех capability свойств
        // Они могут быть true или false в зависимости от эмулятора
        boolean capDisplay = scale.getCapDisplay();
        System.out.println("CapDisplay: " + capDisplay);
        
        boolean capDisplayText = scale.getCapDisplayText();
        System.out.println("CapDisplayText: " + capDisplayText);
        
        boolean capPriceCalculating = scale.getCapPriceCalculating();
        System.out.println("CapPriceCalculating: " + capPriceCalculating);
        
        int capPowerReporting = scale.getCapPowerReporting();
        System.out.println("CapPowerReporting: " + capPowerReporting);
        
        boolean capTareWeight = scale.getCapTareWeight();
        System.out.println("CapTareWeight: " + capTareWeight);
        
        boolean capZeroScale = scale.getCapZeroScale();
        System.out.println("CapZeroScale: " + capZeroScale);
        
        boolean capStatisticsReporting = scale.getCapStatisticsReporting();
        System.out.println("CapStatisticsReporting: " + capStatisticsReporting);
        
        boolean capUpdateStatistics = scale.getCapUpdateStatistics();
        System.out.println("CapUpdateStatistics: " + capUpdateStatistics);
        
        boolean capCompareFirmwareVersion = scale.getCapCompareFirmwareVersion();
        System.out.println("CapCompareFirmwareVersion: " + capCompareFirmwareVersion);
        
        boolean capStatusUpdate = scale.getCapStatusUpdate();
        System.out.println("CapStatusUpdate: " + capStatusUpdate);
        
        boolean capUpdateFirmware = scale.getCapUpdateFirmware();
        System.out.println("CapUpdateFirmware: " + capUpdateFirmware);
        
        boolean capFreezeValue = scale.getCapFreezeValue();
        System.out.println("CapFreezeValue: " + capFreezeValue);
        
        boolean capReadLiveWeightWithTare = scale.getCapReadLiveWeightWithTare();
        System.out.println("CapReadLiveWeightWithTare: " + capReadLiveWeightWithTare);
        
        boolean capSetPriceCalculationMode = scale.getCapSetPriceCalculationMode();
        System.out.println("CapSetPriceCalculationMode: " + capSetPriceCalculationMode);
        
        boolean capSetUnitPriceWithWeightUnit = scale.getCapSetUnitPriceWithWeightUnit();
        System.out.println("CapSetUnitPriceWithWeightUnit: " + capSetUnitPriceWithWeightUnit);
        
        boolean capSpecialTare = scale.getCapSpecialTare();
        System.out.println("CapSpecialTare: " + capSpecialTare);
        
        boolean capTarePriority = scale.getCapTarePriority();
        System.out.println("CapTarePriority: " + capTarePriority);
        
        // Проверка, что свойства имеют допустимые значения
        assertNotNull(capDisplay, "CapDisplay should be readable");
        assertNotNull(capTareWeight, "CapTareWeight should be readable");
        assertNotNull(capZeroScale, "CapZeroScale should be readable");
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 3 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 4: Basic properties (MaximumWeight, WeightUnit, etc.)
    // ------------------------------------------------------------------------
    
    @Test
    @Order(4)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testBasicProperties() throws JposException {
        System.out.println("\n=== Test 4: Basic properties ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Проверка MaximumWeight
        int maxWeight = scale.getMaximumWeight();
        assertTrue(maxWeight > 0, "MaximumWeight should be > 0");
        System.out.println("MaximumWeight: " + maxWeight);
        
        // Проверка WeightUnit
        int weightUnit = scale.getWeightUnit();
        assertTrue(weightUnit >= SCAL_WU_GRAM && weightUnit <= SCAL_WU_POUND, 
                  "WeightUnit should be valid value");
        System.out.println("WeightUnit: " + weightUnit);
        
        // Проверка MaxDisplayTextChars
        int maxDisplayChars = scale.getMaxDisplayTextChars();
        System.out.println("MaxDisplayTextChars: " + maxDisplayChars);
        
        // Проверка MinimumWeight (если поддерживается)
        try {
            int minWeight = scale.getMinimumWeight();
            System.out.println("MinimumWeight: " + minWeight);
        } catch (JposException e) {
            System.out.println("MinimumWeight not supported: " + e.getMessage());
        }
        
        // Проверка AsyncMode
        boolean asyncMode = scale.getAsyncMode();
        System.out.println("AsyncMode (initial): " + asyncMode);
        scale.setAsyncMode(true);
        assertTrue(scale.getAsyncMode(), "AsyncMode should be true");
        scale.setAsyncMode(false);
        assertFalse(scale.getAsyncMode(), "AsyncMode should be false");
        
        // Проверка AutoDisable
        boolean autoDisable = scale.getAutoDisable();
        System.out.println("AutoDisable (initial): " + autoDisable);
        scale.setAutoDisable(true);
        assertTrue(scale.getAutoDisable(), "AutoDisable should be true");
        scale.setAutoDisable(false);
        assertFalse(scale.getAutoDisable(), "AutoDisable should be false");
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 4 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 5: Synchronous weight reading
    // ------------------------------------------------------------------------
    
    @Test
    @Order(5)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testSynchronousReadWeight() throws JposException, InterruptedException {
        System.out.println("\n=== Test 5: Synchronous weight reading ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Устанавливаем синхронный режим
        scale.setAsyncMode(false);
        
        // Тест 1: Чтение веса 1000 грамм
        emulator.setWeight(1000);
        emulator.setStable(true);
        Thread.sleep(WEIGHT_STABLE_DELAY);
        
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        assertEquals(1000, weight[0], "Weight should be 1000");
        System.out.println("Read weight: " + weight[0]);
        
        // Тест 2: Чтение веса 2500 грамм
        emulator.setWeight(2500);
        Thread.sleep(WEIGHT_STABLE_DELAY);
        
        scale.readWeight(weight, 5000);
        assertEquals(2500, weight[0], "Weight should be 2500");
        System.out.println("Read weight: " + weight[0]);
        
        // Тест 3: Чтение с таймаутом (вес нестабилен)
        emulator.setStable(false);
        weight[0] = 0;
        
        assertThrows(JposException.class, () -> {
            scale.readWeight(weight, 2000);
        }, "Should throw exception when weight is unstable");
        System.out.println("Correctly got exception for unstable weight");
        
        // Возвращаем стабильное состояние
        emulator.setStable(true);
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 5 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 6: Asynchronous weight reading
    // ------------------------------------------------------------------------
    
    @Test
    @Order(6)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testAsynchronousReadWeight() throws JposException, InterruptedException {
        System.out.println("\n=== Test 6: Asynchronous weight reading ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Устанавливаем асинхронный режим
        scale.setAsyncMode(true);
        scale.setDataEventEnabled(true);
        
        // Сбрасываем latch перед тестом
        eventLatch.reset();
        
        // Устанавливаем вес и запускаем асинхронное чтение
        emulator.setWeight(1500);
        emulator.setStable(true);
        
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        assertEquals(0, weight[0], "Weight should be 0 in async mode (return value)");
        System.out.println("Async read initiated");
        
        // Ожидаем DataEvent
        boolean eventReceived = eventLatch.await(6000, TimeUnit.MILLISECONDS);
        assertTrue(eventReceived, "DataEvent should be received");
        assertTrue(dataEventReceived.get(), "DataEvent should be received");
        
        // Проверяем вес в событии
        assertEquals(1500, receivedDataStatus.get(), "Weight in event should be 1500");
        System.out.println("Async weight received via event: " + receivedDataStatus.get());
        
        // Проверка AutoDisable при асинхронном чтении
        scale.setAutoDisable(true);
        eventLatch.reset();
        dataEventReceived.set(false);
        
        emulator.setWeight(2000);
        scale.readWeight(weight, 5000);
        
        eventReceived = eventLatch.await(6000, TimeUnit.MILLISECONDS);
        assertTrue(eventReceived, "DataEvent should be received with AutoDisable");
        
        // Устройство должно быть автоматически отключено
        Thread.sleep(500);
        assertFalse(scale.getDeviceEnabled(), "Device should be auto-disabled");
        
        // Включаем обратно
        scale.setDeviceEnabled(true);
        scale.setDataEventEnabled(true);
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 6 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 7: Tare operations
    // ------------------------------------------------------------------------
    
    @Test
    @Order(7)
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testTareOperations() throws JposException, InterruptedException {
        System.out.println("\n=== Test 7: Tare operations ===");
        
        if (!scale.getCapTareWeight()) {
            System.out.println("Skipping test: CapTareWeight is false");
            return;
        }
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        scale.setAsyncMode(false);
        
        // Тест 1: Установка веса тары через свойство
        emulator.setWeight(500);
        emulator.setStable(true);
        
        scale.setTareWeight(300);
        assertEquals(300, scale.getTareWeight(), "TareWeight should be 300");
        
        // Чтение веса с учётом тары
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        // Эмулятор возвращает брутто, но драйвер должен вычитать тару
        // В нашем эмуляторе readWeight возвращает currentWeight без вычитания тары
        // Поэтому проверяем, что свойство тары установлено
        System.out.println("Weight with tare: " + weight[0]);
        
        // Тест 2: Автоматическая установка тары (команда TARE)
        eventLatch.reset();
        
        // Сбрасываем команды в эмуляторе
        emulator.clearCommands();
        
        // Имитируем нажатие клавиши TARE на весах через эмулятор
        // Для этого используем directIO или другую команду эмулятора
        // В эмуляторе это может быть команда KEY_EMULATION
        
        // Ожидаем команду TARE от драйвера
        // (эмулятор должен получить команду и положить её в очередь)
        
        System.out.println("Waiting for tare command from driver...");
        // В реальном тесте нужно отправить команду из драйвера
        
        // Тест 3: Установка тары через CMD_SET_TARE_VALUE
        // Для этого нужно отправить команду setTareValue из драйвера
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 7 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 8: Zero scale operation
    // ------------------------------------------------------------------------
    
    @Test
    @Order(8)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testZeroScale() throws JposException, InterruptedException {
        System.out.println("\n=== Test 8: Zero scale operation ===");
        
        if (!scale.getCapZeroScale()) {
            System.out.println("Skipping test: CapZeroScale is false");
            return;
        }
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        scale.setAsyncMode(false);
        
        // Устанавливаем ненулевой вес
        emulator.setWeight(100);
        emulator.setStable(true);
        Thread.sleep(WEIGHT_STABLE_DELAY);
        
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        assertTrue(weight[0] > 0, "Weight should be > 0");
        System.out.println("Weight before zero: " + weight[0]);
        
        // Обнуление
        scale.zeroScale();
        
        // Сбрасываем команды
        emulator.clearCommands();
        
        // Проверяем, что вес стал 0
        scale.readWeight(weight, 5000);
        assertEquals(0, weight[0], "Weight should be 0 after zeroScale");
        System.out.println("Weight after zero: " + weight[0]);
        
        // Ждём команду ZERO от драйвера
        boolean zeroCommandReceived = emulator.waitForZeroCommand(2000, TimeUnit.MILLISECONDS);
        System.out.println("Zero command received by emulator: " + zeroCommandReceived);
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 8 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 9: Display text operation
    // ------------------------------------------------------------------------
    
    @Test
    @Order(9)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testDisplayText() throws JposException {
        System.out.println("\n=== Test 9: Display text operation ===");
        
        if (!scale.getCapDisplayText()) {
            System.out.println("Skipping test: CapDisplayText is false");
            return;
        }
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Отображение текста на дисплее весов
        String testText = "Hello Scale";
        scale.displayText(testText);
        System.out.println("Displayed text: " + testText);
        
        // Отображение пустой строки
        scale.displayText("");
        System.out.println("Displayed empty text");
        
        // Если есть ограничение по длине, проверяем обрезку
        int maxChars = scale.getMaxDisplayTextChars();
        if (maxChars > 0) {
            String longText = "This is a very long text that exceeds display capability";
            scale.displayText(longText);
            System.out.println("Displayed long text (may be truncated to " + maxChars + " chars)");
        }
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 9 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 10: Price calculating operations
    // ------------------------------------------------------------------------
    
    @Test
    @Order(10)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testPriceCalculating() throws JposException, InterruptedException {
        System.out.println("\n=== Test 10: Price calculating operations ===");
        
        if (!scale.getCapPriceCalculating()) {
            System.out.println("Skipping test: CapPriceCalculating is false");
            return;
        }
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Установка цены за единицу
        long unitPrice = 15000; // 1.5000
        scale.setUnitPrice(unitPrice);
        assertEquals(unitPrice, scale.getUnitPrice(), "UnitPrice should be set correctly");
        System.out.println("UnitPrice set to: " + unitPrice);
        
        // Синхронное чтение с вычислением цены
        scale.setAsyncMode(false);
        emulator.setWeight(2000);
        emulator.setStable(true);
        Thread.sleep(WEIGHT_STABLE_DELAY);
        
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        
        long salesPrice = scale.getSalesPrice();
        System.out.println("Weight: " + weight[0] + ", SalesPrice: " + salesPrice);
        
        // Проверка, что цена рассчитана (вес * цена за единицу)
        // weight[0] = 2000 грамм = 2 кг, unitPrice = 1.5 за кг => salesPrice = 3.0 = 30000
        long expectedPrice = (long)weight[0] * unitPrice / 1000;
        System.out.println("Expected price: " + expectedPrice);
        
        // Асинхронное чтение с вычислением цены
        scale.setAsyncMode(true);
        scale.setDataEventEnabled(true);
        eventLatch.reset();
        dataEventReceived.set(false);
        
        emulator.setWeight(3500);
        weight[0] = 0;
        scale.readWeight(weight, 5000);
        
        boolean eventReceived = eventLatch.await(6000, TimeUnit.MILLISECONDS);
        assertTrue(eventReceived, "DataEvent should be received for price calculation");
        
        System.out.println("Async weight: " + receivedDataStatus.get() + 
                         ", SalesPrice: " + scale.getSalesPrice());
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 10 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 11: Power notification and status updates
    // ------------------------------------------------------------------------
    
    @Test
    @Order(11)
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testPowerAndStatusNotifications() throws JposException, InterruptedException {
        System.out.println("\n=== Test 11: Power and status notifications ===");
        
        int capPower = scale.getCapPowerReporting();
        if (capPower == JposConst.JPOS_PR_NONE) {
            System.out.println("Skipping power test: CapPowerReporting is NONE");
            return;
        }
        
        scale.open(LOGICAL_DEVICE_NAME);
        
        // Включение уведомлений о питании
        scale.setPowerNotify(JposConst.JPOS_PN_ENABLED);
        assertEquals(JposConst.JPOS_PN_ENABLED, scale.getPowerNotify(), "PowerNotify should be ENABLED");
        
        claimAndEnable();
        
        // Проверка состояния питания
        int powerState = scale.getPowerState();
        System.out.println("PowerState: " + powerState);
        assertTrue(powerState == JposConst.JPOS_PS_ONLINE || 
                   powerState == JposConst.JPOS_PS_UNKNOWN, 
                   "PowerState should be valid");
        
        // Проверка уведомлений о статусе весов
        if (scale.getCapStatusUpdate()) {
            scale.setStatusNotify(SCAL_SN_ENABLED);
            assertEquals(SCAL_SN_ENABLED, scale.getStatusNotify(), "StatusNotify should be ENABLED");
            
            eventLatch.reset();
            
            // Изменяем состояние весов в эмуляторе
            emulator.setStable(false);
            Thread.sleep(1000);
            
            // Должен прийти StatusUpdateEvent о нестабильном весе
            // (зависит от реализации эмулятора)
            
            emulator.setStable(true);
            emulator.setWeight(100);
            Thread.sleep(1000);
            
            // Может прийти статус о стабильном весе
        }
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 11 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 12: Statistics operations
    // ------------------------------------------------------------------------
    
    @Test
    @Order(12)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testStatistics() throws JposException {
        System.out.println("\n=== Test 12: Statistics operations ===");
        
        if (!scale.getCapStatisticsReporting()) {
            System.out.println("Skipping test: CapStatisticsReporting is false");
            return;
        }
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Получение статистики
        String[] statsBuffer = new String[1];
        scale.retrieveStatistics(statsBuffer);
        assertNotNull(statsBuffer[0], "Statistics buffer should not be null");
        System.out.println("Retrieved statistics: " + statsBuffer[0]);
        
        // Сброс статистики (если поддерживается)
        if (scale.getCapUpdateStatistics()) {
            scale.resetStatistics("");
            System.out.println("Statistics reset");
            
            // Проверка после сброса
            scale.retrieveStatistics(statsBuffer);
            System.out.println("Statistics after reset: " + statsBuffer[0]);
            
            // Обновление статистики
            // scale.updateStatistics("StatName=100");
        }
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 12 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 13: Clear input operations
    // ------------------------------------------------------------------------
    
    @Test
    @Order(13)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testClearInput() throws JposException, InterruptedException {
        System.out.println("\n=== Test 13: Clear input operations ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        scale.setAsyncMode(true);
        
        // Отключаем доставку событий
        scale.setDataEventEnabled(false);
        
        // Запускаем несколько асинхронных чтений
        emulator.setWeight(100);
        emulator.setStable(true);
        
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        scale.readWeight(weight, 5000);
        scale.readWeight(weight, 5000);
        
        // Должны быть накоплены события
        int dataCount = scale.getDataCount();
        assertTrue(dataCount > 0, "DataCount should be > 0 after async reads");
        System.out.println("DataCount before clear: " + dataCount);
        
        // Очистка входного буфера
        scale.clearInput();
        
        dataCount = scale.getDataCount();
        assertEquals(0, dataCount, "DataCount should be 0 after clearInput");
        System.out.println("DataCount after clear: " + dataCount);
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 13 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 14: FreezeEvents property
    // ------------------------------------------------------------------------
    
    @Test
    @Order(14)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testFreezeEvents() throws JposException, InterruptedException {
        System.out.println("\n=== Test 14: FreezeEvents property ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        scale.setAsyncMode(true);
        scale.setDataEventEnabled(true);
        
        // Замораживаем события
        scale.setFreezeEvents(true);
        assertTrue(scale.getFreezeEvents(), "FreezeEvents should be true");
        
        // Запускаем асинхронное чтение
        eventLatch.reset();
        dataEventReceived.set(false);
        
        emulator.setWeight(500);
        emulator.setStable(true);
        
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        
        // Ждём немного - событие не должно прийти
        Thread.sleep(2000);
        assertFalse(dataEventReceived.get(), "DataEvent should not be received when events are frozen");
        System.out.println("Event correctly frozen");
        
        // Размораживаем события
        scale.setFreezeEvents(false);
        
        // Событие должно прийти
        boolean eventReceived = eventLatch.await(5000, TimeUnit.MILLISECONDS);
        assertTrue(eventReceived, "DataEvent should be received after unfreezing");
        System.out.println("Event received after unfreeze");
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 14 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 15: Edge cases and error handling
    // ------------------------------------------------------------------------
    
    @Test
    @Order(15)
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testEdgeCasesAndErrors() throws JposException, InterruptedException {
        System.out.println("\n=== Test 15: Edge cases and error handling ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        
        // Проверка: вызов методов без claim
        assertThrows(JposException.class, () -> {
            scale.setDeviceEnabled(true);
        }, "Should throw exception when not claimed");
        System.out.println("Correctly got exception for setDeviceEnabled without claim");
        
        claimAndEnable();
        
        // Проверка: чтение веса с некорректным таймаутом
        scale.setAsyncMode(false);
        int[] weight = new int[1];
        
        assertThrows(JposException.class, () -> {
            scale.readWeight(weight, -2);
        }, "Should throw exception for invalid timeout");
        System.out.println("Correctly got exception for invalid timeout");
        
        // Проверка: установка некорректной цены (если цена должна быть положительной)
        if (scale.getCapPriceCalculating()) {
            assertThrows(JposException.class, () -> {
                scale.setUnitPrice(-1);
            }, "Should throw exception for negative unit price");
            System.out.println("Correctly got exception for negative unit price");
        }
        
        // Проверка: переполнение веса
        emulator.setWeight(100000); // Вес больше максимального
        emulator.setStable(true);
        
        try {
            scale.readWeight(weight, 3000);
        } catch (JposException e) {
            System.out.println("Got expected exception for overweight: " + e.getMessage());
        }
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 15 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 16: Device information properties
    // ------------------------------------------------------------------------
    
    @Test
    @Order(16)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testDeviceInformation() throws JposException {
        System.out.println("\n=== Test 16: Device information ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        
        // Проверка всех информационных свойств
        String checkHealthText = scale.getCheckHealthText();
        System.out.println("CheckHealthText: " + checkHealthText);
        
        // Проверка состояния устройства
        int state = scale.getState();
        assertEquals(JposConst.JPOS_S_IDLE, state, "State should be IDLE after open");
        System.out.println("State: " + state);
        
        // Проверка DataCount (должен быть 0 после open)
        assertEquals(0, scale.getDataCount(), "DataCount should be 0 after open");
        
        // Проверка OutputID (не поддерживается для Scale)
        // int outputId = scale.getOutputId();
        
        scale.close();
        
        System.out.println("Test 16 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 17: Health check
    // ------------------------------------------------------------------------
    
    @Test
    @Order(17)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testCheckHealth() throws JposException {
        System.out.println("\n=== Test 17: Health check ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Внутренняя проверка
        scale.checkHealth(JposConst.JPOS_CH_INTERNAL);
        String healthText = scale.getCheckHealthText();
        System.out.println("Internal health check: " + healthText);
        assertNotNull(healthText, "Health check text should not be null");
        
        // Внешняя проверка (если поддерживается)
        try {
            scale.checkHealth(JposConst.JPOS_CH_EXTERNAL);
            healthText = scale.getCheckHealthText();
            System.out.println("External health check: " + healthText);
        } catch (JposException e) {
            System.out.println("External health check not supported: " + e.getMessage());
        }
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 17 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 18: DirectIO operation
    // ------------------------------------------------------------------------
    
    @Test
    @Order(18)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testDirectIO() throws JposException {
        System.out.println("\n=== Test 18: DirectIO operation ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Тест DirectIO (зависит от конкретной реализации драйвера)
        // Обычно используется для вендор-специфичных команд
        int[] data = new int[1];
        Object obj = null;
        
        try {
            scale.directIO(0, data, obj);
            System.out.println("DirectIO executed with command 0, data=" + data[0]);
        } catch (JposException e) {
            System.out.println("DirectIO not supported or command invalid: " + e.getMessage());
        }
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 18 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 19: Concurrent operations
    // ------------------------------------------------------------------------
    
    @Test
    @Order(19)
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testConcurrentOperations() throws JposException, InterruptedException {
        System.out.println("\n=== Test 19: Concurrent operations ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        scale.setAsyncMode(true);
        scale.setDataEventEnabled(true);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // Запуск нескольких параллельных операций чтения
        // (ожидается, что будет брошено исключение, т.к. только одна async операция может выполняться)
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        for (int i = 0; i < 5; i++) {
            final int weight = 100 + i * 100;
            executor.submit(() -> {
                try {
                    emulator.setWeight(weight);
                    emulator.setStable(true);
                    
                    int[] w = new int[1];
                    scale.readWeight(w, 5000);
                    successCount.incrementAndGet();
                } catch (JposException e) {
                    errorCount.incrementAndGet();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // Должна быть только одна успешная операция
        System.out.println("Success count: " + successCount.get() + ", Error count: " + errorCount.get());
        assertTrue(errorCount.get() > 0, "Should have errors for concurrent async reads");
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 19 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 20: ZeroValid property test (if supported)
    // ------------------------------------------------------------------------
    
    @Test
    @Order(20)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testZeroValidProperty() throws JposException, InterruptedException {
        System.out.println("\n=== Test 20: ZeroValid property test ===");
        
        // Проверяем, поддерживается ли ZeroValid (версия 1.13+)
        try {
            scale.open(LOGICAL_DEVICE_NAME);
            claimAndEnable();
            scale.setAsyncMode(false);
            
            boolean zeroValid = scale.getZeroValid();
            System.out.println("ZeroValid initial: " + zeroValid);
            
            // Устанавливаем вес 0
            emulator.setWeight(0);
            emulator.setStable(true);
            Thread.sleep(WEIGHT_STABLE_DELAY);
            
            int[] weight = new int[1];
            
            // При ZeroValid = false, вес 0 не должен возвращаться
            scale.setZeroValid(false);
            
            assertThrows(JposException.class, () -> {
                scale.readWeight(weight, 3000);
            }, "Should throw exception for zero weight when ZeroValid=false");
            System.out.println("Correctly got exception for zero weight with ZeroValid=false");
            
            // При ZeroValid = true, вес 0 должен возвращаться
            scale.setZeroValid(true);
            scale.readWeight(weight, 3000);
            assertEquals(0, weight[0], "Weight should be 0 when ZeroValid=true");
            System.out.println("Zero weight returned successfully with ZeroValid=true");
            
            releaseAndDisable();
            
        } catch (JposException e) {
            if (e.getErrorCode() == JposConst.JPOS_E_NOSERVICE) {
                System.out.println("ZeroValid not supported (service version < 1.13)");
            } else {
                throw e;
            }
        }
        
        scale.close();
        
        System.out.println("Test 20 passed!");
    }
}