import assert from 'node:assert/strict'
import test from 'node:test'
import {
  applySkillProgressEvent,
  createSkillRuntimeSnapshot,
} from '../src/utils/skillExecutionState.ts'
import { diffText } from '../src/utils/textDiff.ts'

const phases = [
  {
    name: 'content_review',
    outputKey: 'reviewResult',
  },
  {
    name: 'ai_tone_fix',
    outputKey: 'polishedContent',
  },
] as API.SkillPhaseDefinition[]

test('skill state tracks real phases and streaming content', () => {
  let state = createSkillRuntimeSnapshot()
  state = applySkillProgressEvent(
    state,
    {
      type: 'skill.phase_started',
      skillExecutionId: 'execution-1',
      skillName: 'proofreading',
      phase: 'ai_tone_fix',
      phaseIndex: 2,
    },
    phases,
  )
  state = applySkillProgressEvent(
    state,
    {
      type: 'skill.progress',
      skillExecutionId: 'execution-1',
      skillName: 'proofreading',
      data: '第一段',
    },
    phases,
  )
  state = applySkillProgressEvent(
    state,
    {
      type: 'skill.progress',
      skillExecutionId: 'execution-1',
      skillName: 'proofreading',
      data: '第二段',
    },
    phases,
  )

  assert.equal(state.phase, 'ai_tone_fix')
  assert.equal(state.phaseIndex, 2)
  assert.equal(state.streamedText, '第一段第二段')
})

test('skill state aggregates phase output and terminal result', () => {
  let state = createSkillRuntimeSnapshot()
  state = applySkillProgressEvent(
    state,
    {
      type: 'skill.phase_complete',
      skillExecutionId: 'execution-1',
      skillName: 'proofreading',
      phase: 'content_review',
      outputData: { overallScore: 88 },
    },
    phases,
  )
  assert.deepEqual(state.outputData.reviewResult, { overallScore: 88 })

  state = applySkillProgressEvent(
    state,
    {
      type: 'skill.complete',
      skillExecutionId: 'execution-1',
      skillName: 'proofreading',
      outputData: {
        reviewResult: { overallScore: 88 },
        polishedContent: '润色稿',
      },
    },
    phases,
  )
  assert.equal(state.terminalState, 'COMPLETED')
  assert.equal(state.outputData.polishedContent, '润色稿')
})

test('skill state preserves a readable failure reason', () => {
  const state = applySkillProgressEvent(
    createSkillRuntimeSnapshot(),
    {
      type: 'skill.error',
      skillExecutionId: 'execution-1',
      skillName: 'proofreading',
      errorMessage: '模型服务暂不可用',
    },
    phases,
  )

  assert.equal(state.terminalState, 'FAILED')
  assert.equal(state.errorMessage, '模型服务暂不可用')
})

test('Chinese text diff exposes additions and removals', () => {
  const segments = diffText('第一句。第二句。', '第一句。新的第二句。')
  assert.ok(segments.some((segment) => segment.type === 'removed' && segment.value.includes('第二句')))
  assert.ok(segments.some((segment) => segment.type === 'added' && segment.value.includes('新的第二句')))
})
