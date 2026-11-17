package com.example.ui

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TacticalCyan
import org.intellij.lang.annotations.Language
import kotlin.math.cos
import kotlin.math.sin

val LocalLiquidGlassTime = compositionLocalOf { 0f }

@Language("AGSL")
private const val LIQUID_AMBIENT_FIELD_AGSL = """
    uniform float2 iResolution;
    uniform float iTime;

    float hash21(float2 p) {
        p = fract(p * float2(123.34, 456.21));
        p += dot(p, p + 45.32);
        return fract(p.x * p.y);
    }

    half4 main(in float2 fragCoord) {
        float2 res = max(iResolution, float2(1.0));
        float2 uv = fragCoord / res;
        float aspect = res.x / res.y;
        float2 p = float2((uv.x - 0.5) * aspect, uv.y - 0.5);

        float t = iTime * 0.45;
        float2 orb1 = float2(sin(t * 0.8) * 0.42, cos(t * 0.6) * 0.35);
        float2 orb2 = float2(cos(t * 0.5 + 2.1) * 0.48, sin(t * 0.7 + 1.0) * 0.40);
        float2 orb3 = float2(sin(t * 0.4 + 4.0) * 0.32, cos(t * 0.9 + 3.2) * 0.28);

        float d1 = exp(-dot(p - orb1, p - orb1) * 3.8);
        float d2 = exp(-dot(p - orb2, p - orb2) * 3.2);
        float d3 = exp(-dot(p - orb3, p - orb3) * 4.5);

        float wave = 0.5 + 0.5 * sin(p.x * 5.2 + t * 1.4 + sin(p.y * 4.0 - t));
        float caustic = smoothstep(0.55, 0.98, wave) * 0.14;

        half3 base = half3(0.018, 0.030, 0.058);
        half3 emerald = half3(0.0, 0.98, 0.62) * (d1 * 0.34 + caustic * 0.55);
        half3 cyan = half3(0.22, 0.74, 0.98) * (d2 * 0.30 + caustic * 0.45);
        half3 indigo = half3(0.38, 0.34, 0.96) * (d3 * 0.24);

        half3 col = base + emerald + cyan + indigo;
        col += (hash21(fragCoord) - 0.5) * 0.012;
        return half4(col, 1.0);
    }
"""

@Language("AGSL")
private const val LIQUID_GLASS_BACKDROP_SAMPLE_AGSL = """
    uniform float2 iSize;
    uniform float2 iOrigin;
    uniform float2 iScreenSize;
    uniform float iTime;

    half4 main(in float2 fragCoord) {
        float2 screen = max(iScreenSize, float2(1.0));
        float2 worldPos = fragCoord + iOrigin;
        float2 uv = worldPos / screen;
        float aspect = screen.x / screen.y;
        float2 p = float2((uv.x - 0.5) * aspect, uv.y - 0.5);

        float t = iTime * 0.45;
        float2 orb1 = float2(sin(t * 0.8) * 0.42, cos(t * 0.6) * 0.35);
        float2 orb2 = float2(cos(t * 0.5 + 2.1) * 0.48, sin(t * 0.7 + 1.0) * 0.40);
        float2 orb3 = float2(sin(t * 0.4 + 4.0) * 0.32, cos(t * 0.9 + 3.2) * 0.28);

        float d1 = exp(-dot(p - orb1, p - orb1) * 3.4);
        float d2 = exp(-dot(p - orb2, p - orb2) * 2.9);
        float d3 = exp(-dot(p - orb3, p - orb3) * 4.0);
        float wave = 0.5 + 0.5 * sin(p.x * 5.2 + t * 1.4 + sin(p.y * 4.0 - t));

        half3 col = half3(0.032, 0.052, 0.095);
        col += half3(0.0, 1.0, 0.64) * (d1 * 0.52 + smoothstep(0.5, 1.0, wave) * 0.14);
        col += half3(0.22, 0.75, 0.98) * (d2 * 0.48);
        col += half3(0.45, 0.38, 0.98) * (d3 * 0.36);
        return half4(col, 1.0);
    }
"""

@Language("AGSL")
private const val LIQUID_GLASS_REFRACTION_SHADER_AGSL = """
    uniform shader composable;
    uniform float2 iSize;
    uniform float iCornerRadius;
    uniform float iTime;
    uniform half4 iAccent;

    float sdRoundBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    float2 calcNormal(float2 p, float2 b, float r) {
        float e = 1.5;
        float dx = sdRoundBox(p + float2(e, 0.0), b, r) - sdRoundBox(p - float2(e, 0.0), b, r);
        float dy = sdRoundBox(p + float2(0.0, e), b, r) - sdRoundBox(p - float2(0.0, e), b, r);
        float2 g = float2(dx, dy);
        float len = max(length(g), 0.0001);
        return g / len;
    }

    half4 main(in float2 fragCoord) {
        float2 size = max(iSize, float2(2.0));
        float2 halfSize = size * 0.5;
        float2 p = fragCoord - halfSize;
        float r = clamp(iCornerRadius, 2.0, min(halfSize.x, halfSize.y));

        float sd = sdRoundBox(p, halfSize, r);
        float edgeDepth = clamp(-sd / 34.0, 0.0, 1.0);
        float rimZone = 1.0 - edgeDepth;

        // Curved optical glass surface normal + liquid wave distortion
        float2 normal = calcNormal(p, halfSize, r);
        float ripple = sin(fragCoord.x * 0.025 + iTime * 1.1) * cos(fragCoord.y * 0.025 - iTime * 0.9);
        float2 refractOffset = normal * (rimZone * rimZone * 26.0) + float2(ripple, -ripple) * 3.2;

        // Chromatic RGB dispersion along the refracted glass lens bezel
        float2 disp = normal * (rimZone * 4.5 + 0.8);
        float2 sampleCenter = clamp(fragCoord - refractOffset, float2(0.0), size - 1.0);
        half rCh = composable.eval(clamp(sampleCenter + disp, float2(0.0), size - 1.0)).r;
        half gCh = composable.eval(sampleCenter).g;
        half bCh = composable.eval(clamp(sampleCenter - disp, float2(0.0), size - 1.0)).b;
        half3 refracted = half3(rCh, gCh, bCh);

        // Frosted obsidian glass absorption tint for high readability
        half3 glassBody = mix(refracted, half3(0.045, 0.072, 0.120), 0.56);

        // Directional Apple Cupertino Fresnel specular reflection
        float2 uvNorm = p / halfSize;
        float topLeftFresnel = clamp(dot(normal, normalize(float2(-0.75, -0.95))), 0.0, 1.0);
        float bottomRightFresnel = clamp(dot(normal, normalize(float2(0.75, 0.95))), 0.0, 1.0);
        float sweep = smoothstep(0.86, 1.0, sin((uvNorm.x + uvNorm.y) * 2.2 - iTime * 0.55) * 0.5 + 0.5);

        float bezelHighlight = smoothstep(-5.5, 0.0, sd) * (0.28 + 0.72 * topLeftFresnel);
        float lowerCatchlight = smoothstep(-4.0, 0.0, sd) * bottomRightFresnel * 0.35;
        float topSheen = exp(-max(fragCoord.y, 0.0) * 0.045) * 0.10;

        half3 finalRgb = glassBody;
        finalRgb += half3(1.0, 1.0, 1.0) * (bezelHighlight * 0.42 + topSheen + sweep * 0.05);
        finalRgb += iAccent.rgb * (lowerCatchlight * 0.36 + rimZone * 0.08);

        return half4(clamp(finalRgb, 0.0, 1.0), 0.94);
    }
"""

@Composable
fun ProvideLiquidGlassClock(
    batterySaver: Boolean,
    content: @Composable () -> Unit
) {
    val time = if (batterySaver) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "liquid_glass_clock")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 62.8318f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 28000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "liquid_time"
        )
        animated
    }
    CompositionLocalProvider(LocalLiquidGlassTime provides time, content = content)
}

@Composable
fun LiquidAmbientBackground(
    batterySaver: Boolean,
    modifier: Modifier = Modifier
) {
    val time = LocalLiquidGlassTime.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !batterySaver) {
        LiquidAmbientShaderCanvas(time = time, modifier = modifier)
    } else {
        Canvas(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg)
        ) {
            val t = if (batterySaver) 0.3f else time * 0.45f
            val c1 = Offset(size.width * (0.28f + 0.16f * sin(t)), size.height * (0.25f + 0.12f * cos(t * 0.8f)))
            val c2 = Offset(size.width * (0.72f + 0.14f * cos(t * 0.7f)), size.height * (0.68f + 0.15f * sin(t * 0.6f)))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(PhosphorEmerald.copy(alpha = 0.18f), Color.Transparent),
                    center = c1,
                    radius = size.maxDimension * 0.55f
                ),
                center = c1,
                radius = size.maxDimension * 0.55f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(TacticalCyan.copy(alpha = 0.16f), Color.Transparent),
                    center = c2,
                    radius = size.maxDimension * 0.6f
                ),
                center = c2,
                radius = size.maxDimension * 0.6f
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LiquidAmbientShaderCanvas(
    time: Float,
    modifier: Modifier = Modifier
) {
    val shader = remember { RuntimeShader(LIQUID_AMBIENT_FIELD_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier = modifier.fillMaxSize()) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", time)
        drawRect(brush = brush)
    }
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    accentColor: Color = TacticalCyan,
    batterySaver: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val time = LocalLiquidGlassTime.current

    var originInRoot by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var rootSize by remember { mutableStateOf(Offset(1080f, 2400f)) }

    Box(
        modifier = modifier
            .clip(shape)
            .onGloballyPositioned { coords ->
                originInRoot = coords.positionInRoot()
                boxSize = coords.size
                val parent = coords.parentLayoutCoordinates
                if (parent != null) {
                    var root: LayoutCoordinates = parent
                    while (true) {
                        val up = root.parentLayoutCoordinates ?: break
                        root = up
                    }
                    rootSize = Offset(root.size.width.toFloat(), root.size.height.toFloat())
                }
            }
    ) {
        // Real Liquid Glass Optical Layer (Blur -> Snell Refraction -> Chromatic Dispersion -> Specular Reflection)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !batterySaver && boxSize.width > 0 && boxSize.height > 0) {
            LiquidGlassShaderBackdrop(
                originInRoot = originInRoot,
                rootSize = rootSize,
                boxSize = boxSize,
                cornerRadiusPx = cornerRadiusPx,
                time = time,
                accentColor = accentColor,
                shape = shape,
                modifier = Modifier.matchParentSize()
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !batterySaver) {
            val blurModifier = remember {
                Modifier.graphicsLayer {
                    renderEffect = AndroidRenderEffect
                        .createBlurEffect(26f, 26f, Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(blurModifier)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.14f),
                                Color(0xCC0D1422),
                                Color(0xD90A101B)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.12f),
                                Color(0xE00C1320),
                                Color(0xEB080D17)
                            )
                        )
                    )
            )
        }

        // Specular Cupertino Glass Rim Border
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.34f),
                            accentColor.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.08f),
                            accentColor.copy(alpha = 0.18f)
                        )
                    ),
                    shape = shape
                )
        )

        content()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LiquidGlassShaderBackdrop(
    originInRoot: Offset,
    rootSize: Offset,
    boxSize: IntSize,
    cornerRadiusPx: Float,
    time: Float,
    accentColor: Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    val sampleShader = remember { RuntimeShader(LIQUID_GLASS_BACKDROP_SAMPLE_AGSL) }
    val sampleBrush = remember(sampleShader) { ShaderBrush(sampleShader) }
    val lensShader = remember { RuntimeShader(LIQUID_GLASS_REFRACTION_SHADER_AGSL) }

    Canvas(
        modifier = modifier.graphicsLayer {
            val w = boxSize.width.toFloat().coerceAtLeast(2f)
            val h = boxSize.height.toFloat().coerceAtLeast(2f)
            lensShader.setFloatUniform("iSize", w, h)
            lensShader.setFloatUniform("iCornerRadius", cornerRadiusPx)
            lensShader.setFloatUniform("iTime", time)
            lensShader.setFloatUniform(
                "iAccent",
                accentColor.red,
                accentColor.green,
                accentColor.blue,
                accentColor.alpha
            )

            val blurEffect = AndroidRenderEffect.createBlurEffect(
                24f,
                24f,
                Shader.TileMode.CLAMP
            )
            val refractEffect = AndroidRenderEffect.createRuntimeShaderEffect(
                lensShader,
                "composable"
            )
            renderEffect = AndroidRenderEffect
                .createChainEffect(refractEffect, blurEffect)
                .asComposeRenderEffect()
            this.shape = shape
            clip = true
        }
    ) {
        sampleShader.setFloatUniform("iSize", size.width, size.height)
        sampleShader.setFloatUniform("iOrigin", originInRoot.x, originInRoot.y)
        sampleShader.setFloatUniform("iScreenSize", rootSize.x.coerceAtLeast(1f), rootSize.y.coerceAtLeast(1f))
        sampleShader.setFloatUniform("iTime", time)
        drawRect(brush = sampleBrush)
    }
}
