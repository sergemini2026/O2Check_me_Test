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

        // Расчет средних значений с фильтрацией артефактов
        double avgRmssd = 0, avgSdnn = 0, avgSi = 0, avgLfHf = 0, avgSd1 = 0, avgSd2 = 0;
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
                validCount++;
            }
            if (validCount > 0) {
                avgRmssd /= validCount;
                avgSdnn /= validCount;
                avgSi /= validCount;
                avgLfHf /= validCount;
                avgSd1 /= validCount;
                avgSd2 /= validCount;
            }
        }

        try {
            // ==========================================
            // СТРАНИЦА 1: Шапка, Сводка, Интерпретация, Графики (РР и SpO2)
            // ==========================================
            PdfDocument.PageInfo pageInfo1 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
            PdfDocument.Page page1 = document.startPage(pageInfo1);
            Canvas canvas1 = page1.getCanvas();
            canvas1.drawColor(Color.WHITE);

            Paint paint = new Paint();
            paint.setAntiAlias(true);

            // Шапка документа (В стиле Viatom)
            paint.setColor(Color.parseColor("#0F172A")); // Slate 900
            paint.setTextSize(16);
            paint.setFakeBoldText(true);
            canvas1.drawText("ОТЧЕТ АНАЛИЗА ВАРИАБЕЛЬНОСТИ СЕРДЕЧНОГО РИТМА", MARGIN, 45, paint);

            paint.setColor(Color.parseColor("#334155")); // Slate 700
            paint.setTextSize(10);
            paint.setFakeBoldText(false);
            canvas1.drawText("Профиль: " + profileName + "  |  Режим: " + workoutMode, MARGIN, 65, paint);
            canvas1.drawText("Период: " + sdf.format(new Date(startTime)) + " — " + sdf.format(new Date(endTime)), MARGIN, 80, paint);

            // Разделитель
            paint.setColor(Color.parseColor("#CBD5E1")); // Slate 300
            paint.setStrokeWidth(1f);
            canvas1.drawLine(MARGIN, 90, PAGE_WIDTH - MARGIN, 90, paint);

            // Блок 1: Сводные вегетативные показатели
            paint.setColor(Color.parseColor("#0F172A"));
            paint.setTextSize(12);
            paint.setFakeBoldText(true);
            canvas1.drawText("1. Сводные вегетативные показатели", MARGIN, 110, paint);

            drawMetricRow(canvas1, 130, "RMSSD (мск):", String.format(Locale.US, "%.1f", avgRmssd), "Норма: 20-100");
            drawMetricRow(canvas1, 148, "SDNN (мск):", String.format(Locale.US, "%.1f", avgSdnn), "Общая вариабельность");
            drawMetricRow(canvas1, 166, "Индекс Баевского (SI):", String.format(Locale.US, "%.1f у.е.", avgSi), "Индекс напряжения");
            drawMetricRow(canvas1, 184, "Баланс LF/HF:", String.format(Locale.US, "%.2f", avgLfHf), "Симпато-вагальный баланс");

            // Блок 2: Клиническая интерпретация состояния
            paint.setTextSize(12);
            paint.setFakeBoldText(true);
            paint.setColor(Color.parseColor("#0F172A"));
            canvas1.drawText("2. Клиническая интерпретация состояния", MARGIN, 212, paint);

            String statusTitle;
            String statusDesc;
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

            // Контейнер клинической интерпретации
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#F8FAFC"));
            canvas1.drawRect(MARGIN, 222, PAGE_WIDTH - MARGIN, 280, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(statusColor);
            canvas1.drawRect(MARGIN, 222, PAGE_WIDTH - MARGIN, 280, paint);

            TextPaint titlePaint = new TextPaint();
            titlePaint.setAntiAlias(true);
            titlePaint.setTextSize(11);
            titlePaint.setFakeBoldText(true);
            titlePaint.setColor(statusColor);

            TextPaint descPaint = new TextPaint();
            descPaint.setAntiAlias(true);
            descPaint.setTextSize(9.5f);
            descPaint.setColor(Color.parseColor("#1E293B"));

            canvas1.save();
            canvas1.translate(MARGIN + 12, 230);
            StaticLayout titleLayout = StaticLayout.Builder.obtain(statusTitle, 0, statusTitle.length(), titlePaint, PAGE_WIDTH - 2 * MARGIN - 24)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
            titleLayout.draw(canvas1);
            canvas1.restore();

            canvas1.save();
            canvas1.translate(MARGIN + 12, 248);
            StaticLayout descLayout = StaticLayout.Builder.obtain(statusDesc, 0, statusDesc.length(), descPaint, PAGE_WIDTH - 2 * MARGIN - 24)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
            descLayout.draw(canvas1);
            canvas1.restore();

            // Графики на первой странице (Гистограмма RR - красная, Оксигенация - голубая)
            drawHistogram(canvas1, rawRrList, MARGIN, 295, PAGE_WIDTH - 2 * MARGIN, 115, Color.parseColor("#DC2626"), "Гистограмма RR-интервалов (мс)");
            drawOxygenationChart(canvas1, dataPoints, MARGIN, 425, PAGE_WIDTH - 2 * MARGIN, 115, Color.parseColor("#0284C7"), "Динамика оксигенации SpO2 (%)");

            document.finishPage(page1);

            // ==========================================
            // СТРАНИЦА 2: Скатертограмма Пуанкаре + Описание и расшифровка
            // ==========================================
            PdfDocument.PageInfo pageInfo2 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create();
            PdfDocument.Page page2 = document.startPage(pageInfo2);
            Canvas canvas2 = page2.getCanvas();
            canvas2.drawColor(Color.WHITE);

            paint.setColor(Color.parseColor("#0F172A"));
            paint.setTextSize(14);
            paint.setFakeBoldText(true);
            canvas2.drawText("3. Скатертограмма Пуанкаре и анализ геометрии", MARGIN, 45, paint);

            // Крупная скатертограмма по центру
            drawPoincarePlot(canvas2, snapshots, MARGIN, 65, PAGE_WIDTH - 2 * MARGIN, 350);

            // Текстовое описание и расшифровка под графиком
            paint.setTextSize(11);
            paint.setFakeBoldText(true);
            paint.setColor(Color.parseColor("#0F172A"));
            canvas2.drawText("Расшифровка показателей Пуанкаре:", MARGIN, 435, paint);

            paint.setTextSize(10f);
            paint.setFakeBoldText(false);
            paint.setColor(Color.parseColor("#334155"));
            
            int textY = 455;
            canvas2.drawText("• SD1 (поперечная ось): Характеризует краткосрочную (дыхательную) вариабельность и парасимпатический тонус.", MARGIN, textY, paint);
            textY += 18;
            canvas2.drawText("• SD2 (продольная ось): Характеризует долгосрочную вариабельность и общие адаптационные резервы организма.", MARGIN, textY, paint);
            textY += 18;
            canvas2.drawText("• Цветовая кодировка точек: Серые точки — стабильные зоны, синие и голубые — фазы активной вегетативной регуляции.", MARGIN, textY, paint);
            textY += 18;
            canvas2.drawText("• Облако точек (форма кометы): Вытянутая форма указывает на нормальный синусовый ритм и сбалансированность регуляции.", MARGIN, textY, paint);

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

                paint.setColor(Color.parseColor("#0F172A"));
                paint.setTextSize(14);
                paint.setFakeBoldText(true);
                canvasN.drawText("4. Хронологический лог снимков (Страница " + (pageIndex + 1) + " из " + totalPages + ")", MARGIN, 45, paint);

                // Заголовки таблицы
                int startY = 80;
                paint.setTextSize(10);
                paint.setFakeBoldText(true);
                paint.setColor(Color.parseColor("#0F172A"));
                canvasN.drawText("Время", MARGIN, startY, paint);
                canvasN.drawText("RMSSD", MARGIN + 80, startY, paint);
                canvasN.drawText("SDNN", MARGIN + 160, startY, paint);
                canvasN.drawText("SI (Баевский)", MARGIN + 240, startY, paint);
                canvasN.drawText("LF/HF", MARGIN + 360, startY, paint);
                canvasN.drawText("Артефакты", MARGIN + 440, startY, paint);

                paint.setColor(Color.parseColor("#CBD5E1"));
                canvasN.drawLine(MARGIN, startY + 6, PAGE_WIDTH - MARGIN, startY + 6, paint);

                paint.setColor(Color.parseColor("#1E293B"));
                paint.setFakeBoldText(false);

                int startRow = pageIndex * rowsPerPage;
                int endRow = Math.min(startRow + rowsPerPage, totalSnapshots);
                int currentY = startY + 24;

                for (int i = startRow; i < endRow; i++) {
                    HrvSnapshotEntity s = snapshots.get(i);
                    canvasN.drawText(timeFormat.format(new Date(s.getTimestamp())), MARGIN, currentY, paint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getRmssd()), MARGIN + 80, currentY, paint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getSdnn()), MARGIN + 160, currentY, paint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getBaevskySi()), MARGIN + 240, currentY, paint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getLfHfRatio()), MARGIN + 360, currentY, paint);
                    canvasN.drawText(s.getArtifactsDetected() + " (" + String.format(Locale.US, "%.1f%%", s.getArtifactPct()) + ")", MARGIN + 440, currentY, paint);

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
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(11);

        paint.setColor(Color.parseColor("#334155"));
        canvas.drawText(label, MARGIN, y, paint);

        paint.setColor(Color.parseColor("#0F172A"));
        paint.setFakeBoldText(true);
        canvas.drawText(val, MARGIN + 180, y, paint);

        paint.setFakeBoldText(false);
        paint.setColor(Color.parseColor("#64748B"));
        canvas.drawText(desc, MARGIN + 280, y, paint);
    }

    private static void drawHistogram(Canvas canvas, List<Integer> rawRrList, int left, int top, int width, int height, int lineColor, String title) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Рамка и фон графика (в стиле Viatom)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawRect(left, top, left + width, top + height, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawRect(left, top, left + width, top + height, paint);

        // Заголовок
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#0F172A"));
        paint.setTextSize(10.5f);
        paint.setFakeBoldText(true);
        canvas.drawText(title, left + 10, top + 16, paint);

        if (rawRrList == null || rawRrList.isEmpty()) return;

        // Построение гистограммы распределения
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

        paint.setColor(lineColor);
        paint.setStyle(Paint.Style.FILL);

        float barW = (float) plotWidth / binCount;
        for (int i = 0; i < binCount; i++) {
            float barH = ((float) bins[i] / maxFreq) * plotHeight;
            canvas.drawRect(plotLeft + i * barW, plotTop + plotHeight - barH, plotLeft + (i + 1) * barW - 1, plotTop + plotHeight, paint);
        }
    }

    private static void drawOxygenationChart(Canvas canvas, List<DataPoint> dataPoints, int left, int top, int width, int height, int lineColor, String title) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawRect(left, top, left + width, top + height, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawRect(left, top, left + width, top + height, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#0F172A"));
        paint.setTextSize(10.5f);
        paint.setFakeBoldText(true);
        canvas.drawText(title, left + 10, top + 16, paint);

        int plotLeft = left + 15;
        int plotTop = top + 26;
        int plotWidth = width - 30;
        int plotHeight = height - 36;

        // Горизонтальные линии сетки (90%, 95%, 100%)
        paint.setColor(Color.parseColor("#E2E8F0"));
        paint.setStrokeWidth(0.5f);
        canvas.drawLine(plotLeft, plotTop + plotHeight * 0.2f, plotLeft + plotWidth, plotTop + plotHeight * 0.2f, paint);
        canvas.drawLine(plotLeft, plotTop + plotHeight * 0.6f, plotLeft + plotWidth, plotTop + plotHeight * 0.6f, paint);

        if (dataPoints == null || dataPoints.isEmpty()) return;

        paint.setColor(lineColor);
        paint.setStrokeWidth(2f);
        paint.setStyle(Paint.Style.STROKE);

        Path path = new Path();
        int size = dataPoints.size();
        for (int i = 0; i < size; i++) {
            DataPoint dp = dataPoints.get(i);
            float x = plotLeft + (i / (float) Math.max(1, size - 1)) * plotWidth;
            // Масштабируем SpO2 от 85% до 100%
            float spo2Val = Math.max(85f, Math.min(100f, dp.spo2));
            float y = plotTop + plotHeight - ((spo2Val - 85f) / 15.0f) * plotHeight;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }

    private static void drawPoincarePlot(Canvas canvas, List<HrvSnapshotEntity> snapshots, int left, int top, int width, int height) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawRect(left, top, left + width, top + height, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawRect(left, top, left + width, top + height, paint);

        int centerX = left + width / 2;
        int centerY = top + height / 2;

        // Оси координат
        paint.setColor(Color.parseColor("#E2E8F0"));
        paint.setStrokeWidth(1f);
        canvas.drawLine(left, centerY, left + width, centerY, paint);
        canvas.drawLine(centerX, top, centerX, top + height, paint);

        // Отрисовка точек с цветовой кодировкой
        int index = 0;
        for (HrvSnapshotEntity s : snapshots) {
            float px = (float) (centerX + (s.getSd1() - 50) * 2.2);
            float py = (float) (centerY - (s.getSd2() - 50) * 2.2);
            
            if (px >= left + 15 && px <= left + width - 15 && py >= top + 15 && py <= top + height - 15) {
                if (index % 3 == 0) {
                    paint.setColor(Color.parseColor("#64748B")); // Серый
                } else if (index % 3 == 1) {
                    paint.setColor(Color.parseColor("#0284C7")); // Синий
                } else {
                    paint.setColor(Color.parseColor("#0EA5E9")); // Голубой
                }
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(px, py, 4.5f, paint);
            }
            index++;
        }
    }
}
