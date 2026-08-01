import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'

const distDirectory = new URL('../dist/', import.meta.url)
const assetsDirectory = new URL('../dist/assets/', import.meta.url)
const distPath = distDirectory.pathname.replace(/^\/([A-Za-z]:)/, '$1')
const assetsPath = assetsDirectory.pathname.replace(/^\/([A-Za-z]:)/, '$1')

if (!existsSync(assetsPath) || !existsSync(join(distPath, 'index.html'))) {
  console.error('未找到完整的 dist 生产构建，请先执行生产构建。')
  process.exit(1)
}

const indexHtml = readFileSync(join(distPath, 'index.html'), 'utf8')
const javascriptAssets = readdirSync(assetsPath).filter((file) => file.endsWith('.js'))
const assetMetrics = javascriptAssets.map((file) => {
  const contents = readFileSync(join(assetsPath, file))
  return {
    file,
    rawBytes: contents.byteLength,
    gzipBytes: gzipSync(contents).byteLength,
  }
})
const metricsByFile = new Map(assetMetrics.map((metric) => [metric.file, metric]))

const kibibytes = (bytes) => `${(bytes / 1024).toFixed(2)} KiB`
const failures = []

const assertMetric = ({ label, metric, rawLimitKiB, gzipLimitKiB }) => {
  if (!metric) {
    failures.push(`${label}：未找到对应构建产物`)
    return
  }

  console.log(
    `${label.padEnd(18)} ${kibibytes(metric.rawBytes).padStart(12)} / gzip ${kibibytes(metric.gzipBytes).padStart(12)}`,
  )

  if (metric.rawBytes > rawLimitKiB * 1024) {
    failures.push(`${label} 原始体积超过 ${rawLimitKiB} KiB`)
  }
  if (metric.gzipBytes > gzipLimitKiB * 1024) {
    failures.push(`${label} gzip 体积超过 ${gzipLimitKiB} KiB`)
  }
}

const assertMatchingAsset = ({ label, prefix, rawLimitKiB, gzipLimitKiB }) => {
  const asset = assetMetrics.find(({ file }) => file.startsWith(`${prefix}-`))
  if (!asset) {
    failures.push(`${label}：未找到 ${prefix}-*.js`)
    return
  }

  assertMetric({ label, metric: asset, rawLimitKiB, gzipLimitKiB })
}

console.log('生产构建体积预算')
const entryFile = indexHtml.match(/<script[^>]+src="\/assets\/([^"]+\.js)"/)?.[1]
const initialFiles = [
  ...new Set(
    [...indexHtml.matchAll(/(?:src|href)="\/assets\/([^"]+\.js)"/g)].map((match) => match[1]),
  ),
]
const initialMetrics = initialFiles
  .map((file) => metricsByFile.get(file))
  .filter((metric) => metric !== undefined)
const initialAggregate = initialMetrics.reduce(
  (total, metric) => ({
    file: `${initialMetrics.length} 个首屏文件`,
    rawBytes: total.rawBytes + metric.rawBytes,
    gzipBytes: total.gzipBytes + metric.gzipBytes,
  }),
  { file: '', rawBytes: 0, gzipBytes: 0 },
)

assertMetric({
  label: '首屏入口 chunk',
  metric: entryFile ? metricsByFile.get(entryFile) : undefined,
  rawLimitKiB: 488,
  gzipLimitKiB: 160,
})
assertMetric({
  label: '首屏 JS 合计',
  metric: initialAggregate,
  rawLimitKiB: 720,
  gzipLimitKiB: 245,
})
assertMatchingAsset({
  label: 'ECharts vendor',
  prefix: 'echarts',
  rawLimitKiB: 550,
  gzipLimitKiB: 190,
})

const initialFileSet = new Set(initialFiles)
const routeAssets = assetMetrics.filter(
  ({ file }) => !initialFileSet.has(file) && !file.startsWith('echarts-'),
)
const largestRouteAsset = routeAssets.sort((left, right) => right.gzipBytes - left.gzipBytes)[0]

if (!largestRouteAsset) {
  failures.push('未找到业务路由 JavaScript 产物')
} else {
  console.log(
    `${'最大延迟 chunk'.padEnd(18)} ${kibibytes(largestRouteAsset.rawBytes).padStart(12)} / gzip ${kibibytes(largestRouteAsset.gzipBytes).padStart(12)}  ${largestRouteAsset.file}`,
  )
  if (largestRouteAsset.rawBytes > 200 * 1024) {
    failures.push(`最大延迟 chunk 原始体积超过 200 KiB：${largestRouteAsset.file}`)
  }
  if (largestRouteAsset.gzipBytes > 70 * 1024) {
    failures.push(`最大延迟 chunk gzip 体积超过 70 KiB：${largestRouteAsset.file}`)
  }
}

if (failures.length > 0) {
  console.error('\n构建体积预算未通过：')
  failures.forEach((failure) => console.error(`- ${failure}`))
  process.exit(1)
}

console.log('\n构建体积预算通过。')
