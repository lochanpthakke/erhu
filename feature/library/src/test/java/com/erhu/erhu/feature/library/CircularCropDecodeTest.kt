package com.erhu.erhu.feature.library
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CircularCropDecodeTest {
 @get:Rule val folder=TemporaryFolder()
 @Test fun chosenLocalImageDecodesAndLargeSourcesAreBounded() {
  val source=Bitmap.createBitmap(4096,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.BLUE)}
  val file=folder.newFile("cover.png")
  file.outputStream().use{source.compress(Bitmap.CompressFormat.PNG,100,it)};source.recycle()
  val result=decodeForCircularCrop(RuntimeEnvironment.getApplication(),Uri.fromFile(file))
  assertNotNull(result);assertTrue(result!!.width<=2048);assertTrue(result.height>0)
  assertNotEquals(Bitmap.Config.HARDWARE,result.config)
  result.recycle()
 }
 @Test fun malformedOrMissingLocalImagesFailWithoutCrashingThePicker() {
  val file=folder.newFile("invalid.png").apply{writeText("invalid")}
  assertNull(decodeForCircularCrop(RuntimeEnvironment.getApplication(),Uri.fromFile(file)))
  assertNull(decodeForCircularCrop(RuntimeEnvironment.getApplication(),Uri.fromFile(folder.root.resolve("missing.png"))))
 }
}
