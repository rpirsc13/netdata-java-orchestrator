// SPDX-License-Identifier: GPL-3.0-or-later

package org.firehol.netdata.module.jmx;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.firehol.netdata.exception.InitializationException;
import org.firehol.netdata.exception.UnreachableCodeException;
import org.firehol.netdata.model.Chart;
import org.firehol.netdata.model.Dimension;
import org.firehol.netdata.module.jmx.configuration.JmxChartConfiguration;
import org.firehol.netdata.module.jmx.configuration.JmxDimensionConfiguration;
import org.firehol.netdata.module.jmx.configuration.JmxServerConfiguration;
import org.firehol.netdata.module.jmx.exception.JmxMBeanServerQueryException;
import org.firehol.netdata.module.jmx.query.MBeanQuery;
import org.firehol.netdata.module.jmx.utils.MBeanServerUtils;
import org.firehol.netdata.orchestrator.Collector;
import org.firehol.netdata.utils.LoggingUtils;

import lombok.Getter;

/**
 * Collects metrics of one MBeanServerConnection.
 *
 * @since 1.0.0
 * @author Simon Nagl
 *
 */
public class MBeanServerCollector implements Collector, Closeable {

	private final Logger log = Logger.getLogger("org.firehol.netdata.module.jmx");

	private JmxServerConfiguration serverConfiguration;

	@Getter
	private final MBeanServerConnection mBeanServer;

	private JMXConnector jmxConnector;

	private List<MBeanQuery> allMBeanQuery = new LinkedList<>();

	private List<Chart> allChart = new LinkedList<>();

	/**
	 * Creates an MBeanServerCollector.
	 *
	 * <p>
	 * <b>Warning:</b> Only use this when you do not want to close the
	 * underlying JMXConnetor when closing the generated MBeanServerCollector.
	 * </p>
	 *
	 * @param configuration
	 *            Configuration to apply to this collector.
	 * @param mBeanServer
	 *            to query
	 */
	public MBeanServerCollector(JmxServerConfiguration configuration, MBeanServerConnection mBeanServer) {
		this.serverConfiguration = configuration;
		this.mBeanServer = mBeanServer;
	}

	/**
	 * Creates an MBeanServerCollector.
	 *
	 * <p>
	 * Calling {@link #close()}} on the resulting {@code MBeanServerCollector}
	 * closes {@code jmxConnector} too.
	 * </p>
	 *
	 * @param configuration
	 * @param mBeanServer
	 * @param jmxConnector
	 */
	public MBeanServerCollector(JmxServerConfiguration configuration, MBeanServerConnection mBeanServer,
			JMXConnector jmxConnector) {
		this(configuration, mBeanServer);
		this.jmxConnector = jmxConnector;
	}

	/**
	 * <p>
	 * Queries MBean {@code java.lang:type=Runtime} for attribute {@code Name}.
	 * </p>
	 *
	 * <p>
	 * This attribute can be used as a unique identifier of the underlying JMX
	 * agent
	 * </p>
	 *
	 * @return the name representing the Java virtual machine of the queried
	 *         server..
	 * @throws JmxMBeanServerQueryException
	 *             on errors.
	 */
	public String getRuntimeName() throws JmxMBeanServerQueryException {

		// Final names.
		final String runtimeMBeanName = "java.lang:type=Runtime";
		final String runtimeNameAttributeName = "Name";

		// Build object name.
		ObjectName runtimeObjectName;
		try {
			runtimeObjectName = ObjectName.getInstance("java.lang:type=Runtime");
		} catch (MalformedObjectNameException e) {
			throw new UnreachableCodeException("Can not be reached because argument of getInstance() is static.", e);
		}

		// Query mBeanServer.
		Object attribute = getAttribute(runtimeObjectName, "Name");
		if (attribute instanceof String) {
			return (String) attribute;
		}

		// Error handling
		throw new JmxMBeanServerQueryException(LoggingUtils.buildMessage("Expected attribute '",
				runtimeNameAttributeName, " 'of MBean '", runtimeMBeanName,
				"' to return a string. Instead it returned a '", attribute.getClass().getSimpleName(), "'."));

	}

	public Collection<Chart> initialize() throws InitializationException {

		// Step 1
		// Check commonChart configuration
		for (JmxChartConfiguration chartConfig : serverConfiguration.getCharts()) {
			Chart chart = initializeChart(chartConfig);

			// Check if the mBeanServer has the desired sources.
			for (JmxDimensionConfiguration dimensionConfig : chartConfig.getDimensions()) {

				final ObjectName objectName;
				final MBeanQuery mBeanQuery;

				try {
					try {
						objectName = ObjectName.getInstance(dimensionConfig.getFrom());
					} catch (MalformedObjectNameException e) {
						throw new JmxMBeanServerQueryException(
								"'" + dimensionConfig.getFrom() + "' is no valid JMX ObjectName", e);
					} catch (NullPointerException e) {
						throw new JmxMBeanServerQueryException("'' is no valid JMX OBjectName", e);
					}

					// Initialize Query Info if needed
					mBeanQuery = getMBeanQueryForName(objectName, dimensionConfig.getValue())
							.orElse(addNewMBeanQuery(objectName, dimensionConfig.getValue()));
				} catch (JmxMBeanServerQueryException | NullPointerException e) {
					String exception = "";
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					e.printStackTrace(pw);
					exception = sw.toString(); // stack trace as a string
					log.warning(LoggingUtils.buildMessage("Could not query one dimension. Skipping...", exception));
					continue;
				}

				// Initialize Dimension
				final Dimension dimension = initializeDimension(chartConfig, dimensionConfig);

				try {
					mBeanQuery.addDimension(dimension, dimensionConfig.getValue());
				} catch (JmxMBeanServerQueryException | NullPointerException e) {
					log.warning(LoggingUtils
							.buildMessage("Could not query dimension " + dimension.getName() + ". Skippint...", e));
					continue;
				}

				chart.getAllDimension().add(dimension);
			}

			allChart.add(chart);
		}

		return allChart;
	}

	private Optional<MBeanQuery> getMBeanQueryForName(final ObjectName objectName, final String attribute) {
		return allMBeanQuery.stream()
				.filter(mBeanQuery -> mBeanQuery.getName().equals(objectName)
						&& mBeanQuery.getAttribute().equals(attribute))
				.findAny();
	}

	Chart initializeChart(JmxChartConfiguration config) {
		Chart chart = new Chart();

		chart.setType("jmx_" + serverConfiguration.getName());
		chart.setFamily(config.getFamily());
		chart.setId(config.getId());
		chart.setTitle(config.getTitle());
		chart.setUnits(config.getUnits());
		chart.setContext(serverConfiguration.getName());
		chart.setChartType(config.getChartType());
		if (config.getPriority() != null) {
			chart.setPriority(config.getPriority());
		}

		return chart;
	}

	Dimension initializeDimension(JmxChartConfiguration chartConfig, JmxDimensionConfiguration dimensionConfig) {
		Dimension dimension = new Dimension();
		dimension.setId(dimensionConfig.getName());
		dimension.setName(dimensionConfig.getName());
		dimension.setAlgorithm(chartConfig.getDimensionAlgorithm());
		dimension.setMultiplier(dimensionConfig.getMultiplier());
		dimension.setDivisor(dimensionConfig.getDivisor());

		return dimension;
	}

	private MBeanQuery addNewMBeanQuery(final ObjectName objectName, final String valueName)
			throws JmxMBeanServerQueryException {
		final MBeanQuery query = MBeanQuery.newInstance(mBeanServer, objectName, valueName);
		allMBeanQuery.add(query);
		return query;
	}

	Object getAttribute(ObjectName name, String attribute) throws JmxMBeanServerQueryException {
		try {
			return MBeanServerUtils.getAttribute(mBeanServer, name, attribute);
		} catch (JmxMBeanServerQueryException e) {
			return 0D;
		}
	}

	public Collection<Chart> collectValues() {
		// Query all attributes and fill charts.
		Iterator<MBeanQuery> queryIterator = allMBeanQuery.iterator();

		while (queryIterator.hasNext()) {
			final MBeanQuery query = queryIterator.next();

			try {
				query.query();
			} catch (JmxMBeanServerQueryException e) {
				// Stop collecting this value.
				log.warning(LoggingUtils.buildMessage(
						"Stop collection value '" + query.getAttribute() + "' of '" + query.getName() + "'.", e));
				queryIterator.remove();
			}
		}

		// Return Updated Charts.
		return allChart;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		if (this.jmxConnector != null) {
			this.jmxConnector.close();
		}
	}

	@Override
	public void cleanup() {
		try {
			close();
		} catch (IOException e) {
			log.warning(LoggingUtils.buildMessage("Could not cleanup MBeanServerCollector.", e));
		}
	}
}
