package com.local.o2test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.widget.Toast;

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

    // Стандартные размеры страницы А4 в пунктах (72 DPI)
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
            PdfExportCallback callback
    ) {
        PdfDocument document = new PdfDocument();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);

        // Расчет средних значений по снимкам для сводки
        double avgRmssd = 0, avgSdnn = 0, avgSi = 0, avgLfHf = 0, avgSd1 = 0, avgSd2 = 0;
        if (!snapshots.isEmpty()) {
            for (HrvSnapshotEntity s : snapshots) {
                avgRmssd += s.getRmssd();
                avgSdnn += s.getSdnn();
                avgSi += s.getBaevskySi();
                avgLfHf += s.getLfHfRatio();
                avgSd1 += s.getSd1();
                avgSd2 += s.getSd2();
            }
            int size = snapshots.size();
            avgRmssd /= size;
            avgSdnn /= size;
            avgSi /= size;
            avgLfHf /= size;
            avgSd1 /= size;
            avgSd2 /= size;
        }

        try {
            // ==========================================
            // СТРАНИЦА 1: Шапка и Клинический интерпретатор
            // ==========================================
            PdfDocument.PageInfo pageInfo1 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
            PdfDocument.Page page1 = document.startPage(pageInfo1);
            Canvas canvas1 = page1.getCanvas();

            Paint paint = new Paint();
            paint.setAntiAlias(true);

            // Заголовок документа
            paint.setColor(Color.parseColor("#1E3A8A")); // Темно-синий
            paint.setTextSize(20);
            paint.setFakeBoldText(true);
            canvas1.drawText("ОТЧЕТ АНАЛИЗА ВАРИАБЕЛЬНОСТИ СЕРДЕЧНОГО РИТМА (ВСР)", MARGIN, 60, paint);

            // Метаданные
            paint.setColor(Color.DKGRAY);
            paint.setTextSize(11);
            paint.setFakeBoldText(false);
            canvas1.drawText("Профиль: " + profileName, MARGIN, 90, paint);
            canvas1.drawText("Режим сеанса: " + workoutMode, MARGIN, 110, paint);
            canvas1.drawText("Начало: " + sdf.format(new Date(startTime)) + " | Окончание: " + sdf.format(new Date(endTime)), MARGIN, 130, paint);

            // Линия разделителя
            paint.setColor(Color.LTGRAY);
            canvas1.drawLine(MARGIN, 145, PAGE_WIDTH - MARGIN, 145, paint);

            // Блок 1: Сводные показатели
            paint.setColor(Color.BLACK);
            paint.setTextSize(14);
            paint.setFakeBoldText(true);
            canvas1.drawText("1. Сводные вегетативные показатели", MARGIN, 175, paint);

            drawMetricRow(canvas1, 205, "RMSSD (мск):", String.format(Locale.US, "%.1f", avgRmssd), "Норма: 20-100");
            drawMetricRow(canvas1, 230, "SDNN (мск):", String.format(Locale.US, "%.1f", avgSdnn), "Общая вариабельность");
            drawMetricRow(canvas1, 255, "Индекс Баевского (SI):", String.format(Locale.US, "%.1f у.е.", avgSi), "Индекс напряжения");
            drawMetricRow(canvas1, 280, "Баланс LF/HF:", String.format(Locale.US, "%.2f", avgLfHf), "Симпато-вагальный баланс");

            // Блок 2: Клинический интерпретатор
            paint.setTextSize(14);
            paint.setFakeBoldText(true);
            paint.setColor(Color.BLACK);
            canvas1.drawText("2. Клиническая интерпретация состояния", MARGIN, 335, paint);

            // Логика определения статуса по SI и RMSSD
            String statusTitle;
            String statusDesc;
            int statusColor;

            if (avgSi < 50) {
                statusTitle = "ВАГОТОНИЯ (Парасимпатическое преобладание)";
                statusDesc = "Выраженное преобладание парасимпатического отдела. Характерно для глубокого восстановления, отдыха или спортивной брадикардии.";
                statusColor = Color.parseColor("#047857"); // Зеленый
            } else if (avgSi >= 50 && avgSi <= 150) {
                statusTitle = "НОРМА (Сбалансированная вегетативная регуляция)";
                statusDesc = "Оптимальный уровень адаптационных резервов. Организм находится в состоянии функционального баланса.";
                statusColor = Color.parseColor("#1D4ED8"); // Синий
            } else if (avgSi > 150 && avgSi <= 500) {
                statusTitle = "УМЕРЕННЫЙ СТРЕСС / СИМПАТИКОТОНИЯ";
                statusDesc = "Преобладание симпатического тонуса. Указывает на мобилизацию ресурсов, умеренную нагрузку или начальную фазу утомления.";
                statusColor = Color.parseColor("#B45309"); // Оранжевый
            } else {
                statusTitle = "ВЫРАЖЕННОЕ ПЕРЕНАПРЯЖЕНИЕ / СТРЕСС";
                statusDesc = "Резкое снижение адаптационного потенциала, перенапряжение регуляторных систем. Рекомендован отдых и контроль нагрузки.";
                statusColor = Color.parseColor("#B91C1C"); // Красный
            }

            // Отрисовка плашки статуса
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(statusColor);
            canvas1.drawRect(MARGIN, 355, PAGE_WIDTH - MARGIN, 430, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(13);
            paint.setFakeBoldText(true);
            canvas1.drawText(statusTitle, MARGIN + 15, 380, paint);

            paint.setTextSize(11);
            paint.setFakeBoldText(false);
            // Простейший перенос текста в плашке
            canvas1.drawText(statusDesc, MARGIN + 15, 405, paint);

            document.finishPage(page1);

            // ==========================================
            // СТРАНИЦА 2: Графический анализ (Пуанкаре и Гистограмма)
            // ==========================================
            PdfDocument.PageInfo pageInfo2 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create();
            PdfDocument.Page page2 = document.startPage(pageInfo2);
            Canvas canvas2 = page2.getCanvas();

            paint.setColor(Color.BLACK);
            paint.setTextSize(16);
            paint.setFakeBoldText(true);
            canvas2.drawText("3. Графический анализ вариабельности", MARGIN, 50, paint);

            // График 1: Скатертограмма Пуанкаре (слева)
            drawPoincarePlot(canvas2, snapshots, MARGIN, 80, 240, 240);

            // График 2: Гистограмма RR-интервалов (справа)
            drawHistogram(canvas2, rawRrList, 320, 80, 240, 240);

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

                paint.setColor(Color.BLACK);
                paint.setTextSize(14);
                paint.setFakeBoldText(true);
                canvasN.drawText("4. Хронологический лог снимков (Страница " + (pageIndex + 1) + " из " + totalPages + ")", MARGIN, 50, paint);

                // Заголовки таблицы
                int startY = 90;
                paint.setTextSize(10);
                paint.setFakeBoldText(true);
                canvasN.drawText("Время", MARGIN, startY, paint);
                canvasN.drawText("RMSSD", MARGIN + 80, startY, paint);
                canvasN.drawText("SDNN", MARGIN + 160, startY, paint);
                canvasN.drawText("SI (Баевский)", MARGIN + 240, startY, paint);
                canvasN.drawText("LF/HF", MARGIN + 360, startY, paint);
                canvasN.drawText("Артефакты", MARGIN + 440, startY, paint);

                paint.setColor(Color.LTGRAY);
                canvasN.drawLine(MARGIN, startY + 8, PAGE_WIDTH - MARGIN, startY + 8, paint);

                paint.setColor(Color.BLACK);
                paint.setFakeBoldText(false);

                int startRow = pageIndex * rowsPerPage;
                int endRow = Math.min(startRow + rowsPerPage, totalSnapshots);
                int currentY = startY + 30;

                for (int i = startRow; i < endRow; i++) {
                    HrvSnapshotEntity s = snapshots.get(i);
                    canvasN.drawText(timeFormat.format(new Date(s.getTimestamp())), MARGIN, currentY, paint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getRmssd()), MARGIN + 80, currentY, paint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getSdnn()), MARGIN + 160, currentY, paint);
                    canvasN.drawText(String.format(Locale.US, "%.1f", s.getBaevskySi()), MARGIN + 240, currentY, paint);
                    canvasN.drawText(String.format(Locale.US, "%.2f", s.getLfHfRatio()), MARGIN + 360, currentY, paint);
                    canvasN.drawText(s.getArtifactsDetected() + " (" + String.format(Locale.US, "%.1f%%", s.getArtifactPct()) + ")", MARGIN + 440, currentY, paint);

                    currentY += 25;
                }

                document.finishPage(pageN);
            }

            // Сохранение файла в директорию документов
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (!downloadsDir.exists()) downloadsDir.mkdirs();

            String fileName = "HRV_Report_" + System.currentTimeMillis() + ".pdf";
            File file = new File(downloadsDir, fileName);

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
        paint.setTextSize(12);

        paint.setColor(Color.DKGRAY);
        canvas.drawText(label, MARGIN, y, paint);

        paint.setColor(Color.BLACK);
        paint.setFakeBoldText(true);
        canvas.drawText(val, MARGIN + 180, y, paint);

        paint.setFakeBoldText(false);
        paint.setColor(Color.GRAY);
        canvas.drawText(desc, MARGIN + 280, y, paint);
    }

    private static void drawPoincarePlot(Canvas canvas, List<HrvSnapshotEntity> snapshots, int left, int top, int width, int height) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Рамка и фон графика
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawRect(left, top, left + width, top + height, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.LTGRAY);
        canvas.drawRect(left, top, left + width, top + height, paint);

        // Заголовок графика
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        paint.setTextSize(12);
        paint.setFakeBoldText(true);
        canvas.drawText("Скатертограмма Пуанкаре", left + 10, top + 20, paint);

        // Отрисовка точек на основе SD1 и SD2 из снимков
        paint.setColor(Color.parseColor("#2563EB")); // Синие точки
        paint.setStyle(Paint.Style.FILL);

        int centerX = left + width / 2;
        int centerY = top + height / 2;

        for (HrvSnapshotEntity s : snapshots) {
            // Масштабируем SD1 и SD2 для визуализации в пределах области графика
            float px = (float) (centerX + (s.getSd1() - 50) * 1.5);
            float py = (float) (centerY - (s.getSd2() - 50) * 1.5);
            if (px >= left + 10 && px <= left + width - 10 && py >= top + 30 && py <= top + height - 10) {
                canvas.drawCircle(px, py, 3f, paint);
            }
        }
    }

    private static void drawHistogram(Canvas canvas, List<Integer> rawRrList, int left, int top, int width, int height) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Фон и рамка
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawRect(left, top, left + width, top + height, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.LTGRAY);
        canvas.drawRect(left, top, left + width, top + height, paint);

        // Заголовок
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        paint.setTextSize(12);
        paint.setFakeBoldText(true);
        canvas.drawText("Гистограмма RR-интервалов", left + 10, top + 20, paint);

        if (rawRrList == null || rawRrList.isEmpty()) return;

        // Простейшая отрисовка столбцов гистограммы
        paint.setColor(Color.parseColor("#0D9488")); // Бирюзовый
        paint.setStyle(Paint.Style.FILL);

        int maxVal = 1;
        for (int rr : rawRrList) {
            if (rr > maxVal) maxVal = rr;
        }

        // Отрисовка условных столбцов распределения
        int barWidth = Math.max(2, width / Math.max(1, rawRrList.size()));
        for (int i = 0; i < Math.min(rawRrList.size(), width - 20); i++) {
            int rr = rawRrList.get(i);
            int barHeight = (int) (((double) rr / maxVal) * (height - 50));
            canvas.drawRect(left + 10 + i * barWidth, top + height - 20 - barHeight, left + 10 + (i + 1) * barWidth - 1, top + height - 20, paint);
        }
    }
}
