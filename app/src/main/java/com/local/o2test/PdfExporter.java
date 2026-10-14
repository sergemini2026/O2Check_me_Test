package com.local.o2test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.local.o2test.db.entity.HrvSnapshotEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfExporter {

    public interface PdfExportCallback {
        void onSuccess(String filePath, String fileName);
        void onError(String errorMessage);
    }

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;

    public static void generateHrvPdfReport(
            Context context,
            String profileName,
            String workoutMode,
            long startTime,
            long endTime,
            List<HrvSnapshotEntity> snapshots,
            List<Integer> rawRrList,
            List<DataPoint> dataPoints,
            String customDirPath,
            PdfExportCallback callback
    ) {
        PdfDocument document = new PdfDocument();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);

        double avgRmssd = 0, avgSdnn = 0, avgSi = 0, avgLfHf = 0, avgSd1 = 0, avgSd2 = 0, avgSampEn = 0, avgDfa = 0;
        int validCount = 0;
        if (!snapshots.isEmpty()) {
            for (HrvSnapshotEntity s : snapshots) {
                if (s.getBaevskySi() > 300 || s.getBaevskySi() <= 0) continue;
                avgRmssd += s.getRmssd();
                avgSdnn += s.getSdnn();
                avgSi += s.getBaevskySi();
                avgLfHf += s.getLfHfRatio();
                avgSd1 += s.getSd1();
                avgSd2 += s.getSd2();
                avgSampEn += s.getSampleEntropy();
                avgDfa += s.getDfaAlpha1();
                validCount++;
            }
            if (validCount > 0) {
                avgRmssd /= validCount;
                avgSdnn /= validCount;
                avgSi /= validCount;
                avgLfHf /= validCount;
                avgSd1 /= validCount;
                avgSd2 /= validCount;
                avgSampEn /= validCount;
                avgDfa /= validCount;
            }
        }

        try {
            // ==========================================
            // СТРАНИЦА 1: Шапка, Сводка, Интерпретация, Графики
            // ==========================================
            PdfDocument.PageInfo pageInfo1 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
            PdfDocument.Page page1 = document.startPage(pageInfo1);
            Canvas canvas1 = page1.getCanvas();
            canvas1.drawColor(Color.WHITE);

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setStyle(Paint.Style.FILL);

            Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);

            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setStyle(Paint.Style.FILL);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(16);
            textPaint.setFakeBoldText(true);
            canvas1.drawText("ОТЧЕТ АНАЛИЗА ВАРИАБЕЛЬНОСТИ СЕРДЕЧНОГО РИТМА", MARGIN, 45, textPaint);

            textPaint.setTextSize(10);
            textPaint.setFakeBoldText(false);
            textPaint.setColor(Color.parseColor("#334155"));
            canvas1.drawText("Профиль: " + profileName + "  |  Режим: " + workoutMode, MARGIN, 65, textPaint);
            canvas1.drawText("Период: " + sdf.format(new Date(startTime)) + " — " + sdf.format(new Date(endTime)), MARGIN, 80, textPaint);

            strokePaint.setColor(Color.parseColor("#CBD5E1"));
            strokePaint.setStrokeWidth(1f);
            canvas1.drawLine(MARGIN, 90, PAGE_WIDTH - MARGIN, 90, strokePaint);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(12);
            textPaint.setFakeBoldText(true);
            canvas1.drawText("1. Сводные вегетативные показатели", MARGIN, 110, textPaint);

            drawMetricRow(canvas1, 126, "RMSSD (мск):", String.format(Locale.US, "%.1f", avgRmssd), "Норма: 20-100");
            drawMetricRow(canvas1, 142, "SDNN (мск):", String.format(Locale.US, "%.1f", avgSdnn), "Общая вариабельность");
            drawMetricRow(canvas1, 158, "Индекс Баевского (SI):", String.format(Locale.US, "%.1f у.е.", avgSi), "Индекс напряжения");
            drawMetricRow(canvas1, 174, "Баланс LF/HF:", String.format(Locale.US, "%.2f", avgLfHf), "Симпато-вагальный баланс");
            drawMetricRow(canvas1, 190, "Энтропия (SampEn):", String.format(Locale.US, "%.2f", avgSampEn), "Хаотичность / Сложность");
            drawMetricRow(canvas1, 206, "DFA alpha1:", String.format(Locale.US, "%.2f", avgDfa), "Фрактальная корреляция");

            textPaint.setTextSize(12);
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.parseColor("#0F172A"));
            canvas1.drawText("2. Клиническая интерпретация состояния", MARGIN, 230, textPaint);

            String statusTitle, statusDesc;
            int statusColor;

            if (avgSi < 50) {
                statusTitle = "ВАГОТОНИЯ (Парасимпатическое преобладание)";
                statusDesc = "Выраженное преобладание парасимпатического отдела. Характерно для глубокого восстановления, отдыха или спортивной брадикардии.";
                statusColor = Color.parseColor("#047857");
            } else if (avgSi >= 50 && avgSi <= 150) {
                statusTitle = "НОРМА (Сбалансированная вегетативная регуляция)";
                statusDesc = "Оптимальный уровень адаптационных резервов. Организм находится в состоянии функционального баланса.";
                statusColor = Color.parseColor("#0284C7");
            } else if (avgSi > 150 && avgSi <= 500) {
                statusTitle = "УМЕРЕННЫЙ СТРЕСС / СИМПАТИКОТОНИЯ";
                statusDesc = "Преобладание симпатического тонуса. Указывает на мобилизацию ресурсов, умеренную нагрузку или начальную фазу утомления.";
                statusColor = Color.parseColor("#B45309");
            } else {
                statusTitle = "ВЫРАЖЕННОЕ ПЕРЕНАПРЯЖЕНИЕ / СТРЕСС";
                statusDesc = "Резкое снижение адаптационного потенциала, перенапряжение регуляторных систем. Рекомендован отдых и контроль нагрузки.";
                statusColor = Color.parseColor("#B91C1C");
            }

            fillPaint.setColor(Color.parseColor("#F8FAFC"));
            canvas1.drawRect(MARGIN, 238, PAGE_WIDTH - MARGIN, 294, fillPaint);
            strokePaint.setColor(statusColor);
            strokePaint.setStrokeWidth(1f);
            canvas1.drawRect(MARGIN, 238, PAGE_WIDTH - MARGIN, 294, strokePaint);

            TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setTextSize(11);
            titlePaint.setFakeBoldText(true);
            titlePaint.setColor(statusColor);

            TextPaint descPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            descPaint.setTextSize(9.5f);
            descPaint.setColor(Color.parseColor("#1E293B"));

            canvas1.save();
            canvas1.translate(MARGIN + 12, 246);
            StaticLayout.Builder.obtain(statusTitle, 0, statusTitle.length(), titlePaint, PAGE_WIDTH - 2 * MARGIN - 24)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build().draw(canvas1);
            canvas1.restore();

            canvas1.save();
            canvas1.translate(MARGIN + 12, 264);
            StaticLayout.Builder.obtain(statusDesc, 0, statusDesc.length(), descPaint, PAGE_WIDTH - 2 * MARGIN - 24)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build().draw(canvas1);
            canvas1.restore();

            drawHistogram(canvas1, rawRrList, MARGIN, 305, PAGE_WIDTH - 2 * MARGIN, 110, Color.parseColor("#DC2626"), "Гистограмма RR-интервалов (мс)");
            drawOxygenationChart(canvas1, dataPoints, MARGIN, 425, PAGE_WIDTH - 2 * MARGIN, 110, Color.parseColor("#0284C7"), "Динамика оксигенации SpO2 (%)");

            document.finishPage(page1);

            // ==========================================
            // СТРАНИЦА 2: Скатертограмма Пуанкаре
            // ==========================================
            PdfDocument.PageInfo pageInfo2 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create();
            PdfDocument.Page page2 = document.startPage(pageInfo2);
            Canvas canvas2 = page2.getCanvas();
            canvas2.drawColor(Color.WHITE);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(14);
            textPaint.setFakeBoldText(true);
            canvas2.drawText("3. Скатертограмма Пуанкаре и анализ геометрии", MARGIN, 45, textPaint);

            drawPoincarePlot(canvas2, snapshots, MARGIN, 65, PAGE_WIDTH - 2 * MARGIN, 350);

            textPaint.setTextSize(11);
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.parseColor("#0F172A"));
            canvas2.drawText("Расшифровка показателей Пуанкаре:", MARGIN, 432, textPaint);

            TextPaint poincareDescPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            poincareDescPaint.setTextSize(9.5f);
            poincareDescPaint.setColor(Color.parseColor("#334155"));

            String[] poincareDescriptions = {
                "• SD1 (поперечная ось): Характеризует краткосрочную (дыхательную) вариабельность и парасимпатический тонус.",
                "• SD2 (продольная ось): Характеризует долгосрочную вариабельность и общие адаптационные резервы организма.",
                "• DFA alpha1: Оценивает фрактальные корреляции. Значения около 1.0 указывают на сбалансированную работу регуляторных систем."
            };

            int currentDescY = 448;
            int availableWidth = PAGE_WIDTH - 2 * MARGIN;

            for (String desc : poincareDescriptions) {
                canvas2.save();
                canvas2.translate(MARGIN, currentDescY);
                StaticLayout layout = StaticLayout.Builder.obtain(desc, 0, desc.length(), poincareDescPaint, availableWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build();
                layout.draw(canvas2);
                canvas2.restore();
                currentDescY += layout.getHeight() + 4;
            }

            document.finishPage(page2);

            // ==========================================
            // СТРАНИЦЫ 3+: Хронологический лог снимков
            // ==========================================
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
            int rowsPerPage = 22;
            int totalSnapshots = snapshots.size();
            int totalPages = (int) Math.ceil((double) totalSnapshots / rowsPerPage);
            if (totalPages < 1) totalPages = 1;

            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                PdfDocument.PageInfo pageInfoN = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 3 + pageIndex).create();
                PdfDocument.Page pageN = document.startPage(pageInfoN);
                Canvas canvasN = pageN.getCanvas();
                canvasN.drawColor(Color.WHITE);

                textPaint.setColor(Color.parseColor("#0F172A"));
                textPaint.setTextSize(14);
                textPaint.setFakeBoldText(true);
                canvasN.drawText("4. Хронологический лог снимков (Страница " + (pageIndex + 1) + " из " + totalPages + ")", MARGIN, 45, textPaint);

                int startY = 80;
                textPaint.setTextSize(9.5f);
                textPaint.setFakeBoldText(true);
                textPaint.setColor(Color.parseColor("#0F172A"));
                canvasN.drawText("Время", MARGIN, startY, textPaint);
                canvasN.drawText("RMSSD", MARGIN + 65, startY, textPaint);
                canvasN.drawText("SDNN", MARGIN + 125, startY, textPaint);
                canvasN.drawText("SI", MARGIN + 185, startY, textPaint);
                canvasN.drawText("LF/HF", MARGIN + 240, startY, textPaint);
                canvasN.drawText("SampEn", MARGIN + 300, startY, textPaint);
                canvasN.drawText("DFA α1", MARGIN + 360, startY, textPaint);
                canvasN.drawText("Артефакты", MARGIN + 430, startY, textPaint);

                strokePaint.setColor(Color.parseColor("#CBD5E1"));
                strokePaint.setStrokeWidth(1f);
                canvasN.drawLine(MARGIN, startY + 6, PAGE_WIDTH - MARGIN, startY + 6, strokePaint);

                textPaint.setColor(Color.parseColor("#1E293B"));
                textPaint.setFakeBoldText(false);

                int startRow = pageIndex * rowsPerPage;
                int endRow = Math.min(startRow + rowsPerPage, totalSnapshots);
                int currentY = startY + 24;

                for (int i = startRow; i < endRow; i++) {
                    HrvSnapshotEntity s = snapshots.get(i);
                    canvasN.drawText(timeFormat.format(new Date(s.getTimestamp())), MARGIN, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getRmssd()), MARGIN + 65, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getSdnn()), MARGIN + 125, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getBaevskySi()), MARGIN + 185, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getLfHfRatio()), MARGIN + 240, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getSampleEntropy()), MARGIN + 300, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getDfaAlpha1()), MARGIN + 360, currentY, textPaint);
                    canvasN.drawText(s.getArtifactsDetected() + " (" + String.format(Locale.US, "%.1f%%", s.getArtifactPct()) + ")", MARGIN + 430, currentY, textPaint);

                    currentY += 22;
                }

                document.finishPage(pageN);
            }

            File targetDir;
            if (customDirPath != null && !customDirPath.isEmpty()) {
                targetDir = new File(customDirPath);
            } else {
                targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            }
            if (!targetDir.exists()) targetDir.mkdirs();

            String fileName = "HRV_Report_" + System.currentTimeMillis() + ".pdf";
            File file = new File(targetDir, fileName);

            document.writeTo(new FileOutputStream(file));
            document.close();

            if (callback != null) {
                callback.onSuccess(file.getAbsolutePath(), fileName);
            }

        } catch (IOException e) {
            document.close();
            if (callback != null) {
                callback.onError(e.getMessage());
            }
        }
    }

    private static void drawMetricRow(Canvas canvas, int y, String label, String val, String desc) {
        Paint rowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rowPaint.setTextSize(11);

        rowPaint.setColor(Color.parseColor("#334155"));
        rowPaint.setFakeBoldText(false);
        canvas.drawText(label, MARGIN, y, rowPaint);

        rowPaint.setColor(Color.parseColor("#0F172A"));
        rowPaint.setFakeBoldText(true);
        canvas.drawText(val, MARGIN + 180, y, rowPaint);

        rowPaint.setFakeBoldText(false);
        rowPaint.setColor(Color.parseColor("#64748B"));
        canvas.drawText(desc, MARGIN + 280, y, rowPaint);
    }

    private static void drawHistogram(Canvas canvas, List<Integer> rawRrList, int left, int top, int width, int height, int lineColor, String title) {
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);

        fillPaint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawRect(left, top, left + width, top + height, fillPaint);

        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(1f);
        canvas.drawRect(left, top, left + width, top + height, strokePaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#0F172A"));
        textPaint.setTextSize(10.5f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(title, left + 10, top + 16, textPaint);

        if (rawRrList == null || rawRrList.isEmpty()) return;

        int minRr = 400, maxRr = 1200;
        int binCount = 35;
        int binWidth = Math.max(1, (maxRr - minRr) / binCount);
        int[] bins = new int[binCount];
        int maxFreq = 1;

        for (int rr : rawRrList) {
            int binIdx = (rr - minRr) / binWidth;
            if (binIdx >= 0 && binIdx < binCount) {
                bins[binIdx]++;
                if (bins[binIdx] > maxFreq) maxFreq = bins[binIdx];
            }
        }

        int plotLeft = left + 15;
        int plotTop = top + 26;
        int plotWidth = width - 30;
        int plotHeight = height - 36;

        fillPaint.setColor(lineColor);
        float barW = (float) plotWidth / binCount;
        for (int i = 0; i < binCount; i++) {
            float barH = ((float) bins[i] / maxFreq) * plotHeight;
            canvas.drawRect(plotLeft + i * barW, plotTop + plotHeight - barH, plotLeft + (i + 1) * barW - 1, plotTop + plotHeight, fillPaint);
        }
    }

    private static void drawOxygenationChart(Canvas canvas, List<DataPoint> dataPoints, int left, int top, int width, int height, int lineColor, String title) {
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);

        fillPaint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawRect(left, top, left + width, top + height, fillPaint);

        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(1f);
        canvas.drawRect(left, top, left + width, top + height, strokePaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#0F172A"));
        textPaint.setTextSize(10.5f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(title, left + 10, top + 16, textPaint);

        int plotLeft = left + 45;
        int plotTop = top + 26;
        int plotWidth = width - 60;
        int plotHeight = height - 36;

        textPaint.setTextSize(8f);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(Color.parseColor("#64748B"));

        strokePaint.setColor(Color.parseColor("#E2E8F0"));
        strokePaint.setStrokeWidth(0.5f);

        float[] levels = {100f, 95f, 90f, 85f};
        for (float lvl : levels) {
            float y = plotTop + ((100f - lvl) / 15.0f) * plotHeight;
            canvas.drawLine(plotLeft, y, plotLeft + plotWidth, y, strokePaint);
            canvas.drawText(String.format(Locale.US, "%.0f%%", lvl), left + 12, y + 3, textPaint);
        }

        if (dataPoints == null || dataPoints.isEmpty()) return;

        strokePaint.setColor(lineColor);
        strokePaint.setStrokeWidth(2f);

        Path path = new Path();
        int size = dataPoints.size();
        for (int i = 0; i < size; i++) {
            DataPoint dp = dataPoints.get(i);
            float x = plotLeft + (i / (float) Math.max(1, size - 1)) * plotWidth;
            float spo2Val = Math.max(85f, Math.min(100f, (float) dp.spo2));
            float y = plotTop + ((100f - spo2Val) / 15.0f) * plotHeight;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, strokePaint);
    }

    private static void drawPoincarePlot(Canvas canvas, List<HrvSnapshotEntity> snapshots, int left, int top, int width, int height) {
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);

        fillPaint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawRect(left, top, left + width, top + height, fillPaint);

        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(1f);
        canvas.drawRect(left, top, left + width, top + height, strokePaint);

        int centerX = left + width / 2;
        int centerY = top + height / 2;

        strokePaint.setColor(Color.parseColor("#E2E8F0"));
        strokePaint.setStrokeWidth(1f);
        canvas.drawLine(left, centerY, left + width, centerY, strokePaint);
        canvas.drawLine(centerX, top, centerX, top + height, strokePaint);

        int index = 0;
        for (HrvSnapshotEntity s : snapshots) {
            float px = (float) (centerX + (s.getSd1() - 50) * 2.2);
            float py = (float) (centerY - (s.getSd2() - 50) * 2.2);

            if (px >= left + 15 && px <= left + width - 15 && py >= top + 15 && py <= top + height - 15) {
                if (index % 3 == 0) {
                    fillPaint.setColor(Color.parseColor("#64748B"));
                } else if (index % 3 == 1) {
                    fillPaint.setColor(Color.parseColor("#0284C7"));
                } else {
                    fillPaint.setColor(Color.parseColor("#0EA5E9"));
                }
                canvas.drawCircle(px, py, 4.5f, fillPaint);
            }
            index++;
        }
    }
}
