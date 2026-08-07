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

/**
 * 专业交互点阵背景（首页 hero 专用）
 *
 * 设计参考 shadcn dot-pattern + Physics-Based Dot Grid：
 * - 波浪呼吸：静止时 sin 波上下涌动，背景有生命感
 * - 交互发光：鼠标靠近点发光+放大（弹簧物理，回弹带惯性阻尼）
 * - 弹性回弹：速度/惯性/阻尼弹簧系统，点阵有机地追踪目标值
 * - 扫过余晖：鼠标移动路径留下衰减光迹（additive 发光）
 * - 点击冲击波：pointerdown 扩散涟漪
 * - 性能：ResizeObserver 重建 + DPR 感知 + 逐帧插值（mousemove 仅记录坐标）
 *
 * 仅 dotmatrix 主题 + 非 prefers-reduced-motion 启用，其余返回空组件。
 */
const canvasRef = ref<HTMLCanvasElement | null>(null)

const SPACING = 24
const BASE_RADIUS = 1.2
const MAX_RADIUS = 3.6
const GLOW_RADIUS = 110
const FADED_ALPHA = 0.13 // 背景柔和可见（用户对齐：平时纹理，交互时主角）
const ACTIVE_ALPHA = 0.9
const WAVE_AMP = 3 // 波浪振幅 px
const WAVE_SPEED = 1.05 // ≈ 2π/6s 周期 6s
const SPRING_K = 0.08 // 弹簧刚度
const DAMPING = 0.86 // 阻尼系数（回弹惯性）
const REPEL = 14 // 鼠标排斥位移 px（弹性回弹深度）

interface Dot {
  bx: number // 基准 x
  by: number // 基准 y
  phase: number // 波浪相位
  alpha: number // 当前 alpha
  aVel: number // alpha 弹簧速度
  radius: number // 当前半径
  rVel: number // 半径弹簧速度
  dispX: number // 排斥位移 x
  dispY: number // 排斥位移 y
  dVelX: number // 位移弹簧速度 x
  dVelY: number // 位移弹簧速度 y
}

interface TrailPoint {
  x: number
  y: number
  life: number // 0..1 衰减
}

interface Ring {
  x: number
  y: number
  r: number
  alpha: number
}

let ctx: CanvasRenderingContext2D | null = null
let dots: Dot[] = []
let width = 0
let height = 0
let mouseX = -9999
let mouseY = -9999
let time = 0
let lastT = 0
let rafId = 0
const enabled = ref(false)
let unmounted = false
let resizeObserver: ResizeObserver | null = null
const trail: TrailPoint[] = []
const rings: Ring[] = []

function shouldEnable(): boolean {
  // 读取 DOM 主题（与组件挂载时序无关），仅点阵终端 + 非精简动效
  const theme = document.documentElement.dataset.theme ?? 'default'
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

  // 重建点阵（保持基准坐标，相位随机）
  dots = []
  for (let x = SPACING / 2; x < width; x += SPACING) {
    for (let y = SPACING / 2; y < height; y += SPACING) {
      const jitter = (Math.random() - 0.5) * 2
      dots.push({
        bx: x + jitter,
        by: y + jitter,
        phase: Math.random() * Math.PI * 2,
        alpha: FADED_ALPHA,
        aVel: 0,
        radius: BASE_RADIUS,
        rVel: 0,
        dispX: 0,
        dispY: 0,
        dVelX: 0,
        dVelY: 0,
      })
    }
  }
}

/** 弹簧物理：有机地追踪目标值，带回弹惯性 */
function springStep(current: number, target: number, vel: number): { val: number; vel: number } {
  vel = (vel + (target - current) * SPRING_K) * DAMPING
  current += vel
  return { val: current, vel }
}

function draw(): void {
  if (!ctx || unmounted) return
  const now = performance.now()
  const dt = lastT ? Math.min((now - lastT) / 1000, 0.05) : 0.016
  lastT = now
  time += dt

  ctx.clearRect(0, 0, width, height)
  ctx.globalCompositeOperation = 'lighter' // 叠加发光

  // 光迹余晖衰减
  for (let i = trail.length - 1; i >= 0; i--) {
    const p = trail[i]
    p.life -= dt * 2.2
    if (p.life <= 0) {
      trail.splice(i, 1)
      continue
    }
    const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, 34)
    g.addColorStop(0, `rgba(0, 240, 255, ${0.22 * p.life})`)
    g.addColorStop(1, 'rgba(0, 240, 255, 0)')
    ctx.fillStyle = g
    ctx.fillRect(p.x - 34, p.y - 34, 68, 68)
  }

  // 点击冲击波扩散
  for (let i = rings.length - 1; i >= 0; i--) {
    const ring = rings[i]
    ring.r += dt * 90
    ring.alpha -= dt * 1.6
    if (ring.alpha <= 0) {
      rings.splice(i, 1)
      continue
    }
    ctx.strokeStyle = `rgba(0, 240, 255, ${Math.max(ring.alpha, 0)})`
    ctx.lineWidth = 1.5
    ctx.beginPath()
    ctx.arc(ring.x, ring.y, ring.r, 0, Math.PI * 2)
    ctx.stroke()
  }

  // 波浪 + 交互 + 弹簧物理
  for (const dot of dots) {
    const waveY = Math.sin(time * WAVE_SPEED + dot.phase) * WAVE_AMP
    const dx = dot.bx + dot.dispX - mouseX
    const dy = dot.by + dot.dispY + waveY - mouseY
    const dist2 = dx * dx + dy * dy
    const inGlow = dist2 < GLOW_RADIUS * GLOW_RADIUS

    let targetAlpha = FADED_ALPHA
    let targetRadius = BASE_RADIUS
    if (inGlow) {
      const dist = Math.sqrt(dist2)
      const t = 1 - dist / GLOW_RADIUS
      const ease = t * t // 平方缓入，近处更亮
      targetAlpha = FADED_ALPHA + (ACTIVE_ALPHA - FADED_ALPHA) * ease
      targetRadius = BASE_RADIUS + (MAX_RADIUS - BASE_RADIUS) * ease
    }

    // 发光/半径弹簧
    const a = springStep(dot.alpha, targetAlpha, dot.aVel)
    dot.alpha = a.val
    dot.aVel = a.vel
    const r = springStep(dot.radius, targetRadius, dot.rVel)
    dot.radius = r.val
    dot.rVel = r.vel

    // 排斥位移 + 弹性回弹
    if (inGlow && dist2 > 1) {
      const dist = Math.sqrt(dist2)
      const force = (1 - dist / GLOW_RADIUS) * REPEL
      dot.dVelX += (dx / dist) * force * 0.08
      dot.dVelY += (dy / dist) * force * 0.08
    }
    const dxs = springStep(dot.dispX, 0, dot.dVelX)
    dot.dispX = dxs.val
    dot.dVelX = dxs.vel
    const dys = springStep(dot.dispY, 0, dot.dVelY)
    dot.dispY = dys.val
    dot.dVelY = dys.vel

    ctx.globalAlpha = Math.min(Math.max(dot.alpha, 0), 1)
    ctx.fillStyle = 'rgba(0, 240, 255, 1)'
    ctx.beginPath()
    ctx.arc(dot.bx + dot.dispX, dot.by + dot.dispY + waveY, Math.max(dot.radius, 0.3), 0, Math.PI * 2)
    ctx.fill()
  }

  ctx.globalAlpha = 1
  ctx.globalCompositeOperation = 'source-over'
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
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  mouseX = x
  mouseY = y
  // 记录移动路径，扫过留余晖
  trail.push({ x, y, life: 1 })
  if (trail.length > 60) trail.shift()
}

function onMouseLeave(): void {
  mouseX = -9999
  mouseY = -9999
}

function onPointerDown(e: PointerEvent): void {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  rings.push({ x: e.clientX - rect.left, y: e.clientY - rect.top, r: 4, alpha: 0.5 })
}

onMounted(() => {
  enabled.value = shouldEnable()
  if (!enabled.value) return
  const canvas = canvasRef.value
  if (!canvas) return
  ctx = canvas.getContext('2d')
  if (!ctx) return

  resize()

  resizeObserver = new ResizeObserver(() => resize())
  resizeObserver.observe(canvas.parentElement ?? canvas)

  canvas.addEventListener('mousemove', onMouseMove)
  canvas.addEventListener('mouseleave', onMouseLeave)
  canvas.addEventListener('pointerdown', onPointerDown)
  window.addEventListener('resize', resize)
  loop()
})

onBeforeUnmount(() => {
  unmounted = true
  cancelAnimationFrame(rafId)
  resizeObserver?.disconnect()
  resizeObserver = null
  const canvas = canvasRef.value
  if (canvas) {
    canvas.removeEventListener('mousemove', onMouseMove)
    canvas.removeEventListener('mouseleave', onMouseLeave)
    canvas.removeEventListener('pointerdown', onPointerDown)
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
