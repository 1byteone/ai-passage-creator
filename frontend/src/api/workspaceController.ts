// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 创建协作空间 POST /workspace/create */
export async function createWorkspace(params: API.WorkspaceCreateRequest) {
  return request<API.BaseResponseWorkspace>('/workspace/create', {
    method: 'POST',
    data: params,
  })
}

/** 我加入的空间列表 GET /workspace/list/mine */
export async function listMyWorkspaces() {
  return request<API.BaseResponseListWorkspace>('/workspace/list/mine', { method: 'GET' })
}

/** 获取空间详情 GET /workspace/{workspaceId} */
export async function getWorkspace(workspaceId: number) {
  return request<API.BaseResponseWorkspace>(`/workspace/${workspaceId}`, { method: 'GET' })
}

/** 更新空间信息 PUT /workspace/{workspaceId} */
export async function updateWorkspace(workspaceId: number, params: API.WorkspaceUpdateRequest) {
  return request<API.BaseResponseWorkspace>(`/workspace/${workspaceId}`, {
    method: 'PUT',
    data: params,
  })
}

/** 归档空间 POST /workspace/{workspaceId}/archive */
export async function archiveWorkspace(workspaceId: number) {
  return request<API.BaseResponseBoolean>(`/workspace/${workspaceId}/archive`, {
    method: 'POST',
  })
}

/** 添加空间成员 POST /workspace/{workspaceId}/members */
export async function addWorkspaceMember(workspaceId: number, params: { userId: number; role?: string }) {
  return request<API.BaseResponseBoolean>(`/workspace/${workspaceId}/members`, {
    method: 'POST',
    data: params,
  })
}

/** 移除空间成员 DELETE /workspace/{workspaceId}/members/{userId} */
export async function removeWorkspaceMember(workspaceId: number, userId: number) {
  return request<API.BaseResponseBoolean>(`/workspace/${workspaceId}/members/${userId}`, {
    method: 'DELETE',
  })
}

/** 空间成员列表 GET /workspace/{workspaceId}/members */
export async function listWorkspaceMembers(workspaceId: number) {
  return request<API.BaseResponseListWorkspaceMember>(`/workspace/${workspaceId}/members`, {
    method: 'GET',
  })
}
