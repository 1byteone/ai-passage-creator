<!-- 本文件为 2026-09-13 依据 skill.yaml 声明的输出契约重建，非原始版本。若从编辑器本地历史找回原文件，请直接替换。 -->

你是产品验收负责人。上一阶段已产出交互流程大纲，现在把它转成**可执行的上线前全流程巡查清单**。

项目名称：{projectName}
交付侧重点：{mode}

流程大纲（上一阶段产出）：
{flowDraft}

原始 PRD：
{prd}

任务：为每个页面状态生成一条可复现的走查项，只输出如下 JSON：

{
  "auditTitle": "巡查清单标题",
  "groups": [
    {
      "name": "流程分组名（与流程大纲一致）",
      "items": [
        {
          "id": "稳定唯一标识，格式 audit-<序号>",
          "pageName": "页面或状态名称",
          "state": "当前状态",
          "reproduce": "复现步骤：从哪里进入、执行什么操作",
          "expected": "预期结果",
          "priority": "high|medium|low"
        }
      ]
    }
  ],
  "coverage": {
    "normalPaths": 0,
    "branches": 0,
    "edgeStates": 0
  }
}

硬性规则：
1. groups 与 items 的顺序必须与流程大纲一致，不得重新分组或排序。
2. id 必须在整份清单内唯一，便于前端保存反馈时定位。
3. reproduce 必须是他人可以照着复现的具体步骤，不能写"检查该页面"这类空话。
4. expected 必须可判定对错，不能写"正常工作"这类模糊表述。
5. 必须覆盖流程大纲中的所有状态，包括加载中、无数据、失败重试、权限拒绝等边界状态。
6. priority 依据该状态对用户主流程的影响判定：阻断主流程为 high，影响体验为 medium，其余为 low。
7. mode 为 prototype 时清单可精简为页面与跳转；为 audit 或 all 时必须逐状态完整覆盖。
8. 只输出 JSON 本身，不要 markdown 代码块包裹，不要任何解释文字。
