package com.local.o2test;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.local.o2test.db.entity.HrvSnapshotEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

        double avgRmssd = 0, avgSdnn = 0, avgSi = 0, avgLfHf = 0, avgTp = 0, avgSd1 = 0, avgSd2 = 0, avgSampEn = 0, avgDfa = 0;
        int validCount = 0;
        if (!snapshots.isEmpty()) {
            for (HrvSnapshotEntity s : snapshots) {
                if (s.getBaevskySi() > 300 || s.getBaevskySi() <= 0) continue;
                avgRmssd += s.getRmssd();
                avgSdnn += s.getSdnn();
                avgSi += s.getBaevskySi();
                avgLfHf += s.getLfHfRatio();
                avgTp += s.getTotalPower();
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
                avgTp /= validCount;
                avgSd1 /= validCount;
                avgSd2 /= validCount;
                avgSampEn /= validCount;
                avgDfa /= validCount;
            }
        }

        try {
            // ==========================================
            // СТРАНИЦА 1: Шапка, Сводка, Интерпретация, Графики (Гистограмма, SpO2, HR)
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
            canvas1.drawText("ОТЧЕТ АНАЛИЗА ВАРИАБЕЛЬНОСТИ СЕРДЕЧНОГО РИТМА", MARGIN, 40, textPaint);

            textPaint.setTextSize(10);
            textPaint.setFakeBoldText(false);
            textPaint.setColor(Color.parseColor("#334155"));
            canvas1.drawText("Профиль: " + profileName + "  |  Режим: " + workoutMode, MARGIN, 56, textPaint);
            canvas1.drawText("Период: " + sdf.format(new Date(startTime)) + " — " + sdf.format(new Date(endTime)), MARGIN, 70, textPaint);

            strokePaint.setColor(Color.parseColor("#CBD5E1"));
            strokePaint.setStrokeWidth(1f);
            canvas1.drawLine(MARGIN, 78, PAGE_WIDTH - MARGIN, 78, strokePaint);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(11.5f);
            textPaint.setFakeBoldText(true);
            canvas1.drawText("1. Сводные вегетативные показатели", MARGIN, 94, textPaint);

            drawMetricRow(canvas1, 108, "RMSSD (мск):", String.format(Locale.US, "%.1f", avgRmssd), "Норма: 20-100 (Вагусный тонус)");
            drawMetricRow(canvas1, 122, "SDNN (мск):", String.format(Locale.US, "%.1f", avgSdnn), "Общая вариабельность ритма");
            drawMetricRow(canvas1, 136, "Индекс Баевского (SI):", String.format(Locale.US, "%.1f у.е.", avgSi), "Индекс напряжения регуляторных систем");
            drawMetricRow(canvas1, 150, "Баланс LF/HF:", String.format(Locale.US, "%.2f", avgLfHf), "Симпато-вагальный коэффициент");
            drawMetricRow(canvas1, 164, "Общая мощность (TP):", String.format(Locale.US, "%.0f ms²", avgTp), "Суммарный вегетативный резерв");
            drawMetricRow(canvas1, 178, "Энтропия (SampEn):", String.format(Locale.US, "%.2f", avgSampEn), "Хаотичность и сложность регуляции");
            drawMetricRow(canvas1, 192, "DFA alpha1:", String.format(Locale.US, "%.2f", avgDfa), "Фрактальная корреляция (самоподобие)");

            textPaint.setTextSize(11.5f);
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.parseColor("#0F172A"));
            canvas1.drawText("2. Клиническое заключение и статус", MARGIN, 212, textPaint);

            String statusTitle, statusDesc;
            int statusColor;

            if (avgSi < 50) {
                statusTitle = "ВАГОТОНИЯ (Парасимпатическое преобладание)";
                statusDesc = "Выраженное преобладание парасимпатического отдела. Организм находится в зоне глубокого восстановления, отдыха или спортивной брадикардии.";
                statusColor = Color.parseColor("#047857");
            } else if (avgSi >= 50 && avgSi <= 150) {
                statusTitle = "НОРМА (Сбалансированная вегетативная регуляция)";
                statusDesc = "Оптимальный уровень адаптационных резервов. Вегетативная нервная система сбалансирована, нагрузка переносится гармонично.";
                statusColor = Color.parseColor("#0284C7");
            } else if (avgSi > 150 && avgSi <= 500) {
                statusTitle = "УМЕРЕННЫЙ СТРЕСС / СИМПАТИКОТОНИЯ";
                statusDesc = "Преобладание симпатического тонуса. Указывает на мобилизацию ресурсов, активную работу, умеренную нагрузку или начальную фазу утомления.";
                statusColor = Color.parseColor("#B45309");
            } else {
                statusTitle = "ВЫРАЖЕННОЕ ПЕРЕНАПРЯЖЕНИЕ / СТРЕСС";
                statusDesc = "Резкое снижение адаптационного потенциала, перенапряжение регуляторных систем. Рекомендован отдых, контроль нагрузки и пауза для восстановления.";
                statusColor = Color.parseColor("#B91C1C");
            }

            fillPaint.setColor(Color.parseColor("#F8FAFC"));
            canvas1.drawRect(MARGIN, 220, PAGE_WIDTH - MARGIN, 272, fillPaint);
            strokePaint.setColor(statusColor);
            strokePaint.setStrokeWidth(1f);
            canvas1.drawRect(MARGIN, 220, PAGE_WIDTH - MARGIN, 272, strokePaint);

            TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setTextSize(10.5f);
            titlePaint.setFakeBoldText(true);
            titlePaint.setColor(statusColor);

            TextPaint descPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            descPaint.setTextSize(9f);
            descPaint.setColor(Color.parseColor("#1E293B"));

            canvas1.save();
            canvas1.translate(MARGIN + 10, 226);
            StaticLayout.Builder.obtain(statusTitle, 0, statusTitle.length(), titlePaint, PAGE_WIDTH - 2 * MARGIN - 20)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build().draw(canvas1);
            canvas1.restore();

            canvas1.save();
            canvas1.translate(MARGIN + 10, 242);
            StaticLayout.Builder.obtain(statusDesc, 0, statusDesc.length(), descPaint, PAGE_WIDTH - 2 * MARGIN - 20)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build().draw(canvas1);
            canvas1.restore();

            drawHistogram(canvas1, rawRrList, MARGIN, 282, PAGE_WIDTH - 2 * MARGIN, 90, Color.parseColor("#DC2626"), "Гистограмма RR-интервалов (мс) — распределение ритма");
            drawOxygenationChart(canvas1, dataPoints, MARGIN, 384, PAGE_WIDTH - 2 * MARGIN, 75, Color.parseColor("#0284C7"), "Динамика оксигенации SpO2 (%)");
            drawHeartRateChart(canvas1, dataPoints, MARGIN, 470, PAGE_WIDTH - 2 * MARGIN, 75, Color.parseColor("#10B981"), "Динамика ЧСС (HR, уд/мин)");

            document.finishPage(page1);

            // ==========================================
            // СТРАНИЦА 2: Скатертограмма Пуанкаре + Исправленная расшифровка
            // ==========================================
            PdfDocument.PageInfo pageInfo2 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create();
            PdfDocument.Page page2 = document.startPage(pageInfo2);
            Canvas canvas2 = page2.getCanvas();
            canvas2.drawColor(Color.WHITE);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(14);
            textPaint.setFakeBoldText(true);
            canvas2.drawText("3. Скатертограмма Пуанкаре и анализ геометрии", MARGIN, 45, textPaint);

            drawPoincarePlot(canvas2, rawRrList, MARGIN, 65, PAGE_WIDTH - 2 * MARGIN, 340);

            textPaint.setTextSize(11);
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.parseColor("#0F172A"));
            canvas2.drawText("Расшифровка показателей Пуанкаре:", MARGIN, 420, textPaint);

            TextPaint poincareDescPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            poincareDescPaint.setTextSize(9.0f);
            poincareDescPaint.setColor(Color.parseColor("#334155"));

            String[] poincareDescriptions = {
                "• SD1 (поперечная ось): Характеризует краткосрочную (дыхательную) вариабельность и парасимпатический тонус.",
                "• SD2 (продольная ось): Характеризует долгосрочную вариабельность и общие адаптационные резервы организма.",
                "• Цветовая градация точек: От светлых (начало серий / стабильные зоны) до насыщенных синих (фазы активной вегетативной регуляции).",
                "• Облако точек (форма кометы): Вытянутая эллиптическая форма указывает на нормальный синусовый ритм и сбалансированность регуляции."
            };

            int currentDescY = 434;
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
            // СТРАНИЦЫ 3+: Хронологический лог снимков (с колонкой TP)
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
                textPaint.setTextSize(9f);
                textPaint.setFakeBoldText(true);
                textPaint.setColor(Color.parseColor("#0F172A"));
                canvasN.drawText("Время", MARGIN, startY, textPaint);
                canvasN.drawText("RMSSD", MARGIN + 55, startY, textPaint);
                canvasN.drawText("SDNN", MARGIN + 105, startY, textPaint);
                canvasN.drawText("SI", MARGIN + 155, startY, textPaint);
                canvasN.drawText("LF/HF", MARGIN + 200, startY, textPaint);
                canvasN.drawText("TP", MARGIN + 250, startY, textPaint);
                canvasN.drawText("SampEn", MARGIN + 310, startY, textPaint);
                canvasN.drawText("DFA α1", MARGIN + 370, startY, textPaint);
                canvasN.drawText("Артефакты", MARGIN + 435, startY, textPaint);

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
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getRmssd()), MARGIN + 55, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getSdnn()), MARGIN + 105, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getBaevskySi()), MARGIN + 155, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getLfHfRatio()), MARGIN + 200, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.0f", s.getTotalPower()), MARGIN + 250, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getSampleEntropy()), MARGIN + 310, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getDfaAlpha1()), MARGIN + 370, currentY, textPaint);
                    canvasN.drawText(s.getArtifactsDetected() + " (" + String.format(Locale.US, "%.1f%%", s.getArtifactPct()) + ")", MARGIN + 435, currentY, textPaint);

                    currentY += 22;
                }

                document.finishPage(pageN);
            }

            // ==========================================
            // Сохранение файла
            // ==========================================
            String fileName = "HRV_Report_" + System.currentTimeMillis() + ".pdf";
            Uri publicUri = null;
            File legacyFile = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);

                ContentResolver resolver = context.getContentResolver();
                Uri collection = MediaStore.Files.getContentUri("external");
                publicUri = resolver.insert(collection, values);

                if (publicUri != null) {
                    try (OutputStream fos = resolver.openOutputStream(publicUri)) {
                        document.writeTo(fos);
                    }
                }
            } else {
                File publicDir;
                if (customDirPath != null && !customDirPath.isEmpty()) {
                    publicDir = new File(customDirPath);
                } else {
                    publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                }
                if (!publicDir.exists()) publicDir.mkdirs();
                legacyFile = new File(publicDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(legacyFile)) {
                    document.writeTo(fos);
                }
            }
            document.close();

            File internalDir = context.getExternalFilesDir(null);
            if (internalDir != null && !internalDir.exists()) internalDir.mkdirs();
            File internalFile = new File(internalDir, fileName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && publicUri != null) {
                try (InputStream in = context.getContentResolver().openInputStream(publicUri);
                     OutputStream out = new FileOutputStream(internalFile)) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
            } else if (legacyFile != null) {
                copyFile(legacyFile, internalFile);
            }

            if (callback != null) {
                callback.onSuccess(internalFile.getAbsolutePath(), fileName);
            }

        } catch (IOException e) {
            document.close();
            if (callback != null) {
                callback.onError(e.getMessage());
            }
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private static void drawMetricRow(Canvas canvas, int y, String label, String val, String desc) {
        Paint rowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rowPaint.setTextSize(10.5f);

        rowPaint.setColor(Color.parseColor("#334155"));
        rowPaint.setFakeBoldText(false);
        canvas.drawText(label, MARGIN, y, rowPaint);

        rowPaint.setColor(Color.parseColor("#0F172A"));
        rowPaint.setFakeBoldText(true);
        canvas.drawText(val, MARGIN + 160, y, rowPaint);

        rowPaint.setFakeBoldText(false);
        rowPaint.setColor(Color.parseColor("#64748B"));
        canvas.drawText(desc, MARGIN + 260, y, rowPaint);
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
        textPaint.setTextSize(10f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(title, left + 8, top + 14, textPaint);

        if (rawRrList == null || rawRrList.isEmpty()) return;

        int minRr = 2000, maxRr = 400;
        for (int rr : rawRrList) {
            if (rr >= 300 && rr <= 2500) {
                if (rr < minRr) minRr = rr;
                if (rr > maxRr) maxRr = rr;
            }
        }
        if (minRr >= maxRr) {
            minRr = 600;
            maxRr = 1200;
        } else {
            minRr = (minRr / 100) * 100;
            maxRr = ((maxRr / 100) + 1) * 100;
        }

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

        int plotLeft = left + 12;
        int plotTop = top + 20;
        int plotWidth = width - 24;
        int plotHeight = height - 28;

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
        textPaint.setTextSize(10f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(title, left + 8, top + 14, textPaint);

        int plotLeft = left + 40;
        int plotTop = top + 20;
        int plotWidth = width - 55;
        int plotHeight = height - 26;

        textPaint.setTextSize(7.5f);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(Color.parseColor("#64748B"));

        strokePaint.setColor(Color.parseColor("#E2E8F0"));
        strokePaint.setStrokeWidth(0.5f);

        float[] levels = {100f, 95f, 90f, 85f};
        for (float lvl : levels) {
            float y = plotTop + ((100f - lvl) / 15.0f) * plotHeight;
            canvas.drawLine(plotLeft, y, plotLeft + plotWidth, y, strokePaint);
            canvas.drawText(String.format(Locale.US, "%.0f%%", lvl), left + 10, y + 3, textPaint);
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

    private static void drawHeartRateChart(Canvas canvas, List<DataPoint> dataPoints, int left, int top, int width, int height, int lineColor, String title) {
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
        textPaint.setTextSize(10f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(title, left + 8, top + 14, textPaint);

        int plotLeft = left + 40;
        int plotTop = top + 20;
        int plotWidth = width - 55;
        int plotHeight = height - 26;

        textPaint.setTextSize(7.5f);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(Color.parseColor("#64748B"));

        strokePaint.setColor(Color.parseColor("#E2E8F0"));
        strokePaint.setStrokeWidth(0.5f);

        float minHr = 40f;
        float maxHr = 200f;
        float[] hrLevels = {200f, 160f, 120f, 80f, 40f};
        for (float lvl : hrLevels) {
            float y = plotTop + ((maxHr - lvl) / (maxHr - minHr)) * plotHeight;
            canvas.drawLine(plotLeft, y, plotLeft + plotWidth, y, strokePaint);
            canvas.drawText(String.format(Locale.US, "%.0f", lvl), left + 12, y + 3, textPaint);
        }

        if (dataPoints == null || dataPoints.isEmpty()) return;

        strokePaint.setColor(lineColor);
        strokePaint.setStrokeWidth(2f);

        Path path = new Path();
        int size = dataPoints.size();
        for (int i = 0; i < size; i++) {
            DataPoint dp = dataPoints.get(i);
            float x = plotLeft + (i / (float) Math.max(1, size - 1)) * plotWidth;
            float hrVal = Math.max(minHr, Math.min(maxHr, (float) dp.hr));
            float y = plotTop + ((maxHr - hrVal) / (maxHr - minHr)) * plotHeight;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, strokePaint);
    }

    private static void drawPoincarePlot(Canvas canvas, List<Integer> rawRrList, int left, int top, int width, int height) {
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);

        fillPaint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawRect(left, top, left + width, top + height, fillPaint);

        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(1f);
        canvas.drawRect(left, top, left + width, top + height, strokePaint);

        int plotLeft = left + 40;
        int plotTop = top + 20;
        int plotWidth = width - 60;
        int plotHeight = height - 50;

        if (rawRrList == null || rawRrList.size() < 2) {
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(11f);
            canvas.drawText("Недостаточно данных RR для построения Пуанкаре", left + 20, top + height / 2, textPaint);
            return;
        }

        int minRr = 500;
        int maxRr = 1300;
        for (int rr : rawRrList) {
            if (rr > 300 && rr < 2500) {
                if (rr < minRr) minRr = (rr / 100) * 100;
                if (rr > maxRr) maxRr = ((rr / 100) + 1) * 100;
            }
        }
        if (maxRr <= minRr) maxRr = minRr + 500;

        strokePaint.setColor(Color.parseColor("#94A3B8"));
        strokePaint.setStrokeWidth(1.5f);
        canvas.drawLine(plotLeft, plotTop + plotHeight, plotLeft + plotWidth, plotTop, strokePaint);

        int totalPairs = rawRrList.size() - 1;
        for (int i = 0; i < totalPairs; i++) {
            int rrN = rawRrList.get(i);
            int rrN1 = rawRrList.get(i + 1);

            if (rrN >= 300 && rrN <= 2000 && rrN1 >= 300 && rrN1 <= 2000) {
                float x = plotLeft + ((float) (rrN - minRr) / (maxRr - minRr)) * plotWidth;
                float y = plotTop + plotHeight - ((float) (rrN1 - minRr) / (maxRr - minRr)) * plotHeight;

                if (x >= plotLeft && x <= plotLeft + plotWidth && y >= plotTop && y <= plotTop + plotHeight) {
                    float progress = (float) i / Math.max(1, totalPairs);
                    int red = (int) (14 + progress * (2 - 14));
                    int green = (int) (132 + progress * (132 - 132));
                    int blue = (int) (199 + progress * (199 - 199));
                    
                    fillPaint.setColor(Color.rgb(Math.max(0, red), Math.min(255, green), Math.min(255, blue)));
                    canvas.drawCircle(x, y, 2.0f, fillPaint);
                }
            }
        }

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#475569"));
        labelPaint.setTextSize(9f);
        canvas.drawText(minRr + " мс", plotLeft, plotTop + plotHeight + 15, labelPaint);
        canvas.drawText(maxRr + " мс", plotLeft + plotWidth - 40, plotTop + plotHeight + 15, labelPaint);
        canvas.drawText("RR(n) мс", plotLeft + plotWidth / 2 - 20, plotTop + plotHeight + 28, labelPaint);
    }
}
