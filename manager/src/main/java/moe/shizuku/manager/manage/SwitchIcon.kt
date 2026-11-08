package moe.shizuku.manager.manage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import java.util.concurrent.ConcurrentHashMap

/**
 * The switch's own icon, in a shape a tile and a widget can use.
 *
 * The Labs screen draws these as Compose images ([LabSwitch.image]); a tile needs a bitmap and a
 * widget needs one too, and neither can be had from a Compose image without drawing it. Drawing it
 * from the very vector the screen uses is what keeps the two identical: a copy of an icon is a copy
 * that drifts, and these are the same marks on the same screen.
 */
object SwitchIcon {

    private val cache = ConcurrentHashMap<Int, Bitmap>()

    /** [image] drawn [sizePx] square in white, or a cached copy of it. */
    fun bitmap(image: ImageVector, sizePx: Int): Bitmap {
        val key = image.name.hashCode() * 31 + sizePx
        return cache.getOrPut(key) { render(image, sizePx) }
    }

    private fun render(image: ImageVector, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // The vector's own coordinate space onto the bitmap, the whole of it: the icons are built
        // for a viewport that is not always 24 by 24.
        canvas.scale(sizePx / image.viewportWidth, sizePx / image.viewportHeight)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }

        draw(canvas, image.root, paint, Matrix())
        return bitmap
    }

    private fun draw(canvas: Canvas, group: VectorGroup, paint: Paint, parent: Matrix) {
        val matrix = Matrix(parent)
        if (group.translationX != 0f || group.translationY != 0f) {
            matrix.postTranslate(group.translationX, group.translationY)
        }
        if (group.rotation != 0f) {
            matrix.postRotate(group.rotation, group.pivotX, group.pivotY)
        }
        if (group.scaleX != 1f || group.scaleY != 1f) {
            matrix.postScale(group.scaleX, group.scaleY, group.pivotX, group.pivotY)
        }

        for (child: VectorNode in group) {
            when (child) {
                is VectorGroup -> draw(canvas, child, paint, matrix)
                is VectorPath -> draw(canvas, child, paint, matrix)
            }
        }
    }

    private fun draw(canvas: Canvas, path: VectorPath, paint: Paint, matrix: Matrix) {
        val outline = Path()
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f

        // The last control point, kept for the reflective segments: S and T name only the end, and
        // the control they are missing is the mirror of the one before.
        var controlX = 0f
        var controlY = 0f
        var hadCurve = false

        for (node in path.pathData) {
            when (node) {
                is PathNode.MoveTo -> {
                    x = node.x
                    y = node.y
                    outline.moveTo(x, y)
                    startX = x
                    startY = y
                    hadCurve = false
                }

                is PathNode.RelativeMoveTo -> {
                    x += node.dx
                    y += node.dy
                    outline.moveTo(x, y)
                    startX = x
                    startY = y
                    hadCurve = false
                }

                is PathNode.LineTo -> {
                    x = node.x
                    y = node.y
                    outline.lineTo(x, y)
                    hadCurve = false
                }

                is PathNode.RelativeLineTo -> {
                    x += node.dx
                    y += node.dy
                    outline.lineTo(x, y)
                    hadCurve = false
                }

                is PathNode.HorizontalTo -> {
                    x = node.x
                    outline.lineTo(x, y)
                    hadCurve = false
                }

                is PathNode.RelativeHorizontalTo -> {
                    x += node.dx
                    outline.lineTo(x, y)
                    hadCurve = false
                }

                is PathNode.VerticalTo -> {
                    y = node.y
                    outline.lineTo(x, y)
                    hadCurve = false
                }

                is PathNode.RelativeVerticalTo -> {
                    y += node.dy
                    outline.lineTo(x, y)
                    hadCurve = false
                }

                is PathNode.CurveTo -> {
                    outline.cubicTo(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
                    x = node.x3
                    y = node.y3
                    controlX = node.x2
                    controlY = node.y2
                    hadCurve = true
                }

                is PathNode.RelativeCurveTo -> {
                    val c1x = x + node.dx1
                    val c1y = y + node.dy1
                    val c2x = x + node.dx2
                    val c2y = y + node.dy2
                    outline.cubicTo(c1x, c1y, c2x, c2y, x + node.dx3, y + node.dy3)
                    x += node.dx3
                    y += node.dy3
                    controlX = c2x
                    controlY = c2y
                    hadCurve = true
                }

                is PathNode.ReflectiveCurveTo -> {
                    val c1x = if (hadCurve) 2 * x - controlX else x
                    val c1y = if (hadCurve) 2 * y - controlY else y
                    outline.cubicTo(c1x, c1y, node.x1, node.y1, node.x2, node.y2)
                    controlX = node.x1
                    controlY = node.y1
                    x = node.x2
                    y = node.y2
                    hadCurve = true
                }

                is PathNode.RelativeReflectiveCurveTo -> {
                    val c1x = if (hadCurve) 2 * x - controlX else x
                    val c1y = if (hadCurve) 2 * y - controlY else y
                    val c2x = x + node.dx1
                    val c2y = y + node.dy1
                    outline.cubicTo(c1x, c1y, c2x, c2y, x + node.dx2, y + node.dy2)
                    controlX = c2x
                    controlY = c2y
                    x += node.dx2
                    y += node.dy2
                    hadCurve = true
                }

                is PathNode.QuadTo -> {
                    outline.quadTo(node.x1, node.y1, node.x2, node.y2)
                    controlX = node.x1
                    controlY = node.y1
                    x = node.x2
                    y = node.y2
                    hadCurve = true
                }

                is PathNode.RelativeQuadTo -> {
                    val c1x = x + node.dx1
                    val c1y = y + node.dy1
                    outline.quadTo(c1x, c1y, x + node.dx2, y + node.dy2)
                    controlX = c1x
                    controlY = c1y
                    x += node.dx2
                    y += node.dy2
                    hadCurve = true
                }

                is PathNode.ReflectiveQuadTo -> {
                    val c1x = if (hadCurve) 2 * x - controlX else x
                    val c1y = if (hadCurve) 2 * y - controlY else y
                    outline.quadTo(c1x, c1y, node.x, node.y)
                    controlX = c1x
                    controlY = c1y
                    x = node.x
                    y = node.y
                    hadCurve = true
                }

                is PathNode.RelativeReflectiveQuadTo -> {
                    val c1x = if (hadCurve) 2 * x - controlX else x
                    val c1y = if (hadCurve) 2 * y - controlY else y
                    outline.quadTo(c1x, c1y, x + node.dx, y + node.dy)
                    controlX = c1x
                    controlY = c1y
                    x += node.dx
                    y += node.dy
                    hadCurve = true
                }

                // No Material icon in this app's set uses one; a straight line to the end is the
                // least wrong thing to draw if one ever does.
                is PathNode.ArcTo -> {
                    outline.lineTo(node.arcStartX, node.arcStartY)
                    x = node.arcStartX
                    y = node.arcStartY
                    hadCurve = false
                }

                is PathNode.RelativeArcTo -> {
                    outline.rLineTo(node.arcStartDx, node.arcStartDy)
                    x += node.arcStartDx
                    y += node.arcStartDy
                    hadCurve = false
                }

                is PathNode.Close -> {
                    outline.close()
                    x = startX
                    y = startY
                    hadCurve = false
                }
            }
        }

        val transformed = Path(outline)
        transformed.transform(matrix)
        canvas.drawPath(transformed, paint)
    }
}
