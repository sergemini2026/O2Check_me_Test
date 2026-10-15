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
            // СТРАНИЦА 2: Скатертограмма Пуанкаре + Расшифровка
            // ==========================================
            PdfDocument.PageInfo pageInfo2 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create();
            PdfDocument.Page page2 = document.startPage(pageInfo2);
            Canvas canvas2 = page2.getCanvas();
            canvas2.drawColor(Color.WHITE);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(14);
            textPaint.setFakeBoldText(true);
            canvas2.drawText("3. Скатертограмма Пуанкаре и анализ геометрии", MARGIN, 45, textPaint);

            drawPoincarePlot(canvas2, snapshots, MARGIN, 65, PAGE_WIDTH - 2 * MARGIN, 340);

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
                "• Цветовая кодировка точек: Серые точки — стабильные зоны, синие и голубые — фазы активной вегетативной регуляции.",
                "• Облако точек (форма кометы): Вытянутая форма указывает на нормальный синусовый ритм и сбалансированность регуляции.",
                "• DFA alpha1: Оценивает фрактальные корреляции. Значения около 1.0 указывают на сбалансированную работу регуляторных систем."
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
                currentDescY += layout.getHeight() + 3;
            }

            document.finishPage(page2);

            // ==========================================
            // СТРАНИЦЫ 3+: Хронологический лог снимков
            // ==========================================
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
            int rowsPerPage = 2
