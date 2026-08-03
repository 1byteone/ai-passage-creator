import request from '@/request'

export async function listSkills(options?: { [key: string]: unknown }) {
  return request<API.BaseResponseSkillSummaryList>('/skill/list', {
    method: 'GET',
    ...(options || {}),
  })
}

export async function getSkillDefinition(
  skillName: string,
  options?: { [key: string]: unknown },
) {
  return request<API.BaseResponseSkillDefinition>(`/skill/${encodeURIComponent(skillName)}/definition`, {
    method: 'GET',
    ...(options || {}),
  })
}

export async function executeSkill(
  skillName: string,
  body: API.SkillExecuteRequest,
  options?: { [key: string]: unknown },
) {
  return request<API.BaseResponseSkillExecuteResponse>(`/skill/${encodeURIComponent(skillName)}/execute`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

export async function getSkillResult(
  executionId: string,
  options?: { [key: string]: unknown },
) {
  return request<API.BaseResponseSkillResultResponse>(`/skill/${executionId}/result`, {
    method: 'GET',
    ...(options || {}),
  })
}

export async function confirmSkill(
  executionId: string,
  body: API.SkillConfirmRequest,
  options?: { [key: string]: unknown },
) {
  return request<API.BaseResponseVoid>(`/skill/${executionId}/confirm`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 分页查询 Skill 执行历史 POST /skill/executions */
export async function listSkillExecutions(
  body?: API.SkillExecutionQueryRequest,
  options?: { [key: string]: unknown },
) {
  return request<API.BaseResponsePageSkillExecutionVO>('/skill/executions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body ?? {},
    ...(options || {}),
  })
}
