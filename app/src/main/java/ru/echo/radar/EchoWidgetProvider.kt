package ru.echo.radar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

/**
 * Домашний виджет «ЭХО · Сейчас играет».
 *
 * Не держит сеть и не запускает сервисы: репозиторий складывает последний трек в
 * SharedPreferences, виджет рисует его через RemoteViews. Тап открывает ДЕКУ.
 */
class EchoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        private const val PREFS = "echo_widget_prefs"
        private const val KEY_TRACK = "track"
        private const val KEY_ARTIST = "artist"
        private const val KEY_PERSONA = "persona"
        private const val KEY_COLOR = "color"

        /** Вызывается из EchoStateRepository при каждом изменении текущего трека. */
        fun save(context: Context, track: String, artist: String, persona: String, color: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TRACK, track)
                .putString(KEY_ARTIST, artist)
                .putString(KEY_PERSONA, persona)
                .putInt(KEY_COLOR, color)
                .apply()
            refresh(context)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, EchoWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { widgetId -> updateWidget(context, manager, widgetId) }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val track = prefs.getString(KEY_TRACK, "")?.takeIf { it.isNotBlank() } ?: "ЭХО"
            val artist = prefs.getString(KEY_ARTIST, "")?.takeIf { it.isNotBlank() }
                ?: "включите музыку"
            val persona = prefs.getString(KEY_PERSONA, "")?.takeIf { it.isNotBlank() }
                ?: "Ночной акустический атлас"
            val color = prefs.getInt(KEY_COLOR, Color.parseColor("#38BDF8"))

            val views = RemoteViews(context.packageName, R.layout.echo_widget)
            views.setTextViewText(R.id.widget_track, track)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setTextViewText(R.id.widget_persona, persona)
            views.setTextColor(R.id.widget_persona, color)

            val openApp = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
