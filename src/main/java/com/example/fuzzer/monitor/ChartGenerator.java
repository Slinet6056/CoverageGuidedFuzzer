package com.example.fuzzer.monitor;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;

public class ChartGenerator {
    private static final int CHART_WIDTH = 1024;
    private static final int CHART_HEIGHT = 768;
    private static final Color BACKGROUND_COLOR = Color.WHITE;
    private static final Color GRID_COLOR = new Color(220, 220, 220);
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 18);
    private static final Font LABEL_FONT = new Font("Arial", Font.PLAIN, 14);

    /**
     * 生成执行速度和覆盖率趋势图
     */
    public static void generatePerformanceChart(File outputFile, TimeSeriesCollection dataset) throws IOException {
        // 分离执行速度和覆盖率数据集
        TimeSeriesCollection speedDataset = new TimeSeriesCollection();
        TimeSeriesCollection coverageDataset = new TimeSeriesCollection();

        speedDataset.addSeries(dataset.getSeries(0));  // Execution Speed
        coverageDataset.addSeries(dataset.getSeries(1));  // Coverage

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Fuzzing Performance Over Time",  // 标题
                "Time",                          // x轴标签
                "Execution Speed (exec/s)",      // y轴标签
                speedDataset,                    // 数据集
                true,                           // 显示图例
                true,                           // 显示工具提示
                false                           // 不生成URLs
        );

        // 设置图表样式
        customizeTimeSeriesChart(chart);

        // 设置双Y轴
        XYPlot plot = (XYPlot) chart.getPlot();

        // 配置执行速度轴（左轴）
        NumberAxis speedAxis = (NumberAxis) plot.getRangeAxis();
        speedAxis.setAutoRangeIncludesZero(true);
        speedAxis.setAutoRange(true);  // 自动调整范围

        // 配置覆盖率轴（右轴）
        NumberAxis coverageAxis = new NumberAxis("Coverage (%)");
        coverageAxis.setRange(0.0, 100.0);
        plot.setRangeAxis(1, coverageAxis);

        // 设置数据集和轴的映射
        plot.setDataset(1, coverageDataset);
        plot.mapDatasetToRangeAxis(0, 0);  // 速度数据映射到左轴
        plot.mapDatasetToRangeAxis(1, 1);  // 覆盖率数据映射到右轴

        // 设置渲染器
        XYLineAndShapeRenderer speedRenderer = new XYLineAndShapeRenderer();
        XYLineAndShapeRenderer coverageRenderer = new XYLineAndShapeRenderer();

        // 配置执行速度线条
        speedRenderer.setSeriesPaint(0, new Color(65, 105, 225));
        speedRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(0, speedRenderer);

        // 配置覆盖率线条
        coverageRenderer.setSeriesPaint(0, new Color(50, 205, 50));
        coverageRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(1, coverageRenderer);

        // 保存图表
        ChartUtils.saveChartAsPNG(outputFile, chart, CHART_WIDTH, CHART_HEIGHT);
    }

    /**
     * 生成异常发现趋势图
     */
    public static void generateAnomalyTrendChart(File outputFile, TimeSeriesCollection dataset) throws IOException {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Anomaly Discovery Trend",     // 标题
                "Time",                        // x轴标签
                "Count",                       // y轴标签
                dataset,                       // 数据集
                true,                         // 显示图例
                true,                         // 显示工具提示
                false                         // 不生成URLs
        );

        // 设置图表样式
        customizeTimeSeriesChart(chart);

        // 设置渲染器
        XYPlot plot = (XYPlot) chart.getPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, new Color(220, 20, 60));   // Crashes线条颜色
        renderer.setSeriesPaint(1, new Color(255, 140, 0));   // Hangs线条颜色
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new BasicStroke(2.0f));
        plot.setRenderer(renderer);

        // 保存图表
        ChartUtils.saveChartAsPNG(outputFile, chart, CHART_WIDTH, CHART_HEIGHT);
    }

    /**
     * 自定义时间序列图表样式
     */
    private static void customizeTimeSeriesChart(JFreeChart chart) {
        chart.setBackgroundPaint(BACKGROUND_COLOR);
        chart.getTitle().setFont(TITLE_FONT);

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(BACKGROUND_COLOR);
        plot.setDomainGridlinePaint(GRID_COLOR);
        plot.setRangeGridlinePaint(GRID_COLOR);

        // 设置日期格式
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("HH:mm:ss"));
        axis.setLabelFont(LABEL_FONT);

        // 设置数值轴
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLabelFont(LABEL_FONT);
        rangeAxis.setAutoRangeIncludesZero(true);
    }
}
