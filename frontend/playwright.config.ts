import { defineConfig } from '@playwright/test'
import { createServer } from 'node:net'

const previewHost = '127.0.0.1'
const findAvailablePort = () =>
  new Promise<number>((resolve, reject) => {
    const server = createServer()
    server.unref()
    server.once('error', reject)
    server.listen({ host: previewHost, port: 0, exclusive: true }, () => {
      const address = server.address()
      if (!address || typeof address === 'string') {
        server.close()
        reject(new Error('无法为 Playwright 生产预览选择动态端口。'))
        return
      }
      server.close((error) => {
        if (error) reject(error)
        else resolve(address.port)
      })
    })
  })

const externalBaseURL = process.env.UI_TEST_BASE_URL
const inheritedPreviewPort = process.env.UI_TEST_PORT
const parsedInheritedPreviewPort = inheritedPreviewPort
  ? Number.parseInt(inheritedPreviewPort, 10)
  : undefined
if (
  parsedInheritedPreviewPort !== undefined &&
  (!/^\d+$/.test(inheritedPreviewPort ?? '') ||
    parsedInheritedPreviewPort < 1 ||
    parsedInheritedPreviewPort > 65_535)
) {
  throw new Error('UI_TEST_PORT 必须是 1 到 65535 之间的整数。')
}
const previewPort = externalBaseURL
  ? undefined
  : parsedInheritedPreviewPort ?? (await findAvailablePort())
if (!externalBaseURL) {
  process.env.UI_TEST_PORT = String(previewPort)
}
const baseURL = externalBaseURL ?? `http://${previewHost}:${previewPort}`
const browserChannel = process.env.PLAYWRIGHT_BROWSER_CHANNEL ?? 'chrome'

export default defineConfig({
  testDir: './tests/ui',
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: {
    timeout: 10_000,
  },
  reporter: [['line']],
  use: {
    baseURL,
    browserName: 'chromium',
    channel: browserChannel,
    headless: true,
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: 'retain-on-failure',
  },
  webServer: process.env.UI_TEST_BASE_URL
    ? undefined
    : {
        command:
          `npm run build-only && npm run preview -- --host ${previewHost} --port ${previewPort} --strictPort`,
        url: baseURL,
        reuseExistingServer: false,
        timeout: 180_000,
      },
})
