/**
 * 头像工具函数 — 基于 DiceBear 开源头像库
 *
 * 核心思路：用任意字符串（用户名/邮箱/ID）作为 seed，DiceBear 生成
 * 确定性 SVG 头像 —— 同一用户永远得到同一头像，无需上传/存储。
 *
 * 默认风格 Open Peeps（手绘插画、暖色调、多样角色），
 * 备选风格 Lorelei/Notionists 供后续主题切换复用。
 */

/** 支持的头像风格映射（为后续主题切换留扩展点） */
export const AVATAR_STYLES = {
  openPeeps: 'open-peeps',
  lorelei: 'lorelei',
  notionists: 'notionists',
} as const

export type AvatarStyle = keyof typeof AVATAR_STYLES

const API_BASE = 'https://api.dicebear.com/10.x'

/** 项目主题主色（variables.css --color-primary #22C55E），用作头像背景 */
const THEME_BG = 'd5f5e3'

/**
 * 生成 DiceBear 头像 URL
 * @param seed 确定性种子（用户名/邮箱/ID），同一 seed 恒定同一头像
 * @param style 头像风格，默认 openPeeps
 * @param backgroundColor 背景色（hex 无 #），默认主题绿色系
 */
export const buildAvatarUrl = (
  seed: string,
  style: AvatarStyle = 'openPeeps',
  backgroundColor: string = THEME_BG,
): string => {
  const safeSeed = encodeURIComponent(seed.trim() || 'default')
  return `${API_BASE}/${AVATAR_STYLES[style]}/svg?seed=${safeSeed}&backgroundColor=${backgroundColor}`
}

/**
 * 获取用户头像：优先真实头像，缺失时用 DiceBear 兜底
 * @param userAvatar 用户已存头像（可能为空）
 * @param seed 生成兜底头像的种子（用户名/账号/ID）
 * @param style 可选风格覆盖
 */
export const getAvatar = (
  userAvatar?: string | null,
  seed?: string | null,
  style?: AvatarStyle,
): string | undefined => {
  if (userAvatar) return userAvatar
  return buildAvatarUrl(seed || 'default', style)
}
