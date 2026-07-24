export type SkillTerminalState = 'COMPLETED' | 'FAILED' | null

export interface SkillRuntimeSnapshot {
  phase: string
  phaseIndex: number
  streamedText: string
  outputData: Record<string, unknown>
  errorMessage: string
  terminalState: SkillTerminalState
}

export const createSkillRuntimeSnapshot = (): SkillRuntimeSnapshot => ({
  phase: '',
  phaseIndex: 1,
  streamedText: '',
  outputData: {},
  errorMessage: '',
  terminalState: null,
})

export const applySkillProgressEvent = (
  snapshot: SkillRuntimeSnapshot,
  event: API.SkillProgressEvent,
  phases: API.SkillPhaseDefinition[],
): SkillRuntimeSnapshot => {
  const next: SkillRuntimeSnapshot = {
    ...snapshot,
    outputData: { ...snapshot.outputData },
    phase: event.phase || snapshot.phase,
    phaseIndex: event.phaseIndex || snapshot.phaseIndex,
  }

  if (event.type === 'skill.phase_started') {
    next.streamedText = ''
  } else if (event.type === 'skill.progress' && event.data) {
    next.streamedText += event.data
  } else if (event.type === 'skill.phase_complete') {
    const phase = phases.find((item) => item.name === event.phase)
    if (phase && event.outputData !== undefined) {
      next.outputData[phase.outputKey] = event.outputData
    }
  } else if (event.type === 'skill.complete') {
    if (event.outputData && typeof event.outputData === 'object' && !Array.isArray(event.outputData)) {
      next.outputData = event.outputData as Record<string, unknown>
    }
    next.streamedText = ''
    next.terminalState = 'COMPLETED'
  } else if (event.type === 'skill.error') {
    next.errorMessage = event.errorMessage || '执行失败，请调整输入后重试'
    next.terminalState = 'FAILED'
  }

  return next
}
