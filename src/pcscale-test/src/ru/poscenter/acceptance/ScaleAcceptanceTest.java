package ru.poscenter.acceptance;

import jpos.*;
import jpos.events.*;
import org.junit.*;
import org.junit.rules.Timeout;
import org.junit.runner.JUnitCore;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Приемочные испытания JPos Scale драйвера
 * 
 * Для работы тестов необходимо:
 * 1. Настроить виртуальный COM-порт через com0com (например, COM5 <-> COM6)
 * 2. Указать в настройках JPos Scale Service реальный COM-порт (например, COM6)
 * 3. Эмулятор подключается к парному порту (например, COM5)
 * 4. Для Linux использовать socat или аналоги
 * <p>Запуск из IDE: Run File на этом классе (см. {@link #main(String[])}).</p>
 * <p>В JavaPOS 1.14 {@code jpos/res/jpos.properties} по умолчанию читается через {@code ClassLoader#getResourceAsStream},
 * а не с диска; перед тестами конфиг подхватывается с диска в {@code System} и задаётся абсолютный
 * {@code jpos.config.populatorFile}. Каталог {@code user.dir} выставляется под XML/DTD.
 * Предпочтителен {@code test/resources} (не {@code res}), см. {@link #installJposForAcceptanceTests()}.</p>
 * 
 * @version 1.0
 */
public class ScaleAcceptanceTest {

    /**
     * Запуск всех тестов класса через JUnit (удобно для NetBeans: Run File).
     * Системные свойства те же, что и у класса: {@code -Demulator.port=...}, {@code -Dlogical.device.name=...}.
     */
    public static void main(String[] args) throws Exception {
        installJposForAcceptanceTests();
        JUnitCore.main(ScaleAcceptanceTest.class.getName());
    }

    private static boolean isJposTestResourcesBundle(Path root) {
        return Files.isRegularFile(root.resolve("jpos/res/jpos.properties"))
                && Files.isRegularFile(root.resolve("jpos.xml"));
    }

    /**
     * Каталог с парой {@code jpos/res/jpos.properties} + {@code jpos.xml}.
     * Если подходит и {@code res}, и {@code test/resources}, выбирается {@code test/resources}
     * (там приёмочный {@code jpos.xml} с {@code ScaleSimulator}).
     */
    private static Path findAcceptanceJposConfigDirectory() {
        List<Path> matches = new ArrayList<>();
        String override = System.getProperty("acceptance.jpos.root");
        if (override != null && !override.trim().isEmpty()) {
            Path p = Paths.get(override.trim()).toAbsolutePath().normalize();
            if (isJposTestResourcesBundle(p)) {
                matches.add(p);
            }
        }
        Path[] candidates = new Path[] {
                Paths.get("test/resources"),
                Paths.get("pcscale/test/resources"),
                Paths.get("src/pcscale/test/resources"),
        };
        for (Path c : candidates) {
            Path p = c.toAbsolutePath().normalize();
            if (isJposTestResourcesBundle(p)) {
                matches.add(p);
            }
        }
        Path here = Paths.get("").toAbsolutePath();
        Path cur = here;
        for (int i = 0; i < 8 && cur != null; i++) {
            Path tr = cur.resolve("test/resources");
            if (isJposTestResourcesBundle(tr)) {
                matches.add(tr);
            }
            tr = cur.resolve("src/pcscale/test/resources");
            if (isJposTestResourcesBundle(tr)) {
                matches.add(tr);
            }
            cur = cur.getParent();
        }
        if (matches.isEmpty()) {
            return null;
        }
        Path testResources = Paths.get("test", "resources").normalize();
        for (Path p : matches) {
            if (p.endsWith(testResources)) {
                return p;
            }
        }
        return matches.get(0);
    }

    /**
     * JavaPOS 1.14: {@code DefaultProperties} ищет {@code jpos/res/jpos.properties} в classpath и пишет
     * «file not found», если ресурса нет — дублируем содержимое файла в {@code System#setProperty}
     * и задаём абсолютный {@code jpos.config.populatorFile}. Плюс {@code user.dir} для разбора XML/DTD.
     */
    private static void installJposForAcceptanceTests() throws Exception {
        Path root = findAcceptanceJposConfigDirectory();
        if (root == null) {
            throw new IllegalStateException(
                    "Не найден каталог с jpos/res/jpos.properties и jpos.xml. "
                            + "Запустите из корня модуля pcscale или укажите -Dacceptance.jpos.root=<путь_к_test/resources>");
        }
        System.setProperty("user.dir", root.toAbsolutePath().normalize().toString());

        Path propsPath = root.resolve("jpos/res/jpos.properties");
        Properties jposProps = new Properties();
        try (InputStream in = Files.newInputStream(propsPath)) {
            jposProps.load(in);
        }
        for (String name : jposProps.stringPropertyNames()) {
            if (System.getProperty(name) == null) {
                System.setProperty(name, jposProps.getProperty(name));
            }
        }
        if (System.getProperty("jpos.config.populatorFile") == null) {
            System.setProperty(
                    "jpos.config.populatorFile",
                    root.resolve("jpos.xml").toAbsolutePath().normalize().toString());
        }
        System.out.println("[ScaleAcceptanceTest] JavaPOS bundle (filesystem): " + root.toAbsolutePath());
    }
    
    // Читаем параметры из System.getProperty()
    // Порт эмулятора (менять под свою конфигурацию)
    private static final String EMULATOR_PORT = System.getProperty("emulator.port", "COM7");
    // Логическое имя в JPos конфигурации
    private static final String LOGICAL_DEVICE_NAME = System.getProperty("logical.device.name", "ScaleSimulator");    
    
    private static Pos2ProtocolEmulator emulator;
    private static Scale scale;
    
    private CountDownLatch eventLatch = new CountDownLatch(1);
    private final AtomicInteger receivedStatus = new AtomicInteger(0);
    private final AtomicInteger receivedErrorCode = new AtomicInteger(0);
    private final AtomicInteger receivedDataStatus = new AtomicInteger(0);
    private final AtomicReference<String> directIOData = new AtomicReference<>();
    private final AtomicBoolean dataEventReceived = new AtomicBoolean(false);
    private final AtomicBoolean outputCompleteReceived = new AtomicBoolean(false);
    
    private static final int DEFAULT_TIMEOUT = 10000;
    private static final int WEIGHT_STABLE_DELAY = 500;
    
    // Константы для весов (дублируем из ScaleConst, чтобы не зависеть от версии)
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
    
    private DataListener dataListener;
    private ErrorListener errorListener;
    private StatusUpdateListener statusUpdateListener;
    private DirectIOListener directIOListener;
    
    @Rule
    public Timeout globalTimeout = Timeout.seconds(20);
    
    // ------------------------------------------------------------------------
    // Setup and teardown
    // ------------------------------------------------------------------------
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        installJposForAcceptanceTests();
        // Запуск эмулятора
        emulator = new Pos2ProtocolEmulator(EMULATOR_PORT);
        emulator.start();
        
        // Небольшая пауза для инициализации порта
        Thread.sleep(1000);
        
        // Инициализация JPos Scale
        scale = new Scale();
    }
    
    @AfterClass
    public static void tearDownClass() {
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
    
    @Before
    public void setUp() throws Exception {
        eventLatch = new CountDownLatch(1);
        receivedStatus.set(0);
        receivedErrorCode.set(0);
        receivedDataStatus.set(0);
        directIOData.set(null);
        dataEventReceived.set(false);
        outputCompleteReceived.set(false);
        
        // Открываем устройство для каждого теста
        try {
            scale.open(LOGICAL_DEVICE_NAME);
            Thread.sleep(500);
        } catch (JposException e) {
            // Может быть уже открыто
            if (e.getErrorCode() != JposConst.JPOS_E_ILLEGAL) {
                throw e;
            }
        }
        
        // Создаем слушателей
        dataListener = new DataListener() {
            @Override
            public void dataOccurred(DataEvent e) {
                dataEventReceived.set(true);
                receivedDataStatus.set(e.getStatus());
                eventLatch.countDown();
                System.out.println("DataEvent received, status=" + e.getStatus());
            }
        };
        
        errorListener = new ErrorListener() {
            @Override
            public void errorOccurred(ErrorEvent e) {
                receivedErrorCode.set(e.getErrorCode());
                eventLatch.countDown();
                System.out.println("ErrorEvent received, code=" + e.getErrorCode() + 
                                 ", extended=" + e.getErrorCodeExtended() + 
                                 ", locus=" + e.getErrorLocus());
            }
        };
        
        statusUpdateListener = new StatusUpdateListener() {
            @Override
            public void statusUpdateOccurred(StatusUpdateEvent e) {
                receivedStatus.set(e.getStatus());
                eventLatch.countDown();
                System.out.println("StatusUpdateEvent received, status=" + e.getStatus());
            }
        };
        
        directIOListener = new DirectIOListener() {
            @Override
            public void directIOOccurred(DirectIOEvent e) {
                directIOData.set("EventNumber=" + e.getEventNumber() + ", Data=" + e.getData());
                eventLatch.countDown();
                System.out.println("DirectIOEvent received: " + directIOData.get());
            }
        };
        
        // Регистрируем слушателей
        scale.addDataListener(dataListener);
        scale.addErrorListener(errorListener);
        scale.addStatusUpdateListener(statusUpdateListener);
        scale.addDirectIOListener(directIOListener);
    }
    
    @After
    public void tearDown() throws Exception {
        try {
            // Удаляем слушателей
            if (dataListener != null) {
                scale.removeDataListener(dataListener);
            }
            if (errorListener != null) {
                scale.removeErrorListener(errorListener);
            }
            if (statusUpdateListener != null) {
                scale.removeStatusUpdateListener(statusUpdateListener);
            }
            if (directIOListener != null) {
                scale.removeDirectIOListener(directIOListener);
            }
            
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
        try {
            Thread.sleep(WEIGHT_STABLE_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void releaseAndDisable() throws JposException {
        if (scale.getDeviceEnabled()) {
            scale.setDeviceEnabled(false);
        }
        if (scale.getClaimed()) {
            scale.release();
        }
    }
    
    private boolean waitForEvent(long timeoutMs) {
        try {
            return eventLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    // ------------------------------------------------------------------------
    // Test 1: Device initialization
    // ------------------------------------------------------------------------
    
    @Test
    public void testOpenAndClose() throws JposException {
        System.out.println("\n=== Test 1: Device initialization ===");
        
        // Уже открыто в setUp, закрываем и открываем заново
        scale.close();
        scale.open(LOGICAL_DEVICE_NAME);
        
        // Проверка, что устройство открыто
        assertNotEquals(JposConst.JPOS_S_CLOSED, scale.getState());
        assertEquals(JposConst.JPOS_S_IDLE, scale.getState());
        
        // Проверка основных свойств после открытия
        assertNotNull(scale.getDeviceControlDescription());
        assertNotNull(scale.getDeviceServiceDescription());
        assertNotNull(scale.getPhysicalDeviceDescription());
        assertNotNull(scale.getPhysicalDeviceName());
        
        System.out.println("DeviceControlDescription: " + scale.getDeviceControlDescription());
        System.out.println("DeviceServiceDescription: " + scale.getDeviceServiceDescription());
        System.out.println("PhysicalDeviceDescription: " + scale.getPhysicalDeviceDescription());
        System.out.println("PhysicalDeviceName: " + scale.getPhysicalDeviceName());
        
        // Проверка версий
        assertTrue(scale.getDeviceControlVersion() > 0);
        assertTrue(scale.getDeviceServiceVersion() > 0);
        
        System.out.println("DeviceControlVersion: " + scale.getDeviceControlVersion());
        System.out.println("DeviceServiceVersion: " + scale.getDeviceServiceVersion());
        
        // Проверка закрытия
        scale.close();
        assertEquals(JposConst.JPOS_S_CLOSED, scale.getState());
        
        System.out.println("Test 1 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 2: Claim and release
    // ------------------------------------------------------------------------
    
    @Test
    public void testClaimAndRelease() throws JposException {
        System.out.println("\n=== Test 2: Claim and release ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        
        // Проверка, что устройство не захвачено
        assertFalse(scale.getClaimed());
        
        // Захват устройства
        scale.claim(5000);
        assertTrue(scale.getClaimed());
        
        // Повторный захват не должен вызвать ошибку
        scale.claim(5000);
        assertTrue(scale.getClaimed());
        
        // Освобождение устройства
        scale.release();
        assertFalse(scale.getClaimed());
        
        scale.close();
        
        System.out.println("Test 2 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 3: Capabilities properties
    // ------------------------------------------------------------------------
    
    @Test
    public void testCapabilities() throws JposException {
        System.out.println("\n=== Test 3: Capabilities properties ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Проверка всех capability свойств
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
        
        // Свойства из версии 1.14
        try {
            boolean capFreezeValue = scale.getCapFreezeValue();
            System.out.println("CapFreezeValue: " + capFreezeValue);
        } catch (JposException e) {
            System.out.println("CapFreezeValue not supported");
        }
        
        try {
            boolean capReadLiveWeightWithTare = scale.getCapReadLiveWeightWithTare();
            System.out.println("CapReadLiveWeightWithTare: " + capReadLiveWeightWithTare);
        } catch (JposException e) {
            System.out.println("CapReadLiveWeightWithTare not supported");
        }
        
        try {
            boolean capSetPriceCalculationMode = scale.getCapSetPriceCalculationMode();
            System.out.println("CapSetPriceCalculationMode: " + capSetPriceCalculationMode);
        } catch (JposException e) {
            System.out.println("CapSetPriceCalculationMode not supported");
        }
        
        try {
            boolean capSetUnitPriceWithWeightUnit = scale.getCapSetUnitPriceWithWeightUnit();
            System.out.println("CapSetUnitPriceWithWeightUnit: " + capSetUnitPriceWithWeightUnit);
        } catch (JposException e) {
            System.out.println("CapSetUnitPriceWithWeightUnit not supported");
        }
        
        try {
            boolean capSpecialTare = scale.getCapSpecialTare();
            System.out.println("CapSpecialTare: " + capSpecialTare);
        } catch (JposException e) {
            System.out.println("CapSpecialTare not supported");
        }
        
        try {
            boolean capTarePriority = scale.getCapTarePriority();
            System.out.println("CapTarePriority: " + capTarePriority);
        } catch (JposException e) {
            System.out.println("CapTarePriority not supported");
        }
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 3 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 4: Basic properties (MaximumWeight, WeightUnit, etc.)
    // ------------------------------------------------------------------------
    
    @Test
    public void testBasicProperties() throws JposException {
        System.out.println("\n=== Test 4: Basic properties ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Проверка MaximumWeight
        int maxWeight = scale.getMaximumWeight();
        assertTrue(maxWeight > 0);
        System.out.println("MaximumWeight: " + maxWeight);
        
        // Проверка WeightUnit
        int weightUnit = scale.getWeightUnit();
        assertTrue(weightUnit >= SCAL_WU_GRAM && weightUnit <= SCAL_WU_POUND);
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
        assertTrue(scale.getAsyncMode());
        scale.setAsyncMode(false);
        assertFalse(scale.getAsyncMode());
        
        // Проверка AutoDisable
        boolean autoDisable = scale.getAutoDisable();
        System.out.println("AutoDisable (initial): " + autoDisable);
        scale.setAutoDisable(true);
        assertTrue(scale.getAutoDisable());
        scale.setAutoDisable(false);
        assertFalse(scale.getAutoDisable());
        
        // Проверка ZeroValid (если поддерживается)
        try {
            boolean zeroValid = scale.getZeroValid();
            System.out.println("ZeroValid (initial): " + zeroValid);
            scale.setZeroValid(true);
            assertTrue(scale.getZeroValid());
            scale.setZeroValid(false);
            assertFalse(scale.getZeroValid());
        } catch (JposException e) {
            System.out.println("ZeroValid not supported");
        }
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 4 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 5: Synchronous weight reading
    // ------------------------------------------------------------------------
    
    @Test
    public void testSynchronousReadWeight() throws JposException, InterruptedException {
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
        assertEquals(1000, weight[0]);
        System.out.println("Read weight: " + weight[0]);
        
        // Тест 2: Чтение веса 2500 грамм
        emulator.setWeight(2500);
        Thread.sleep(WEIGHT_STABLE_DELAY);
        
        scale.readWeight(weight, 5000);
        assertEquals(2500, weight[0]);
        System.out.println("Read weight: " + weight[0]);
        
        // Тест 3: Чтение с таймаутом (вес нестабилен)
        emulator.setStable(false);
        weight[0] = 0;
        
        try {
            scale.readWeight(weight, 2000);
            fail("Should throw exception when weight is unstable");
        } catch (JposException e) {
            System.out.println("Correctly got exception for unstable weight: " + e.getMessage());
        }
        
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
    public void testAsynchronousReadWeight() throws JposException, InterruptedException {
        System.out.println("\n=== Test 6: Asynchronous weight reading ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Устанавливаем асинхронный режим
        scale.setAsyncMode(true);
        scale.setDataEventEnabled(true);
        
        // Создаем новый latch для этого теста
        eventLatch = new CountDownLatch(1);
        dataEventReceived.set(false);
        
        // Устанавливаем вес и запускаем асинхронное чтение
        emulator.setWeight(1500);
        emulator.setStable(true);
        
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        assertEquals(0, weight[0]); // Weight should be 0 in async mode (return value)
        System.out.println("Async read initiated");
        
        // Ожидаем DataEvent
        boolean eventReceived = waitForEvent(6000);
        assertTrue("DataEvent should be received", eventReceived);
        assertTrue(dataEventReceived.get());
        
        // Проверяем вес в событии
        assertEquals(1500, receivedDataStatus.get());
        System.out.println("Async weight received via event: " + receivedDataStatus.get());
        
        // Проверка AutoDisable при асинхронном чтении
        scale.setAutoDisable(true);
        eventLatch = new CountDownLatch(1);
        dataEventReceived.set(false);
        
        emulator.setWeight(2000);
        scale.readWeight(weight, 5000);
        
        eventReceived = waitForEvent(6000);
        assertTrue("DataEvent should be received with AutoDisable", eventReceived);
        
        // Устройство должно быть автоматически отключено
        Thread.sleep(500);
        assertFalse(scale.getDeviceEnabled());
        
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
    public void testTareOperations() throws JposException, InterruptedException {
        System.out.println("\n=== Test 7: Tare operations ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        scale.setAsyncMode(false);
        
        // Проверяем поддержку тары
        if (!scale.getCapTareWeight()) {
            System.out.println("Skipping test: CapTareWeight is false");
            releaseAndDisable();
            scale.close();
            return;
        }
        
        // Тест 1: Установка веса тары через свойство
        emulator.setWeight(500);
        emulator.setStable(true);
        
        scale.setTareWeight(300);
        assertEquals(300, scale.getTareWeight());
        System.out.println("TareWeight set to: " + scale.getTareWeight());
        
        // Чтение веса после установки тары
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        System.out.println("Weight after tare set: " + weight[0]);
        
        // Тест 2: Сброс тары
        scale.setTareWeight(0);
        assertEquals(0, scale.getTareWeight());
        System.out.println("TareWeight reset to 0");
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 7 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 8: Zero scale operation
    // ------------------------------------------------------------------------
    
    @Test
    public void testZeroScale() throws JposException, InterruptedException {
        System.out.println("\n=== Test 8: Zero scale operation ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        
        if (!scale.getCapZeroScale()) {
            System.out.println("Skipping test: CapZeroScale is false");
            scale.close();
            return;
        }
        
        claimAndEnable();
        scale.setAsyncMode(false);
        
        // Устанавливаем ненулевой вес
        emulator.setWeight(100);
        emulator.setStable(true);
        Thread.sleep(WEIGHT_STABLE_DELAY);
        
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        assertTrue(weight[0] > 0);
        System.out.println("Weight before zero: " + weight[0]);
        
        // Обнуление
        scale.zeroScale();
        
        // Сбрасываем команды
        emulator.clearCommands();
        
        // Проверяем, что вес стал 0
        scale.readWeight(weight, 5000);
        assertEquals(0, weight[0]);
        System.out.println("Weight after zero: " + weight[0]);
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 8 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 9: Display text operation
    // ------------------------------------------------------------------------
    
    @Test
    public void testDisplayText() throws JposException {
        System.out.println("\n=== Test 9: Display text operation ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        if (!scale.getCapDisplayText()) {
            System.out.println("Skipping test: CapDisplayText is false");
            releaseAndDisable();
            scale.close();
            return;
        }
        
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
            StringBuilder longText = new StringBuilder();
            for (int i = 0; i < maxChars + 10; i++) {
                longText.append("X");
            }
            scale.displayText(longText.toString());
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
    public void testPriceCalculating() throws JposException, InterruptedException {
        System.out.println("\n=== Test 10: Price calculating operations ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        if (!scale.getCapPriceCalculating()) {
            System.out.println("Skipping test: CapPriceCalculating is false");
            releaseAndDisable();
            scale.close();
            return;
        }
        
        // Установка цены за единицу
        long unitPrice = 15000; // 1.5000
        scale.setUnitPrice(unitPrice);
        assertEquals(unitPrice, scale.getUnitPrice());
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
        
        // Асинхронное чтение с вычислением цены
        scale.setAsyncMode(true);
        scale.setDataEventEnabled(true);
        eventLatch = new CountDownLatch(1);
        dataEventReceived.set(false);
        
        emulator.setWeight(3500);
        weight[0] = 0;
        scale.readWeight(weight, 5000);
        
        boolean eventReceived = waitForEvent(6000);
        assertTrue("DataEvent should be received for price calculation", eventReceived);
        
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
    public void testPowerAndStatusNotifications() throws JposException, InterruptedException {
        System.out.println("\n=== Test 11: Power and status notifications ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        
        int capPower = scale.getCapPowerReporting();
        if (capPower == JposConst.JPOS_PR_NONE) {
            System.out.println("Skipping power test: CapPowerReporting is NONE");
            scale.close();
            return;
        }
        
        // Включение уведомлений о питании
        scale.setPowerNotify(JposConst.JPOS_PN_ENABLED);
        assertEquals(JposConst.JPOS_PN_ENABLED, scale.getPowerNotify());
        
        claimAndEnable();
        
        // Проверка состояния питания
        int powerState = scale.getPowerState();
        System.out.println("PowerState: " + powerState);
        assertTrue(powerState == JposConst.JPOS_PS_ONLINE || 
                   powerState == JposConst.JPOS_PS_UNKNOWN);
        
        // Проверка уведомлений о статусе весов
        if (scale.getCapStatusUpdate()) {
            scale.setStatusNotify(SCAL_SN_ENABLED);
            assertEquals(SCAL_SN_ENABLED, scale.getStatusNotify());
            
            eventLatch = new CountDownLatch(1);
            
            // Изменяем состояние весов в эмуляторе
            emulator.setStable(false);
            Thread.sleep(1000);
            
            emulator.setStable(true);
            emulator.setWeight(100);
            Thread.sleep(1000);
        }
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 11 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 12: Statistics operations
    // ------------------------------------------------------------------------
    
    @Test
    public void testStatistics() throws JposException {
        System.out.println("\n=== Test 12: Statistics operations ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        if (!scale.getCapStatisticsReporting()) {
            System.out.println("Skipping test: CapStatisticsReporting is false");
            releaseAndDisable();
            scale.close();
            return;
        }
        
        // Получение статистики
        String[] statsBuffer = new String[1];
        scale.retrieveStatistics(statsBuffer);
        assertNotNull(statsBuffer[0]);
        System.out.println("Retrieved statistics: " + statsBuffer[0]);
        
        // Сброс статистики (если поддерживается)
        if (scale.getCapUpdateStatistics()) {
            scale.resetStatistics("");
            System.out.println("Statistics reset");
            
            // Проверка после сброса
            scale.retrieveStatistics(statsBuffer);
            System.out.println("Statistics after reset: " + statsBuffer[0]);
        }
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 12 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 13: Clear input operations
    // ------------------------------------------------------------------------
    
    @Test
    public void testClearInput() throws JposException, InterruptedException {
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
        assertTrue(dataCount > 0);
        System.out.println("DataCount before clear: " + dataCount);
        
        // Очистка входного буфера
        scale.clearInput();
        
        dataCount = scale.getDataCount();
        assertEquals(0, dataCount);
        System.out.println("DataCount after clear: " + dataCount);
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 13 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 14: FreezeEvents property
    // ------------------------------------------------------------------------
    
    @Test
    public void testFreezeEvents() throws JposException, InterruptedException {
        System.out.println("\n=== Test 14: FreezeEvents property ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        scale.setAsyncMode(true);
        scale.setDataEventEnabled(true);
        
        // Замораживаем события
        scale.setFreezeEvents(true);
        assertTrue(scale.getFreezeEvents());
        
        // Запускаем асинхронное чтение
        eventLatch = new CountDownLatch(1);
        dataEventReceived.set(false);
        
        emulator.setWeight(500);
        emulator.setStable(true);
        
        int[] weight = new int[1];
        scale.readWeight(weight, 5000);
        
        // Ждём немного - событие не должно прийти
        Thread.sleep(2000);
        assertFalse(dataEventReceived.get());
        System.out.println("Event correctly frozen");
        
        // Размораживаем события
        scale.setFreezeEvents(false);
        
        // Событие должно прийти
        boolean eventReceived = waitForEvent(5000);
        assertTrue(eventReceived);
        System.out.println("Event received after unfreeze");
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 14 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 15: Edge cases and error handling
    // ------------------------------------------------------------------------
    
    @Test
    public void testEdgeCasesAndErrors() throws JposException, InterruptedException {
        System.out.println("\n=== Test 15: Edge cases and error handling ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        
        // Проверка: вызов методов без claim
        try {
            scale.setDeviceEnabled(true);
            fail("Should throw exception when not claimed");
        } catch (JposException e) {
            System.out.println("Correctly got exception for setDeviceEnabled without claim: " + e.getMessage());
        }
        
        claimAndEnable();
        
        // Проверка: чтение веса с некорректным таймаутом
        scale.setAsyncMode(false);
        int[] weight = new int[1];
        
        try {
            scale.readWeight(weight, -2);
            fail("Should throw exception for invalid timeout");
        } catch (JposException e) {
            System.out.println("Correctly got exception for invalid timeout: " + e.getMessage());
        }
        
        // Проверка: установка некорректной цены (если цена должна быть положительной)
        if (scale.getCapPriceCalculating()) {
            try {
                scale.setUnitPrice(-1);
                fail("Should throw exception for negative unit price");
            } catch (JposException e) {
                System.out.println("Correctly got exception for negative unit price: " + e.getMessage());
            }
        }
        
        // Проверка: переполнение веса
        emulator.setWeight(100000);
        emulator.setStable(true);
        
        try {
            scale.readWeight(weight, 3000);
            System.out.println("Warning: Overweight not detected by driver");
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
    public void testDeviceInformation() throws JposException {
        System.out.println("\n=== Test 16: Device information ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        
        // Проверка всех информационных свойств
        String checkHealthText = scale.getCheckHealthText();
        System.out.println("CheckHealthText: " + checkHealthText);
        
        // Проверка состояния устройства
        int state = scale.getState();
        assertEquals(JposConst.JPOS_S_IDLE, state);
        System.out.println("State: " + state);
        
        // Проверка DataCount (должен быть 0 после open)
        assertEquals(0, scale.getDataCount());
        
        scale.close();
        
        System.out.println("Test 16 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 17: Health check
    // ------------------------------------------------------------------------
    
    @Test
    public void testCheckHealth() throws JposException {
        System.out.println("\n=== Test 17: Health check ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Внутренняя проверка
        scale.checkHealth(JposConst.JPOS_CH_INTERNAL);
        String healthText = scale.getCheckHealthText();
        System.out.println("Internal health check: " + healthText);
        assertNotNull(healthText);
        
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
    public void testDirectIO() throws JposException {
        System.out.println("\n=== Test 18: DirectIO operation ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        
        // Тест DirectIO (зависит от конкретной реализации драйвера)
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
    public void testConcurrentOperations() throws JposException, InterruptedException {
        System.out.println("\n=== Test 19: Concurrent operations ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        scale.setAsyncMode(true);
        scale.setDataEventEnabled(true);
        
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);
        
        // Запуск нескольких параллельных операций чтения
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        for (int i = 0; i < 5; i++) {
            final int weight = 100 + i * 100;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        emulator.setWeight(weight);
                        emulator.setStable(true);
                        
                        int[] w = new int[1];
                        scale.readWeight(w, 5000);
                        successCount.incrementAndGet();
                    } catch (JposException e) {
                        errorCount.incrementAndGet();
                    }
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // Должна быть только одна успешная операция
        System.out.println("Success count: " + successCount.get() + ", Error count: " + errorCount.get());
        assertTrue(errorCount.get() > 0);
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 19 passed!");
    }
    
    // ------------------------------------------------------------------------
    // Test 20: ZeroValid property test (if supported)
    // ------------------------------------------------------------------------
    
    @Test
    public void testZeroValidProperty() throws JposException, InterruptedException {
        System.out.println("\n=== Test 20: ZeroValid property test ===");
        
        scale.open(LOGICAL_DEVICE_NAME);
        claimAndEnable();
        scale.setAsyncMode(false);
        
        // Проверяем, поддерживается ли ZeroValid
        try {
            boolean zeroValid = scale.getZeroValid();
            System.out.println("ZeroValid initial: " + zeroValid);
        } catch (JposException e) {
            if (e.getErrorCode() == JposConst.JPOS_E_NOSERVICE) {
                System.out.println("ZeroValid not supported (service version < 1.13)");
                releaseAndDisable();
                scale.close();
                return;
            }
            throw e;
        }
        
        // Устанавливаем вес 0
        emulator.setWeight(0);
        emulator.setStable(true);
        Thread.sleep(WEIGHT_STABLE_DELAY);
        
        int[] weight = new int[1];
        
        // При ZeroValid = false, вес 0 не должен возвращаться
        scale.setZeroValid(false);
        
        try {
            scale.readWeight(weight, 3000);
            fail("Should throw exception for zero weight when ZeroValid=false");
        } catch (JposException e) {
            System.out.println("Correctly got exception for zero weight with ZeroValid=false: " + e.getMessage());
        }
        
        // При ZeroValid = true, вес 0 должен возвращаться
        scale.setZeroValid(true);
        scale.readWeight(weight, 3000);
        assertEquals(0, weight[0]);
        System.out.println("Zero weight returned successfully with ZeroValid=true");
        
        releaseAndDisable();
        scale.close();
        
        System.out.println("Test 20 passed!");
    }
}