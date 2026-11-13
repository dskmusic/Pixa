package com.dskmusic.pixa.ui.editor

import androidx.annotation.StringRes
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.draw.BrushKind
import com.dskmusic.pixa.core.draw.SymmetryMode
import com.dskmusic.pixa.core.export.ExportFormat
import com.dskmusic.pixa.core.model.Anchor9
import com.dskmusic.pixa.core.model.EffectKind
import com.dskmusic.pixa.core.model.FrameKind
import com.dskmusic.pixa.core.model.GradientKind
import com.dskmusic.pixa.core.model.HslBand
import com.dskmusic.pixa.core.model.PixaBlend
import com.dskmusic.pixa.core.model.QrErrorLevel
import com.dskmusic.pixa.core.model.QrPayloadKind
import com.dskmusic.pixa.core.model.ShapeKind
import com.dskmusic.pixa.core.model.TextAlign
import com.dskmusic.pixa.core.model.WatermarkKind
import com.dskmusic.pixa.core.selection.Selection
import com.dskmusic.pixa.core.transform.TransformOps

/**
 * Etiquetas de los enumerados del editor.
 *
 * Estan juntas y no repartidas por los paneles porque varios enumerados aparecen en mas de
 * un sitio (los modos de fusion en capas y en estilos, el formato en exportar y en lotes),
 * y una sola tabla evita que el mismo valor salga con dos nombres distintos.
 */
object EditorLabels {

    @StringRes
    fun blend(value: PixaBlend): Int = when (value) {
        PixaBlend.NORMAL -> R.string.blend_normal
        PixaBlend.MULTIPLY -> R.string.blend_multiply
        PixaBlend.SCREEN -> R.string.blend_screen
        PixaBlend.OVERLAY -> R.string.blend_overlay
        PixaBlend.SOFT_LIGHT -> R.string.blend_soft_light
        PixaBlend.HARD_LIGHT -> R.string.blend_hard_light
        PixaBlend.LIGHTEN -> R.string.blend_lighten
        PixaBlend.DARKEN -> R.string.blend_darken
        PixaBlend.DIFFERENCE -> R.string.blend_difference
        PixaBlend.EXCLUSION -> R.string.blend_exclusion
        PixaBlend.COLOR_BURN -> R.string.blend_color_burn
        PixaBlend.COLOR_DODGE -> R.string.blend_color_dodge
        PixaBlend.HUE -> R.string.blend_hue
        PixaBlend.SATURATION -> R.string.blend_saturation
        PixaBlend.COLOR -> R.string.blend_color
        PixaBlend.LUMINOSITY -> R.string.blend_luminosity
    }

    @StringRes
    fun brush(value: BrushKind): Int = when (value) {
        BrushKind.BRUSH -> R.string.tool_brush
        BrushKind.PENCIL -> R.string.tool_pencil
        BrushKind.AIRBRUSH -> R.string.tool_airbrush
        BrushKind.ERASER -> R.string.tool_eraser
    }

    @StringRes
    fun symmetry(value: SymmetryMode): Int = when (value) {
        SymmetryMode.NONE -> R.string.symmetry_off
        SymmetryMode.VERTICAL -> R.string.symmetry_vertical
        SymmetryMode.HORIZONTAL -> R.string.symmetry_horizontal
        SymmetryMode.BOTH -> R.string.symmetry_both
        SymmetryMode.RADIAL -> R.string.symmetry_radial
    }

    @StringRes
    fun gradient(value: GradientKind): Int = when (value) {
        GradientKind.LINEAR -> R.string.gradient_linear
        GradientKind.RADIAL -> R.string.gradient_radial
        GradientKind.SWEEP -> R.string.gradient_sweep
        GradientKind.REFLECTED -> R.string.gradient_reflected
        GradientKind.REPEATING -> R.string.gradient_repeating
        GradientKind.RADIAL_REPEAT -> R.string.gradient_radial_repeat
    }

    @StringRes
    fun shape(value: ShapeKind): Int = when (value) {
        ShapeKind.RECT -> R.string.shape_rect
        ShapeKind.ELLIPSE -> R.string.shape_ellipse
        ShapeKind.LINE -> R.string.shape_line
        ShapeKind.ARROW -> R.string.shape_arrow
        ShapeKind.POLYGON -> R.string.shape_polygon
        ShapeKind.STAR -> R.string.shape_star
        ShapeKind.HEART -> R.string.shape_heart
    }

    @StringRes
    fun textAlign(value: TextAlign): Int = when (value) {
        TextAlign.LEFT -> R.string.text_align_left
        TextAlign.CENTER -> R.string.text_align_center
        TextAlign.RIGHT -> R.string.text_align_right
    }

    @StringRes
    fun selectionMode(value: Selection.Mode): Int = when (value) {
        Selection.Mode.NEW -> R.string.select_mode_new
        Selection.Mode.ADD -> R.string.select_mode_add
        Selection.Mode.SUBTRACT -> R.string.select_mode_subtract
        Selection.Mode.INTERSECT -> R.string.select_mode_intersect
    }

    @StringRes
    fun effect(value: EffectKind): Int = when (value) {
        EffectKind.NONE -> R.string.label_none
        EffectKind.BW -> R.string.fx_bw
        EffectKind.SEPIA -> R.string.fx_sepia
        EffectKind.INVERT -> R.string.fx_invert
        EffectKind.THRESHOLD -> R.string.fx_threshold
        EffectKind.POSTERIZE -> R.string.fx_posterize
        EffectKind.DUOTONE -> R.string.fx_duotone
        EffectKind.GLITCH -> R.string.fx_glitch
        EffectKind.RGB_SPLIT -> R.string.fx_rgb_split
        EffectKind.HALFTONE -> R.string.fx_halftone
        EffectKind.VINTAGE -> R.string.fx_vintage
        EffectKind.MOTION_BLUR -> R.string.fx_motion_blur
        EffectKind.RADIAL_BLUR -> R.string.fx_radial_blur
        EffectKind.GAUSSIAN_BLUR -> R.string.fx_gaussian_blur
        EffectKind.PIXELATE -> R.string.fx_pixelate
        EffectKind.SOLARIZE -> R.string.fx_solarize
        EffectKind.SCANLINES -> R.string.fx_scanlines
        EffectKind.MIRROR -> R.string.fx_mirror
        EffectKind.BROKEN_TV -> R.string.fx_broken_tv
        EffectKind.FILM_GRAIN -> R.string.fx_film_grain
        EffectKind.VIGNETTE -> R.string.fx_vignette
        EffectKind.CHROMATIC -> R.string.fx_chromatic
        EffectKind.BLOOM -> R.string.fx_bloom
        EffectKind.TILT_SHIFT -> R.string.fx_tilt_shift
        EffectKind.BOKEH -> R.string.fx_bokeh
        EffectKind.LIGHT_LEAK -> R.string.fx_light_leak
        EffectKind.BURNED_EDGES -> R.string.fx_burned_edges
        EffectKind.SKETCH -> R.string.fx_sketch
        EffectKind.COMIC -> R.string.fx_comic
        EffectKind.OIL -> R.string.fx_oil
        EffectKind.WATERCOLOR -> R.string.fx_watercolor
        EffectKind.MOSAIC -> R.string.fx_mosaic
        EffectKind.SCATTER -> R.string.fx_scatter
        EffectKind.SPEED_LINES -> R.string.fx_speed_lines
        EffectKind.RAIN -> R.string.fx_rain
        EffectKind.SNOW -> R.string.fx_snow
        EffectKind.SPARKLES -> R.string.fx_sparkles
        EffectKind.DOUBLE_EXPOSURE -> R.string.fx_double_exposure
        EffectKind.ANAGLYPH -> R.string.fx_anaglyph
        EffectKind.HEATMAP -> R.string.fx_heatmap
        EffectKind.SENSOR_NOISE -> R.string.fx_sensor_noise
        EffectKind.SKY_GOLDEN -> R.string.fx_sky_golden
        EffectKind.SKY_NIGHT -> R.string.fx_sky_night
        EffectKind.SKY_DRAMATIC -> R.string.fx_sky_dramatic
        EffectKind.SKY_BLUE -> R.string.fx_sky_blue
        EffectKind.SPLIT_TONE -> R.string.fx_split_tone
        EffectKind.EMBOSS -> R.string.fx_emboss
        EffectKind.DREAMY -> R.string.fx_dreamy
        EffectKind.LOMO -> R.string.fx_lomo
        EffectKind.CROSS_PROCESS -> R.string.fx_cross_process
        EffectKind.SWIRL -> R.string.fx_swirl
        EffectKind.OLD_PHOTO -> R.string.fx_old_photo
    }

    @StringRes
    fun frame(value: FrameKind): Int = when (value) {
        FrameKind.NONE -> R.string.frame_none
        FrameKind.SOLID -> R.string.frame_solid
        FrameKind.ROUNDED -> R.string.frame_rounded
        FrameKind.DOUBLE -> R.string.frame_double
        FrameKind.DASHED -> R.string.frame_dashed
        FrameKind.INSET -> R.string.frame_inset
        FrameKind.POLAROID -> R.string.frame_polaroid
        FrameKind.FILM -> R.string.frame_film
        FrameKind.VIGNETTE_FRAME -> R.string.frame_vignette
        FrameKind.CORNERS -> R.string.frame_corners
        FrameKind.DOTTED -> R.string.frame_dotted
        FrameKind.GRADIENT -> R.string.frame_gradient
        FrameKind.NEON -> R.string.frame_neon
        FrameKind.SHADOW -> R.string.frame_shadow
        FrameKind.STAMP -> R.string.frame_stamp
        FrameKind.CHECKERED -> R.string.frame_checkered
        FrameKind.RAINBOW -> R.string.frame_rainbow
        FrameKind.ORNATE -> R.string.frame_ornate
        FrameKind.INNER_GLOW -> R.string.frame_inner_glow
        FrameKind.DOUBLE_ROUNDED -> R.string.frame_double_rounded
        FrameKind.BRACKETS -> R.string.frame_brackets
    }

    @StringRes
    fun hslBand(value: HslBand): Int = when (value) {
        HslBand.RED -> R.string.hsl_red
        HslBand.ORANGE -> R.string.hsl_orange
        HslBand.YELLOW -> R.string.hsl_yellow
        HslBand.GREEN -> R.string.hsl_green
        HslBand.CYAN -> R.string.hsl_cyan
        HslBand.BLUE -> R.string.hsl_blue
        HslBand.PURPLE -> R.string.hsl_purple
        HslBand.MAGENTA -> R.string.hsl_magenta
    }

    @StringRes
    fun qrKind(value: QrPayloadKind): Int = when (value) {
        QrPayloadKind.URL -> R.string.qr_type_url
        QrPayloadKind.TEXT -> R.string.qr_type_text
        QrPayloadKind.WIFI -> R.string.qr_type_wifi
        QrPayloadKind.VCARD -> R.string.qr_type_contact
        QrPayloadKind.EMAIL -> R.string.qr_type_email
        QrPayloadKind.PHONE -> R.string.qr_type_phone
    }

    fun qrLevel(value: QrErrorLevel): String = value.name

    @StringRes
    fun exportFormat(value: ExportFormat): Int = when (value) {
        ExportFormat.PNG -> R.string.export_format_png
        ExportFormat.JPEG -> R.string.export_format_jpeg
        ExportFormat.WEBP -> R.string.export_format_webp_lossy
        ExportFormat.PDF -> R.string.export_format_pdf
    }

    @StringRes
    fun watermarkKind(value: WatermarkKind): Int = when (value) {
        WatermarkKind.TEXT -> R.string.watermark_text
        WatermarkKind.LOGO -> R.string.watermark_image
    }

    @StringRes
    fun canvasFill(value: TransformOps.CanvasFill): Int = when (value) {
        TransformOps.CanvasFill.TRANSPARENT -> R.string.canvas_extend_transparent
        TransformOps.CanvasFill.COLOR -> R.string.canvas_extend_fill
        TransformOps.CanvasFill.STRETCH -> R.string.canvas_extend_stretch
    }

    @StringRes
    fun mirrorAxis(value: TransformOps.MirrorAxis): Int = when (value) {
        TransformOps.MirrorAxis.LEFT -> R.string.align_left
        TransformOps.MirrorAxis.RIGHT -> R.string.align_right
        TransformOps.MirrorAxis.TOP -> R.string.align_top
        TransformOps.MirrorAxis.BOTTOM -> R.string.align_bottom
    }

    @StringRes
    fun anchor(value: Anchor9): Int = when (value) {
        Anchor9.TOP_LEFT -> R.string.anchor_top_left
        Anchor9.TOP_CENTER -> R.string.anchor_top_center
        Anchor9.TOP_RIGHT -> R.string.anchor_top_right
        Anchor9.MIDDLE_LEFT -> R.string.anchor_middle_left
        Anchor9.CENTER -> R.string.anchor_center
        Anchor9.MIDDLE_RIGHT -> R.string.anchor_middle_right
        Anchor9.BOTTOM_LEFT -> R.string.anchor_bottom_left
        Anchor9.BOTTOM_CENTER -> R.string.anchor_bottom_center
        Anchor9.BOTTOM_RIGHT -> R.string.anchor_bottom_right
        Anchor9.FREE -> R.string.watermark_free_position
    }

    @StringRes
    fun privacy(value: PrivacyStyle): Int = when (value) {
        PrivacyStyle.PIXELATE -> R.string.privacy_pixelate
        PrivacyStyle.BLUR -> R.string.privacy_blur
    }
}
