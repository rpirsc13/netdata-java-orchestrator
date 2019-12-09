// SPDX-License-Identifier: GPL-3.0-or-later

package org.firehol.netdata.module.jmx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;

import org.firehol.netdata.model.Chart;
import org.firehol.netdata.model.ChartType;
import org.firehol.netdata.model.Dimension;
import org.firehol.netdata.model.DimensionAlgorithm;
import org.firehol.netdata.module.jmx.MBeanServerCollector;
import org.firehol.netdata.module.jmx.configuration.JmxChartConfiguration;
import org.firehol.netdata.module.jmx.configuration.JmxDimensionConfiguration;
import org.firehol.netdata.module.jmx.configuration.JmxServerConfiguration;
import org.firehol.netdata.module.jmx.exception.JmxMBeanServerQueryException;
import org.firehol.netdata.testutils.ReflectionUtils;
import org.firehol.netdata.testutils.TestObjectBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MBeanServerCollectorTest {

	@InjectMocks
	private MBeanServerCollector mBeanServerCollector;

	@Mock
	private JMXConnector jmxConnector;

	@Mock
	private MBeanServerConnection mBeanServer;

	@Test
	public void testInitializeChart() throws NoSuchFieldException, IllegalAccessException, SecurityException {
		// Static Objects
		JmxChartConfiguration config = TestObjectBuilder.buildJmxChartConfiguration();
		JmxServerConfiguration serverConfig = new JmxServerConfiguration();
		String serverName = "TestServer";
		serverConfig.setName(serverName);
		ReflectionUtils.setPrivateFiled(mBeanServerCollector, "serverConfiguration", serverConfig);

		// Test
		Chart chart = mBeanServerCollector.initializeChart(config);

		// Verify
		assertEquals("jmx_TestServer", chart.getType());
		assertEquals("id", chart.getId());
		assertNull(chart.getName());
		assertEquals("title", chart.getTitle());
		assertEquals("units", config.getUnits());
		assertEquals("family", chart.getFamily());
		assertEquals(serverName, chart.getContext());
		assertEquals(ChartType.LINE, chart.getChartType());
		assertEquals(1000, chart.getPriority());
		assertNull(chart.getUpdateEvery());
	}

	@Test
	public void testInitializeChartDynamicPriority()
			throws NoSuchFieldException, IllegalAccessException, SecurityException {
		// Static Objects
		JmxChartConfiguration config = TestObjectBuilder.buildJmxChartConfiguration();
		config.setPriority(1);
		JmxServerConfiguration serverConfig = new JmxServerConfiguration();
		String serverName = "TestServer";
		serverConfig.setName(serverName);
		ReflectionUtils.setPrivateFiled(mBeanServerCollector, "serverConfiguration", serverConfig);

		// Test
		Chart chart = mBeanServerCollector.initializeChart(config);

		// Verify
		assertEquals(1, chart.getPriority());
	}

	@Test
	public void testInitializeDimension() {
		// Static Objects
		JmxChartConfiguration chartConfig = TestObjectBuilder.buildJmxChartConfiguration();
		JmxDimensionConfiguration dimensionConfig = TestObjectBuilder.buildJmxDimensionConfiguration();
		chartConfig.getDimensions().add(dimensionConfig);

		// Test
		Dimension dimension = mBeanServerCollector.initializeDimension(chartConfig, dimensionConfig);

		// Verify
		assertEquals("name", dimension.getId());
		assertEquals("name", dimension.getName());
		assertEquals(DimensionAlgorithm.ABSOLUTE, dimension.getAlgorithm());
		assertEquals(1, dimension.getMultiplier());
		assertEquals(1, dimension.getDivisor());
		assertFalse(dimension.isHidden());
		assertNull(dimension.getCurrentValue());
	}

	@Test
	public void testGetAttribute() throws MalformedObjectNameException, AttributeNotFoundException,
			InstanceNotFoundException, MBeanException, ReflectionException, IOException, JmxMBeanServerQueryException {
		// Static Objects
		ObjectName name = new ObjectName("org.firehol.netdata.module.jmx", "key", "value");
		String attribute = "attribute";

		// Mock
		when(mBeanServer.getAttribute(name, attribute)).thenReturn(1234L);

		// Test
		Object value = mBeanServerCollector.getAttribute(name, attribute);

		// Verify
		assertEquals(1234L, value);
	}

	@Test
	public void testGetAttributeFailure() throws MalformedObjectNameException, AttributeNotFoundException,
			InstanceNotFoundException, MBeanException, ReflectionException, IOException, JmxMBeanServerQueryException {
		// Static Objects
		ObjectName name = new ObjectName("org.firehol.netdata.module.jmx", "key", "value");
		String attribute = "attribute";

		// Mock
		when(mBeanServer.getAttribute(name, attribute)).thenThrow(new AttributeNotFoundException());

		// Test
		mBeanServerCollector.getAttribute(name, attribute);
	}

	@Test
	public void testClose() throws IOException {
		// Test
		mBeanServerCollector.close();
		// Verify
		verify(jmxConnector, times(1)).close();
	}

	@Test(expected = IOException.class)
	public void testCloseFailure() throws IOException {
		// Mock
		doThrow(new IOException()).when(jmxConnector).close();
		// Test
		mBeanServerCollector.close();
	}

}
