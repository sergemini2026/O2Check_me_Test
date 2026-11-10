package com.local.o2test;
​import android.app.AlertDialog;
import android.content.Context;
import android.text.SpannableString;
import android.text.Layout;
import android.text.style.AlignmentSpan;
import android.widget.Button;
​public class MayerTestManager {
​private final Context context;
private final Button btnTest;
private final MayerTestListener listener;
​private boolean isAvailable = false;
private boolean isTestModeActive = false;
​public interface MayerTestListener {
void onTestModeChanged(boolean isActive);
void onLog(String message);
}
​public MayerTestManager(Context context, Button btnTest, MayerTestListener listener) {
this.context = context;
this.btnTest = btnTest;
this.listener = listener;
​// Изначально кнопка заблокирована до завершения калибровки
applyInitialState();
}
​private void applyInitialState() {
if (btnTest != null) {
btnTest.setEnabled(false);
btnTest.setAlpha(0.5f);
}
}
​/
* Установка доступности кнопки после завершения калибровки (первые 100 RR-интервалов)
*/
public void setAvailable(boolean available) {
this.isAvailable = available;
if (btnTest != null) {
btnTest.setEnabled(available);
btnTest.setAlpha(available ? 1.0f : 0.5f);
}
}
​public boolean isAvailable() {
return isAvailable;
}
​public boolean isMayerTestModeActive() {
return isTestModeActive;
}
​/
* Обработка нажатия на кнопку «Тест»
*/
public void handleTestButtonClick() {
if (!isAvailable) return;
​// Переходим в режим тестирования (открытый контур)
isTestModeActive = true;
​// Применяем визуальное оформление активного состояния (розовый градиент / индикация)
if (btnTest != null) {
btnTest.setBackgroundResource(R.drawable.bg_test_button_pink_gradient);
}
​// Вывод сообщения в системный лог
String logMsg = "[МАЙЕР ТЕСТ] Режим тестирования активирован: автоподстройка фазы отключена до конца текущего дампа.";
if (listener != null) {
listener.onLog(logMsg);
listener.onTestModeChanged(true);
}
​// Показ модального предупреждения пользователю
showTestAlert();
}
​/
* Автоматический сброс режима (по окончании макро-дампа или остановке сессии)
*/
public void resetTestMode() {
if (!isTestModeActive) return;
​isTestModeActive = false;
​// Возвращаем кнопку в стандартный вид с шевроном
if (btnTest != null) {
btnTest.setBackgroundResource(R.drawable.bg_test_button_chevron);
}
​// Логируем возврат в штатный режим
String logMsg = "[МАЙЕР ТЕСТ] Режим тестирования завершен. Возврат к штатной автоподстройке фазы.";
if (listener != null) {
listener.onLog(logMsg);
listener.onTestModeChanged(false);
}
}
​private void showTestAlert() {
try {
AlertDialog.Builder builder = new AlertDialog.Builder(context);
builder.setTitle(getCenteredTitle("⚠️ [ВНИМАНИЕ!]"));
builder.setMessage("\nРежим тестирования, автоподстройка фазы отключена до конца текущего дампа.");
builder.setPositiveButton("ОК", null);
AlertDialog dialog = builder.create();
dialog.show();
} catch (Exception e) {
e.printStackTrace();
}
}
​private SpannableString getCenteredTitle(String title) {
SpannableString s = new SpannableString(title);
s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
return s;
}
}