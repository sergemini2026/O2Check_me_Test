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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
            double pstiScore,
            double pstiRmssdRatio,
            List<Double> pstiHistory,
            PdfExportCallback callback
    ) {
        PdfDocument document = new PdfDocument();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);

        double avgRmssd = 0, avgSdnn = 0, avgSi = 0, avgLfHf = 0, avgTp = 0, avgSampEn = 0, avgDfa = 0;
        int validCount = 0;
        if (snapshots != null && !snapshots.isEmpty()) {
            for (HrvSnapshotEntity s : snapshots) {
                if (s.getBaevskySi() > 300 || s.getBaevskySi() <= 0) continue;
                avgRmssd += s.getRmssd();
                avgSdnn += s.getSdnn();
                avgSi += s.getBaevskySi();

                double lf = s.getLfPower();
                double hf = s.getHfPower();
                double lfHfVal = (lf + hf > 0) ? (2.0 * lf / (lf + hf)) : s.getLfHfRatio();
                if (lfHfVal < 0) lfHfVal = 0;
                if (lfHfVal > 2.0) lfHfVal = 2.0;
                avgLfHf += lfHfVal;

                avgTp += s.getTotalPower();
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
                avgSampEn /= validCount;
                avgDfa /= validCount;
            }
        }

        try {
            int currentPageNumber = 1;

            // ==========================================
            // СТРАНИЦА 1: Сводка, Заключение, Гистограмма RR, Пуанкаре
            // ==========================================
            PdfDocument.PageInfo pageInfo1 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber++).create();
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
            textPaint.setTextSize(15);
            textPaint.setFakeBoldText(true);
            canvas1.drawText("ОТЧЕТ АНАЛИЗА ВАРИАБЕЛЬНОСТИ СЕРДЕЧНОГО РИТМА", MARGIN, 36, textPaint);

            textPaint.setTextSize(9.0f);
            textPaint.setFakeBoldText(false);
            textPaint.setColor(Color.parseColor("#334155"));
            String headerInfoLine = String.format(Locale.US, "Профиль: %s  |  Режим: %s  |  Период: %s — %s",
                    profileName, workoutMode, sdf.format(new Date(startTime)), sdf.format(new Date(endTime)));
            canvas1.drawText(headerInfoLine, MARGIN, 51, textPaint);

            strokePaint.setColor(Color.parseColor("#CBD5E1"));
            strokePaint.setStrokeWidth(1f);
            canvas1.drawLine(MARGIN, 58, PAGE_WIDTH - MARGIN, 58, strokePaint);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(11f);
            textPaint.setFakeBoldText(true);
            canvas1.drawText("1. Сводные вегетативные показатели и индекс следа (PSTI)", MARGIN, 73, textPaint);

            double iaPercent = Math.max(0.0, Math.min(100.0, (avgLfHf - 1.0) * 100.0));
            drawMetricRow(canvas1, 86, "Индекс Аттрактора (IA):", String.format(Locale.US, "%.1f%%", iaPercent), "Степень вовлечения в когерентность (0-100%)");
            drawMetricRow(canvas1, 99, "Индекс PSTI (след):", String.format(Locale.US, "%.1f%% (ratio: %.2f)", pstiScore, pstiRmssdRatio), "Консолидация барорефлекса (след. реакция)");
            drawMetricRow(canvas1, 112, "Индекс Баевского (SI):", String.format(Locale.US, "%.1f у.е.", avgSi), "Индекс напряжения регуляторных систем");
            drawMetricRow(canvas1, 125, "Энтропия (SampEn):", String.format(Locale.US, "%.2f", avgSampEn), "Хаотичность и сложность регуляции");
            drawMetricRow(canvas1, 138, "DFA alpha1:", String.format(Locale.US, "%.2f", avgDfa), "Фрактальная корреляция (самоподобие)");
            drawMetricRow(canvas1, 151, "RMSSD (мск):", String.format(Locale.US, "%.1f", avgRmssd), "Норма: 20-100 (Вагусный тонус)");
            drawMetricRow(canvas1, 164, "SDNN (мск):", String.format(Locale.US, "%.1f", avgSdnn), "Общая вариабельность ритма");
            drawMetricRow(canvas1, 177, "LF/HF (средний):", String.format(Locale.US, "%.2f", avgLfHf), "Баланс симпатической и вагусной активности");
            drawMetricRow(canvas1, 190, "Общая мощность (TP):", String.format(Locale.US, "%.0f ms²", avgTp), "Суммарный вегетативный резерв");

            textPaint.setTextSize(11f);
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.parseColor("#0F172A"));
            canvas1.drawText("2. Клиническое заключение и статус", MARGIN, 205, textPaint);

            String statusTitle, statusDesc;
            int statusColor;
            if (avgSi < 50) {
                statusTitle = "ВАГОТОНИЯ (Парасимпатическое преобладание)";
                statusDesc = "Выраженное преобладание парасимпатического отдела. Организм находится в зоне глубокого восстановления, отдыха или спортивной брадикардии.";
                statusColor = Color.parseColor("#047857");
            } else if (avgSi <= 150) {
                statusTitle = "НОРМА (Сбалансированная вегетативная регуляция)";
                statusDesc = "Оптимальный уровень адаптационных резервов. Вегетативная нервная система сбалансирована, нагрузка переносится гармонично.";
                statusColor = Color.parseColor("#0284C7");
            } else if (avgSi <= 500) {
                statusTitle = "УМЕРЕННЫЙ СТРЕСС / СИМПАТИКОТОНИЯ";
                statusDesc = "Преобладание симпатического тонуса. Указывает на мобилизацию ресурсов, активную работу, умеренную нагрузку или начальную фазу утомления.";
                statusColor = Color.parseColor("#B45309");
            } else {
                statusTitle = "ВЫРАЖЕННОЕ ПЕРЕНАПРЯЖЕНИЕ / СТРЕСС";
                statusDesc = "Резкое снижение адаптационного потенциала, перенапряжение регуляторных систем. Рекомендован отдых, контроль нагрузки и пауза для восстановления.";
                statusColor = Color.parseColor("#B91C1C");
            }

            fillPaint.setColor(Color.parseColor("#F8FAFC"));
            canvas1.drawRect(MARGIN, 211, PAGE_WIDTH - MARGIN, 255, fillPaint);
            strokePaint.setColor(statusColor);
            strokePaint.setStrokeWidth(1f);
            canvas1.drawRect(MARGIN, 211, PAGE_WIDTH - MARGIN, 255, strokePaint);

            TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setTextSize(10f);
            titlePaint.setFakeBoldText(true);
            titlePaint.setColor(statusColor);

            TextPaint descPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            descPaint.setTextSize(8.5f);
            descPaint.setColor(Color.parseColor("#1E293B"));

            canvas1.save();
            canvas1.translate(MARGIN + 10, 215);
            StaticLayout.Builder.obtain(statusTitle, 0, statusTitle.length(), titlePaint, PAGE_WIDTH - 2 * MARGIN - 20)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build().draw(canvas1);
            canvas1.restore();

            canvas1.save();
            canvas1.translate(MARGIN + 10, 229);
            StaticLayout.Builder.obtain(statusDesc, 0, statusDesc.length(), descPaint, PAGE_WIDTH - 2 * MARGIN - 20)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build().draw(canvas1);
            canvas1.restore();

            drawHistogram(canvas1, rawRrList, MARGIN, 263, PAGE_WIDTH - 2 * MARGIN, 60, Color.parseColor("#DC2626"), "Гистограмма RR-интервалов (мс) — распределение ритма");

            TextPaint histExpPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            histExpPaint.setTextSize(7.5f);
            histExpPaint.setColor(Color.parseColor("#334155"));
            String histLine = "Ширина и форма: Широкое основание («колокол») говорит о высокой вариабельности. Узкий, высокий пик — о ригидности ритма.";
            canvas1.save();
            canvas1.translate(MARGIN, 326);
            StaticLayout.Builder.obtain(histLine, 0, histLine.length(), histExpPaint, PAGE_WIDTH - 2 * MARGIN)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build().draw(canvas1);
            canvas1.restore();

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(11f);
            textPaint.setFakeBoldText(true);
            canvas1.drawText("3. Скатертограмма Пуанкаре и анализ геометрии", MARGIN, 350, textPaint);

            drawPoincarePlot(canvas1, rawRrList, MARGIN, 360, PAGE_WIDTH - 2 * MARGIN, 325);

            textPaint.setTextSize(9.5f);
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.parseColor("#0F172A"));
            canvas1.drawText("Расшифровка показателей Пуанкаре:", MARGIN, 701, textPaint);

            TextPaint poincareDescPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            poincareDescPaint.setTextSize(8.0f);
            poincareDescPaint.setColor(Color.parseColor("#334155"));

            String singlePoincareDesc = "Каждый удар сердца сравнивается с предыдущим. Точки образуют «облако», показывающее общую стабильность ритма. Цветовой градиент отражает смену фаз от серовато-голубых точек в начале сеанса до насыщенных темно-синих во время активных фаз теста. SD1 (поперечная ось) отвечает за короткие интервалы и парасимпатический тонус, а SD2 (продольная ось) характеризует долгосрочные резервы и общую стабильность регуляции.";

            canvas1.save();
            canvas1.translate(MARGIN, 711);
            StaticLayout.Builder.obtain(singlePoincareDesc, 0, singlePoincareDesc.length(), poincareDescPaint, PAGE_WIDTH - 2 * MARGIN)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build().draw(canvas1);
            canvas1.restore();

            document.finishPage(page1);

            // ==========================================
            // СТРАНИЦА 2+: Динамика PSTI по сессиям (по 15 штук в строке)
            // ==========================================
            PdfDocument.PageInfo pageInfo2 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber++).create();
            PdfDocument.Page page2 = document.startPage(pageInfo2);
            Canvas canvas2 = page2.getCanvas();
            canvas2.drawColor(Color.WHITE);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(14f);
            textPaint.setFakeBoldText(true);
            canvas2.drawText("4. Динамика индекса PSTI по сессиям", MARGIN, 45, textPaint);

            int startYPsti = 65;
            if (pstiHistory == null || pstiHistory.isEmpty()) {
                textPaint.setTextSize(9.5f);
                textPaint.setFakeBoldText(false);
                textPaint.setColor(Color.parseColor("#64748B"));
                canvas2.drawText("Нет предшествующих сессий для отображения динамики PSTI.", MARGIN, startYPsti + 20, textPaint);
            } else {
                int totalPsti = pstiHistory.size();
                int itemsPerRow = 15;
                int rowsCount = (int) Math.ceil((double) totalPsti / itemsPerRow);

                for (int r = 0; r < rowsCount; r++) {
                    int subStart = r * itemsPerRow;
                    int subEnd = Math.min(subStart + itemsPerRow, totalPsti);
                    List<Double> subHistory = pstiHistory.subList(subStart, subEnd);

                    String title = String.format(Locale.US, "Сессии %d - %d (рост=зеленый, равно=синий, спад=красный)", subStart + 1, subEnd);
                    drawPstiGridRow(canvas2, subHistory, MARGIN, startYPsti, PAGE_WIDTH - 2 * MARGIN, 75, title);
                    startYPsti += 90;

                    if (startYPsti + 90 > PAGE_HEIGHT - MARGIN && r < rowsCount - 1) {
                        document.finishPage(page2);
                        pageInfo2 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber++).create();
                        page2 = document.startPage(pageInfo2);
                        canvas2 = page2.getCanvas();
                        canvas2.drawColor(Color.WHITE);
                        startYPsti = 45;
                    }
                }
            }

            // ==========================================
            // ПОПАРНЫЕ ГРАФИКИ ОКСИГЕНАЦИИ И ПУЛЬСА (Безопасная обработка дампов и графиков)
            // ==========================================
            List<HrvSnapshotEntity> dumpSnapshots = new ArrayList<>();
            if (snapshots != null) {
                for (HrvSnapshotEntity s : snapshots) {
                    if (s.isDumpSnapshot()) {
                        dumpSnapshots.add(s);
                    }
                }
                Collections.sort(dumpSnapshots, Comparator.comparingInt(HrvSnapshotEntity::getDumpIndex));
            }

            // Надежное определение количества блоков: если dumpSnapshots пуст, берем оценку по rawRrList или ставим минимум 1
            int dumpTotal = dumpSnapshots.size();
            if (dumpTotal == 0) {
                dumpTotal = (rawRrList != null && !rawRrList.isEmpty()) ? (int) Math.ceil((double) rawRrList.size() / 300.0) : 1;
            }

            int totalPatternBlocks = 3;
            if (workoutMode.contains("2-1")) totalPatternBlocks = 2;
            else if (workoutMode.contains("1-1")) totalPatternBlocks = 1;

            int currentYCharts = startYPsti + 20;
            if (currentYCharts + 205 > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page2);
                pageInfo2 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber++).create();
                page2 = document.startPage(pageInfo2);
                canvas2 = page2.getCanvas();
                canvas2.drawColor(Color.WHITE);
                currentYCharts = 45;
            }

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(13f);
            textPaint.setFakeBoldText(true);
            canvas2.drawText("5. Динамика SpO2 и ЧСС по дампам сессии (строго по времени дампов)", MARGIN, currentYCharts, textPaint);
            currentYCharts += 15;

            for (int d = 0; d < dumpTotal; d++) {
                if (currentYCharts + 225 > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(page2);
                    pageInfo2 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber++).create();
                    page2 = document.startPage(pageInfo2);
                    canvas2 = page2.getCanvas();
                    canvas2.drawColor(Color.WHITE);
                    currentYCharts = 45;
                }

                // Безопасное извлечение снимка без риска IndexOutOfBoundsException
                HrvSnapshotEntity snapshot = (d < dumpSnapshots.size()) ? dumpSnapshots.get(d) : null;
                int dumpIdx = (snapshot != null) ? snapshot.getDumpIndex() : (d + 1);
                boolean isPstiPhase = (snapshot != null) ? snapshot.isPstiPhase() : (d >= totalPatternBlocks);

                // Расчет временных границ дампа с фоллбэками
                long dumpEndTime = (snapshot != null) ? snapshot.getTimestamp() : endTime;
                long dumpStartTime;
                if (d > 0) {
                    if (d - 1 < dumpSnapshots.size() && dumpSnapshots.get(d - 1) != null) {
                        dumpStartTime = dumpSnapshots.get(d - 1).getTimestamp();
                    } else {
                        dumpStartTime = startTime + (d - 1) * 300000L;
                    }
                } else {
                    dumpStartTime = startTime > 0 ? startTime : (dumpEndTime - 300000L);
                }

                // Сбор точек DataPoint по диапазону времени [dumpStartTime, dumpEndTime]
                List<DataPoint> dumpPoints = new ArrayList<>();
                if (dataPoints != null) {
                    for (DataPoint dp : dataPoints) {
                        long dpTime = startTime + (dp.elapsedSec * 1000L);
                        if (dpTime >= dumpStartTime && dpTime <= dumpEndTime) {
                            dumpPoints.add(dp);
                        }
                    }
                }

                // Фоллбэк деления точек по индексу, если по точным меткам времени ничего не нашлось
                if (dumpPoints.isEmpty() && dataPoints != null && !dataPoints.isEmpty()) {
                    int pointsPerDump = Math.max(1, dataPoints.size() / dumpTotal);
                    int pStart = d * pointsPerDump;
                    int pEnd = Math.min(pStart + pointsPerDump, dataPoints.size());
                    if (pStart < dataPoints.size()) {
                        dumpPoints = dataPoints.subList(pStart, pEnd);
                    }
                }

                int blockNumber = (dumpIdx % totalPatternBlocks) + 1;
                String phaseStr = isPstiPhase ? "Тест PSTI" : "Тренировка";
                String headerTitle = String.format(Locale.US, "Дамп #%d | Блок %d/%d | %s | Фаза: %s",
                        dumpIdx, blockNumber, isPstiPhase ? 1 : totalPatternBlocks, workoutMode, phaseStr);

                // Отрисовка графиков с временной шкалой по оси X
                drawOxygenationChart(canvas2, dumpPoints, MARGIN, currentYCharts, PAGE_WIDTH - 2 * MARGIN, 45, Color.parseColor("#0284C7"), headerTitle + " — SpO2 (%)", dumpStartTime, dumpEndTime);
                currentYCharts += 50;

                drawHeartRateChart(canvas2, dumpPoints, MARGIN, currentYCharts, PAGE_WIDTH - 2 * MARGIN, 90, Color.parseColor("#10B981"), "ЧСС (HR, уд/мин)", dumpStartTime, dumpEndTime);
                currentYCharts += 105;

                // Вывод плашки метрик (только если снимок существует)
                if (snapshot != null) {
                    SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
                    double lf = snapshot.getLfPower();
                    double hf = snapshot.getHfPower();
                    double lfHfVal = (lf + hf > 0) ? (2.0 * lf / (lf + hf)) : snapshot.getLfHfRatio();
                    double iaPct = Math.max(0.0, Math.min(100.0, (lfHfVal - 1.0) * 100.0));
                    float modSec = snapshot.getModulationSec() > 0 ? snapshot.getModulationSec() : 10.0f;

                    String snapshotLine = String.format(Locale.US,
                            "[%s] RMSSD:%.1f | SDNN:%.1f | SI:%.1f | IA:%.0f%% | LF/HF:%.2f | TP:%.0f | SampEn:%.2f | DFA:%.2f | T:%.1fс",
                            timeFmt.format(new Date(snapshot.getTimestamp())), snapshot.getRmssd(), snapshot.getSdnn(),
                            snapshot.getBaevskySi(), iaPct, snapshot.getLfHfRatio(), snapshot.getTotalPower(),
                            snapshot.getSampleEntropy(), snapshot.getDfaAlpha1(), modSec);

                    fillPaint.setColor(Color.parseColor("#F1F5F9"));
                    canvas2.drawRect(MARGIN, currentYCharts, PAGE_WIDTH - MARGIN, currentYCharts + 14, fillPaint);

                    textPaint.setTextSize(7.5f);
                    textPaint.setFakeBoldText(true);
                    textPaint.setColor(Color.parseColor("#0F172A"));
                    canvas2.drawText(snapshotLine, MARGIN + 4, currentYCharts + 10, textPaint);
                    currentYCharts += 20;
                } else {
                    currentYCharts += 10;
                }
            }

            document.finishPage(page2);

            // ==========================================
            // ХРОНОЛОГИЧЕСКИЙ ЛОГ СНИМКОВ
            // ==========================================
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
            int rowsPerPage = 22;
            int totalSnapshots = (snapshots != null) ? snapshots.size() : 0;

            int snapshotIdx = 0;
            while (snapshotIdx < totalSnapshots) {
                PdfDocument.PageInfo pageInfoN = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber++).create();
                PdfDocument.Page pageN = document.startPage(pageInfoN);
                Canvas canvasN = pageN.getCanvas();
                canvasN.drawColor(Color.WHITE);

                textPaint.setColor(Color.parseColor("#0F172A"));
                textPaint.setTextSize(13f);
                textPaint.setFakeBoldText(true);
                canvasN.drawText("6. Хронологический лог снимков по фазам", MARGIN, 42, textPaint);

                int startY = 65;
                textPaint.setTextSize(8.0f);
                textPaint.setFakeBoldText(true);
                textPaint.setColor(Color.parseColor("#0F172A"));
                canvasN.drawText("Время", MARGIN, startY, textPaint);
                canvasN.drawText("RMSSD", MARGIN + 48, startY, textPaint);
                canvasN.drawText("SDNN", MARGIN + 92, startY, textPaint);
                canvasN.drawText("SI", MARGIN + 132, startY, textPaint);
                canvasN.drawText("IA", MARGIN + 172, startY, textPaint);
                canvasN.drawText("LF/HF", MARGIN + 212, startY, textPaint);
                canvasN.drawText("TP", MARGIN + 252, startY, textPaint);
                canvasN.drawText("SampEn", MARGIN + 308, startY, textPaint);
                canvasN.drawText("DFA α1", MARGIN + 368, startY, textPaint);
                canvasN.drawText("Модуляция", MARGIN + 435, startY, textPaint);

                strokePaint.setColor(Color.parseColor("#CBD5E1"));
                strokePaint.setStrokeWidth(1f);
                canvasN.drawLine(MARGIN, startY + 4, PAGE_WIDTH - MARGIN, startY + 4, strokePaint);

                textPaint.setColor(Color.parseColor("#1E293B"));
                textPaint.setFakeBoldText(false);

                int currentY = startY + 20;
                int rowsOnPage = 0;

                while (snapshotIdx < totalSnapshots && rowsOnPage < rowsPerPage) {
                    HrvSnapshotEntity s = snapshots.get(snapshotIdx);
                    boolean isPstiPhase = s.isPstiPhase();

                    boolean isPhaseChanged = (snapshotIdx == 0) || (snapshots.get(snapshotIdx - 1).isPstiPhase() != isPstiPhase);
                    boolean isNewPageStart = (rowsOnPage == 0);

                    if (isPhaseChanged || isNewPageStart) {
                        fillPaint.setColor(Color.parseColor("#E2E8F0"));
                        canvasN.drawRect(MARGIN, currentY - 10, PAGE_WIDTH - MARGIN, currentY + 4, fillPaint);

                        textPaint.setTextSize(8.0f);
                        textPaint.setFakeBoldText(true);
                        textPaint.setColor(Color.parseColor("#0F172A"));
                        String centerTitleText = (isPstiPhase ? "Тест PSTI" : "Тренировка") + " — " + workoutMode;
                        
                        float textWidth = textPaint.measureText(centerTitleText);
                        float centerX = MARGIN + (PAGE_WIDTH - 2 * MARGIN - textWidth) / 2f;
                        canvasN.drawText(centerTitleText, centerX, currentY, textPaint);
                        
                        currentY += 16;
                        textPaint.setFakeBoldText(false);
                    }

                    double lf = s.getLfPower();
                    double hf = s.getHfPower();
                    double logLfHf = (lf + hf > 0) ? (2.0 * lf / (lf + hf)) : s.getLfHfRatio();
                    if (logLfHf < 0) logLfHf = 0;
                    if (logLfHf > 2.0) logLfHf = 2.0;

                    double iaPct = Math.max(0.0, Math.min(100.0, (logLfHf - 1.0) * 100.0));

                    canvasN.drawText(timeFormat.format(new Date(s.getTimestamp())), MARGIN, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getRmssd()), MARGIN + 48, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getSdnn()), MARGIN + 92, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getBaevskySi()), MARGIN + 132, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.0f%%", iaPct), MARGIN + 172, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getLfHfRatio()), MARGIN + 212, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.0f", s.getTotalPower()), MARGIN + 252, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getSampleEntropy()), MARGIN + 308, currentY, textPaint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getDfaAlpha1()), MARGIN + 368, currentY, textPaint);

                    float modSec = s.getModulationSec() > 0 ? s.getModulationSec() : 10.0f;
                    canvasN.drawText(String.format(Locale.US, "%.1f с", modSec), MARGIN + 435, currentY, textPaint);

                    currentY += 18;
                    rowsOnPage++;
                    snapshotIdx++;
                }

                document.finishPage(pageN);
            }

            // ==========================================
            // Сохранение файла
            // ==========================================
            String fileName = "HRV_Report_" + System.currentTimeMillis() + ".pdf";
            File targetDir;
            if (customDirPath != null && !customDirPath.isEmpty()) {
                targetDir = new File(customDirPath);
            } else {
                targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            }
            if (!targetDir.exists()) targetDir.mkdirs();

            File pdfFile = new File(targetDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                document.writeTo(fos);
            }
            document.close();

            File internalDir = context.getExternalFilesDir(null);
            if (internalDir != null && !internalDir.exists()) internalDir.mkdirs();
            File internalFile = new File(internalDir, fileName);
            copyFile(pdfFile, internalFile);

            if (callback != null) {
                callback.onSuccess(pdfFile.getAbsolutePath(), fileName);
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
        rowPaint.setTextSize(10f);

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

    private static void drawPstiGridRow(Canvas canvas, List<Double> history, int left, int top, int width, int height, String title) {
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
        textPaint.setTextSize(8.5f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(title, left + 8, top + 12, textPaint);

        if (history == null || history.isEmpty()) return;

        int plotLeft = left + 12;
        int plotTop = top + 18;
        int plotWidth = width - 24;
        int plotHeight = height - 32;

        int count = history.size();
        float barW = (float) plotWidth / 15f;
        double maxVal = 100.0;

        Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valPaint.setTextSize(6.5f);
        valPaint.setColor(Color.parseColor("#334155"));

        for (int i = 0; i < count; i++) {
            double val = history.get(i);
            float barH = (float) Math.min(plotHeight, (val / maxVal) * plotHeight);

            int barColor;
            if (i == 0) {
                barColor = Color.parseColor("#0284C7");
            } else {
                double prevVal = history.get(i - 1);
                if (val > prevVal) {
                    barColor = Color.parseColor("#047857");
                } else if (val == prevVal) {
                    barColor = Color.parseColor("#0284C7");
                } else {
                    barColor = Color.parseColor("#DC2626");
                }
            }

            fillPaint.setColor(barColor);
            float x1 = plotLeft + i * barW + 2;
            float x2 = plotLeft + (i + 1) * barW - 2;
            float y1 = plotTop + plotHeight - barH;
            float y2 = plotTop + plotHeight;

            canvas.drawRect(x1, y1, x2, y2, fillPaint);

            String valStr = String.format(Locale.US, "%.0f", val);
            float textWidth = valPaint.measureText(valStr);
            float columnCenterX = x1 + (x2 - x1) / 2f;
            canvas.drawText(valStr, columnCenterX - (textWidth / 2f), top + height - 3, valPaint);
        }
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
        textPaint.setTextSize(9.0f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(title, left + 8, top + 12, textPaint);

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
        int plotTop = top + 18;
        int plotWidth = width - 24;
        int plotHeight = height - 28;

        fillPaint.setColor(lineColor);
        float barW = (float) plotWidth / binCount;
        for (int i = 0; i < binCount; i++) {
            float barH = ((float) bins[i] / maxFreq) * plotHeight;
            canvas.drawRect(plotLeft + i * barW, plotTop + plotHeight - barH, plotLeft + (i + 1) * barW - 1, plotTop + plotHeight, fillPaint);
        }

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#64748B"));
        labelPaint.setTextSize(7.5f);
        canvas.drawText(minRr + " мс", plotLeft, plotTop + plotHeight + 9, labelPaint);
        canvas.drawText(maxRr + " мс", plotLeft + plotWidth - 40, plotTop + plotHeight + 9, labelPaint);
    }

    private static void drawOxygenationChart(Canvas canvas, List<DataPoint> dataPoints, int left, int top, int width, int height, int lineColor, String title, long startTime, long endTime) {
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
        textPaint.setTextSize(8.5f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(title, left + 8, top + 11, textPaint);

        int plotLeft = left + 30;
        int plotTop = top + 15;
        int plotWidth = width - 40;
        int plotHeight = height - 18;

        textPaint.setTextSize(6.5f);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(Color.parseColor("#64748B"));

        strokePaint.setColor(Color.parseColor("#E2E8F0"));
        strokePaint.setStrokeWidth(0.5f);

        float[] levels = {100f, 95f, 90f, 85f};
        for (float lvl : levels) {
            float y = plotTop + ((100f - lvl) / 15.0f) * plotHeight;
            canvas.drawLine(plotLeft, y, plotLeft + plotWidth, y, strokePaint);
            canvas.drawText(String.format(Locale.US, "%.0f%%", lvl), left + 6, y + 2, textPaint);
        }

        if (dataPoints == null || dataPoints.isEmpty()) return;

        strokePaint.setColor(lineColor);
        strokePaint.setStrokeWidth(1.5f);

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

    private static float mapHrToY(float hr, float plotTop, float plotHeight) {
        float clamped = Math.max(40f, Math.min(200f, hr));
        if (clamped <= 50f) {
            float norm = (clamped - 40f) / 10f;
            return plotTop + plotHeight - (norm * 0.15f * plotHeight);
        } else if (clamped <= 80f) {
            float norm = (clamped - 50f) / 30f;
            return plotTop + plotHeight - ((0.15f + norm * 0.70f) * plotHeight);
        } else {
            float norm = (clamped - 80f) / 120f;
            return plotTop + plotHeight - ((0.85f + norm * 0.15f) * plotHeight);
        }
    }

    private static void drawHeartRateChart(Canvas canvas, List<DataPoint> dataPoints, int left, int top, int width, int height, int lineColor, String title, long startTime, long endTime) {
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
        textPaint.setTextSize(8.5f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(title, left + 8, top + 11, textPaint);

        int plotLeft = left + 30;
        int plotTop = top + 16;
        int plotWidth = width - 40;
        int plotHeight = height - 28;

        textPaint.setTextSize(6.5f);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(Color.parseColor("#64748B"));

        strokePaint.setColor(Color.parseColor("#E2E8F0"));
        strokePaint.setStrokeWidth(0.5f);

        float[] hrLevels = {200f, 120f, 80f, 65f, 50f, 40f};
        for (float lvl : hrLevels) {
            float y = mapHrToY(lvl, plotTop, plotHeight);
            canvas.drawLine(plotLeft, y, plotLeft + plotWidth, y, strokePaint);
            canvas.drawText(String.format(Locale.US, "%.0f", lvl), left + 6, y + 2, textPaint);
        }

        if (dataPoints != null && !dataPoints.isEmpty()) {
            strokePaint.setColor(lineColor);
            strokePaint.setStrokeWidth(1.5f);

            Path path = new Path();
            int size = dataPoints.size();
            for (int i = 0; i < size; i++) {
                DataPoint dp = dataPoints.get(i);
                float x = plotLeft + (i / (float) Math.max(1, size - 1)) * plotWidth;
                float y = mapHrToY((float) dp.hr, plotTop, plotHeight);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            canvas.drawPath(path, strokePaint);
        }

        // Отрисовка временной шкалы по оси X под графиком ЧСС
        SimpleDateFormat timeAxisFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
        String startStr = timeAxisFmt.format(new Date(startTime));
        String midStr = timeAxisFmt.format(new Date((startTime + endTime) / 2));
        String endStr = timeAxisFmt.format(new Date(endTime));

        Paint axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisTextPaint.setColor(Color.parseColor("#475569"));
        axisTextPaint.setTextSize(7.0f);

        float axisY = plotTop + plotHeight + 10;
        canvas.drawText(startStr, plotLeft, axisY, axisTextPaint);

        float midWidth = axisTextPaint.measureText(midStr);
        canvas.drawText(midStr, plotLeft + (plotWidth / 2f) - (midWidth / 2f), axisY, axisTextPaint);

        float endWidth = axisTextPaint.measureText(endStr);
        canvas.drawText(endStr, plotLeft + plotWidth - endWidth, axisY, axisTextPaint);
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

        int plotLeft = left + 35;
        int plotTop = top + 15;
        int plotWidth = width - 50;
        int plotHeight = height - 40;

        if (rawRrList == null || rawRrList.size() < 2) {
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(10f);
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
        strokePaint.setStrokeWidth(1.2f);
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
                    int red = (int) (148 + progress * (2 - 148));
                    int green = (int) (163 + progress * (132 - 163));
                    int blue = (int) (184 + progress * (199 - 184));

                    fillPaint.setColor(Color.rgb(Math.max(0, red), Math.min(255, green), Math.min(255, blue)));
                    canvas.drawCircle(x, y, 1.8f, fillPaint);
                }
            }
        }

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#475569"));
        labelPaint.setTextSize(8f);
        canvas.drawText(minRr + " мс", plotLeft, plotTop + plotHeight + 12, labelPaint);
        canvas.drawText(maxRr + " мс", plotLeft + plotWidth - 35, plotTop + plotHeight + 12, labelPaint);
        canvas.drawText("RR(n) мс", plotLeft + plotWidth / 2 - 15, plotTop + plotHeight + 22, labelPaint);
    }
}
