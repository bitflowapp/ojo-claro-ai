package com.ojoclaro.android.presence

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * V1.14 — presencia animada de Estela (botón flotante).
 *
 * Estética sobria de "asistente premium": una esfera con glow y un anillo que
 * cambia de comportamiento según el estado (respiración, sonar, órbita, ondas,
 * cámara, alerta). Diseño liviano y barato de dibujar:
 *  - UN solo [ValueAnimator] infinito que avanza una fase 0..1; el dibujo es
 *    procedural a partir de la fase, sin FFT ni audio real;
 *  - CERO allocations en [onDraw] (Paints y buffers viven como campos);
 *  - se PAUSA al desadjuntarse o quedar invisible y se LIBERA en detach;
 *  - respeta "reduced motion": si ANIMATOR_DURATION_SCALE es 0, dibuja un
 *    fotograma estático representativo y no anima.
 *
 * La voz sigue siendo el canal principal: esta vista es refuerzo visual y
 * trae contentDescription por estado para TalkBack.
 */
class AssistantPresenceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private var state: AssistantVisualState = AssistantVisualState.IDLE
    private var phase: Float = 0f

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val animator: ValueAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BASE_PERIOD_MILLIS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = state.contentDescription
    }

    /** Cambia el estado SIN reconstruir la vista: la animación es continua. */
    fun setState(newState: AssistantVisualState) {
        if (newState == state) return
        state = newState
        contentDescription = newState.contentDescription
        invalidate()
    }

    fun currentState(): AssistantVisualState = state

    private fun reducedMotion(): Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f

    private fun startAnimationIfPossible() {
        if (reducedMotion()) {
            phase = STATIC_PHASE
            invalidate()
            return
        }
        if (!animator.isStarted) animator.start()
        else if (animator.isPaused) animator.resume()
    }

    private fun stopAnimation() {
        if (animator.isStarted) animator.pause()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown) startAnimationIfPossible()
    }

    override fun onDetachedFromWindow() {
        // Libera el animador: nada corriendo cuando la vista no existe.
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startAnimationIfPossible() else stopAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startAnimationIfPossible() else stopAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (DEFAULT_SIZE_DP * density).toInt()
        setMeasuredDimension(
            resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        // Radio base con margen para que el glow/anillos no se corten.
        val maxRadius = min(width, height) / 2f
        val coreBase = maxRadius * 0.42f

        val accent = accentColorFor(state)

        when (state) {
            AssistantVisualState.IDLE -> {
                // Respiración lenta: el núcleo late suave, glow tenue.
                val breathe = 0.5f + 0.5f * sin(phase * TWO_PI)
                val core = coreBase * (0.92f + 0.08f * breathe)
                drawGlow(canvas, cx, cy, maxRadius * (0.78f + 0.06f * breathe), accent, 40)
                drawCore(canvas, cx, cy, core, accent)
            }

            AssistantVisualState.LISTENING -> {
                // Sonar: anillos que se expanden hacia afuera ("te oigo").
                drawCore(canvas, cx, cy, coreBase, accent)
                drawExpandingRings(canvas, cx, cy, coreBase, maxRadius, accent, ringCount = 2)
            }

            AssistantVisualState.THINKING -> {
                // Órbita suave: un punto gira alrededor del núcleo.
                drawCore(canvas, cx, cy, coreBase * 0.9f, accent)
                val orbitR = maxRadius * 0.72f
                val angle = phase * TWO_PI
                val dotX = cx + orbitR * cos(angle)
                val dotY = cy + orbitR * sin(angle)
                accentPaint.color = withAlpha(accent, 230)
                canvas.drawCircle(dotX, dotY, 3.2f * density, accentPaint)
                // Estela del punto: un segundo punto más tenue atrás.
                val trailAngle = angle - 0.5f
                accentPaint.color = withAlpha(accent, 90)
                canvas.drawCircle(
                    cx + orbitR * cos(trailAngle),
                    cy + orbitR * sin(trailAngle),
                    2.2f * density,
                    accentPaint
                )
            }

            AssistantVisualState.SPEAKING -> {
                // Ondas procedurales: anillos concéntricos pulsando rápido.
                drawCore(canvas, cx, cy, coreBase * 0.95f, accent)
                drawExpandingRings(canvas, cx, cy, coreBase, maxRadius, accent, ringCount = 3)
            }

            AssistantVisualState.CAMERA_ACTIVE -> {
                // Indicador claro de cámara ENCENDIDA: anillo estable + un
                // punto de "lente" que parpadea lento (jamás silenciosa).
                drawGlow(canvas, cx, cy, maxRadius * 0.8f, accent, 50)
                drawCore(canvas, cx, cy, coreBase, accent)
                ringPaint.color = withAlpha(accent, 220)
                canvas.drawCircle(cx, cy, maxRadius * 0.82f, ringPaint)
                val blink = 0.5f + 0.5f * sin(phase * TWO_PI)
                accentPaint.color = withAlpha(Color.WHITE, (120 + 135 * blink).toInt())
                canvas.drawCircle(cx, cy, coreBase * 0.32f, accentPaint)
            }

            AssistantVisualState.WARNING -> {
                // Alerta: doble pulso ámbar, "me frené por seguridad".
                val pulse = doublePulse(phase)
                drawGlow(canvas, cx, cy, maxRadius * (0.7f + 0.2f * pulse), accent, 70)
                drawCore(canvas, cx, cy, coreBase * (0.95f + 0.12f * pulse), accent)
            }
        }
    }

    private fun drawCore(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        corePaint.color = withAlpha(color, 235)
        canvas.drawCircle(cx, cy, radius, corePaint)
    }

    private fun drawGlow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        alpha: Int
    ) {
        glowPaint.color = withAlpha(color, alpha)
        canvas.drawCircle(cx, cy, radius, glowPaint)
    }

    /** Anillos que nacen en el núcleo y se desvanecen al llegar al borde. */
    private fun drawExpandingRings(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        innerRadius: Float,
        maxRadius: Float,
        color: Int,
        ringCount: Int
    ) {
        for (i in 0 until ringCount) {
            // Cada anillo desfasado para un efecto continuo.
            var p = phase + i.toFloat() / ringCount
            if (p > 1f) p -= 1f
            val radius = innerRadius + (maxRadius - innerRadius) * p
            val alpha = ((1f - p) * 180f).toInt().coerceIn(0, 255)
            ringPaint.color = withAlpha(color, alpha)
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }
    }

    /** Dos picos por ciclo: tac-tac de alerta, no un pulso continuo. */
    private fun doublePulse(p: Float): Float {
        val first = sin(p * TWO_PI * 2f)
        return if (first > 0f) first else 0f
    }

    private fun accentColorFor(state: AssistantVisualState): Int = when (state) {
        AssistantVisualState.IDLE -> COLOR_NEUTRAL
        AssistantVisualState.LISTENING -> COLOR_LISTEN
        AssistantVisualState.THINKING -> COLOR_THINK
        AssistantVisualState.SPEAKING -> COLOR_SPEAK
        AssistantVisualState.CAMERA_ACTIVE -> COLOR_CAMERA
        AssistantVisualState.WARNING -> COLOR_WARNING
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        private const val DEFAULT_SIZE_DP = 56f
        private const val BASE_PERIOD_MILLIS = 2_600L
        private const val TWO_PI = (2.0 * Math.PI).toFloat()
        private const val STATIC_PHASE = 0.25f

        // Paleta sobria (no infantil, no publicidad).
        private const val COLOR_NEUTRAL = 0xFF6E7B91.toInt() // gris azulado
        private const val COLOR_LISTEN = 0xFF3D7BE8.toInt()  // azul activo
        private const val COLOR_THINK = 0xFF8A6FD4.toInt()   // violeta sobrio
        private const val COLOR_SPEAK = 0xFF2BB6A3.toInt()   // verde azulado
        private const val COLOR_CAMERA = 0xFF2E9E63.toInt()  // verde "encendido"
        private const val COLOR_WARNING = 0xFFE0922A.toInt() // ámbar alerta
    }
}
