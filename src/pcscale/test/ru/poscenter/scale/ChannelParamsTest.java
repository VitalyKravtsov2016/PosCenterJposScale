/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.poscenter.scale;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author User
 */
public class ChannelParamsTest {
    
    public ChannelParamsTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of convertWeight method, of class ChannelParams.
     */
    @Test
    public void testConvertWeight() {
        System.out.println("convertWeight");
        int weight = 1230;
        int expResult = 123;
        ChannelParams instance = new ChannelParams();
        instance.setPower(-4);
        int result = instance.convertWeight(weight);
        assertEquals(expResult, result);
    }

    /**
     * Test of convertWeight method, of class ChannelParams.
     */
    @Test
    public void testConvertWeight2() {
        System.out.println("convertWeight2");
        int weight = 123;
        int expResult = 123;
        ChannelParams instance = new ChannelParams();
        instance.setPower(-3);
        int result = instance.convertWeight(weight);
        assertEquals(expResult, result);
    }
    
    /**
     * Test of convertWeight method, of class ChannelParams.
     */
    @Test
    public void testConvertWeight3() {
        System.out.println("convertWeight2");
        int weight = 123;
        int expResult = 1230;
        ChannelParams instance = new ChannelParams();
        instance.setPower(-2);
        int result = instance.convertWeight(weight);
        assertEquals(expResult, result);
    }
}
