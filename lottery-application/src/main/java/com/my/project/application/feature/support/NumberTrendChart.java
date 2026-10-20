package com.my.project.application.feature.support;

import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.bo.NumberTrendObservation.Ball;
import com.my.project.llm.bo.ZoneTrendImage;
import com.my.project.llm.service.NumberTrendModelService.Draw;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public final class NumberTrendChart {
    private static final int MARGIN = 16;
    private static final int PERIOD_WIDTH = 96;
    private static final int CELL_WIDTH = 32;
    private static final int CELL_HEIGHT = 26;
    private static final int TOP = 52;
    private static final Color TEXT = new Color(0x1F2937);
    private static final Color GRID = new Color(0xE5E7EB);
    private static final Color ALTERNATE = new Color(0xF8FAFC);
    private static final Color OMISSION = new Color(0x64748B);
    private static final Color GUIDE = new Color(0x8794A5);
    private static final Color RED = new Color(0xC92143);
    private static final Color BLUE = new Color(0x2155AB);
    private static final Color RED_LINE = new Color(0xC6677B);
    private static final Color BLUE_LINE = new Color(0x6287C7);
    private static final Color RED_CANDIDATE = new Color(0x287052);
    private static final Color CANDIDATE_ROW = new Color(0xEDF7F2);
    private static final BasicStroke TREND_STROKE = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke PROJECTION_STROKE = new BasicStroke(2.2f, BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND, 10f, new float[]{4f, 3f}, 0f);

    private NumberTrendChart() { }

    public static RedChart renderRed(List<Draw> draws) {
        var lines = RedTrendPatterns.calculate(draws);
        return new RedChart(render(draws, Ball.RED, lines), lines.observations());
    }

    public static ZoneTrendImage render(List<Draw> draws, Ball ball) {
        var red = ball == Ball.RED ? RedTrendPatterns.calculate(draws) : null;
        return render(draws, ball, red);
    }

    private static ZoneTrendImage render(List<Draw> draws, Ball ball, RedTrendPatterns.Lines red) {
        boolean hasCandidates = red != null && !red.candidates().isEmpty();
        int right = columnEdge(ball.count());
        int width = right + MARGIN;
        int bottom = rowTop(draws.size() + (hasCandidates ? 1 : 0));
        int height = bottom + MARGIN;
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            graphics.setColor(TEXT);
            graphics.drawString(ball == Ball.RED ? "Red ball trends" : "Blue ball trends", MARGIN, 26);
            graphics.setColor(OMISSION);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            String legend = ball == Ball.RED
                ? "Gray = missed draws   |   Solid lines = red patterns   |   Dashed red = projection   |   Dividers = zones"
                : "Gray = missed draws   |   Blue line = consecutive draws   |   Dividers = zones";
            graphics.drawString(legend, MARGIN, 44);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            drawGrid(graphics, ball, draws.size(), right, bottom, hasCandidates);
            if (red != null) {
                drawRedPatterns(graphics, red);
            } else {
                drawBlueTrend(graphics, draws);
            }
            drawHeader(graphics, ball);
            for (int index = 0; index < draws.size(); index++) {
                drawRow(graphics, draws.get(index), ball, index);
            }
            if (hasCandidates) {
                drawCandidates(graphics, draws.size(), red.candidates());
            }
        } finally {
            graphics.dispose();
        }
        try (var bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", bytes)) throw new IOException("PNG 编码器不可用");
            String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
            return new ZoneTrendImage(ball.name().toLowerCase(Locale.ROOT), dataUrl);
        } catch (IOException e) {
            throw new UncheckedIOException("导出号码走势图失败", e);
        }
    }

    private static void drawGrid(Graphics2D graphics, Ball ball, int rowCount, int right, int bottom,
                                 boolean hasCandidates) {
        for (int row = 0; row < rowCount; row++) {
            int y = rowTop(row);
            if (row % 2 == 0) {
                graphics.setColor(ALTERNATE);
                graphics.fillRect(MARGIN, y, right - MARGIN, CELL_HEIGHT);
            }
            graphics.setColor(GRID);
            graphics.drawLine(MARGIN, y, right, y);
        }
        if (hasCandidates) {
            graphics.setColor(CANDIDATE_ROW);
            graphics.fillRect(MARGIN, rowTop(rowCount), right - MARGIN, CELL_HEIGHT);
        }
        graphics.setColor(GUIDE);
        int zoneSize = ball == Ball.RED ? 11 : 4;
        for (int end = zoneSize; end <= ball.count(); end += zoneSize) {
            int x = columnEdge(end);
            graphics.drawLine(x, TOP, x, bottom);
        }
    }

    private static void drawRedPatterns(Graphics2D graphics, RedTrendPatterns.Lines lines) {
        graphics.setColor(RED_LINE);
        graphics.setStroke(TREND_STROKE);
        lines.history().forEach(segment -> drawRedSegment(graphics, segment));
        graphics.setStroke(PROJECTION_STROKE);
        lines.projections().forEach(segment -> drawRedSegment(graphics, segment));
    }

    private static void drawRedSegment(Graphics2D graphics, RedTrendPatterns.Segment segment) {
        var from = segment.from();
        var to = segment.to();
        graphics.drawLine(columnCenter(from.number()), rowCenter(from.row()),
            columnCenter(to.number()), rowCenter(to.row()));
    }

    private static void drawBlueTrend(Graphics2D graphics, List<Draw> draws) {
        graphics.setColor(BLUE_LINE);
        graphics.setStroke(TREND_STROKE);
        for (int row = 1; row < draws.size(); row++) {
            graphics.drawLine(columnCenter(draws.get(row - 1).blue()), rowCenter(row - 1),
                columnCenter(draws.get(row).blue()), rowCenter(row));
        }
    }

    private static void drawHeader(Graphics2D graphics, Ball ball) {
        graphics.setColor(TEXT);
        graphics.drawString("Period", MARGIN, TOP + 18);
        for (int number = 1; number <= ball.count(); number++) {
            drawCentered(graphics, label(number), columnCenter(number), TOP + 18);
        }
    }

    private static void drawRow(Graphics2D graphics, Draw draw, Ball ball, int row) {
        graphics.setColor(TEXT);
        graphics.drawString(draw.period(), MARGIN, rowTop(row) + 18);
        var omissions = draw.omissions(ball);
        for (int number = 1; number <= ball.count(); number++) {
            int omission = omissions.get(number - 1);
            if (omission == 0) {
                drawBall(graphics, number, row, ball == Ball.RED ? RED : BLUE);
            } else {
                graphics.setColor(OMISSION);
                drawCentered(graphics, Integer.toString(omission), columnCenter(number), rowTop(row) + 18);
            }
        }
    }

    private static void drawCandidates(Graphics2D graphics, int row, List<Integer> candidates) {
        graphics.setColor(TEXT);
        graphics.drawString("Next (est.)", MARGIN, rowTop(row) + 18);
        candidates.forEach(number -> drawBall(graphics, number, row, RED_CANDIDATE));
    }

    private static void drawBall(Graphics2D graphics, int number, int row, Color color) {
        graphics.setColor(color);
        graphics.fillOval(columnCenter(number) - 11, rowCenter(row) - 11, 22, 22);
        graphics.setColor(Color.WHITE);
        drawCentered(graphics, label(number), columnCenter(number), rowTop(row) + 18);
    }

    private static void drawCentered(Graphics2D graphics, String text, int x, int baseline) {
        graphics.drawString(text, x - graphics.getFontMetrics().stringWidth(text) / 2, baseline);
    }

    private static int columnEdge(int number) {
        return MARGIN + PERIOD_WIDTH + number * CELL_WIDTH;
    }

    private static int columnCenter(int number) {
        return columnEdge(number) - CELL_WIDTH / 2;
    }

    private static int rowTop(int row) {
        return TOP + (row + 1) * CELL_HEIGHT;
    }

    private static int rowCenter(int row) {
        return rowTop(row) + CELL_HEIGHT / 2;
    }

    private static String label(int number) {
        return number < 10 ? "0" + number : Integer.toString(number);
    }

    public record RedChart(ZoneTrendImage image, List<NumberTrendObservation> candidates) { }
}
