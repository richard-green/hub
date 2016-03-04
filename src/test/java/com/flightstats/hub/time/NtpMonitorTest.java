package com.flightstats.hub.time;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class NtpMonitorTest {

    @Test
    public void testPositive() throws Exception {
        String[] output = {
                "     remote           refid      st t when poll reach   delay   offset  jitter",
                "==============================================================================",
                "+util            91.189.94.4      3 u  545 1024  377   99.523    1.032   0.631",
                "*n2              20.9.10.5        4 u  996 1024  377    1.527    1.128   0.371",
                "+hub-v2-01       20.20.20.5       5 u  622 1024  337    0.269    0.606   0.449",
                "-hub-v2-02       20.20.20.5       5 u 1026 1024  376    0.390    0.277   0.146"
        };
        assertEquals(0.606, NtpMonitor.parseClusterRange(Arrays.asList(output)), 0.001);
        assertEquals(1.080, NtpMonitor.parsePrimary(Arrays.asList(output)), 0.001);
    }

    @Test
    public void testNegative() throws Exception {
        String[] output = {
                "     remote           refid      st t when poll reach   delay   offset  jitter",
                "==============================================================================",
                "+util            91.189.94.4      3 u  545 1024  377   99.523   -1.032   0.631",
                "*n2              20.9.10.5        4 u  996 1024  377    1.527   -1.128   0.371",
                "+hub-v2-01       20.20.20.5       5 u  622 1024  337    0.269   -0.606   0.449",
                "-hub-v2-02       20.20.20.5       5 u 1026 1024  376    0.390   -0.277   0.146"
        };
        assertEquals(0.606, NtpMonitor.parseClusterRange(Arrays.asList(output)), 0.001);
        assertEquals(-1.080, NtpMonitor.parsePrimary(Arrays.asList(output)), 0.001);
    }

    @Test
    public void testPlusMinus() throws Exception {
        String[] output = {
                "     remote           refid      st t when poll reach   delay   offset  jitter",
                "==============================================================================",
                "+util            91.189.94.4      3 u  545 1024  377   99.523   -1.032   0.631",
                "*n2              20.9.10.5        4 u  996 1024  377    1.527   1.128   0.371",
                "+hub-v2-01       20.20.20.5       5 u  622 1024  337    0.269    0.606   0.449",
                "-hub-v2-02       20.20.20.5       5 u 1026 1024  376    0.390   -0.277   0.146"
        };
        assertEquals(0.883, NtpMonitor.parseClusterRange(Arrays.asList(output)), 0.001);
        assertEquals(0.048, NtpMonitor.parsePrimary(Arrays.asList(output)), 0.001);
    }

    @Test
    public void testSingleServer() throws Exception {
        String[] output = {
                "remote           refid      st t when poll reach   delay   offset  jitter",
                "==============================================================================",
                "*n2             20.9.10.5        4 u  898 1024  377    1.538   -1.325   1.167"
        };
        assertEquals(0.0, NtpMonitor.parseClusterRange(Arrays.asList(output)), 0.001);
        assertEquals(-1.325, NtpMonitor.parsePrimary(Arrays.asList(output)), 0.001);
    }

    @Test
    public void testPositiveSelf() throws Exception {
        String[] output = {
                "     remote           refid      st t when poll reach   delay   offset  jitter",
                "==============================================================================",
                "+util            91.189.94.4      3 u  545 1024  377   99.523   -1.032   0.631",
                "*n2              20.9.10.5        4 u  996 1024  377    1.527   -1.128   0.371",
                "+hub-v2-01       20.20.20.5       5 u  622 1024  337    0.269    0.606   0.449",
                "-hub-v2-02       20.20.20.5       5 u 1026 1024  376    0.390    0.277   0.146",
                " hub-v2-03       .INIT.          16 u    -   64    0    0.000    0.000   0.000"
        };
        assertEquals(0.606, NtpMonitor.parseClusterRange(Arrays.asList(output)), 0.001);
        assertEquals(-1.080, NtpMonitor.parsePrimary(Arrays.asList(output)), 0.001);
    }

}