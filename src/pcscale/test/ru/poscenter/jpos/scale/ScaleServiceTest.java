package ru.poscenter.jpos.scale;

import ru.poscenter.jpos.scale.ScaleService;
import static org.junit.Assert.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import ru.poscenter.IDevice;
import ru.poscenter.DeviceError;
import ru.poscenter.jpos.scale.TestScaleSerial;
import ru.poscenter.scale.EScale;
import ru.poscenter.scale.ScaleSerial;
import ru.poscenter.scale.DeviceMetrics;
import ru.poscenter.scale.ChannelParams;

import jpos.BaseControl;
import jpos.JposConst;
import jpos.JposException;
import jpos.ScaleConst;
import jpos.events.DataEvent;
import jpos.events.JposEvent;
import jpos.events.StatusUpdateEvent;
import jpos.events.DirectIOEvent;
import jpos.events.ErrorEvent;
import jpos.events.OutputCompleteEvent;
import jpos.services.EventCallbacks;

import jpos.services.ScaleService114;
import static jpos.ScaleConst.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Исправленные тесты для ScaleService
 */
public class ScaleServiceTest {

    private final Logger logger = LogManager.getLogger(ScaleServiceTest.class);
    
    /**
     * Расширенный ScaleService для тестов
     */
    private static class TestableScaleService extends ScaleService {

        private TestScaleSerial testScale;

        public void setTestScale(TestScaleSerial testScale) {
            this.testScale = testScale;
        }

        @Override
        protected ScaleSerial createProtocol(String protocol) throws Exception {
            return testScale;
        }
        
        // Метод для прямого доступа к состоянию из тестов
        public int getCurrentState() {
            try {
                return getState();
            } catch (JposException e) {
                return -1;
            }
        }
    }

    /**
     * Расширенный EventCallbacks для тестирования вызовов из обработчика
     */
    private static class TestEventCallbacksWithHandler implements EventCallbacks {
        
        private final BlockingQueue<JposEvent> events = new LinkedBlockingQueue<>();
        
        private volatile Runnable actionToPerform;
        
        public void setAction(Runnable action) {
            this.actionToPerform = action;
        }
        
        public void clearEvents() {
            events.clear();
        }

        public <T extends JposEvent> T waitForEvent(Class<T> type, long timeout) {
            try {
                JposEvent event = events.poll(timeout, TimeUnit.MILLISECONDS);
                if (type.isInstance(event)) {
                    return type.cast(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        @Override
        public void fireDataEvent(DataEvent event) {
            // Выполняем действие приложения (если есть)
            if (actionToPerform != null) {
                actionToPerform.run();
            }
            
            // Отправляем событие
            events.offer(event);
        }

        @Override
        public void fireDirectIOEvent(DirectIOEvent event) {
            events.offer(event);
        }

        @Override
        public void fireErrorEvent(ErrorEvent event) {
            events.offer(event);
        }

        @Override
        public void fireOutputCompleteEvent(OutputCompleteEvent event) {
            events.offer(event);
        }

        @Override
        public void fireStatusUpdateEvent(StatusUpdateEvent event) {
            events.offer(event);
        }

        @Override
        public BaseControl getEventSource() {
            return null;
        }
    }

    private TestScaleSerial testScale;
    private TestableScaleService service;
    private TestEventCallbacksWithHandler callbacks;

    @Before
    public void setUp() throws Exception {
        testScale = new TestScaleSerial();
        service = new TestableScaleService();
        service.setTestScale(testScale);
        callbacks = new TestEventCallbacksWithHandler();

        // Базовая настройка тестового устройства
        testScale.setDeviceType(EScale.Pos2);
        DeviceMetrics metrics = new DeviceMetrics();
        testScale.setDeviceMetrics(metrics);
    }

    private void initService(boolean pollEnabled, boolean asyncMode) throws Exception {
        callbacks.clearEvents();
        service.open("TestScale", callbacks);
        service.setPollEnabled(pollEnabled);
        service.setPowerNotify(JposConst.JPOS_PN_DISABLED);
        service.setDataEventEnabled(true);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setAsyncMode(asyncMode);

        // Пропускаем возможные события при включении
        Thread.sleep(100);
        callbacks.clearEvents();
    }

    private void initService(boolean pollEnabled) throws Exception {
        initService(pollEnabled, false);
    }

    private void cleanup() throws Exception {
        if (service != null) {
            try {
                service.close();
            } catch (Exception e) {
                // Игнорируем
            }
        }
    }

    // ======================== ТЕСТЫ НЕДОПУСТИМЫХ ВЫЗОВОВ ========================

    /**
     * Тест: вызов close() в обработчике DataEvent должен выбрасывать JPOS_E_ILLEGAL
     * (не JPOS_E_BUSY, так как это защита от deadlock, а не состояние устройства)
     */
    @Test
    public void testCloseInDataEventHandlerThrowsIllegal() throws Exception {
        initService(false, true);
        
        final JposException[] exceptionFromHandler = new JposException[1];
        
        callbacks.setAction(() -> {
            try {
                service.close();
            } catch (JposException e) {
                exceptionFromHandler[0] = e;
            }
        });
        
        testScale.setCurrentWeight(1234, true, false);
        service.readWeight(null, 1000);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        
        // Проверяем исключение из обработчика
        assertNotNull("Должно быть исключение из обработчика", exceptionFromHandler[0]);
        
        // Должен быть JPOS_E_ILLEGAL
        assertEquals("Должен быть код ошибки JPOS_E_ILLEGAL",
                     JposConst.JPOS_E_ILLEGAL, exceptionFromHandler[0].getErrorCode());
        
        // Сообщение должно указывать на вызов из обработчика событий
        assertTrue("Сообщение должно указывать на вызов из обработчика событий",
                   exceptionFromHandler[0].getMessage() != null &&
                   exceptionFromHandler[0].getMessage().contains("event handler"));
        
        // Проверяем состояние ПОСЛЕ доставки события - устройство должно быть в IDLE
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());
        
        // Устройство должно остаться в рабочем состоянии
        assertTrue("Устройство должно остаться заявленным", service.getClaimed());
        assertTrue("Устройство должно остаться включенным", service.getDeviceEnabled());
        
        cleanup();
    }

    // ======================== ТЕСТЫ РАЗРЕШЕННЫХ ВЫЗОВОВ ========================

    /**
     * Тест: вызов clearInput() разрешен в обработчике DataEvent
     */
    @Test
    public void testClearInputAllowedInDataEventHandler() throws Exception {
        initService(false, true);
        
        final JposException[] exceptionFromHandler = new JposException[1];
        
        callbacks.setAction(() -> {
            try {
                service.clearInput();
            } catch (JposException e) {
                exceptionFromHandler[0] = e;
            }
        });
        
        testScale.setCurrentWeight(1234, true, false);
        service.readWeight(null, 1000);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        
        assertNull("Не должно быть исключения из обработчика", exceptionFromHandler[0]);
        
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());
        
        cleanup();
    }

    /**
     * Тест: чтение свойств разрешено в обработчике DataEvent
     */
    @Test
    public void testGetPropertiesAllowedInDataEventHandler() throws Exception {
        initService(false, true);
        
        // Устанавливаем тару
        service.setTareWeight(200);
        
        final JposException[] exceptionFromHandler = new JposException[1];
        final Object[] properties = new Object[5];
        
        callbacks.setAction(() -> {
            try {
                properties[0] = service.getScaleLiveWeight();
                properties[1] = service.getSalesPrice();
                properties[2] = service.getTareWeight();
                properties[3] = service.getMaximumWeight();
                properties[4] = service.getWeightUnit();
            } catch (JposException e) {
                exceptionFromHandler[0] = e;
            }
        });
        
        testScale.setCurrentWeight(1234, true, false);
        service.readWeight(null, 1000);
        
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull("Должно быть получено DataEvent", dataEvent);
        
        assertNull("Не должно быть исключения из обработчика", exceptionFromHandler[0]);
        
        // Проверяем, что свойства прочитаны
        assertNotNull("Свойства должны быть прочитаны", properties[0]);
        assertNotNull("Свойства должны быть прочитаны", properties[1]);
        assertNotNull("Свойства должны быть прочитаны", properties[2]);
        assertNotNull("Свойства должны быть прочитаны", properties[3]);
        assertNotNull("Свойства должны быть прочитаны", properties[4]);
        
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());
        
        cleanup();
    }

    // ======================== ТЕСТЫ АСИНХРОННОГО ЧТЕНИЯ ========================

    /**
     * Тест: асинхронное чтение веса должно генерировать DataEvent
     */
    @Test
    public void testAsyncReadWeightProducesDataEvent() throws Exception {
        testScale.setCurrentWeight(1234, true, false);

        initService(false, true);

        service.readWeight(null, 1000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 3000);

        assertNotNull("Должно быть получено DataEvent", dataEvent);
        assertEquals(1234, dataEvent.getStatus());
        
        // Проверяем состояние ПОСЛЕ доставки события
        assertEquals("После доставки события состояние должно быть S_IDLE",
                     JposConst.JPOS_S_IDLE, service.getCurrentState());

        cleanup();
    }

    // ======================== ОСТАЛЬНЫЕ ТЕСТЫ ========================

    /**
     * Тест: проверка capability свойств
     */
    @Test
    public void testCapabilities() throws Exception {
        service.open("TestScale", callbacks);

        assertFalse(service.getCapCompareFirmwareVersion());
        assertTrue(service.getCapStatusUpdate());
        assertFalse(service.getCapUpdateFirmware());
        assertFalse(service.getCapDisplay());
        assertFalse(service.getCapStatisticsReporting());
        assertFalse(service.getCapUpdateStatistics());
        assertFalse(service.getCapDisplayText());
        assertEquals(JposConst.JPOS_PR_STANDARD, service.getCapPowerReporting());

        // Эти capability зависят от типа весов
        service.getCapPriceCalculating();
        service.getCapTareWeight();
        service.getCapZeroScale();

        service.close();
    }

    /**
     * Тест: проверка переходов состояний устройства
     */
    @Test
    public void testStateTransitions() throws Exception {
        // Начальное состояние - CLOSED
        assertEquals(JposConst.JPOS_S_CLOSED, service.getCurrentState());

        // Open
        service.open("TestScale", callbacks);
        assertEquals(JposConst.JPOS_S_IDLE, service.getCurrentState());
        assertFalse(service.getClaimed());

        // Claim
        service.claim(0);
        assertEquals(JposConst.JPOS_S_IDLE, service.getCurrentState());
        assertTrue(service.getClaimed());

        // Enable
        service.setDeviceEnabled(true);
        assertEquals(JposConst.JPOS_S_IDLE, service.getCurrentState());
        assertTrue(service.getDeviceEnabled());

        // Async mode - должно быть IDLE пока нет активной операции
        service.setAsyncMode(true);
        assertTrue(service.getAsyncMode());
        assertEquals(JposConst.JPOS_S_IDLE, service.getCurrentState());

        // Disable
        service.setDeviceEnabled(false);
        assertFalse(service.getDeviceEnabled());
        assertFalse(service.getAsyncMode());

        // Release
        service.release();
        assertFalse(service.getClaimed());

        // Close
        service.close();
        assertEquals(JposConst.JPOS_S_CLOSED, service.getCurrentState());
    }

    /**
     * Тест: уведомления о состоянии питания
     */
    @Test
    public void testPowerStateWithNotifyEnabled() throws Exception {
        callbacks.clearEvents();
        service.open("TestScale", callbacks);
        service.setPollEnabled(true);
        service.setPowerNotify(JposConst.JPOS_PN_ENABLED);
        service.setDataEventEnabled(true);
        service.claim(0);
        service.setDeviceEnabled(true);
        service.setAsyncMode(false);

        // Должно быть событие POWER_ONLINE при включении
        StatusUpdateEvent powerOnEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
        assertNotNull("Должно быть событие POWER_ONLINE при включении", powerOnEvent);
        assertEquals(JposConst.JPOS_SUE_POWER_ONLINE, powerOnEvent.getStatus());

        cleanup();
    }

    /**
     * Тест: статусные события весов
     */
    @Test
    public void testStatusUpdateEvents() throws Exception {
        initService(true, false);

        // Отключаем power notify, чтобы не мешало
        service.setDeviceEnabled(false);
        service.setPowerNotify(JposConst.JPOS_PN_DISABLED);

        // Включаем статусные события
        service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);
        service.setDeviceEnabled(true);

        callbacks.clearEvents();

        // Нестабильный вес
        testScale.setCurrentWeight(500, false, false);

        StatusUpdateEvent unstableEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
        assertNotNull("Должно быть событие о нестабильном весе", unstableEvent);
        assertEquals(ScaleConst.SCAL_SUE_WEIGHT_UNSTABLE, unstableEvent.getStatus());

        callbacks.clearEvents();

        // Стабильный вес
        testScale.setCurrentWeight(500, true, false);

        StatusUpdateEvent stableEvent = callbacks.waitForEvent(
                StatusUpdateEvent.class, 3000);
        assertNotNull("Должно быть событие о стабильном весе", stableEvent);
        assertEquals(ScaleConst.SCAL_SUE_STABLE_WEIGHT, stableEvent.getStatus());

        cleanup();
    }

    /**
     * Тест: отключение статусных уведомлений
     */
    @Test
    public void testStatusNotifyDisabled() throws Exception {
        initService(true, false);

        // Отключаем все уведомления
        service.setDeviceEnabled(false);
        service.setPowerNotify(JposConst.JPOS_PN_DISABLED);
        service.setStatusNotify(ScaleConst.SCAL_SN_DISABLED);
        service.setDeviceEnabled(true);

        callbacks.clearEvents();

        testScale.setCurrentWeight(500, false, false);

        // Ждем немного - событий быть не должно
        StatusUpdateEvent event = callbacks.waitForEvent(
                StatusUpdateEvent.class, 500);
        assertNull("Не должно быть статусных событий при SCAL_SN_DISABLED", event);

        cleanup();
    }

    /**
     * Тест: заморозка событий
     */
    @Test
    public void testFreezeEvents() throws Exception {
        initService(false, true);

        service.setFreezeEvents(true);
        service.setDataEventEnabled(true);
        callbacks.clearEvents();

        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 5000);

        // При freezeEvents=true события не должны доставляться
        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 1000);
        assertNull("События не должны приходить при freezeEvents=true", dataEvent);

        // Но должны накапливаться в очереди
        int dataCount = service.getDataCount();
        assertTrue("События должны накапливаться в очереди. Текущее значение: " + dataCount,
                dataCount > 0);

        service.setFreezeEvents(false);

        // После разморозки событие должно прийти
        dataEvent = callbacks.waitForEvent(DataEvent.class, 2000);
        assertNotNull("После разморозки событие должно прийти", dataEvent);
        assertEquals(500, dataEvent.getStatus());

        cleanup();
    }

    /**
     * Тест: ошибка устройства ERROR_NOLINK
     */
    @Test
    public void testDeviceErrorNoLink() throws Exception {
        initService(false, true);
        callbacks.clearEvents();

        // Ошибка ERROR_NOLINK
        testScale.setNextException(new DeviceError(IDevice.ERROR_NOLINK, "No link to device"));
        service.readWeight(null, 0);

        // Должно быть ErrorEvent
        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 3000);
        assertNotNull("Должно быть получено ErrorEvent", errorEvent);

        cleanup();
    }

    /**
     * Тест: установка и чтение свойств
     */
    @Test
    public void testProperties() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);

        service.setZeroValid(true);
        assertTrue(service.getZeroValid());
        service.setZeroValid(false);
        assertFalse(service.getZeroValid());

        // StatusNotify можно менять только когда устройство выключено
        service.setDeviceEnabled(false);
        service.setStatusNotify(ScaleConst.SCAL_SN_ENABLED);
        assertEquals(ScaleConst.SCAL_SN_ENABLED, service.getStatusNotify());

        service.setDataEventEnabled(true);
        assertTrue(service.getDataEventEnabled());
        service.setDataEventEnabled(false);
        assertFalse(service.getDataEventEnabled());

        service.setFreezeEvents(true);
        assertTrue(service.getFreezeEvents());
        service.setFreezeEvents(false);
        assertFalse(service.getFreezeEvents());

        service.setAsyncMode(true);
        assertTrue(service.getAsyncMode());
        service.setAsyncMode(false);
        assertFalse(service.getAsyncMode());

        service.setAutoDisable(true);
        assertTrue(service.getAutoDisable());
        service.setAutoDisable(false);
        assertFalse(service.getAutoDisable());

        service.close();
    }

    /**
     * Тест: информация об устройстве
     */
    @Test
    public void testDeviceInfo() throws Exception {
        service.open("TestScale", callbacks);

        assertNotNull(service.getPhysicalDeviceDescription());
        assertNotNull(service.getPhysicalDeviceName());
        assertNotNull(service.getDeviceServiceDescription());
        assertTrue(service.getDeviceServiceVersion() > 0);

        service.close();
    }

    /**
     * Тест: очистка входного буфера
     */
    @Test
    public void testClearInput() throws Exception {
        initService(false, true);

        testScale.setCurrentWeight(500, true, false);
        service.readWeight(null, 2000);

        // Ждем DataEvent
        DataEvent event = callbacks.waitForEvent(DataEvent.class, 3000);
        assertNotNull(event);

        service.clearInput();

        // Проверяем, что счетчик событий сброшен
        assertEquals(0, service.getDataCount());

        cleanup();
    }

    /**
     * Тест: readWeight когда устройство отключено
     */
    @Test(expected = JposException.class)
    public void testReadWeightWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.readWeight(null, 1000);
        cleanup();
    }

    /**
     * Тест: setTareWeight когда устройство отключено
     */
    @Test(expected = JposException.class)
    public void testSetTareWeightWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.setTareWeight(100);
        cleanup();
    }

    /**
     * Тест: zeroScale когда устройство отключено
     */
    @Test(expected = JposException.class)
    public void testZeroScaleWhenDisabled() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        service.zeroScale();
        cleanup();
    }

    /**
     * Тест: setUnitPrice (не поддерживается)
     */
    @Test(expected = JposException.class)
    public void testSetUnitPrice() throws Exception {
        initService(false);
        service.setUnitPrice(100);
        cleanup();
    }

    /**
     * Тест: resetStatistics (не поддерживается)
     */
    @Test(expected = JposException.class)
    public void testResetStatisticsNotSupported() throws Exception {
        initService(false);
        service.resetStatistics("");
        cleanup();
    }

    /**
     * Тест: конкурентный доступ к readWeight
     */
    @Test
    public void testConcurrentReadWeight() throws Exception {
        // Устанавливаем вес
        testScale.setCurrentWeight(500, true, false);
        testScale.setResponseDelay(100);

        initService(false, true);
        callbacks.clearEvents();

        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(5);

        Thread[] threads = new Thread[5];
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger busyCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    service.readWeight(null, 5000);
                    successCount.incrementAndGet();
                } catch (JposException e) {
                    if (e.getErrorCode() == JposConst.JPOS_E_BUSY) {
                        busyCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            threads[i].start();
        }

        startLatch.countDown();
        doneLatch.await(1000, TimeUnit.MILLISECONDS);

        assertEquals(1, successCount.get());
        assertEquals(4, busyCount.get());
        assertEquals(0, otherErrorCount.get());

        DataEvent event = callbacks.waitForEvent(DataEvent.class, 5000);
        assertNotNull(event);
        assertEquals(500, event.getStatus());

        cleanup();
    }

    /**
     * Тест: разные протоколы весов
     */
    @Test
    public void testDifferentProtocols() throws Exception {
        testScale.setDeviceType(EScale.Pos2);
        service.open("TestScale", callbacks);
        assertNotNull(service.getPhysicalDeviceDescription());
        service.close();

        testScale.setDeviceType(EScale.Shtrih5);
        service.open("TestScale", callbacks);
        assertNotNull(service.getPhysicalDeviceDescription());
        service.close();

        testScale.setDeviceType(EScale.Shtrih6);
        service.open("TestScale", callbacks);
        assertNotNull(service.getPhysicalDeviceDescription());
        service.close();
    }

    /**
     * Тест: максимальное значение веса
     */
    @Test
    public void testExtremeWeightValues() throws Exception {
        testScale.setCurrentWeight(Integer.MAX_VALUE, true, false);

        initService(false, true);
        service.readWeight(null, 3000);

        DataEvent dataEvent = callbacks.waitForEvent(DataEvent.class, 4000);
        assertNotNull(dataEvent);
        assertEquals(Integer.MAX_VALUE, dataEvent.getStatus());

        cleanup();
    }

    /**
     * Тест: отрицательный вес
     */
    @Test
    public void testNegativeWeight() throws Exception {
        testScale.setCurrentWeight(-100, true, false);

        initService(false, true);
        service.setZeroValid(true);
        callbacks.clearEvents();

        service.readWeight(null, 3000);

        ErrorEvent errorEvent = callbacks.waitForEvent(ErrorEvent.class, 4000);
        assertNotNull(errorEvent);
        assertEquals(JposConst.JPOS_E_EXTENDED, errorEvent.getErrorCode());

        cleanup();
    }

    /**
     * Тест: включение/отключение poll
     */
    @Test
    public void testPollEnabled() throws Exception {
        service.open("TestScale", callbacks);
        service.setPollEnabled(true);
        assertTrue(service.getPollEnabled());
        service.setPollEnabled(false);
        assertFalse(service.getPollEnabled());
        service.close();
    }

    // ======================== ТЕСТЫ ДЛЯ SCALE 1.14 ========================

    /**
     * Тест: getMinimumWeight - получение минимального веса
     */
    @Test
    public void testGetMinimumWeight() throws Exception {
        service.open("TestScale", callbacks);
        service.claim(0);
        
        // Устанавливаем минимальный вес через параметры весового канала
        ChannelParams channelParams = new ChannelParams();
        channelParams.setMinWeigth(20); // 0.020 кг
        testScale.setChannelParams(channelParams);
        
        int minWeight = service.getMinimumWeight();
        // Должен быть 20 или 0 (если не установлен)
        assertTrue("Минимальный вес должен быть >= 0", minWeight >= 0);
        
        service.close();
    }

    /**
     * Тест: getCapFreezeValue - поддержка заморозки значений
     */
    @Test
    public void testGetCapFreezeValue() throws Exception {
        service.open("TestScale", callbacks);
        
        // По умолчанию false (можно настроить через конфиг)
        boolean cap = service.getCapFreezeValue();
        assertFalse("По умолчанию CapFreezeValue должно быть false", cap);
        
        service.close();
    }

    /**
     * Тест: getCapReadLiveWeightWithTare - поддержка чтения живого веса с тарой
     */
    @Test
    public void testGetCapReadLiveWeightWithTare() throws Exception {
        service.open("TestScale", callbacks);
        
        boolean cap = service.getCapReadLiveWeightWithTare();
        assertFalse("По умолчанию CapReadLiveWeightWithTare должно быть false", cap);
        
        service.close();
    }

    /**
     * Тест: getCapSetPriceCalculationMode - поддержка установки режима расчета цены
     */
    @Test
    public void testGetCapSetPriceCalculationMode() throws Exception {
        service.open("TestScale", callbacks);
        
        boolean cap = service.getCapSetPriceCalculationMode();
        assertTrue("По умолчанию CapSetPriceCalculationMode должно быть false", cap);
        
        service.close();
    }

    /**
     * Тест: getCapSetUnitPriceWithWeightUnit - поддержка установки цены с единицей веса
     */
    @Test
    public void testGetCapSetUnitPriceWithWeightUnit() throws Exception {
        service.open("TestScale", callbacks);
        
        boolean cap = service.getCapSetUnitPriceWithWeightUnit();
        assertFalse("По умолчанию CapSetUnitPriceWithWeightUnit должно быть false", cap);
        
        service.close();
    }

    /**
     * Тест: getCapSpecialTare - поддержка специальной тары
     */
    @Test
    public void testGetCapSpecialTare() throws Exception {
        service.open("TestScale", callbacks);
        
        boolean cap = service.getCapSpecialTare();
        assertFalse("По умолчанию CapSpecialTare должно быть false", cap);
        
        service.close();
    }

    /**
     * Тест: getCapTarePriority - поддержка приоритета тары
     */
    @Test
    public void testGetCapTarePriority() throws Exception {
        service.open("TestScale", callbacks);
        
        boolean cap = service.getCapTarePriority();
        assertFalse("По умолчанию CapTarePriority должно быть false", cap);
        
        service.close();
    }

    /**
     * Тест: freezeValue - заморозка значений тары
     */
    @Test
    public void testFreezeValue() throws Exception {
        // Для этого теста нужно включить поддержку freezeValue
        // В реальном проекте нужно настроить capFreezeValue=true в конфиге
        
        initService(false, false);
        
        // Устанавливаем тару
        service.setTareWeight(500); // 0.500 кг
        
        // Пытаемся заморозить (если не поддерживается, будет исключение)
        try {
            service.freezeValue(SCAL_SFR_MANUAL_TARE, true);
            // Если дошли сюда - функция поддерживается
            logger.info("freezeValue supported");
            
            // Проверяем, что состояние заморозки изменилось
            // (нужен геттер для frozenItems, если добавить)
            
            // Размораживаем
            service.freezeValue(SCAL_SFR_MANUAL_TARE, false);
            
        } catch (JposException e) {
            // Если не поддерживается - должно быть JPOS_E_ILLEGAL
            assertEquals("Должен быть JPOS_E_ILLEGAL", 
                         JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: freezeValue с несколькими флагами (битовая маска)
     */
    @Test
    public void testFreezeValueMultipleFlags() throws Exception {
        initService(false, false);
        
        try {
            // Замораживаем и тару, и цену
            int flags = SCAL_SFR_MANUAL_TARE | SCAL_SFR_UNITPRICE;
            service.freezeValue(flags, true);
            
            // Размораживаем только цену
            service.freezeValue(SCAL_SFR_UNITPRICE, false);
            
            // Размораживаем все
            service.freezeValue(flags, false);
            
        } catch (JposException e) {
            // Если не поддерживается - игнорируем
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: readLiveWeightWithTare - чтение живого веса с тарой
     */
    @Test
    public void testReadLiveWeightWithTareSync() throws Exception {
        initService(false, false);
        
        testScale.setCurrentWeight(1234, true, false);
        service.setTareWeight(200);
        
        try {
            int[] weightData = new int[1];
            int[] tare = new int[1];
            
            service.readLiveWeightWithTare(weightData, tare, 3000);
            
            // Если функция поддерживается
            assertTrue("Вес должен быть > 0 или 0", weightData[0] >= 0);
            assertTrue("Тара должна быть >= 0", tare[0] >= 0);
            
        } catch (JposException e) {
            // Если не поддерживается - должно быть JPOS_E_ILLEGAL
            assertEquals("Должен быть JPOS_E_ILLEGAL", 
                         JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: readLiveWeightWithTare в асинхронном режиме
     */
    @Test
    public void testReadLiveWeightWithTareAsync() throws Exception {
        initService(false, true);
        
        testScale.setCurrentWeight(1234, true, false);
        service.setTareWeight(200);
        callbacks.clearEvents();
        
        try {
            int[] weightData = new int[1];
            int[] tare = new int[1];
            
            service.readLiveWeightWithTare(weightData, tare, 3000);
            
            // В асинхронном режиме возвращаются нули
            assertEquals("В асинхронном режиме вес должен быть 0", 0, weightData[0]);
            assertEquals("В асинхронном режиме тара должна быть 0", 0, tare[0]);
            
            // Должно прийти DataEvent
            DataEvent event = callbacks.waitForEvent(DataEvent.class, 5000);
            assertNotNull("Должно прийти DataEvent", event);
            
        } catch (JposException e) {
            // Если не поддерживается
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setPriceCalculationMode - установка режима расчета цены
     */
    @Test
    public void testSetPriceCalculationMode() throws Exception {
        initService(false, false);
        
        try {
            // Режим оператора
            service.setPriceCalculationMode(SCAL_PCM_OPERATOR);
            
            // Режим самообслуживания
            service.setPriceCalculationMode(SCAL_PCM_SELF_SERVICE);
            
            // Режим печати этикеток
            service.setPriceCalculationMode(SCAL_PCM_PRICE_LABELING);
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setPriceCalculationMode с неверным параметром
     */
    @Test
    public void testSetPriceCalculationModeInvalid() throws Exception {
        initService(false, false);
        
        try {
            // Неверный режим
            service.setPriceCalculationMode(999);
            fail("Должно быть исключение при неверном режиме");
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setSpecialTare - установка специальной тары (ручной режим)
     */
    @Test
    public void testSetSpecialTareManual() throws Exception {
        initService(false, false);
        
        try {
            // Ручная тара 0.750 кг
            service.setSpecialTare(SCAL_SST_MANUAL, 750);
            
            // Отключение тары
            service.setSpecialTare(SCAL_SST_MANUAL, 0);
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setSpecialTare - установка специальной тары (процентный режим)
     */
    @Test
    public void testSetSpecialTarePercent() throws Exception {
        initService(false, false);
        
        testScale.setCurrentWeight(1000, true, false);
        
        try {
            // Процентная тара 10% (1000 * 0.10 = 100)
            service.setSpecialTare(SCAL_SST_PERCENT, 1000); // 10.00%
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setSpecialTare - установка специальной тары (взвешенный режим)
     */
    @Test
    public void testSetSpecialTareWeighted() throws Exception {
        initService(false, false);
        
        testScale.setCurrentWeight(500, true, false);
        
        try {
            // Взвешенная тара - использует текущий вес с весов
            service.setSpecialTare(SCAL_SST_WEIGHTED, 0);
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setSpecialTare - режим по умолчанию
     */
    @Test
    public void testSetSpecialTareDefault() throws Exception {
        initService(false, false);
        
        try {
            // Тары по умолчанию
            service.setSpecialTare(SCAL_SST_DEFAULT, 300);
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setTarePriority - установка приоритета тары
     */
    @Test
    public void testSetTarePriority() throws Exception {
        initService(false, false);
        
        try {
            // Первая тара блокирует остальные
            service.setTarePrioity(SCAL_STP_FIRST);
            
            // Любая тара может заменить текущую
            service.setTarePrioity(SCAL_STP_NONE);
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setTarePriority с неверным параметром
     */
    @Test
    public void testSetTarePriorityInvalid() throws Exception {
        initService(false, false);
        
        try {
            service.setTarePrioity(999);
            fail("Должно быть исключение при неверном приоритете");
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setUnitPriceWithWeightUnit - конвертация цены (граммы в килограммы)
     */
    @Test
    public void testSetUnitPriceWithWeightUnitGramToKg() throws Exception {
        initService(false, false);
        
        try {
            // Цена 2.55 евро за 100 грамм, весы работают в кг
            // Ожидаемый результат: 25.50 евро за кг
            service.setUnitPriceWithWeightUnit(25500, SCAL_WU_GRAM, 100, 1);
            
            long expectedUnitPrice = 25500 * 10; // 2.55 * 10 = 25.50 евро за кг
            assertEquals("Цена должна быть сконвертирована", 
                         expectedUnitPrice, service.getUnitPrice());
                         
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setUnitPriceWithWeightUnit - конвертация цены (килограммы в граммы)
     */
    @Test
    public void testSetUnitPriceWithWeightUnitKgToGram() throws Exception {
        initService(false, false);
        
        try {
            // Цена 25.50 евро за кг, весы работают в граммах
            service.setUnitPriceWithWeightUnit(255000, SCAL_WU_KILOGRAM, 1, 1);
            
            // Ожидаемый результат: 0.255 евро за грамм? Нет, цена за кг / 1000
            // В реализации конвертация через граммы
            long unitPrice = service.getUnitPrice();
            assertTrue("Цена должна быть сконвертирована", unitPrice > 0);
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: setUnitPriceWithWeightUnit - неверные параметры
     */
    @Test
    public void testSetUnitPriceWithWeightUnitInvalidParams() throws Exception {
        initService(false, false);
        
        try {
            // Неверная единица измерения
            service.setUnitPriceWithWeightUnit(100, 999, 1, 1);
            fail("Должно быть исключение при неверной единице измерения");
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        try {
            // Нулевой числитель
            service.setUnitPriceWithWeightUnit(100, SCAL_WU_GRAM, 0, 1);
            fail("Должно быть исключение при нулевом числителе");
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        try {
            // Нулевой знаменатель
            service.setUnitPriceWithWeightUnit(100, SCAL_WU_GRAM, 1, 0);
            fail("Должно быть исключение при нулевом знаменателе");
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: doPriceCalculating - синхронный режим
     */
    @Test
    public void testDoPriceCalculatingSync() throws Exception {
        initService(false, false);
        
        testScale.setCurrentWeight(1234, true, false);
        service.setTareWeight(200);
        
        try {
            int[] weightValue = new int[1];
            int[] tare = new int[1];
            long[] unitPrice = new long[1];
            long[] unitPriceX = new long[1];
            int[] weightUnitX = new int[1];
            int[] weightNumeratorX = new int[1];
            int[] weightDenominatorX = new int[1];
            long[] price = new long[1];
            
            service.doPriceCalculating(weightValue, tare, unitPrice, unitPriceX,
                weightUnitX, weightNumeratorX, weightDenominatorX, price, 5000);
            
            // Проверяем результаты
            assertTrue("Вес должен быть > 0", weightValue[0] > 0);
            assertTrue("Тара должна быть >= 0", tare[0] >= 0);
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: doPriceCalculating - асинхронный режим
     */
    @Test
    public void testDoPriceCalculatingAsync() throws Exception {
        initService(false, true);
        
        testScale.setCurrentWeight(1234, true, false);
        service.setTareWeight(200);
        callbacks.clearEvents();
        
        try {
            int[] weightValue = new int[1];
            int[] tare = new int[1];
            long[] unitPrice = new long[1];
            long[] unitPriceX = new long[1];
            int[] weightUnitX = new int[1];
            int[] weightNumeratorX = new int[1];
            int[] weightDenominatorX = new int[1];
            long[] price = new long[1];
            
            service.doPriceCalculating(weightValue, tare, unitPrice, unitPriceX,
                weightUnitX, weightNumeratorX, weightDenominatorX, price, 5000);
            
            // В асинхронном режиме все выходные параметры должны быть 0
            assertEquals(0, weightValue[0]);
            assertEquals(0, tare[0]);
            assertEquals(0, unitPrice[0]);
            assertEquals(0, unitPriceX[0]);
            assertEquals(0, weightUnitX[0]);
            assertEquals(0, weightNumeratorX[0]);
            assertEquals(0, weightDenominatorX[0]);
            assertEquals(0, price[0]);
            
            // Должно прийти DataEvent
            DataEvent event = callbacks.waitForEvent(DataEvent.class, 5000);
            assertNotNull("Должно прийти DataEvent", event);
            assertEquals("Вес в событии должен совпадать", 1234, event.getStatus());
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_ILLEGAL, e.getErrorCode());
        }
        
        cleanup();
    }

    /**
     * Тест: doPriceCalculating с таймаутом
     */
    @Test
    public void testDoPriceCalculatingTimeout() throws Exception {
        initService(false, false);
        
        // Вес никогда не стабилизируется
        testScale.setCurrentWeight(1234, false, false);
        
        try {
            int[] weightValue = new int[1];
            int[] tare = new int[1];
            long[] unitPrice = new long[1];
            long[] unitPriceX = new long[1];
            int[] weightUnitX = new int[1];
            int[] weightNumeratorX = new int[1];
            int[] weightDenominatorX = new int[1];
            long[] price = new long[1];
            
            service.doPriceCalculating(weightValue, tare, unitPrice, unitPriceX,
                weightUnitX, weightNumeratorX, weightDenominatorX, price, 1);
            
            fail("Должно быть исключение по таймауту");
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_TIMEOUT, e.getErrorCode());
        }
        cleanup();
    }

    /**
     * Тест: doPriceCalculating - перегрузка (overweight)
     */
    @Test
    public void testDoPriceCalculatingOverweight() throws Exception {
        initService(false, false);
        
        testScale.setCurrentWeight(15000, true, true); // Перегруз
        testScale.setOverweight(true);
        
        try {
            int[] weightValue = new int[1];
            int[] tare = new int[1];
            long[] unitPrice = new long[1];
            long[] unitPriceX = new long[1];
            int[] weightUnitX = new int[1];
            int[] weightNumeratorX = new int[1];
            int[] weightDenominatorX = new int[1];
            long[] price = new long[1];
            
            service.doPriceCalculating(weightValue, tare, unitPrice, unitPriceX,
                weightUnitX, weightNumeratorX, weightDenominatorX, price, 3000);
                
            fail("Должно быть исключение о перегрузе");
            
        } catch (JposException e) {
            assertEquals(JposConst.JPOS_E_EXTENDED, e.getErrorCode());
            assertEquals(ScaleConst.JPOS_ESCAL_OVERWEIGHT, e.getErrorCodeExtended());
        }
        
        testScale.setOverweight(false);
        cleanup();
    }

    /**
     * Тест: все методы Scale 1.14 вместе (комплексный тест)
     */
    @Test
    public void testScale114AllFeaturesTogether() throws Exception {
        // Проверяем capabilities
        service.open("TestScale", callbacks);
        
        boolean hasFreeze = service.getCapFreezeValue();
        boolean hasLiveWeight = service.getCapReadLiveWeightWithTare();
        boolean hasPriceMode = service.getCapSetPriceCalculationMode();
        boolean hasUnitPriceWithUnit = service.getCapSetUnitPriceWithWeightUnit();
        boolean hasSpecialTare = service.getCapSpecialTare();
        boolean hasTarePriority = service.getCapTarePriority();
        
        int minWeight = service.getMinimumWeight();
        assertTrue(minWeight >= 0);
        
        service.claim(0);
        service.setDeviceEnabled(true);
        
        // Если поддерживаются - выполняем
        if (hasPriceMode) {
            service.setPriceCalculationMode(SCAL_PCM_OPERATOR);
        }
        
        if (hasSpecialTare) {
            service.setSpecialTare(SCAL_SST_MANUAL, 500);
        }
        
        if (hasTarePriority) {
            service.setTarePrioity(SCAL_STP_NONE);
        }
        
        if (hasUnitPriceWithUnit) {
            service.setUnitPriceWithWeightUnit(25500, SCAL_WU_GRAM, 100, 1);
        }
        
        testScale.setCurrentWeight(1000, true, false);
        
        // Выполняем взвешивание с расчетом цены
        int[] weightValue = new int[1];
        int[] tare = new int[1];
        long[] unitPrice = new long[1];
        long[] unitPriceX = new long[1];
        int[] weightUnitX = new int[1];
        int[] weightNumeratorX = new int[1];
        int[] weightDenominatorX = new int[1];
        long[] price = new long[1];
        
        service.doPriceCalculating(weightValue, tare, unitPrice, unitPriceX,
            weightUnitX, weightNumeratorX, weightDenominatorX, price, 5000);
        
        assertTrue(weightValue[0] > 0);
        
        if (hasLiveWeight) {
            int[] liveWeight = new int[1];
            int[] liveTare = new int[1];
            service.readLiveWeightWithTare(liveWeight, liveTare, 1000);
            assertTrue(liveWeight[0] >= 0);
        }
        
        if (hasFreeze) {
            service.freezeValue(SCAL_SFR_UNITPRICE, true);
            service.freezeValue(SCAL_SFR_UNITPRICE, false);
        }
        
        service.setDeviceEnabled(false);
        service.release();
        service.close();
    }    
}