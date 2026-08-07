<template>
  <canvas
    ref="canvasRef"
    class="dot-grid-canvas"
    :class="{ visible: enabled }"
    aria-hidden="true"
  ></canvas>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getCurrentTheme } from '@/composables/useTheme'

/**
 * 交互式点阵背景（首页 hero 专用）
 * 鼠标靠近时光点放大发光，静止时极淡点阵陪衬。
 * 仅 dotmatrix 主题启用，其他主题返回空（透明）。
 * Canvas 渲染 + pointer-events:none，不拦截交互。
 */
const canvasRef = ref<HTMLCanvasElement | null>(null)

const SPACING = 24
const DOT_RADIUS = 1.2
const MAX_RADIUS = 4
const GLOW_RADIUS = 90
const FADED_ALPHA = 0.28
const ACTIVE_ALPHA = 0.9

interface Dot {
  x: number
  y: number
  baseAlpha: number
}

let ctx: CanvasRenderingContext2D | null = null
let dots: Dot[] = []
let width = 0
let height = 0
let mouseX = -9999
let mouseY = -9999
let rafId = 0
let enabled = false
let unmounted = false

function shouldEnable(): boolean {
  const theme = getCurrentTheme()
  // 仅点阵终端 + 非精简动效模式
  return theme === 'dotmatrix' && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function resize(): void {
  const canvas = canvasRef.value
  if (!canvas) return
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const rect = canvas.parentElement?.getBoundingClientRect()
  width = rect?.width ?? window.innerWidth
  height = rect?.height ?? 400
  canvas.width = Math.floor(width * dpr)
  canvas.height = Math.floor(height * dpr)
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  if (ctx) ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

  // 生成点阵
  dots = []
  for (let x = SPACING / 2; x < width; x += SPACING) {
    for (let y = SPACING / 2; y < height; y += SPACING) {
      // 轻微抖动让点阵自然
      const jitter = (Math.random() - 0.5) * 2
      dots.push({ x: x + jitter, y: y + jitter, baseAlpha: FADED_ALPHA })
    }
  }
}

function draw(): void {
  if (!ctx || unmounted) return
  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = 'rgba(0, 240, 255, 1)'

  for (const dot of dots) {
    const dx = dot.x - mouseX
    const dy = dot.y - mouseY
    const dist2 = dx * dx + dy * dy
    const inGlow = dist2 < GLOW_RADIUS * GLOW_RADIUS

    let alpha = FADED_ALPHA
    let radius = DOT_RADIUS
    if (inGlow) {
      const dist = Math.sqrt(dist2)
      const t = 1 - dist / GLOW_RADIUS
      alpha = FADED_ALPHA + (ACTIVE_ALPHA - FADED_ALPHA) * t
      radius = DOT_RADIUS + (MAX_RADIUS - DOT_RADIUS) * t
    }

    ctx.globalAlpha = Math.min(alpha, 1)
    ctx.beginPath()
    ctx.arc(dot.x, dot.y, radius, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.globalAlpha = 1
}

function loop(): void {
  if (unmounted) return
  draw()
  rafId = requestAnimationFrame(loop)
}

function onMouseMove(e: MouseEvent): void {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  mouseX = e.clientX - rect.left
  mouseY = e.clientY - rect.top
}

function onMouseLeave(): void {
  mouseX = -9999
  mouseY = -9999
}

onMounted(() => {
  enabled = shouldEnable()
  if (!enabled) return
  const canvas = canvasRef.value
  if (!canvas) return
  ctx = canvas.getContext('2d')
  if (!ctx) return

  resize()
  window.addEventListener('resize', resize)
  canvas.addEventListener('mousemove', onMouseMove)
  canvas.addEventListener('mouseleave', onMouseLeave)
  loop()
})

onBeforeUnmount(() => {
  unmounted = true
  cancelAnimationFrame(rafId)
  const canvas = canvasRef.value
  if (canvas) {
    canvas.removeEventListener('mousemove', onMouseMove)
    canvas.removeEventListener('mouseleave', onMouseLeave)
  }
  window.removeEventListener('resize', resize)
})
</script>

<style scoped>
.dot-grid-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
  opacity: 0;
  transition: opacity 0.5s ease;
}

.dot-grid-canvas.visible {
  opacity: 1;
}
</style>