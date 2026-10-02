package com.techhamara.bolt.generator

import com.techhamara.bolt.parser.AnnotationParser
import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Native JVM/Kotlin generator for App Inventor & Kodular block PNG images.
 * Produces pixel-perfect visual documentation blocks for Methods, Events, Getters, and Setters
 * with authentic Blockly notch and puzzle-tab geometry, helper red dropdown blocks,
 * and high-contrast, crisp typography.
 */
object BlockPngGenerator {

    init {
        System.setProperty("java.awt.headless", "true")
    }

    enum class BlockTheme(
        val methodColor: Color,
        val methodFieldColor: Color,
        val eventColor: Color,
        val eventFieldColor: Color,
        val eventParamColor: Color,
        val propertyColor: Color,
        val propertyFieldColor: Color,
        val helperColor: Color = Color(0xBF, 0x43, 0x43),
        val helperFieldColor: Color = Color(0xE5, 0xB4, 0xB4),
        val textColor: Color = Color.WHITE,
        val fieldTextColor: Color = Color.BLACK,
        val arrowColor: Color = Color(0x5C, 0x50, 0x5C)
    ) {
        APP_INVENTOR(
            methodColor = Color(0x7C, 0x53, 0x85),
            methodFieldColor = Color(0xCB, 0xBA, 0xCE),
            eventColor = Color(0xB1, 0x8E, 0x35),
            eventFieldColor = Color(0xE0, 0xD2, 0xAE),
            eventParamColor = Color(0xDE, 0x8F, 0x6C),
            propertyColor = Color(0x26, 0x66, 0x43),
            propertyFieldColor = Color(0xA8, 0xC2, 0xB4),
            helperColor = Color(0xBF, 0x43, 0x43),
            helperFieldColor = Color(0xE5, 0xB4, 0xB4)
        ),
        KODULAR(
            methodColor = Color(0x67, 0x41, 0xB6),
            methodFieldColor = Color(0xC4, 0xB4, 0xE4),
            eventColor = Color(0xFF, 0xA7, 0x26),
            eventFieldColor = Color(0xFF, 0xE0, 0xB2),
            eventParamColor = Color(0xDE, 0x8F, 0x6C),
            propertyColor = Color(0x38, 0x8E, 0x3C),
            propertyFieldColor = Color(0xAF, 0xD2, 0xB1),
            helperColor = Color(0xBF, 0x43, 0x43),
            helperFieldColor = Color(0xE5, 0xB4, 0xB4)
        ),
        NIOTRON(
            methodColor = Color(0x87, 0x74, 0xB0),
            methodFieldColor = Color(0xD2, 0xCB, 0xE3),
            eventColor = Color(0xFF, 0xB3, 0x48),
            eventFieldColor = Color(0xFF, 0xE5, 0xC4),
            eventParamColor = Color(0xDE, 0x8F, 0x6C),
            propertyColor = Color(0x26, 0x66, 0x42),
            propertyFieldColor = Color(0xA8, 0xC2, 0xB4),
            helperColor = Color(0xBF, 0x44, 0x43),
            helperFieldColor = Color(0xE5, 0xB4, 0xB4)
        ),
        ANDROID_BUILDER(
            methodColor = Color(0x8B, 0x54, 0xCC),
            methodFieldColor = Color(0xCB, 0xBA, 0xCE),
            eventColor = Color(0xFF, 0xA0, 0x00),
            eventFieldColor = Color(0xE0, 0xD2, 0xAE),
            eventParamColor = Color(0xDE, 0x8F, 0x6C),
            propertyColor = Color(0x3B, 0x7E, 0x58),
            propertyFieldColor = Color(0xAE, 0xCD, 0xBC)
        )
    }

    data class Platform(val id: String, val theme: BlockTheme)

    val SUPPORTED_PLATFORMS = listOf(
        Platform("appinventor", BlockTheme.APP_INVENTOR),
        Platform("kodular", BlockTheme.KODULAR),
        Platform("niotron", BlockTheme.NIOTRON)
    )

    private val FONT_BOLD: Font by lazy {
        try {
            resolveFont(Font.BOLD, 13f)
        } catch (_: Throwable) {
            Font(Font.SANS_SERIF, Font.BOLD, 13)
        }
    }

    private val FONT_REGULAR: Font by lazy {
        try {
            resolveFont(Font.PLAIN, 13f)
        } catch (_: Throwable) {
            Font(Font.SANS_SERIF, Font.PLAIN, 13)
        }
    }

    private fun resolveFont(style: Int, size: Float): Font {
        // 1. If on Android / Termux, try direct file fonts
        val candidateFiles = if (style == Font.BOLD) {
            listOf("/system/fonts/Roboto-Bold.ttf", "/system/fonts/Roboto-Regular.ttf", "/system/fonts/DroidSans-Bold.ttf")
        } else {
            listOf("/system/fonts/Roboto-Regular.ttf", "/system/fonts/DroidSans.ttf")
        }
        for (p in candidateFiles) {
            val f = File(p)
            if (f.exists() && f.canRead()) {
                try {
                    return Font.createFont(Font.TRUETYPE_FONT, f).deriveFont(style, size)
                } catch (_: Throwable) {}
            }
        }

        // 2. Safe check of available system font names without calling f.family (which crashes on headless Linux/Termux)
        try {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val available = ge.availableFontFamilyNames.toSet()
            val preferred = listOf("Segoe UI", "Helvetica Neue", "Arial", "DejaVu Sans", "Roboto", "SansSerif")
            for (name in preferred) {
                if (available.any { it.equals(name, ignoreCase = true) }) {
                    return Font(name, style, size.toInt())
                }
            }
        } catch (_: Throwable) {}

        return Font(Font.SANS_SERIF, style, size.toInt())
    }

    /**
     * Checks whether AWT Font metrics and rendering subsystem is actually operational in this environment.
     * Prevents unhandled InternalError / Fontconfig crashes on minimal Linux / Termux installations.
     */
    fun isAwtFontAvailable(): Boolean {
        return try {
            val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.font = FONT_BOLD
            val fm = g.fontMetrics
            val w = fm.stringWidth("PinView")
            g.dispose()
            w >= 0
        } catch (_: Throwable) {
            false
        }
    }

    private const val NOTCH_WIDTH = 15f
    private const val NOTCH_HEIGHT = 4f
    private const val TAB_WIDTH = 8f
    private const val TAB_HEIGHT = 15f
    private const val CORNER_RADIUS = 8f

    private fun configureGraphics(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    var useSoftwareFont = false

    private fun safeGetFontMetrics(): FontMetrics? {
        if (useSoftwareFont) return null
        return try {
            val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.font = FONT_BOLD
            val fm = g.fontMetrics
            g.dispose()
            fm
        } catch (_: Throwable) {
            useSoftwareFont = true
            null
        }
    }

    private fun getTextWidth(text: String, fm: FontMetrics?): Int {
        if (!useSoftwareFont && fm != null) {
            try {
                return fm.stringWidth(text)
            } catch (_: Throwable) {
                useSoftwareFont = true
            }
        }
        return SoftwareFont.stringWidth(text)
    }

    private fun drawLabel(g: Graphics2D, text: String, x: Int, y: Int, color: Color) {
        if (!useSoftwareFont) {
            try {
                g.color = color
                g.font = FONT_BOLD
                g.drawString(text, x, y)
                return
            } catch (_: Throwable) {
                useSoftwareFont = true
            }
        }
        SoftwareFont.drawString(g, text, x, y, color)
    }

    /**
     * Draws authentic Blockly statement notch (left-to-right on top edge)
     */
    private fun appendTopNotch(path: Path2D.Float, notchX: Float) {
        path.lineTo(notchX, 0f)
        path.lineTo(notchX + 6f, NOTCH_HEIGHT)
        path.lineTo(notchX + 9f, NOTCH_HEIGHT)
        path.lineTo(notchX + NOTCH_WIDTH, 0f)
    }

    /**
     * Draws authentic Blockly statement tab (right-to-left on bottom edge)
     */
    private fun appendBottomTab(path: Path2D.Float, tabX: Float, bottomY: Float) {
        path.lineTo(tabX + NOTCH_WIDTH, bottomY)
        path.lineTo(tabX + 9f, bottomY + NOTCH_HEIGHT)
        path.lineTo(tabX + 6f, bottomY + NOTCH_HEIGHT)
        path.lineTo(tabX, bottomY)
    }

    /**
     * Draws authentic Blockly male puzzle tab protruding leftwards (top-to-bottom on left edge)
     */
    private fun appendMalePuzzleTabDown(path: Path2D.Float, x0: Float, y0: Float) {
        path.curveTo(x0, y0 + 10f, x0 - TAB_WIDTH, y0 - 0.5f, x0 - TAB_WIDTH, y0 + 7.5f)
        path.curveTo(x0 - TAB_WIDTH, y0 + 12.5f, x0, y0 + 10f, x0, y0 + TAB_HEIGHT)
    }

    /**
     * Draws authentic Blockly male puzzle tab protruding leftwards (bottom-to-top on left edge)
     */
    private fun appendMaleTabUp(path: Path2D.Float, x0: Float, y0: Float) {
        path.curveTo(x0, y0 + 10f, x0 - TAB_WIDTH, y0 + 12.5f, x0 - TAB_WIDTH, y0 + 7.5f)
        path.curveTo(x0 - TAB_WIDTH, y0 - 0.5f, x0, y0 + 10f, x0, y0)
    }

    /**
     * Draws authentic Blockly female puzzle socket indenting leftwards (top-to-bottom on right edge)
     */
    private fun appendFemalePuzzleSocketDown(path: Path2D.Float, x0: Float, y0: Float) {
        path.curveTo(x0, y0 + 10f, x0 - TAB_WIDTH, y0 - 0.5f, x0 - TAB_WIDTH, y0 + 7.5f)
        path.curveTo(x0 - TAB_WIDTH, y0 + 12.5f, x0, y0 + 10f, x0, y0 + TAB_HEIGHT)
    }

    /**
     * Renders a dropdown field badge [ Text ▼ ]
     */
    private fun drawDropdownBadge(
        g: Graphics2D,
        text: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        bgColor: Color,
        textColor: Color,
        arrowColor: Color
    ) {
        g.color = bgColor
        g.fillRoundRect(x, y, width, height, 6, 6)

        drawLabel(g, text, x + 7, y + height - 5, textColor)

        // Dropdown downward arrow
        val arrowX = x + width - 10
        val arrowY = y + height / 2 + 1
        val ax = intArrayOf(arrowX - 3, arrowX + 3, arrowX)
        val ay = intArrayOf(arrowY - 2, arrowY - 2, arrowY + 2)
        g.color = arrowColor
        g.fillPolygon(ax, ay, 3)
    }

    /**
     * Renders a parameter badge [ paramName ] for Event blocks
     */
    private fun drawParamBadge(
        g: Graphics2D,
        paramName: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        bgColor: Color,
        textColor: Color
    ) {
        g.color = bgColor
        g.fillRoundRect(x, y, width, height, 6, 6)

        drawLabel(g, paramName, x + 7, y + height - 5, textColor)
    }

    /**
     * Renders a Method block (e.g. ClearPin_Method.png, GetPin_Method.png, InitializeView_Method.png)
     */
    fun renderMethod(
        method: AnnotationParser.MethodInfo,
        componentName: String,
        theme: BlockTheme = BlockTheme.APP_INVENTOR
    ): BufferedImage {
        val hasReturn = !method.returnType.isNullOrBlank() && method.returnType != "void" && method.returnType != "Unit"
        val params = method.params

        val fm = safeGetFontMetrics()

        val compBadgeText = "${componentName}1"
        val compBadgeW = getTextWidth(compBadgeText, fm) + 24
        val callW = getTextWidth("call", fm)
        val dotW = getTextWidth(".", fm)
        val methodW = getTextWidth(method.name, fm)

        val headerLeftPad = if (hasReturn) 14 else 11
        val headerW = headerLeftPad + callW + 11 + compBadgeW + 12 + dotW + 1 + methodW + 14

        var maxParamW = 0
        for (p in params) {
            val pw = getTextWidth(p.name, fm) + 24
            if (pw > maxParamW) maxParamW = pw
        }

        val baseMethodW = maxOf(headerW, maxParamW + 80).coerceAtLeast(if (hasReturn) 201 else 205)

        data class ParamHelperLayout(
            val tag: String,
            val defaultOpt: String,
            val tagW: Int,
            val optBadgeW: Int,
            val width: Int
        )

        val helperLayouts = mutableMapOf<Int, ParamHelperLayout>()
        var maxHelperW = 0

        for (i in params.indices) {
            val helper = params[i].helper
            if (helper != null) {
                val helperTag = helper.data.tag.ifEmpty { helper.data.key.substringAfterLast('.') }
                val helperDefault = helper.data.defaultOpt.ifEmpty { helper.data.options.firstOrNull()?.name ?: "" }
                val tagW = getTextWidth(helperTag, fm)
                val optW = getTextWidth(helperDefault, fm)
                val optBadgeW = optW + 24
                val helperW = 12 + tagW + 8 + optBadgeW + 12
                helperLayouts[i] = ParamHelperLayout(helperTag, helperDefault, tagW, optBadgeW, helperW)
                if (helperW > maxHelperW) {
                    maxHelperW = helperW
                }
            }
        }

        val totalW = if (maxHelperW > 0) baseMethodW + maxHelperW else baseMethodW
        val headerH = if (hasReturn) 26 else 30
        val rowH = 25
        val totalH = if (params.isEmpty()) headerH else headerH + params.size * rowH

        val img = BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        configureGraphics(g)

        val path = Path2D.Float()
        val notchX = 15f

        if (!hasReturn) {
            // Statement block
            path.moveTo(CORNER_RADIUS, 0f)
            appendTopNotch(path, notchX)
            path.lineTo(baseMethodW.toFloat(), 0f)

            if (params.isEmpty()) {
                path.lineTo(baseMethodW.toFloat(), (totalH - 4).toFloat())
                path.lineTo((baseMethodW - 4).toFloat(), (totalH - 4).toFloat())
            } else {
                var curY = headerH.toFloat()
                for (i in params.indices) {
                    val socketCenterY = curY + rowH / 2f
                    val socketTopY = socketCenterY - TAB_HEIGHT / 2f
                    path.lineTo(baseMethodW.toFloat(), socketTopY)
                    appendFemalePuzzleSocketDown(path, baseMethodW.toFloat(), socketTopY)
                    curY += rowH
                    if (i == params.size - 1) {
                        path.lineTo(baseMethodW.toFloat(), (totalH - 4).toFloat())
                        path.lineTo((baseMethodW - 4).toFloat(), (totalH - 4).toFloat())
                    } else {
                        path.lineTo(baseMethodW.toFloat(), curY)
                    }
                }
            }

            appendBottomTab(path, notchX, (totalH - 4).toFloat())
            path.lineTo(CORNER_RADIUS, (totalH - 4).toFloat())
            path.quadTo(0f, (totalH - 4).toFloat(), 0f, (totalH - 4 - CORNER_RADIUS).toFloat())
            path.lineTo(0f, CORNER_RADIUS)
            path.quadTo(0f, 0f, CORNER_RADIUS, 0f)
            path.closePath()
        } else {
            // Expression / Value block with left male puzzle tab
            val tabY = (headerH - TAB_HEIGHT) / 2f
            path.moveTo(TAB_WIDTH, 0f)
            path.lineTo((baseMethodW - 4).toFloat(), 0f)
            path.quadTo(baseMethodW.toFloat(), 0f, baseMethodW.toFloat(), 4f)

            if (params.isEmpty()) {
                path.lineTo(baseMethodW.toFloat(), (totalH - 4).toFloat())
                path.quadTo(baseMethodW.toFloat(), totalH.toFloat(), (baseMethodW - 4).toFloat(), totalH.toFloat())
            } else {
                var curY = headerH.toFloat()
                for (i in params.indices) {
                    val socketCenterY = curY + rowH / 2f
                    val socketTopY = socketCenterY - TAB_HEIGHT / 2f
                    path.lineTo(baseMethodW.toFloat(), socketTopY)
                    appendFemalePuzzleSocketDown(path, baseMethodW.toFloat(), socketTopY)
                    curY += rowH
                    if (i == params.size - 1) {
                        path.lineTo(baseMethodW.toFloat(), (totalH - 4).toFloat())
                        path.quadTo(baseMethodW.toFloat(), totalH.toFloat(), (baseMethodW - 4).toFloat(), totalH.toFloat())
                    } else {
                        path.lineTo(baseMethodW.toFloat(), curY)
                    }
                }
            }

            path.lineTo(TAB_WIDTH, totalH.toFloat())
            path.lineTo(TAB_WIDTH, tabY + TAB_HEIGHT)
            appendMaleTabUp(path, TAB_WIDTH, tabY)
            path.lineTo(TAB_WIDTH, 0f)
            path.closePath()
        }

        g.color = theme.methodColor
        g.fill(path)

        // Draw header text & badge
        var curX = headerLeftPad
        val textY = if (hasReturn) 18 else 20

        drawLabel(g, "call", curX, textY, theme.textColor)
        curX += callW + 11

        val badgeH = 20
        val badgeY = if (hasReturn) 3 else 5
        drawDropdownBadge(g, compBadgeText, curX, badgeY, compBadgeW, badgeH, theme.methodFieldColor, theme.fieldTextColor, theme.arrowColor)
        curX += compBadgeW + 12

        drawLabel(g, ".", curX, textY, theme.textColor)
        curX += dotW + 1
        drawLabel(g, method.name, curX, textY, theme.textColor)

        // Draw parameter rows
        var rowTop = headerH
        for (param in params) {
            val pw = getTextWidth(param.name, fm)
            val px = baseMethodW - pw - 14
            val py = rowTop + 17
            drawLabel(g, param.name, px, py, theme.textColor)
            rowTop += rowH
        }

        // Draw connected helper blocks for parameters with @Options
        for (i in params.indices) {
            val hl = helperLayouts[i] ?: continue
            val rTop = headerH + i * rowH
            val isLastRow = (i == params.size - 1)
            val helperX = baseMethodW.toFloat()
            val helperRight = helperX + hl.width
            val helperTopY = rTop.toFloat()
            val helperBottomY = if (isLastRow) (totalH - 4).toFloat() else (rTop + rowH).toFloat()
            val socketCenterY = rTop + rowH / 2f
            val socketTopY = socketCenterY - TAB_HEIGHT / 2f

            val helperPath = Path2D.Float()
            helperPath.moveTo(helperX, helperTopY)
            helperPath.lineTo(helperRight - 4f, helperTopY)
            helperPath.quadTo(helperRight, helperTopY, helperRight, helperTopY + 4f)
            helperPath.lineTo(helperRight, helperBottomY - 4f)
            helperPath.quadTo(helperRight, helperBottomY, helperRight - 4f, helperBottomY)
            helperPath.lineTo(helperX, helperBottomY)
            helperPath.lineTo(helperX, socketTopY + TAB_HEIGHT)
            appendMaleTabUp(helperPath, helperX, socketTopY)
            helperPath.lineTo(helperX, helperTopY)
            helperPath.closePath()

            g.color = theme.helperColor
            g.fill(helperPath)

            var hCurX = baseMethodW + 12
            val hTextY = rTop + 17
            val hBadgeH = 20
            val hBadgeY = rTop + 2

            drawLabel(g, hl.tag, hCurX, hTextY, theme.textColor)
            hCurX += hl.tagW + 8
            drawDropdownBadge(g, hl.defaultOpt, hCurX, hBadgeY, hl.optBadgeW, hBadgeH, theme.helperFieldColor, theme.fieldTextColor, theme.arrowColor)
        }

        g.dispose()
        return img
    }

    /**
     * Renders an Event block (e.g. PinCompleted_Event.png, PinCleared_Event.png)
     */
    fun renderEvent(
        event: AnnotationParser.EventInfo,
        componentName: String,
        theme: BlockTheme = BlockTheme.APP_INVENTOR
    ): BufferedImage {
        val params = event.params
        val fm = safeGetFontMetrics()

        val compBadgeText = "${componentName}1"
        val compBadgeW = getTextWidth(compBadgeText, fm) + 24
        val whenW = getTextWidth("when", fm)
        val dotW = getTextWidth(".", fm)
        val eventW = getTextWidth(event.name, fm)

        val headerW = 12 + whenW + 10 + compBadgeW + 10 + dotW + 1 + eventW + 16

        // Compute parameter badges width if parameters exist
        var paramBadgesW = 0
        val paramBadgeWidths = mutableListOf<Int>()
        for (p in params) {
            val pw = getTextWidth(p.name, fm) + 16
            paramBadgeWidths.add(pw)
            paramBadgesW += pw + 8
        }

        val totalW = maxOf(headerW, paramBadgesW + 28).coerceAtLeast(235)
        val hasParams = params.isNotEmpty()
        val headerH = 28
        val paramRowH = if (hasParams) 26 else 0
        val mouthH = 31
        val totalH = headerH + paramRowH + mouthH

        val img = BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        configureGraphics(g)

        val path = Path2D.Float()
        val mouthX = 42f
        val mouthTopY = (headerH + paramRowH).toFloat()
        val mouthBottomY = (totalH - 6).toFloat()

        // Event top outline
        path.moveTo(CORNER_RADIUS, 0f)
        path.lineTo(totalW.toFloat(), 0f)
        path.lineTo(totalW.toFloat(), mouthTopY)

        // Mouth ceiling with standard Blockly statement notch
        path.lineTo(mouthX + 30f, mouthTopY)
        path.lineTo(mouthX + 24f, mouthTopY + NOTCH_HEIGHT)
        path.lineTo(mouthX + 21f, mouthTopY + NOTCH_HEIGHT)
        path.lineTo(mouthX + 15f, mouthTopY)
        path.lineTo(mouthX + 4f, mouthTopY)
        path.quadTo(mouthX, mouthTopY, mouthX, mouthTopY + 4f)

        // Mouth inner left wall
        path.lineTo(mouthX, mouthBottomY - 4f)
        path.quadTo(mouthX, mouthBottomY, mouthX + 4f, mouthBottomY)
        path.lineTo(totalW.toFloat(), mouthBottomY)
        path.lineTo(totalW.toFloat(), totalH.toFloat())
        path.lineTo(CORNER_RADIUS, totalH.toFloat())
        path.quadTo(0f, totalH.toFloat(), 0f, (totalH - CORNER_RADIUS).toFloat())
        path.lineTo(0f, CORNER_RADIUS)
        path.quadTo(0f, 0f, CORNER_RADIUS, 0f)
        path.closePath()

        g.color = theme.eventColor
        g.fill(path)

        // Header text & component badge
        var curX = 12
        val textY = 19

        drawLabel(g, "when", curX, textY, theme.textColor)
        curX += whenW + 10

        val badgeH = 20
        val badgeY = 5
        drawDropdownBadge(g, compBadgeText, curX, badgeY, compBadgeW, badgeH, theme.eventFieldColor, theme.fieldTextColor, theme.arrowColor)
        curX += compBadgeW + 10

        drawLabel(g, ".", curX, textY, theme.textColor)
        curX += dotW + 1
        drawLabel(g, event.name, curX, textY, theme.textColor)

        // Parameter badges row
        if (hasParams) {
            var px = 22
            val py = headerH + 3
            for (i in params.indices) {
                val bw = paramBadgeWidths[i]
                drawParamBadge(g, params[i].name, px, py, bw, 20, theme.eventParamColor, theme.fieldTextColor)
                px += bw + 8
            }
        }

        // "do" label next to mouth
        drawLabel(g, "do", 12, mouthTopY.toInt() + 19, theme.textColor)

        g.dispose()
        return img
    }

    /**
     * Renders a Property Getter block (e.g. TextColor_Get_Property.png)
     */
    fun renderPropertyGetter(
        propertyName: String,
        componentName: String,
        theme: BlockTheme = BlockTheme.APP_INVENTOR
    ): BufferedImage {
        val fm = safeGetFontMetrics()

        val compBadgeText = "${componentName}1"
        val compBadgeW = getTextWidth(compBadgeText, fm) + 24
        val propBadgeText = propertyName
        val propBadgeW = getTextWidth(propBadgeText, fm) + 24
        val dotW = getTextWidth(".", fm)

        val totalH = 26
        val leftPad = 14
        val totalW = (leftPad + compBadgeW + 6 + dotW + 6 + propBadgeW + 10).coerceAtLeast(206)

        val img = BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        configureGraphics(g)

        val path = Path2D.Float()
        val tabY = (totalH - TAB_HEIGHT) / 2f

        path.moveTo(TAB_WIDTH, 0f)
        path.lineTo((totalW - 4).toFloat(), 0f)
        path.quadTo(totalW.toFloat(), 0f, totalW.toFloat(), 4f)
        path.lineTo(totalW.toFloat(), (totalH - 4).toFloat())
        path.quadTo(totalW.toFloat(), totalH.toFloat(), (totalW - 4).toFloat(), totalH.toFloat())
        path.lineTo(TAB_WIDTH, totalH.toFloat())
        path.lineTo(TAB_WIDTH, tabY + TAB_HEIGHT)
        appendMaleTabUp(path, TAB_WIDTH, tabY)
        path.lineTo(TAB_WIDTH, 0f)
        path.closePath()

        g.color = theme.propertyColor
        g.fill(path)

        var curX = leftPad
        val badgeH = 20
        val badgeY = 3
        drawDropdownBadge(g, compBadgeText, curX, badgeY, compBadgeW, badgeH, theme.propertyFieldColor, theme.fieldTextColor, theme.arrowColor)
        curX += compBadgeW + 6

        drawLabel(g, ".", curX, 18, theme.textColor)
        curX += dotW + 6

        drawDropdownBadge(g, propBadgeText, curX, badgeY, propBadgeW, badgeH, theme.propertyFieldColor, theme.fieldTextColor, theme.arrowColor)

        g.dispose()
        return img
    }

    /**
     * Renders a Property Setter block (e.g. TextColor_Set_Property.png, AnimationStyle_Set_Property.png)
     * If helperTag & helperDefault are provided, attaches the authentic Red Helper Dropdown Block to the socket!
     */
    fun renderPropertySetter(
        propertyName: String,
        componentName: String,
        theme: BlockTheme = BlockTheme.APP_INVENTOR,
        helperTag: String? = null,
        helperDefault: String? = null
    ): BufferedImage {
        val fm = safeGetFontMetrics()

        val setW = getTextWidth("set", fm)
        val toW = getTextWidth("to", fm)
        val dotW = getTextWidth(".", fm)

        val compBadgeText = "${componentName}1"
        val compBadgeW = getTextWidth(compBadgeText, fm) + 24
        val propBadgeText = propertyName
        val propBadgeW = getTextWidth(propBadgeText, fm) + 24

        val totalH = 30
        val socketTopY = (totalH - 4 - TAB_HEIGHT) / 2f
        val notchX = 15f

        val setterContentW = 12 + setW + 8 + compBadgeW + 5 + dotW + 5 + propBadgeW + 8 + toW + 16
        val setterW = maxOf(setterContentW, 275)

        val hasHelper = !helperTag.isNullOrBlank() && !helperDefault.isNullOrBlank()

        var helperW = 0
        var tagW = 0
        var optBadgeW = 0
        if (hasHelper) {
            tagW = getTextWidth(helperTag!!, fm)
            val optW = getTextWidth(helperDefault!!, fm)
            optBadgeW = optW + 24
            helperW = 12 + tagW + 8 + optBadgeW + 12
        }

        val totalW = if (hasHelper) setterW + helperW else setterW

        val img = BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        configureGraphics(g)

        // 1. Draw Setter Block
        val setterPath = Path2D.Float()
        setterPath.moveTo(CORNER_RADIUS, 0f)
        appendTopNotch(setterPath, notchX)
        setterPath.lineTo(setterW.toFloat(), 0f)

        // Right puzzle socket notch (value input)
        setterPath.lineTo(setterW.toFloat(), socketTopY)
        appendFemalePuzzleSocketDown(setterPath, setterW.toFloat(), socketTopY)
        setterPath.lineTo(setterW.toFloat(), (totalH - 4).toFloat())

        // Bottom statement tab
        appendBottomTab(setterPath, notchX, (totalH - 4).toFloat())
        setterPath.lineTo(CORNER_RADIUS, (totalH - 4).toFloat())
        setterPath.quadTo(0f, (totalH - 4).toFloat(), 0f, (totalH - 4 - CORNER_RADIUS).toFloat())
        setterPath.lineTo(0f, CORNER_RADIUS)
        setterPath.quadTo(0f, 0f, CORNER_RADIUS, 0f)
        setterPath.closePath()

        g.color = theme.propertyColor
        g.fill(setterPath)

        var curX = 12
        val textY = 20
        drawLabel(g, "set", curX, textY, theme.textColor)
        curX += setW + 8

        val badgeH = 20
        val badgeY = 5
        drawDropdownBadge(g, compBadgeText, curX, badgeY, compBadgeW, badgeH, theme.propertyFieldColor, theme.fieldTextColor, theme.arrowColor)
        curX += compBadgeW + 5

        drawLabel(g, ".", curX, textY, theme.textColor)
        curX += dotW + 5

        drawDropdownBadge(g, propBadgeText, curX, badgeY, propBadgeW, badgeH, theme.propertyFieldColor, theme.fieldTextColor, theme.arrowColor)
        curX += propBadgeW + 8

        drawLabel(g, "to", curX, textY, theme.textColor)

        // 2. Draw Connected Red Helper Block if helper exists
        if (hasHelper) {
            val helperH = (totalH - 4).toFloat()
            val helperX = setterW.toFloat()
            val helperPath = Path2D.Float()
            helperPath.moveTo(helperX, 0f)
            helperPath.lineTo((totalW - 4).toFloat(), 0f)
            helperPath.quadTo(totalW.toFloat(), 0f, totalW.toFloat(), 4f)
            helperPath.lineTo(totalW.toFloat(), helperH - 4f)
            helperPath.quadTo(totalW.toFloat(), helperH, (totalW - 4).toFloat(), helperH)
            helperPath.lineTo(helperX, helperH)
            helperPath.lineTo(helperX, socketTopY + TAB_HEIGHT)
            // Left puzzle tab seamlessly fitting into setter's socket
            appendMaleTabUp(helperPath, helperX, socketTopY)
            helperPath.lineTo(helperX, 0f)
            helperPath.closePath()

            g.color = theme.helperColor
            g.fill(helperPath)

            var hCurX = setterW + 12
            drawLabel(g, helperTag!!, hCurX, textY, theme.textColor)
            hCurX += tagW + 8

            drawDropdownBadge(g, helperDefault!!, hCurX, badgeY, optBadgeW, badgeH, theme.helperFieldColor, theme.fieldTextColor, theme.arrowColor)
        }

        g.dispose()
        return img
    }

    /**
     * Generates all block PNG files for all components.
     * If theme is null (default when running `bolt build -b`), generates for all 3 supported platforms:
     * - outBlocksDir/appinventor/<ComponentName>/
     * - outBlocksDir/kodular/<ComponentName>/
     * - outBlocksDir/niotron/<ComponentName>/
     * If a specific theme is provided, generates directly in outBlocksDir/<ComponentName>/.
     */
    fun generateAll(
        componentList: List<AnnotationParser.ComponentInfo>,
        outBlocksDir: File,
        theme: BlockTheme? = null,
        logger: (String) -> Unit = {}
    ): Int {
        // Ensure Android / Termux system fontconfig is configured if possible
        com.techhamara.bolt.compiler.AndroidFontConfigHelper.ensureConfigured()

        if (!isAwtFontAvailable()) {
            useSoftwareFont = true
            logger("- Using built-in software font engine (zero-pkg mode).")
        } else {
            useSoftwareFont = false
        }

        val platformsToGenerate = if (theme != null) {
            listOf(Platform("", theme))
        } else {
            SUPPORTED_PLATFORMS
        }

        var totalGenerated = 0

        for (platform in platformsToGenerate) {
            val platformBaseDir = if (platform.id.isEmpty()) outBlocksDir else File(outBlocksDir, platform.id)

            for (comp in componentList) {
                val compDir = File(platformBaseDir, comp.name)
                if (!compDir.exists()) {
                    compDir.mkdirs()
                }

                // Index helpers by property name
                val helperMap = mutableMapOf<String, AnnotationParser.PropertyHelper>()
                for (setter in comp.setters) {
                    if (setter.helper != null) {
                        helperMap[setter.name] = setter.helper
                    }
                }
                for (bp in comp.blockProperties) {
                    if (bp.helper != null && !helperMap.containsKey(bp.name)) {
                        helperMap[bp.name] = bp.helper
                    }
                }

                // 1. Methods
                for (method in comp.methods) {
                    val img = renderMethod(method, comp.name, platform.theme)
                    val outFile = File(compDir, "${method.name}_Method.png")
                    ImageIO.write(img, "PNG", outFile)
                    totalGenerated++
                }

                // 2. Events
                for (event in comp.events) {
                    val img = renderEvent(event, comp.name, platform.theme)
                    val outFile = File(compDir, "${event.name}_Event.png")
                    ImageIO.write(img, "PNG", outFile)
                    totalGenerated++
                }

                // 3. Getters
                val getterNames = mutableSetOf<String>()
                if (comp.getters.isNotEmpty()) {
                    for (getter in comp.getters) {
                        getterNames.add(getter.name)
                    }
                } else {
                    for (bp in comp.blockProperties) {
                        if (bp.rw == "read-only" || bp.rw == "read-write") {
                            getterNames.add(bp.name)
                        }
                    }
                }

                for (getterName in getterNames) {
                    val img = renderPropertyGetter(getterName, comp.name, platform.theme)
                    val outFile = File(compDir, "${getterName}_Get_Property.png")
                    ImageIO.write(img, "PNG", outFile)
                    totalGenerated++
                }

                // 4. Setters
                val setterNames = mutableSetOf<String>()
                if (comp.setters.isNotEmpty()) {
                    for (setter in comp.setters) {
                        setterNames.add(setter.name)
                    }
                } else {
                    for (bp in comp.blockProperties) {
                        if (bp.rw == "write-only" || bp.rw == "read-write") {
                            setterNames.add(bp.name)
                        }
                    }
                }

                for (setterName in setterNames) {
                    val helper = helperMap[setterName]
                    val helperTag = helper?.data?.tag?.ifEmpty { helper.data.key }
                    val helperDefault = helper?.data?.defaultOpt?.ifEmpty { helper.data.options.firstOrNull()?.name ?: "" }

                    val img = renderPropertySetter(setterName, comp.name, platform.theme, helperTag, helperDefault)
                    val outFile = File(compDir, "${setterName}_Set_Property.png")
                    ImageIO.write(img, "PNG", outFile)
                    totalGenerated++
                }
            }
        }

        val compNames = componentList.joinToString(", ") { it.name }
        if (theme == null) {
            val perPlatform = if (SUPPORTED_PLATFORMS.isNotEmpty()) totalGenerated / SUPPORTED_PLATFORMS.size else totalGenerated
            logger("Generated $perPlatform blocks for $compNames...")
        } else {
            logger("Generated $totalGenerated blocks for $compNames...")
        }

        return totalGenerated
    }
}
