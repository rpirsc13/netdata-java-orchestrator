package org.firehol.netdata.plugin;

import org.firehol.netdata.entity.Chart;
import org.firehol.netdata.entity.Dimension;

public abstract class Printer {
	
	private static void print(String command) {
		System.out.println(command);
	}

	public static void initializeChart(Chart chart) {
		// --------------------------------------------------------------------
		// Build the first line.
		// --------------------------------------------------------------------
		// Start new chart
		StringBuilder sb = new StringBuilder("CHART ");
		// Append identifier
		sb.append(chart.getType());
		sb.append('.');
		sb.append(chart.getId());
		sb.append(' ');
		// Append name
		if (chart.hasName()) {
			sb.append(chart.getName());
		} else {
			sb.append("null");
		}
		sb.append(' ');
		// Append title
		sb.append(chart.getTitle());
		sb.append(' ');
		// Append untis
		sb.append(chart.getUnits());
		sb.append(' ');
		// Append familiy
		if (!chart.hasFamily()) {
			sb.append(chart.getFamily());
		} else {
			sb.append(chart.getId());
		}
		sb.append(' ');
		// Append chart type
		sb.append(chart.getChartType());
		sb.append(' ');
		// Append priority
		sb.append(chart.getPriority());
		// Append update_every
		if(chart.hasUpdateEvery()) {			
			sb.append(' ');
			sb.append(chart.getUpdateEvery());
		}
		
		// Append dimension
		for(Dimension dim : chart.getAllDimension()) {
			// Newline
			sb.append(System.lineSeparator() );
			// Start new dimension
			sb.append("DIMENSION ");
			// Append ID
			sb.append(dim.getId());
			sb.append(' ');
			// Append name
			if(dim.hasName()) {
				sb.append(dim.getName());
			} else {
				sb.append(dim.getId());
			}
			sb.append(' ');
			// Append algorithm
			sb.append(dim.getAlgorithm());
			sb.append(' ');
			// Append multiplier
			sb.append(dim.getMultiplier());
			sb.append(' ');
			// Append divisor
			sb.append(dim.getDivisor());
			// Append hidden
			if(dim.isHidden()) {
				sb.append(" hidden");
			}
		}
		
		print(sb.toString());
	}
	

	public static void collect(Chart chart) {
		// TODO Add microseconds to the output.
		print("BEGIN " + chart.getType() + "." + chart.getId());

		for (Dimension dim : chart.getAllDimension()) {
			print("SET " + dim.getId() + " = " + dim.getCurrentValue());
		}

		print("END");
	}

	/**
	 * Tell the caller to disable the plugin. This will prevent it from
	 * restarting the plugin.
	 */
	public static void disable() {
		print("DISABLE");
	}
}
