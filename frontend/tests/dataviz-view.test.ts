import assert from 'node:assert/strict'
import test from 'node:test'
import {
  MAX_POLL_ATTEMPTS,
  buildArtifactUrl,
  isPollExhausted,
  missingExecutionIdMessage,
  shouldStopPolling,
} from '../src/utils/datavizState.ts'

test('buildArtifactUrl 指向后端产物端点', () => {
  assert.equal(buildArtifactUrl('abc-123', 'html'), '/api/dataviz/abc-123/html')
  assert.equal(buildArtifactUrl('abc-123', 'png'), '/api/dataviz/abc-123/png')
})

test('buildArtifactUrl 对标识做编码，防路径穿越', () => {
  assert.equal(buildArtifactUrl('../etc', 'html'), '/api/dataviz/..%2Fetc/html')
  assert.ok(!buildArtifactUrl('..%2f..%2f', 'png').includes('/../'))
})

test('shouldStopPolling 仅在 htmlReady 时停止', () => {
  assert.equal(shouldStopPolling({ htmlReady: false, pngReady: false }), false)
  assert.equal(shouldStopPolling({ htmlReady: true, pngReady: false }), true)
  // PNG 可能永久缺席，不能因它继续轮询
  assert.equal(shouldStopPolling({ htmlReady: true, pngReady: true }), true)
})

test('isPollExhausted 在达到上限时判定超时', () => {
  assert.equal(isPollExhausted(0), false)
  assert.equal(isPollExhausted(MAX_POLL_ATTEMPTS - 1), false)
  assert.equal(isPollExhausted(MAX_POLL_ATTEMPTS), true)
})

test('缺少执行编号时有可读提示', () => {
  assert.ok(missingExecutionIdMessage.includes('执行编号'))
})
