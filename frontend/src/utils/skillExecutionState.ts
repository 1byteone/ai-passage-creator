export type SkillTerminalState = 'COMPLETED' | 'FAILED' | null

export interface AwaitingConfirmation {
  /** 待确认的阶段名 */
  phase: string
  /** 阶段序号 */
  phaseIndex: number
  /** 后端支持的确认动作 */
  supportedActions: Array<'approve' | 'modify'>
  /** 待用户审阅的上一阶段产出 */
  pendingOutput?: unknown
}

export interface SkillRuntimeSnapshot {
  phase: string
  phaseIndex: number
  streamedText: string
  outputData: Record<string, unknown>
  errorMessage: string
  terminalState: SkillTerminalState
  /** 非空时表示执行已暂停，等待用户确认 */
  awaiting: AwaitingConfirmation | null
}

export const createSkillRuntimeSnapshot = (): SkillRuntimeSnapshot => ({
  phase: '',
  phaseIndex: 1,
  streamedText: '',
  outputData: {},
  errorMessage: '',
  terminalState: null,
  awaiting: null,
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
    // 收到任何后续事件即退出待确认态
    awaiting: null,
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
  } else if (event.type === 'skill.awaiting_confirmation') {
    next.streamedText = ''
    next.awaiting = {
      phase: event.phase || snapshot.phase,
      phaseIndex: event.phaseIndex || snapshot.phaseIndex,
      supportedActions: (event.supportedActions as Array<'approve' | 'modify'>) || ['approve'],
      pendingOutput: event.pendingOutput,
    }
    next.terminalState = null
  } else if (event.type === 'skill.complete') {
    if (event.outputData && typeof event.outputData === 'object' && !Array.isArray(event.outputData)) {
      next.outputData = event.outputData as Record<string, unknown>
    }
    next.streamedText = ''
    next.terminalState = 'COMPLETED'
  } else if (event.type === 'skill.error') {
    next.errorMessage = event.errorMessage || '执行失败，请调整输入后重试'
    next.terminalState = 'FAILED'
  } else if (event.type === 'skill.started') {
    // 开始事件，无操作
  } else if (event.type === 'skill.progress') {
    // 空数据分片，无操作
  }

  return next
}