package ru.poscenter.util;

import org.junit.After;
import org.junit.AfterClass;
import ru.poscenter.util.ServiceVersionUtil;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;

import org.junit.Test;

public class ServiceVersionUtilTest {

    /**
     * Test of versionStringToInt method, of class ServiceVersionUtil.
     */
    @Test
    public void testVersionStringToInt() {
        System.out.println("versionStringToInt");
        String version = "1.14.6";
        int expResult = 1014006;
        int result = ServiceVersionUtil.versionStringToInt(version);
        assertEquals(expResult, result);
    }
}

