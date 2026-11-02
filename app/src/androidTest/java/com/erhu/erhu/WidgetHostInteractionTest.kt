package com.erhu.erhu

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.PlaybackStatus
import com.erhu.erhu.widget.PlaybackControllerEntryPoint
import com.lochanpthakke.empee3.widget.MusicWidget
import com.lochanpthakke.empee3.widget.GlassMusicWidget
import com.lochanpthakke.empee3.widget.VinylMusicWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real AppWidgetHost + Glance RemoteViews + PendingIntent dispatch, using only the isolated .bench app. */
@androidx.annotation.RequiresApi(31)
@androidx.test.filters.SdkSuppress(minSdkVersion = 31)
class WidgetHostInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val entry get() = EntryPointAccessors.fromApplication(context, PlaybackControllerEntryPoint::class.java)
    private val controller get() = entry.playbackController()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    private fun waitUntil(message: String, test: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000L
        while (System.currentTimeMillis() < end) {
            var ready = false
            main { ready = test() }
            if (ready) return
            Thread.sleep(100)
        }
        fail(message)
    }
    private fun click(root: View, description: String) = main {
        val leaf = nodes(root).firstOrNull { it.contentDescription?.toString() == description } ?: error("Missing $description")
        var target: View? = leaf
        while (target != null && !target.isClickable) target = target.parent as? View
        assertNotNull("No PendingIntent target for $description", target)
        assertTrue(target!!.performClick())
    }
    private fun capture(view: View, name: String) = main {
        // This host also owns detached instances. Lay out its assigned bounds explicitly,
        // as a launcher does, instead of depending on the test activity remaining focused.
        val width = view.layoutParams.width
        val height = view.layoutParams.height
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        for (label in listOf("Previous", "Next", "Play")) {
            val control = nodes(view).firstOrNull { it.contentDescription?.toString() == label } ?: continue
            val rect = android.graphics.Rect(0, 0, control.width, control.height)
            (view as ViewGroup).offsetDescendantRectToMyCoords(control, rect)
            assertTrue("$name: $label is outside widget bounds: $rect in ${width}x$height", rect.left >= 0 && rect.top >= 0 && rect.right <= width && rect.bottom <= height && rect.width() > 0 && rect.height() > 0)
        }
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File(context.cacheDir, name + ".png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun fixtures(): List<Song> = (0..2).map { n ->
        val wav = File(context.cacheDir, "widget-fixture-$n.wav")
        val bytes = 22_050 * 2 * 30
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(bytes + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(22_050).putInt(44_100).putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes)
        wav.outputStream().use { out -> out.write(header.array()); repeat(30) { out.write(ByteArray(44_100)) } }
        val art = File(context.cacheDir, "widget-cover-$n.png")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(listOf(android.graphics.Color.RED, android.graphics.Color.BLUE, android.graphics.Color.GREEN)[n])
        art.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        Song(9_900_001L + n, "Widget fixture $n", "Test artist $n", "Test album", 30_000L,
            android.net.Uri.fromFile(wav).toString(), android.net.Uri.fromFile(art).toString())
    }

    private class CapturingView(context: Context) : AppWidgetHostView(context) {
        var liveLayout = 0
        override fun updateAppWidget(remoteViews: RemoteViews?) {
            if (remoteViews != null) liveLayout = remoteViews.layoutId
            super.updateAppWidget(remoteViews)
        }
    }

    @Test
    fun previewsLiveRenderingResizingAndEveryTransportAction() {
        check(context.packageName.endsWith(".bench")) { "Widget tests must never change the production queue" }
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var container: FrameLayout
        lateinit var host: AppWidgetHost
        var hostReady = false
        val manager = AppWidgetManager.getInstance(context)
        val views = mutableListOf<CapturingView>()
        val widgets = listOf(MusicWidget(), GlassMusicWidget(), VinylMusicWidget())
        try {
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                container = FrameLayout(activity).apply { setBackgroundColor(android.graphics.Color.GRAY) }
                activity.setContentView(container)
                host = object : AppWidgetHost(context, 0x45524855) {
                    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView = CapturingView(context)
                }
                hostReady = true
                host.startListening()
            }
            val songs = fixtures()
            runBlocking { withContext(Dispatchers.Main) { controller.awaitRestored(); controller.playSongsFromQueue(songs, 1); controller.pause() } }
            for ((index, widget) in widgets.withIndex()) {
                val provider = manager.installedProviders.single { it.provider == ComponentName(context, "com.lochanpthakke.empee3.widget.${widget.javaClass.simpleName}Receiver") }
                assertTrue(provider.initialLayout != 0)
                assertTrue(provider.previewImage != 0)
                assertTrue(provider.previewLayout != 0)
                lateinit var preview: View
                main {
                    preview = RemoteViews(context.packageName, provider.initialLayout).apply(context, FrameLayout(context))
                    preview.layoutParams = FrameLayout.LayoutParams(dp(220), dp(if (index == 2) 220 else 140))
                }
                capture(preview, "preview-${widget.javaClass.simpleName}")
                var id = 0
                lateinit var view: CapturingView
                instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
                main {
                    id = host.allocateAppWidgetId()
                    assertTrue(manager.bindAppWidgetIdIfAllowed(id, provider.provider, options(220, if (index == 2) 220 else 140)))
                    view = host.createView(context, id, provider) as CapturingView
                    views += view
                    container.removeAllViews(); container.addView(view)
                    view.layoutParams = FrameLayout.LayoutParams(dp(220), dp(if (index == 2) 220 else 140))
                }
                instrumentation.uiAutomation.dropShellPermissionIdentity()
                runBlocking { widget.update(context, GlanceAppWidgetManager(context).getGlanceIdBy(id)) }
                waitUntil("${widget.javaClass.simpleName} did not render live RemoteViews") {
                    view.liveLayout != 0 && view.liveLayout != provider.initialLayout && nodes(view).filterIsInstance<TextView>().any { it.text.toString() == "Widget fixture 1" }
                }
                capture(view, "widget-${widget.javaClass.simpleName}-small")
                click(view, "Next")
                waitUntil("Next did not reach playback") { controller.playbackState.value.currentIndex == 2 && nodes(view).filterIsInstance<TextView>().any { it.text.toString() == "Widget fixture 2" } }
                capture(view, "widget-${widget.javaClass.simpleName}-next")
                main { controller.seekTo(0) }
                click(view, "Previous")
                waitUntil("Previous did not reach playback") { controller.playbackState.value.currentIndex == 1 && nodes(view).filterIsInstance<TextView>().any { it.text.toString() == "Widget fixture 1" } }
                click(view, "Play")
                waitUntil("Play did not reach playback") { controller.playbackState.value.status == PlaybackStatus.PLAYING }
                waitUntil("Pause state not reflected in widget") { nodes(view).any { it.contentDescription?.toString() == "Pause" } }
                click(view, "Pause")
                waitUntil("Pause did not reach playback") { controller.playbackState.value.status == PlaybackStatus.PAUSED }
                main {
                    manager.updateAppWidgetOptions(id, options(300, if (index == 2) 300 else 200))
                    view.layoutParams = FrameLayout.LayoutParams(dp(300), dp(if (index == 2) 300 else 200))
                }
                Thread.sleep(1200)
                waitUntil("Resize lost title") { nodes(view).filterIsInstance<TextView>().any { it.text.toString() == "Widget fixture 1" } }
                capture(view, "widget-${widget.javaClass.simpleName}-large")
            }
            // A playback change made outside every widget reaches all active instances.
            main { controller.skipNext() }
            waitUntil("Playback changes did not update all instances") {
                views.all { v -> nodes(v).filterIsInstance<TextView>().any { it.text.toString() == "Widget fixture 2" } }
            }
            // The title/background activity action is direct (no receiver/service trampoline).
            main {
                var target: View? = nodes(views.last()).filterIsInstance<TextView>().first { it.text.toString() == "Widget fixture 2" }
                while (target != null && !target.isClickable) target = target.parent as? View
                assertTrue(target?.performClick() == true)
            }
            Thread.sleep(700)
            val focus = instrumentation.uiAutomation.executeShellCommand("dumpsys window").use { pfd -> java.io.FileInputStream(pfd.fileDescriptor).bufferedReader().readText() }
            assertTrue("Widget did not open erhu", focus.lines().any { it.contains("mCurrentFocus=") && it.contains("com.erhu.erhu.MainActivity") })
        } finally {
            main { controller.pause(); if (hostReady) { host.stopListening(); host.deleteHost() } }
            scenario.close()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
    @Test
    fun pinForOneUiHome() {
        check(context.packageName.endsWith(".bench"))
        val receiver = InstrumentationRegistry.getArguments().getString("provider") ?: "GlassMusicWidgetReceiver"
        check(receiver in listOf("MusicWidgetReceiver", "GlassMusicWidgetReceiver", "VinylMusicWidgetReceiver"))
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                val type = when (receiver) {
                    "MusicWidgetReceiver" -> com.lochanpthakke.empee3.widget.MusicWidgetReceiver::class
                    "VinylMusicWidgetReceiver" -> com.lochanpthakke.empee3.widget.VinylMusicWidgetReceiver::class
                    else -> com.lochanpthakke.empee3.widget.GlassMusicWidgetReceiver::class
                }
                val result = runBlocking { GlanceAppWidgetManager(context).setWidgetPreviews(type) }
                android.util.Log.i("ErhuWidgetTest", "Generated preview $receiver result=$result")
                if (result != GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                    AppWidgetManager.getInstance(context).removeWidgetPreview(ComponentName(context, type.java), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                }
            }
            Thread.sleep(700)
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                assertTrue(AppWidgetManager.getInstance(context).requestPinAppWidget(
                    ComponentName(context, "com.lochanpthakke.empee3.widget.$receiver"), null, null,
                ))
            }
            Thread.sleep(1000)
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun options(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(width.toFloat(), height.toFloat())))
    }
}
