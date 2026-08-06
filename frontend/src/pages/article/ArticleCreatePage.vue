<template>
  <div class="article-create-page">
    <!-- 三栏布局容器 -->
    <div class="create-layout">
      <!-- 左侧：智能体流程可视化 -->
      <aside class="sidebar-left">
        <div class="sidebar-header">
          <h3 class="sidebar-title">创作流程</h3>
          <p class="sidebar-subtitle">智能体协作可视化</p>
        </div>

        <div class="flow-timeline">
          <div
            v-for="(step, index) in agentSteps"
            :key="index"
            :class="['flow-item', {
              'active': currentStep === index,
              'completed': currentStep > index,
              'pending': currentStep < index
            }]"
          >
            <div class="flow-indicator">
              <LoadingOutlined v-if="currentStep === index && isCreating" class="spin-icon" />
              <CheckCircleOutlined v-else-if="currentStep > index" />
              <span v-else class="step-number">{{ index + 1 }}</span>
            </div>
            <div class="flow-content">
              <div class="flow-title">{{ step.title }}</div>
              <div class="flow-desc">{{ step.description }}</div>
              <div v-if="currentStep === index && isCreating" class="flow-status">
                <span class="status-dot"></span>
                执行中...
              </div>
            </div>
          </div>
        </div>

      </aside>

      <!-- 左侧栏配图预览（创作流程下方） -->
      <aside v-if="currentStep >= 3 && totalImages > 0" class="image-preview-sidebar">
        <div class="preview-header">
          <span class="preview-title">配图预览</span>
          <span class="preview-count">{{ imageCount }}/{{ totalImages }}</span>
        </div>
        <div class="preview-list">
          <div
            v-for="(item, index) in imageItems"
            :key="index"
            :class="['preview-card', {
              'card-complete': index < imageCount,
              'card-rendering': false,
            }]"
          >
            <div class="preview-image-wrap">
              <img
                v-if="item.url"
                :src="item.url"
                :class="['preview-image', { loaded: true }]"
                alt="配图"
              />
              <div v-else class="preview-placeholder">
                <PictureOutlined class="placeholder-icon" />
              </div>
            </div>
            <div class="preview-info">
              <span class="preview-label">{{ item.title }}</span>
              <span class="preview-tag">{{ item.type === 'cover' ? '封面' : '配图' }}</span>
            </div>
          </div>
          <!-- 待生成占位 -->
          <div
            v-for="n in Math.max(0, totalImages - imageItems.length)"
            :key="'pending-' + n"
            class="preview-card card-pending"
          >
            <div class="preview-image-wrap">
              <div class="preview-placeholder">
                <PictureOutlined class="placeholder-icon" />
              </div>
            </div>
            <div class="preview-info">
              <span class="preview-label pending">等待配图生成</span>
            </div>
          </div>
        </div>
      </aside>

      <section class="mobile-support-panels" aria-label="创作辅助信息" aria-live="polite">
        <div class="mobile-progress-card">
          <div class="mobile-progress-header">
            <div>
              <span class="mobile-kicker">当前流程</span>
              <strong>{{ mobilePhaseText }}</strong>
            </div>
            <span class="mobile-step-count">{{ mobileStepPosition }}/{{ agentSteps.length }}</span>
          </div>
          <a-progress
            :percent="mobileProgressPercent"
            :show-info="false"
            size="small"
            class="mobile-progress"
          />
          <p>{{ currentAgentStep.description }}</p>
        </div>

        <div class="mobile-context-grid">
          <div class="mobile-context-item">
            <span>配额</span>
            <strong v-if="isAdmin">无限次</strong>
            <strong v-else-if="isVip">VIP 无限次</strong>
            <strong v-else>{{ quota }} 次</strong>
          </div>
          <div v-if="topic" class="mobile-context-item topic">
            <span>当前选题</span>
            <strong>{{ topic }}</strong>
          </div>
        </div>

        <div v-if="mobileLatestLogs.length" class="mobile-log-card">
          <div class="mobile-log-title">最近进度</div>
          <div
            v-for="log in mobileLatestLogs"
            :key="`${log.timestamp}-${log.message}`"
            :class="['mobile-log-entry', log.level]"
          >
            <span>{{ formatLogTime(log.timestamp) }}</span>
            <p>{{ log.message }}</p>
          </div>
        </div>
      </section>

      <!-- 中间：主内容区 -->
      <section ref="mainContentRef" class="main-content">
        <!-- 阶段切换（带过渡动画） -->
        <Transition name="fade-slide" mode="out-in">
          <div :key="currentPhase" class="phase-wrapper">
            <!-- 输入状态 -->
            <div v-if="currentPhase === 'INPUT'" class="input-state">
              <div class="input-card">
                <div class="input-header">
                  <div class="input-kicker">文章生成工作台</div>
                  <h1 class="input-title">创作新文章</h1>
                  <p class="input-subtitle">先确认选题，再选择风格与配图方式，一屏完成创作配置。</p>
                </div>

                <div class="input-area">
                  <section class="topic-section" aria-labelledby="topic-field-title">
                    <div class="field-header">
                      <div>
                        <h2 id="topic-field-title" class="field-title">选题内容</h2>
                        <p id="topic-field-description" class="field-description">写清楚主题、人群或角度，生成结果会更稳定。</p>
                      </div>
                      <span class="field-badge">必填</span>
                    </div>
                    <a-textarea
                      id="article-topic-input"
                      v-model:value="topic"
                      placeholder="请输入您想创作的文章选题，例如：2026年AI如何改变职场"
                      aria-labelledby="topic-field-title"
                      aria-describedby="topic-field-description"
                      :rows="6"
                      :maxlength="500"
                      show-count
                      class="topic-textarea"
                    />
                  </section>

                  <div class="settings-grid" aria-label="创作设置">
                    <!-- 文章风格选择 -->
                    <section class="style-section setting-panel">
                      <div class="section-header">
                        <div>
                          <span class="section-title">文章风格</span>
                          <span class="section-tip">不选择则使用默认风格</span>
                        </div>
                      </div>
                      <a-radio-group v-model:value="selectedStyle" class="style-group">
                        <a-radio value="">默认</a-radio>
                        <a-radio value="tech">科技风格</a-radio>
                        <a-radio value="emotional">情感风格</a-radio>
                        <a-radio value="educational">教育风格</a-radio>
                        <a-radio value="humorous">轻松幽默</a-radio>
                      </a-radio-group>
                    </section>

                    <!-- 配图方式选择 -->
                    <section class="image-methods-section setting-panel">
                      <div class="section-header">
                        <div>
                          <span class="section-title">配图方式</span>
                          <span class="section-tip">不选择则支持所有方式</span>
                        </div>
                      </div>
                      <a-checkbox-group v-model:value="selectedImageMethods" class="methods-group">
                        <a-checkbox value="PEXELS">Pexels</a-checkbox>
                        <a-tooltip :title="isVip ? '' : '仅限 VIP 会员'">
                          <a-checkbox value="NANO_BANANA" :disabled="!isVip">
                            Nano Banana
                            <CrownOutlined v-if="!isVip" class="vip-icon" />
                          </a-checkbox>
                        </a-tooltip>
                        <a-checkbox value="MERMAID">Mermaid</a-checkbox>
                        <a-checkbox value="ICONIFY">Iconify</a-checkbox>
                        <a-checkbox value="EMOJI_PACK">表情包</a-checkbox>
                        <a-tooltip :title="isVip ? '' : '仅限 VIP 会员'">
                          <a-checkbox value="SVG_DIAGRAM" :disabled="!isVip">
                            SVG
                            <CrownOutlined v-if="!isVip" class="vip-icon" />
                          </a-checkbox>
                        </a-tooltip>
                      </a-checkbox-group>
                      <div v-if="!isVip" class="vip-notice">
                        <CrownOutlined />
                        <span>AI 生图和 SVG 图表为 VIP 专属功能，</span>
                        <RouterLink to="/vip" class="upgrade-link">立即升级</RouterLink>
                      </div>
                    </section>

                    <!-- 插画角色风格选择 -->
                    <section class="character-style-section setting-panel">
                      <div class="section-header">
                        <div>
                          <span class="section-title">插画角色风格</span>
                          <span class="section-tip">选择文章的插画视觉风格，仅卡片风格为插画时生效</span>
                        </div>
                      </div>
                      <CharacterStyleSelector
                        v-model:value="selectedCharacterStyle"
                        :disabled="isCreating"
                      />
                    </section>
                  </div>

                  <div class="create-actions">
                    <div class="create-action-copy">
                      <span class="action-label">准备开始</span>
                      <strong v-if="isAdmin">管理员无限创作</strong>
                      <strong v-else-if="isVip">VIP 无限创作</strong>
                      <strong v-else>剩余 {{ quota }} 次创作配额</strong>
                    </div>

                    <a-button
                      type="primary"
                      size="large"
                      :loading="isCreating"
                      :disabled="!topic.trim() || !hasQuota"
                      @click="startCreate"
                      class="create-btn"
                    >
                      <template #icon>
                        <RocketOutlined />
                      </template>
                      开始创作
                    </a-button>
                  </div>

                  <div v-if="!hasQuota" class="quota-warning">
                    <WarningOutlined />
                    <span>配额已用完，无法创建文章</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 标题生成中 -->
            <div v-else-if="currentPhase === 'TITLE_GENERATING'" class="loading-stage">
              <a-spin size="large" />
              <h3>AI 正在生成标题方案...</h3>
              <p>稍等片刻，即将为您呈现多个精彩标题</p>
            </div>

            <!-- 标题选择阶段 -->
            <TitleSelectingStage
              v-else-if="currentPhase === 'TITLE_SELECTING'"
              :title-options="titleOptions"
              :loading="confirmLoading"
              @confirm="handleConfirmTitle"
            />

            <!-- 大纲生成中（流式展示） -->
            <div v-else-if="currentPhase === 'OUTLINE_GENERATING'" class="outline-generating-state">
              <!-- 标题预览 -->
              <div v-if="article.mainTitle" class="preview-header">
                <h1 class="article-title">{{ article.mainTitle }}</h1>
                <p class="article-subtitle">{{ article.subTitle }}</p>
              </div>

              <!-- 大纲流式展示 -->
              <div class="outline-preview">
                <div class="section-label">
                  <BulbOutlined />
                  <span>AI 正在规划文章大纲</span>
                  <span class="typing-cursor">|</span>
                </div>
                <div v-if="parsedOutline.length > 0" class="outline-list">
                  <div
                    v-for="item in parsedOutline"
                    :key="item.section"
                    class="outline-item fade-in"
                  >
                    <div class="outline-title">{{ item.section }}. {{ item.title }}</div>
                    <ul class="outline-points">
                      <li v-for="(point, idx) in item.points" :key="idx">{{ point }}</li>
                    </ul>
                  </div>
                </div>
                <div v-else class="outline-loading">
                  <a-spin />
                  <span>正在构建文章结构...</span>
                </div>
              </div>
            </div>

            <!-- 大纲编辑阶段 -->
            <OutlineEditingStage
              v-else-if="currentPhase === 'OUTLINE_EDITING'"
              :outline="outline"
              :loading="confirmLoading"
              :task-id="taskId"
              @confirm="handleConfirmOutline"
            />

            <!-- 正文生成阶段 -->
            <div v-else-if="currentPhase === 'CONTENT_GENERATING'" class="creating-state">
              <!-- 标题预览 -->
              <div v-if="article.mainTitle" class="preview-header">
                <h1 class="article-title">{{ article.mainTitle }}</h1>
                <p class="article-subtitle">{{ article.subTitle }}</p>
              </div>

              <!-- 大纲预览（流式解析展示） -->
              <div v-if="outlineRaw" class="outline-preview">
                <div class="section-label">
                  <BulbOutlined />
                  <span>文章大纲</span>
                  <span v-if="isOutlineStreaming" class="typing-cursor">|</span>
                </div>
                <div class="outline-list">
                  <div
                    v-for="item in parsedOutline"
                    :key="item.section"
                    class="outline-item"
                  >
                    <div class="outline-title">{{ item.section }}. {{ item.title }}</div>
                    <ul class="outline-points">
                      <li v-for="(point, idx) in item.points" :key="idx">{{ point }}</li>
                    </ul>
                  </div>
                </div>
              </div>

              <!-- 正文预览（流式输出） -->
              <div v-if="article.content" class="content-preview">
                <div v-html="markdownToHtml(article.content)" class="markdown-body"></div>
                <span v-if="isStreaming" class="typing-cursor">|</span>
              </div>

              <!-- 配图生成动画（正文完成即显示；保持挂载至 step5 以呈现 AGENT5 成功态，COMPLETED 后随阶段切换移除） -->
              <ImageGenerationAnimation
                v-if="currentStep >= 3 && currentStep <= 5 && totalImages > 0"
                :total="totalImages"
                :done-count="imageCount"
                :image-urls="imageUrls"
                :phase="imagePhase"
                :completed="allImagesDone"
              />

              <!-- 加载占位 -->
              <div v-if="currentStep === 0 && !article.mainTitle" class="loading-placeholder">
                <a-spin size="large" />
                <p>AI 正在构思标题...</p>
              </div>
            </div>

            <!-- 创作完成 -->
            <CompletedState
              v-else-if="currentPhase === 'COMPLETED'"
              :article="article"
            />
          </div>
        </Transition>

        <!-- 质量门报告（COMPLETED 阶段，在 Transition 外显示） -->
        <div v-if="qualityReport && currentPhase === 'COMPLETED'" class="quality-report-panel">
          <a-alert
            :type="qualityReport.passed ? 'success' : 'warning'"
            :show-icon="true"
            :closable="false"
          >
            <template #message>
              <span class="quality-title">
                创作质量检测 {{ qualityReport.passed ? '✓ 通过' : '✗ 未通过' }}
                <span class="quality-score">{{ qualityReport.score }} 分</span>
              </span>
              <template v-if="qualityReport.detoxed">
                <a-tag color="processing" class="quality-tag">已自动降AI味改写</a-tag>
              </template>
              <template v-if="qualityReport.viralScore != null">
                <a-tag color="blue" class="quality-tag">爆款分 {{ qualityReport.viralScore }}</a-tag>
              </template>
            </template>
            <template v-if="qualityReport.violations.length > 0" #description>
              <div class="quality-violations">
                <span class="violations-label">AI 味违规项 ({{ qualityReport.violations.length }})：</span>
                <ul>
                  <li v-for="(v, idx) in qualityReport.violations" :key="idx">{{ v }}</li>
                </ul>
              </div>
            </template>
          </a-alert>
        </div>
      </section>

      <!-- 右侧：辅助面板 -->
      <aside class="sidebar-right">
        <!-- 配额信息 -->
        <div v-if="currentPhase === 'INPUT'" class="panel-section quota-section">
          <h4 class="panel-title">
            <CrownOutlined />
            创作配额
          </h4>
          <div v-if="isAdmin" class="quota-admin">
            <span class="quota-badge admin">管理员</span>
            <span class="quota-text">无限次</span>
          </div>
          <div v-else-if="isVip" class="quota-admin">
            <span class="quota-badge vip">VIP 会员</span>
            <span class="quota-text">无限次</span>
          </div>
          <div v-else class="quota-info">
            <div class="quota-display">
              <span class="quota-number" :class="{ 'low': quota <= 1, 'empty': quota === 0 }">{{ quota }}</span>
              <span class="quota-unit">次</span>
            </div>
            <div class="quota-label">剩余可用</div>
            <a-progress
              :percent="(quota / 5) * 100"
              :show-info="false"
              :stroke-color="quota <= 1 ? '#ff4d4f' : '#22C55E'"
              size="small"
              class="quota-progress"
            />
          </div>
        </div>

        <!-- 推荐选题（平台热门 + 用户历史 + AI 生成） -->
        <div v-if="currentPhase === 'INPUT'" class="panel-section">
          <div class="panel-header-row">
            <h4 class="panel-title">
              <BulbOutlined />
              推荐选题
            </h4>
            <a-button
              size="small"
              type="text"
              :loading="recommendRefreshing"
              class="refresh-btn"
              @click="loadRecommendedTopics(true)"
            >
              <template #icon><ReloadOutlined /></template>
              换一批
            </a-button>
          </div>
          <div v-if="recommendLoading && exampleTopics.length === 0" class="hot-tags-loading">
            <a-spin size="small" />
            <span>选题加载中...</span>
          </div>
          <div v-else class="hot-tags">
            <span
              v-for="example in exampleTopics"
              :key="example"
              :class="['hot-tag', { 'ai-tag': isAiTopic(example) }]"
              @click="topic = example"
            >
              {{ example }}
              <em v-if="isAiTopic(example)" class="ai-badge">热点</em>
            </span>
          </div>
        </div>

        <!-- 历史参考 -->
        <div v-if="currentPhase === 'INPUT'" class="panel-section">
          <h4 class="panel-title">
            <HistoryOutlined />
            历史参考
          </h4>
          <RagHitsPanel
            :hits="ragHits"
            :loading="ragLoading"
            :no-input="!topic.trim()"
            empty-text="暂无相关历史，换个选题试试"
            @select="handleSelectReference"
          />
        </div>

        <!-- 创作技巧 -->
        <div v-if="currentPhase === 'INPUT'" class="panel-section">
          <h4 class="panel-title">
            <StarOutlined />
            爆款技巧
          </h4>
          <div class="tips-list">
            <div class="tip-item">
              <div class="tip-icon">1</div>
              <div class="tip-content">
                <div class="tip-title">抓住痛点</div>
                <div class="tip-desc">直击用户最关心的问题</div>
              </div>
            </div>
            <div class="tip-item">
              <div class="tip-icon">2</div>
              <div class="tip-content">
                <div class="tip-title">制造悬念</div>
                <div class="tip-desc">让读者产生好奇心</div>
              </div>
            </div>
            <div class="tip-item">
              <div class="tip-icon">3</div>
              <div class="tip-content">
                <div class="tip-title">数字吸引</div>
                <div class="tip-desc">使用具体数据增加说服力</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 创作进行中的提示（所有创作阶段） -->
        <div v-if="isCreating || currentPhase === 'TITLE_SELECTING' || currentPhase === 'OUTLINE_EDITING'" class="panel-section">
          <h4 class="panel-title">
            <ClockCircleOutlined />
            创作进度
          </h4>
          <div class="progress-info">
            <div class="progress-step">
              <span class="step-label">当前步骤</span>
              <span class="step-value">{{ agentSteps[currentStep]?.title }}</span>
            </div>
            <div class="progress-step">
              <span class="step-label">已完成</span>
              <span class="step-value">{{ currentStep }}/{{ agentSteps.length }}</span>
            </div>
          </div>
          <div v-if="isCreating" class="progress-tip">
            <InfoCircleOutlined />
            <span>AI 正在努力创作中，请耐心等待...</span>
          </div>
          <div v-else class="progress-tip waiting">
            <InfoCircleOutlined />
            <span>等待您的确认...</span>
          </div>
        </div>

        <!-- 实时执行日志 -->
        <div v-if="realtimeLogs.length > 0" class="panel-section realtime-logs-section">
          <h4 class="panel-title">
            <FileTextOutlined />
            执行日志
          </h4>
          <div class="logs-container">
            <div
              v-for="(log, index) in realtimeLogs"
              :key="index"
              :class="['log-entry', log.level]"
            >
              <span class="log-time">{{ formatLogTime(log.timestamp) }}</span>
              <span class="log-message">{{ log.message }}</span>
            </div>
          </div>
        </div>

        <!-- 当前选题提示 -->
        <div v-if="currentPhase !== 'INPUT' && currentPhase !== 'COMPLETED' && topic" class="panel-section">
          <h4 class="panel-title">
            <BulbOutlined />
            创作选题
          </h4>
          <div class="topic-display">
            <p>{{ topic }}</p>
          </div>
        </div>

        <!-- 阶段提示 -->
        <div v-if="currentPhase === 'TITLE_GENERATING'" class="panel-section tips-section">
          <h4 class="panel-title">
            <StarOutlined />
            提示
          </h4>
          <div class="tips-list">
            <div class="tip-item">
              <div class="tip-icon">1</div>
              <div class="tip-content">
                <div class="tip-desc">AI 正在分析您的选题，生成多个吸引眼球的标题方案</div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="currentPhase === 'TITLE_SELECTING'" class="panel-section tips-section">
          <h4 class="panel-title">
            <StarOutlined />
            提示
          </h4>
          <div class="tips-list">
            <div class="tip-item">
              <div class="tip-icon">2</div>
              <div class="tip-content">
                <div class="tip-desc">选择最符合您期望的标题，或添加补充描述让 AI 更好地理解您的需求</div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="currentPhase === 'OUTLINE_GENERATING'" class="panel-section tips-section">
          <h4 class="panel-title">
            <StarOutlined />
            提示
          </h4>
          <div class="tips-list">
            <div class="tip-item">
              <div class="tip-icon">3</div>
              <div class="tip-content">
                <div class="tip-desc">AI 正在为您规划文章结构，构建清晰的章节脉络</div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="currentPhase === 'OUTLINE_EDITING'" class="panel-section tips-section">
          <h4 class="panel-title">
            <StarOutlined />
            编辑技巧
          </h4>
          <div class="tips-list">
            <div class="tip-item">
              <div class="tip-icon">1</div>
              <div class="tip-content">
                <div class="tip-title">拖动排序</div>
                <div class="tip-desc">点击章节左侧拖动图标可调整章节顺序</div>
              </div>
            </div>
            <div class="tip-item">
              <div class="tip-icon">2</div>
              <div class="tip-content">
                <div class="tip-title">AI 助手</div>
                <div class="tip-desc">使用 AI 助手快速修改大纲结构</div>
              </div>
            </div>
            <div class="tip-item">
              <div class="tip-icon">3</div>
              <div class="tip-content">
                <div class="tip-title">添加章节</div>
                <div class="tip-desc">根据需要添加或删除章节和要点</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div v-if="currentPhase === 'COMPLETED'" class="panel-section">
          <h4 class="panel-title">
            <ThunderboltOutlined />
            快捷操作
          </h4>
          <div class="action-list">
            <a-button block @click="copyContent" class="action-btn">
              <CopyOutlined />
              复制全文
            </a-button>
            <a-button block @click="viewArticle" class="action-btn">
              <EyeOutlined />
              查看详情
            </a-button>
            <a-button block type="primary" @click="resetCreate" class="action-btn primary">
              <RedoOutlined />
              再创作一篇
            </a-button>
          </div>
        </div>

        <!-- 完成后的统计 -->
        <div v-if="currentPhase === 'COMPLETED'" class="panel-section stats-section">
          <h4 class="panel-title">
            <BarChartOutlined />
            文章统计
          </h4>
          <div class="stats-grid">
            <div class="stat-item">
              <div class="stat-value">{{ (article.fullContent || article.content || '').length }}</div>
              <div class="stat-label">字数</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ article.images?.length || 0 }}</div>
              <div class="stat-label">配图</div>
            </div>
          </div>
        </div>

        <!-- 底部帮助链接 -->
        <div class="panel-footer">
          <a class="help-link">
            <QuestionCircleOutlined />
            使用帮助
          </a>
          <a class="help-link">
            <MessageOutlined />
            反馈建议
          </a>
        </div>
      </aside>
    </div>

    <!-- 错误提示 -->
    <a-modal
      v-model:open="errorVisible"
      title="创作失败"
      @ok="errorVisible = false"
    >
      <p>{{ errorMessage }}</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount, onMounted, nextTick, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  Alert as AAlert,
  Checkbox as ACheckbox,
  CheckboxGroup as ACheckboxGroup,
  Modal as AModal,
  Progress as AProgress,
  Radio as ARadio,
  RadioGroup as ARadioGroup,
  Tooltip as ATooltip,
  message,
} from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  RocketOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  EyeOutlined,
  RedoOutlined,
  ThunderboltOutlined,
  BulbOutlined,
  StarOutlined,
  ClockCircleOutlined,
  InfoCircleOutlined,
  BarChartOutlined,
  QuestionCircleOutlined,
  MessageOutlined,
  WarningOutlined,
  CrownOutlined,
  FileTextOutlined,
  HistoryOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue'
import { createArticle, confirmTitle, confirmOutline } from '@/api/articleController'
import RagHitsPanel from '@/components/RagHitsPanel.vue'
import { useRagSearch } from '@/composables/useRagSearch'
import { getRecommendedTopics } from '@/api/topicRecommendController'
import { connectSSE, closeSSE, type SSEMessage, type SSEConnection } from '@/utils/sse'
import { isAdmin as checkIsAdmin, isVip as checkIsVip, hasQuota as checkHasQuota } from '@/utils/permission'
import { markdownToHtml as safeMarkdownToHtml } from '@/utils/markdown'
import TitleSelectingStage from './components/TitleSelectingStage.vue'
import OutlineEditingStage from './components/OutlineEditingStage.vue'
import CompletedState from './components/CompletedState.vue'
import ImageGenerationAnimation from './components/ImageGenerationAnimation.vue'
import CharacterStyleSelector from './components/CharacterStyleSelector.vue'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()

// 历史参考：选题实时 RAG 检索（防抖 500ms）
const { hits: ragHits, loading: ragLoading, search: ragSearch, clear: ragClear } = useRagSearch({ debounceMs: 500 })

// 配额相关计算属性
const isAdmin = computed(() => checkIsAdmin(loginUserStore.loginUser))
const isVip = computed(() => checkIsVip(loginUserStore.loginUser))
const quota = computed(() => loginUserStore.loginUser.quota ?? 0)
const hasQuota = computed(() => checkHasQuota(loginUserStore.loginUser))

// 智能体步骤（对应后端 6 个步骤）
const agentSteps = [
  { title: '生成标题', description: 'AI 分析选题，生成吸睛标题' },
  { title: '规划大纲', description: '构建文章结构，理清脉络' },
  { title: '撰写正文', description: '流式生成高质量文章内容' },
  { title: '分析配图', description: '智能分析配图需求和位置' },
  { title: '生成配图', description: '自动匹配高清无版权图片' },
  { title: '图文合成', description: '将配图插入正文，完美呈现' },
]

// 推荐选题（动态：平台热门 + 用户历史 + AI 生成）
const exampleTopics = ref<string[]>([])
// 推荐选题原始项（含来源标记）
const recommendItems = ref<API.TopicRecommendItem[]>([])
// 是否有 AI 生成的新鲜选题
const hasAiTopics = ref(false)
// 推荐选题加载中 / 换一批 loading
const recommendLoading = ref(false)
const recommendRefreshing = ref(false)

// 静态兜底选题（接口失败时避免空白）
const FALLBACK_TOPICS = [
  '2026年AI如何改变职场',
  '程序员如何提升竞争力',
  '远程办公的利与弊',
  '如何培养深度思考',
  '新能源汽车趋势',
  '健康饮食指南',
]

/** 加载推荐选题（refresh=true 触发 AI 动态生成） */
const loadRecommendedTopics = async (refresh = false) => {
  const loadingRef = refresh ? recommendRefreshing : recommendLoading
  loadingRef.value = true
  try {
    const res = await getRecommendedTopics({ refresh })
    const data = res.data?.data
    const items = data?.items ?? []
    if (items.length > 0) {
      recommendItems.value = items
      exampleTopics.value = items.map((item) => item.text ?? '')
      hasAiTopics.value = data?.hasAi ?? false
    }
  } catch (error) {
    // 接口失败降级为静态兜底，不阻断创作
    console.warn('推荐选题加载失败，使用兜底选题:', error)
    if (recommendItems.value.length === 0) {
      recommendItems.value = FALLBACK_TOPICS.map((text) => ({ text, source: 'hot' }))
      exampleTopics.value = [...FALLBACK_TOPICS]
    }
  } finally {
    loadingRef.value = false
  }
}

// 判断某选题是否 AI 生成（用于徽标）
const isAiTopic = (text: string) =>
  recommendItems.value.find((item) => item.text === text)?.source === 'ai'

// 阶段状态
const currentPhase = ref<string>('INPUT')  // INPUT, TITLE_SELECTING, OUTLINE_EDITING, CONTENT_GENERATING, COMPLETED

// 状态
const topic = ref('')
const selectedStyle = ref('')  // 选中的文章风格（空字符串表示默认）
const selectedImageMethods = ref<string[]>([])  // 选中的配图方式（空数组表示全部）
const selectedCharacterStyle = ref('')  // 选中的插画子风格（空字符串 = 未选）
const isCreating = ref(false)
const isCompleted = ref(false)
const isStreaming = ref(false)
const isOutlineStreaming = ref(false)
const currentStep = ref(0)
const taskId = ref('')
const errorVisible = ref(false)
const errorMessage = ref('')
const confirmLoading = ref(false)

// 实时日志
interface RealtimeLog {
  timestamp: number
  level: string
  message: string
}

interface QualityReport {
  score: number
  passed: boolean
  detoxed: boolean
  violations: string[]
  viralScore?: number
}
const qualityReport = ref<QualityReport | null>(null)
const realtimeLogs = ref<RealtimeLog[]>([])

const currentAgentStep = computed(() => {
  const index = Math.min(Math.max(currentStep.value, 0), agentSteps.length - 1)
  return agentSteps[index]
})

const mobileStepPosition = computed(() => {
  if (currentPhase.value === 'COMPLETED') return agentSteps.length
  return Math.min(Math.max(currentStep.value + 1, 1), agentSteps.length)
})

const mobileProgressPercent = computed(() =>
  Math.round((mobileStepPosition.value / agentSteps.length) * 100),
)

const mobileLatestLogs = computed(() => realtimeLogs.value.slice(-3).reverse())

const mobilePhaseText = computed(() => {
  const phaseLabels: Record<string, string> = {
    INPUT: '准备创作',
    TITLE_GENERATING: '生成标题',
    TITLE_SELECTING: '选择标题',
    OUTLINE_GENERATING: '规划大纲',
    OUTLINE_EDITING: '编辑大纲',
    CONTENT_GENERATING: '生成正文',
    COMPLETED: '创作完成',
  }
  return phaseLabels[currentPhase.value] || currentAgentStep.value.title
})

// 标题方案
const titleOptions = ref<Array<{mainTitle: string, subTitle: string}>>([])

// 大纲数据
const outline = ref<Array<{section: number, title: string, points: string[]}>>([])

// 大纲数据（流式）
const outlineRaw = ref('')

// 大纲项类型
interface OutlineItem {
  title: string
  points: string[]
  section: number
}

// 解析大纲 JSON（格式为 { "sections": [...] }）
const parsedOutline = computed<OutlineItem[]>(() => {
  if (!outlineRaw.value) return []

  const str = outlineRaw.value.trim()

  // 尝试解析完整的 JSON
  try {
    const parsed = JSON.parse(str)
    if (parsed && Array.isArray(parsed.sections)) {
      return parsed.sections
    }
    return []
  } catch {
    // JSON 不完整时，尝试解析已完成的部分
    try {
      // 找到最后一个完整的 section 对象 }
      // 格式: { "sections": [ {...}, {...} ] }
      const sectionsMatch = str.match(/"sections"\s*:\s*\[/)
      if (!sectionsMatch) return []

      const sectionsStart = str.indexOf('[', sectionsMatch.index)
      if (sectionsStart === -1) return []

      // 从 sections 数组开始，找到最后一个完整的 }
      const afterStart = str.substring(sectionsStart)
      const lastBrace = afterStart.lastIndexOf('}')

      if (lastBrace > 0) {
        const partialArray = afterStart.substring(0, lastBrace + 1) + ']'
        const parsed = JSON.parse(partialArray)
        if (Array.isArray(parsed)) {
          return parsed
        }
      }
      return []
    } catch {
      return []
    }
  }
})

// 内容区域引用（用于自动滚动）
const mainContentRef = ref<HTMLElement | null>(null)

// 配图进度
const imageCount = ref(0)
const imageUrls = ref<string[]>([])
const totalImages = ref(5)

// 配图列表（含标题说明，用于左侧栏预览）
interface ImagePreviewItem {
  url: string
  title: string
  type: string // 'cover' | 'content'
}
const imageItems = ref<ImagePreviewItem[]>([])

// 配图阶段状态（驱动动画组件）
const imagePhase = ref<'analyzing' | 'generating' | 'done'>('analyzing')
const allImagesDone = ref(false)

// 文章数据
const article = ref<Partial<API.ArticleVO>>({
  mainTitle: '',
  subTitle: '',
  content: '',
  fullContent: '',
  images: [],
})

let eventSource: SSEConnection | null = null

// Markdown 转 HTML（经 DOMPurify 清洗，防 XSS）
const markdownToHtml = (markdown: string | undefined) => {
  return safeMarkdownToHtml(markdown || '')
}

// 自动滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (mainContentRef.value) {
      mainContentRef.value.scrollTop = mainContentRef.value.scrollHeight
    }
  })
}

// 开始创作
const startCreate = async () => {
  if (!topic.value.trim()) {
    message.warning('请输入选题')
    return
  }

  if (!hasQuota.value) {
    message.error('配额不足，无法创建文章')
    return
  }

  isCreating.value = true
  currentStep.value = 0
  realtimeLogs.value = []
  // 重置上次运行状态，防止 dirty state 复用
  article.value.content = ''
  article.value.fullContent = ''
  outlineRaw.value = ''
  titleOptions.value = []
  isStreaming.value = false
  isOutlineStreaming.value = false
  // 重置配图动画态，防止 ERROR 后重试沿用上次的计数/阶段（dirty state 复用）
  imageCount.value = 0
  imageUrls.value = []
  imageItems.value = []
  imagePhase.value = 'analyzing'
  allImagesDone.value = false
  totalImages.value = 5
  addLog('开始创建文章任务...', 'info')

  try {
    // 创建任务
    const res = await createArticle({
      topic: topic.value,
      style: selectedStyle.value || undefined,
      enabledImageMethods: selectedImageMethods.value.length > 0 ? selectedImageMethods.value : undefined,
      characterStyle: selectedCharacterStyle.value || undefined,
    })
    const newTaskId = res.data.data
    if (!newTaskId) {
      throw new Error('创建任务失败：未返回任务ID')
    }
    taskId.value = newTaskId
    addLog(`任务创建成功，ID: ${newTaskId}`, 'success')

    // 刷新用户信息（更新配额）
    await loginUserStore.fetchLoginUser()

    // 建立 SSE 连接
    addLog('已建立实时连接，开始生成...', 'info')
    closeSSE(eventSource) // 关闭旧连接，防止残留消息污染新任务
    eventSource = connectSSE(taskId.value, {
      onMessage: handleSSEMessage,
      onError: handleSSEError,
      onComplete: handleSSEComplete,
    })
  } catch (error) {
    const err = error as Error
    message.error(err.message || '创建任务失败')
    isCreating.value = false
  }
}

// 添加日志
const addLog = (message: string, level: string = 'info') => {
  realtimeLogs.value.push({
    timestamp: Date.now(),
    level,
    message
  })
  // 限制日志数量，最多保留 50 条
  if (realtimeLogs.value.length > 50) {
    realtimeLogs.value.shift()
  }
}

// 格式化日志时间
const formatLogTime = (timestamp: number) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}

// 处理 SSE 消息
const handleSSEMessage = (msg: SSEMessage) => {
  console.log('SSE消息:', msg)

  switch (msg.type) {
    case 'AGENT1_COMPLETE':
      // 智能体1完成，进入标题生成阶段（显示加载）
      currentPhase.value = 'TITLE_GENERATING'
      currentStep.value = 1
      addLog('智能体1：标题方案生成完成', 'success')
      break

    case 'TITLES_GENERATED':
      // 标题方案生成完成，切换到选择标题阶段
      currentPhase.value = 'TITLE_SELECTING'
      titleOptions.value = msg.titleOptions || []
      isCreating.value = false
      addLog(`生成了 ${msg.titleOptions?.length || 0} 个标题方案`, 'success')
      break

    case 'AGENT2_STREAMING':
      // 大纲流式输出（显示生成中状态）
      currentPhase.value = 'OUTLINE_GENERATING'
      isOutlineStreaming.value = true
      outlineRaw.value += msg.content || ''
      scrollToBottom()
      break

    case 'OUTLINE_GENERATED':
      // 大纲生成完成，切换到编辑大纲阶段
      currentPhase.value = 'OUTLINE_EDITING'
      outline.value = msg.outline || []
      isCreating.value = false
      isOutlineStreaming.value = false
      addLog('大纲生成完成，等待确认', 'success')
      // 保持在步骤1（规划大纲），用户编辑大纲时仍处于此阶段
      break

    case 'AGENT2_COMPLETE':
      // 大纲完成（内部处理，已在 OUTLINE_GENERATED 中切换阶段）
      // 不改变 currentStep，保持在步骤1，等用户确认大纲后才进入步骤2
      break

    case 'AGENT3_STREAMING':
      // 正文流式输出，进入步骤2（撰写正文）
      currentPhase.value = 'CONTENT_GENERATING'
      currentStep.value = 2
      isStreaming.value = true
      article.value.content += msg.content || ''
      scrollToBottom()
      break

    case 'AGENT3_COMPLETE':
      // 正文完成，进入配图分析步骤
      isStreaming.value = false
      currentStep.value = 3
      imagePhase.value = 'analyzing'
      allImagesDone.value = false
      addLog('正文生成完成', 'success')
      break

    case 'AGENT4_COMPLETE':
      // 配图分析完成，进入配图生成步骤
      currentStep.value = 4
      // 空数组表示无配图文章，此时 totalImages 必须为 0 以隐藏动画；缺失时兜底默认 5
      totalImages.value = Array.isArray(msg.imageRequirements) ? msg.imageRequirements.length : 5
      imagePhase.value = 'generating'
      addLog(`配图需求分析完成，共 ${totalImages.value} 张`, 'success')
      break

    case 'IMAGE_COMPLETE':
      // 单张配图完成
      imageCount.value++
      if (msg.image?.url) {
        imageUrls.value.push(msg.image.url)
        imageItems.value.push({
          url: msg.image.url,
          title: msg.image.sectionTitle || msg.image.keywords || msg.image.description || `配图 ${imageCount.value}`,
          type: imageCount.value === 1 ? 'cover' : 'content',
        })
      }
      addLog(`配图生成中 ${imageCount.value}/${totalImages.value}`, 'info')
      break

    case 'AGENT5_COMPLETE':
      // 所有配图完成，进入图文合成步骤
      currentStep.value = 5
      article.value.images = msg.images
      allImagesDone.value = true
      imagePhase.value = 'done'
      addLog('所有配图生成完成', 'success')
      break

    case 'MERGE_COMPLETE':
      // 图文合成完成
      article.value.fullContent = msg.fullContent
      scrollToBottom()
      addLog('图文合成完成', 'success')
      break

    case 'ALL_COMPLETE':
      // 全部完成
      currentPhase.value = 'COMPLETED'
      currentStep.value = 6
      isCompleted.value = true
      message.success('文章创作完成!')
      addLog('文章创作完成', 'success')
      break

    case 'QUALITY_CHECKED':
      // 质量门报告（ALL_COMPLETE 之前到达）
      qualityReport.value = {
        score: msg.score ?? 100,
        passed: msg.passed ?? true,
        detoxed: msg.detoxed ?? false,
        violations: msg.violations ?? [],
        viralScore: msg.viralScore,
      }
      const qr = qualityReport.value!
      addLog(
        `质量检测: ${qr.score} 分${qr.passed ? ' ✓通过' : ' ✗未通过'}${qr.detoxed ? '（已自动降AI味改写）' : ''}${qr.viralScore != null ? ` | 爆款分: ${qr.viralScore}` : ''}`,
        qr.passed ? 'success' : 'warning'
      )
      break

    case 'ERROR':
      errorMessage.value = msg.message || '创作失败'
      errorVisible.value = true
      isCreating.value = false
      currentPhase.value = 'INPUT'
      addLog(`创作失败: ${msg.message || '未知错误'}`, 'error')
      break
  }
}

// 确认标题
const handleConfirmTitle = async (data: {mainTitle: string, subTitle: string, userDescription: string}) => {
  confirmLoading.value = true
  try {
    await confirmTitle({
      taskId: taskId.value,
      selectedMainTitle: data.mainTitle,
      selectedSubTitle: data.subTitle,
      userDescription: data.userDescription
    })
    // 保存标题信息，用于大纲生成阶段展示
    article.value.mainTitle = data.mainTitle
    article.value.subTitle = data.subTitle
    // 不直接切换阶段，等待 SSE 消息 OUTLINE_GENERATED
    message.success('标题已确认，正在生成大纲...')
  } catch (error) {
    const err = error as Error
    message.error(err.message || '确认标题失败')
  } finally {
    confirmLoading.value = false
  }
}

// 确认大纲
const handleConfirmOutline = async (outlineData: Array<{section: number, title: string, points: string[]}>) => {
  confirmLoading.value = true
  try {
    await confirmOutline({
      taskId: taskId.value,
      outline: outlineData
    })
    // 更新 outlineRaw 为用户修改后的大纲，确保 CONTENT_GENERATING 阶段展示正确的大纲
    outlineRaw.value = JSON.stringify({ sections: outlineData })
    // 不直接切换阶段，等待后端开始生成正文并推送 AGENT3_STREAMING
    message.success('大纲已确认，正在生成正文...')
  } catch (error) {
    const err = error as Error
    message.error(err.message || '确认大纲失败')
  } finally {
    confirmLoading.value = false
  }
}

// 处理 SSE 错误
const handleSSEError = (error: Event) => {
  console.error('SSE错误:', error)
  message.error('连接失败,请重试')
  isCreating.value = false
}

// 处理 SSE 完成
const handleSSEComplete = () => {
  console.log('SSE连接关闭')
}

// 复制全文
const copyContent = async () => {
  const content = article.value.fullContent || article.value.content || ''
  try {
    await navigator.clipboard.writeText(content)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败')
  }
}

// 查看文章详情
const viewArticle = () => {
  router.push(`/article/${taskId.value}`)
}

// 历史参考：套用选题（填入选题框，可编辑后走正常流程）
const handleSelectReference = (hit: API.RagHit) => {
  if (hit.title) {
    topic.value = hit.title
    message.info('已套用历史标题，可编辑后开始创作')
  }
}

// 重新创作
const resetCreate = () => {
  ragClear()
  closeSSE(eventSource)
  eventSource = null
  currentPhase.value = 'INPUT'
  topic.value = ''
  selectedStyle.value = ''
  selectedCharacterStyle.value = ''
  titleOptions.value = []
  outline.value = []
  isCreating.value = false
  isCompleted.value = false
  qualityReport.value = null
  isStreaming.value = false
  isOutlineStreaming.value = false
  currentStep.value = 0
  imageCount.value = 0
  imageUrls.value = []
  imagePhase.value = 'analyzing'
  allImagesDone.value = false
  outlineRaw.value = ''
  confirmLoading.value = false
  realtimeLogs.value = []
  article.value = {
    mainTitle: '',
    subTitle: '',
    content: '',
    fullContent: '',
    images: [],
  }
}

// 历史参考：选题输入防抖检索，仅在 INPUT 阶段触发
watch(topic, (val) => {
  if (currentPhase.value !== 'INPUT') return
  if (!val.trim()) {
    ragClear()
    return
  }
  ragSearch(val, { type: 'article', topK: 5 })
})

// 组件挂载时检查路由参数
onMounted(() => {
  if (route.query.topic) {
    topic.value = route.query.topic as string
  }
  // 进入创作页自动加载推荐选题（不触发 AI，零成本）
  loadRecommendedTopics(false)
})

// 组件卸载前关闭 SSE
onBeforeUnmount(() => {
  closeSSE(eventSource)
})
</script>

<style scoped lang="scss">
.article-create-page {
  flex: 1;
  background: var(--color-background-secondary);
  overflow: hidden;
}

.create-layout {
  display: grid;
  grid-template-columns: 320px 1fr 300px;
  height: 100%;
}

.mobile-support-panels {
  display: none;
}

/* 左侧边栏 */
.sidebar-left {
  background: white;
  border-right: 1px solid var(--color-border);
  padding: 24px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.sidebar-header {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border-light);
}

.sidebar-title {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 4px;
  color: var(--color-text);
}

.sidebar-subtitle {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
}

.flow-timeline {
  flex: 1;
}

/* 左侧栏配图预览 */
.image-preview-sidebar {
  border-top: 1px solid var(--color-border-light);
  padding: 16px 0 0;
  margin-top: 8px;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.preview-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.preview-count {
  font-size: 11px;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.preview-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  transition: border-color 0.3s;

  &.card-complete {
    border-color: var(--color-success, #22c55e);
  }
  &.card-pending {
    border-color: var(--color-border);
    opacity: 0.6;
  }
}

.preview-image-wrap {
  aspect-ratio: 16 / 9;
  background: var(--color-background-secondary);
  overflow: hidden;
  position: relative;
}

.preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.preview-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-icon {
  font-size: 24px;
  color: var(--color-text-disabled, #d1d5db);
}

.preview-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 8px;
  background: white;
}

.preview-label {
  font-size: 11px;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
  line-height: 1.3;

  &.pending {
    color: var(--color-text-disabled, #d1d5db);
  }
}

.preview-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: var(--radius-full, 10px);
  background: var(--color-background-secondary);
  color: var(--color-text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}

// 响应式：窄屏下隐藏左侧栏配图预览
@media (max-width: 900px) {
  .image-preview-sidebar {
    display: none;
  }
}

.flow-item {
  display: flex;
  gap: 14px;
  padding: 14px 0;
  position: relative;

  &:not(:last-child)::before {
    content: '';
    position: absolute;
    left: 15px;
    top: 46px;
    bottom: -14px;
    width: 2px;
    background: var(--color-border);
  }

  &.completed::before {
    background: var(--color-primary);
  }

  &.active::before {
    background: linear-gradient(180deg, var(--color-primary) 50%, var(--color-border) 50%);
  }
}

.flow-indicator {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 14px;
  transition: all var(--transition-normal);

  .pending & {
    background: var(--color-background-tertiary);
    color: var(--color-text-muted);
    border: 2px solid var(--color-border);
  }

  .active & {
    background: rgba(34, 197, 94, 0.1);
    color: var(--color-primary);
    border: 2px solid var(--color-primary);
  }

  .completed & {
    background: var(--color-primary);
    color: white;
  }

  .step-number {
    font-weight: 600;
  }

  .spin-icon {
    animation: spin 1s linear infinite;
  }
}

.flow-content {
  flex: 1;
  min-width: 0;
}

.flow-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 2px;

  .pending & {
    color: var(--color-text-muted);
  }

  .active & {
    color: var(--color-primary-dark);
  }
}

.flow-desc {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.4;
}

.flow-status {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-primary);
  font-weight: 500;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: pulse 1.5s infinite;
}


/* 主内容区 */
.main-content {
  padding: 32px 40px;
  overflow-y: auto;
  background: white;
}

/* 阶段切换容器（Transition 唯一子元素） */
.phase-wrapper {
  width: 100%;
}

/* 输入状态 */
.input-state {
  width: min(100%, 860px);
  margin: 0 auto;
  padding-top: 44px;
}

.input-card {
  background: var(--surface-panel, #fff);
  border: 1px solid var(--border-subtle, var(--color-border-light));
  border-radius: var(--radius-lg);
  padding: 32px;
  box-shadow: var(--shadow-md);
}

.input-header {
  margin-bottom: 24px;
}

.input-kicker {
  margin-bottom: 8px;
  color: var(--color-primary-dark);
  font-size: 13px;
  font-weight: 700;
}

.input-title {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
  margin: 0 0 10px;
  color: var(--color-text);
}

.input-subtitle {
  font-size: 15px;
  color: var(--color-text-secondary);
  margin: 0;
  line-height: 1.7;
}

.input-area {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.topic-section {
  padding: 18px;
  background: var(--surface-muted, var(--color-background-secondary));
  border: 1px solid var(--border-subtle, var(--color-border-light));
  border-radius: var(--radius-lg);
}

.field-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.field-title {
  margin: 0 0 4px;
  color: var(--text-strong, var(--color-text));
  font-size: 16px;
  font-weight: 700;
  line-height: 1.4;
}

.field-description {
  margin: 0;
  color: var(--text-muted, var(--color-text-secondary));
  font-size: 13px;
  line-height: 1.6;
}

.field-badge {
  flex: none;
  min-height: 24px;
  padding: 3px 9px;
  color: var(--color-primary-dark);
  background: rgba(34, 197, 94, 0.1);
  border: 1px solid rgba(34, 197, 94, 0.2);
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 700;
  line-height: 16px;
}

.topic-textarea {
  font-size: 15px;
  border-radius: var(--radius-lg);
  padding: 16px;
  background: white;

  &:focus {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.2);
  }
}

.settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.82fr) minmax(0, 1.18fr);
  gap: 16px;
  align-items: stretch;
}

.setting-panel {
  min-width: 0;
  min-height: 100%;
  padding: 16px;
  background: var(--surface-canvas, #fff);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-subtle, var(--color-border-light));
}

.create-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 240px);
  gap: 16px;
  align-items: center;
  padding: 16px;
  background: var(--surface-muted, var(--color-background-secondary));
  border: 1px solid var(--border-subtle, var(--color-border-light));
  border-radius: var(--radius-lg);
}

.create-action-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.action-label {
  color: var(--text-muted, var(--color-text-muted));
  font-size: 12px;
  font-weight: 600;
}

.create-action-copy strong {
  color: var(--text-strong, var(--color-text));
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
}


.quota-warning {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  padding: 10px 16px;
  background: rgba(255, 77, 79, 0.08);
  border: 1px solid rgba(255, 77, 79, 0.2);
  border-radius: var(--radius-md);
  color: #ff4d4f;
  font-size: 13px;
}

/* 文章风格选择 */
.style-section {
}

.style-group {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.style-group :deep(.ant-radio-wrapper) {
  margin: 0;
  padding: 6px 12px;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: all 0.2s;
}

.style-group :deep(.ant-radio-wrapper:hover) {
  border-color: var(--color-primary);
  background: rgba(34, 197, 94, 0.04);
}

.style-group :deep(.ant-radio-wrapper-checked) {
  border-color: var(--color-primary);
  background: rgba(34, 197, 94, 0.08);
}

/* 配图方式选择 */
.image-methods-section {
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.section-tip {
  display: block;
  margin-top: 3px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.methods-group {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.methods-group :deep(.ant-checkbox-wrapper) {
  margin: 0;
  padding: 6px 12px;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: all 0.2s;
}

.methods-group :deep(.ant-checkbox-wrapper:hover) {
  border-color: var(--color-primary);
  background: rgba(34, 197, 94, 0.04);
}

.methods-group :deep(.ant-checkbox-wrapper-checked) {
  border-color: var(--color-primary);
  background: rgba(34, 197, 94, 0.08);
}

.methods-group :deep(.ant-checkbox-wrapper-disabled) {
  opacity: 0.6;
  cursor: not-allowed;
}

.vip-icon {
  color: var(--color-primary);
  font-size: 12px;
  margin-left: 4px;
}

.vip-notice {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 10px 14px;
  background: rgba(34, 197, 94, 0.08);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--color-primary-dark);
  border: 1px solid rgba(34, 197, 94, 0.2);

  .anticon {
    color: var(--color-primary);
  }

  .upgrade-link {
    color: var(--color-primary);
    font-weight: 600;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

/* 创作进行中 */
.creating-state,
.completed-state {
  max-width: 100%;
}

/* 标题区域 */
.preview-header {
  text-align: center;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border-light);
}

.article-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--color-text);
  line-height: 1.4;
}

.article-subtitle {
  font-size: 16px;
  color: var(--color-text-secondary);
  margin: 0;
}

/* 大纲预览 */
.outline-preview {
  margin-bottom: 24px;
  padding: 20px 24px;
  background: var(--color-background-secondary);
  border-radius: var(--radius-lg);
}

.section-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 16px;
}

.outline-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.outline-item {
  padding: 12px 16px;
  background: white;
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-primary);
}

.outline-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
}

.outline-points {
  margin: 0;
  padding-left: 18px;

  li {
    font-size: 13px;
    color: var(--color-text-secondary);
    line-height: 1.6;
    margin-bottom: 4px;

    &:last-child {
      margin-bottom: 0;
    }
  }
}

/* 正文预览 */
.content-preview {
  contain: layout style;
  overflow-anchor: none;
  line-height: 1.8;
}

.markdown-body {
  line-height: 1.8;
  font-size: 15px;
  color: var(--color-text);

  :deep(h2) {
    font-size: 20px;
    font-weight: 600;
    margin: 24px 0 14px;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--color-border);
    color: var(--color-text);
  }

  :deep(p) {
    margin-bottom: 14px;
    text-indent: 2em;
  }

  :deep(img) {
    display: block;
    max-width: 100%;
    max-height: 600px;
    width: auto;
    height: auto;
    margin: 20px auto;
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-md);
    object-fit: contain;
  }

  // Mermaid 图表特殊处理（SVG 格式）
  :deep(img[src$=".svg"]) {
    max-width: 800px;
    max-height: 500px;
  }
}

.typing-cursor {
  display: inline-block;
  animation: blink 1s infinite;
  color: var(--color-primary);
  font-weight: bold;
  font-size: 18px;
}

.loading-placeholder {
  text-align: center;
  padding: 100px 0;

  p {
    margin: 16px 0 0;
    color: var(--color-text-secondary);
    font-size: 15px;
  }
}

/* 完成状态 */
.success-header {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--gradient-primary);
  border-radius: var(--radius-full);
  margin-bottom: 24px;
  color: white;
  font-size: 14px;
  font-weight: 600;

  .success-icon {
    font-size: 16px;
  }
}

/* 右侧辅助面板 */
.sidebar-right {
  background: white;
  border-left: 1px solid var(--color-border);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}

.panel-section {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border-light);

  &:last-of-type {
    border-bottom: none;
    padding-bottom: 0;
  }
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 16px;
}

/* 配额信息样式 */
.quota-section {
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.05) 0%, rgba(34, 197, 94, 0.02) 100%);
  border-radius: var(--radius-lg);
  padding: 16px !important;
  margin: -8px -8px 12px -8px;
}

.quota-admin {
  display: flex;
  align-items: center;
  gap: 10px;
}

.quota-badge {
  padding: 4px 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;

  &.admin {
    background: linear-gradient(135deg, #0F172A 0%, #1E293B 100%);
    color: white;
  }

  &.vip {
    background: var(--gradient-primary);
    color: white;
  }
}

.quota-text {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.quota-info {
  text-align: center;
}

.quota-display {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4px;
}

.quota-number {
  font-size: 36px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1;

  &.low {
    color: #faad14;
  }

  &.empty {
    color: #ff4d4f;
  }
}

.quota-unit {
  font-size: 14px;
  color: var(--color-text-muted);
}

.quota-label {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 4px 0 12px;
}

.quota-progress {
  max-width: 120px;
  margin: 0 auto;
}

/* 热门选题 */
.hot-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hot-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: var(--color-background-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);

  &:hover {
    border-color: var(--color-primary);
    color: var(--color-primary);
    background: rgba(34, 197, 94, 0.05);
    transform: translateY(-1px);
  }
}

/* AI 生成选题高亮 */
.hot-tag.ai-tag {
  border-color: rgba(59, 130, 246, 0.4);
  background: rgba(59, 130, 246, 0.06);
}

.ai-badge {
  font-style: normal;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  padding: 2px 5px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, #3B82F6, #2563EB);
  color: #fff;
}

/* 面板标题行（标题 + 换一批按钮） */
.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  .panel-title {
    margin-bottom: 0;
  }
}

.refresh-btn {
  color: var(--color-text-muted);

  &:hover {
    color: var(--color-primary);
  }
}

.hot-tags-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

/* 创作技巧 */
.tips-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tip-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  background: var(--color-background-secondary);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);

  &:hover {
    background: rgba(34, 197, 94, 0.05);
  }
}

.tip-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--gradient-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.tip-content {
  flex: 1;
  min-width: 0;
}

.tip-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 2px;
}

.tip-desc {
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.4;
}

/* 创作进度信息 */
.progress-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.progress-step {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: var(--color-background-secondary);
  border-radius: var(--radius-md);
}

.step-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.step-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
}

.progress-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  background: rgba(34, 197, 94, 0.08);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--color-primary-dark);
  line-height: 1.5;

  .anticon {
    flex-shrink: 0;
    margin-top: 2px;
  }

  &.waiting {
    background: rgba(250, 173, 20, 0.08);
    color: #d48806;
  }
}

/* 实时日志 */
.realtime-logs-section {
  .logs-container {
    max-height: 300px;
    overflow-y: auto;
    background: var(--color-background);
    border-radius: var(--radius-md);
    border: 1px solid var(--color-border-light);
    padding: 8px;

    .log-entry {
      display: flex;
      gap: 8px;
      padding: 6px 8px;
      font-size: 11px;
      line-height: 1.4;
      border-radius: var(--radius-sm);
      margin-bottom: 4px;
      transition: background var(--transition-fast);

      &:hover {
        background: var(--color-background-secondary);
      }

      &.success {
        .log-time {
          color: var(--color-success);
        }
      }

      &.error {
        background: rgba(239, 68, 68, 0.05);
        .log-time {
          color: var(--color-error);
        }
        .log-message {
          color: var(--color-error);
        }
      }

      .log-time {
        flex-shrink: 0;
        color: var(--color-text-muted);
        font-weight: 500;
      }

      .log-message {
        flex: 1;
        color: var(--color-text-secondary);
      }
    }

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      background: var(--color-border);
      border-radius: var(--radius-full);
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }
  }
}

/* 选题展示 */
.topic-display {
  padding: 12px 16px;
  background: var(--color-background-secondary);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-primary);

  p {
    margin: 0;
    font-size: 13px;
    color: var(--color-text);
    line-height: 1.6;
  }
}

/* 提示面板样式 */
.tips-section {
  .tip-icon {
    background: transparent;
    font-size: 16px;
  }

  .tip-desc {
    font-size: 12px;
  }
}

/* 文章统计 */
.stats-section {
  margin-top: auto;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.stat-item {
  text-align: center;
  padding: 16px 12px;
  background: var(--color-background-secondary);
  border-radius: var(--radius-md);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

/* 底部帮助链接 */
.panel-footer {
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--color-border-light);
  display: flex;
  justify-content: center;
  gap: 20px;
}

.help-link {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color var(--transition-fast);

  &:hover {
    color: var(--color-primary);
  }
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-btn {
  height: 40px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;

  &.primary {
    background: var(--gradient-primary);
    border: none;
    color: white;

    &:hover {
      opacity: 0.9;
    }
  }
}

/* 阶段切换过渡动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

/* 动画 */
@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 加载阶段样式 */
.loading-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120px 40px;
  text-align: center;

  h3 {
    font-size: 20px;
    font-weight: 600;
    color: var(--color-text);
    margin: 24px 0 8px;
  }

  p {
    font-size: 14px;
    color: var(--color-text-secondary);
    margin: 0;
  }
}

/* 大纲生成中状态 */
.outline-generating-state {
  max-width: 100%;
}

.outline-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 20px;
  font-size: 14px;
  color: var(--color-text-secondary);
}

/* 渐入动画 */
@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fade-in {
  animation: fade-in 0.4s ease-out;
}

/* 响应式 */
@media (max-width: 1400px) {
  .create-layout {
    grid-template-columns: 280px 1fr 260px;
  }
}

@media (max-width: 1200px) {
  .create-layout {
    grid-template-columns: 240px 1fr 220px;
  }
}

@media (max-width: 992px) {
  .article-create-page {
    height: auto;
    min-height: calc(100vh - 64px);
    overflow: visible;
  }

  .create-layout {
    grid-template-columns: 1fr;
    height: auto;
  }

  .sidebar-left,
  .sidebar-right {
    display: none;
  }

  .mobile-support-panels {
    display: grid;
    gap: 12px;
    padding: 16px 20px 0;
    background: var(--color-background-secondary);
  }

  .mobile-progress-card,
  .mobile-log-card,
  .mobile-context-item {
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: white;
  }

  .mobile-progress-card {
    padding: 16px;
  }

  .mobile-progress-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }

  .mobile-kicker,
  .mobile-context-item span,
  .mobile-log-title {
    display: block;
    color: var(--color-text-muted);
    font-size: 12px;
    line-height: 1.4;
  }

  .mobile-progress-header strong,
  .mobile-context-item strong {
    display: block;
    margin-top: 2px;
    color: var(--color-text);
    font-size: 14px;
    line-height: 1.45;
  }

  .mobile-step-count {
    color: var(--color-primary-dark);
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    font-weight: 600;
  }

  .mobile-progress {
    margin-bottom: 8px;
  }

  .mobile-progress-card p {
    margin: 0;
    color: var(--color-text-secondary);
    font-size: 13px;
    line-height: 1.6;
  }

  .mobile-context-grid {
    display: grid;
    grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
    gap: 12px;
  }

  .mobile-context-item {
    min-width: 0;
    padding: 13px 14px;
  }

  .mobile-context-item.topic strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-log-card {
    padding: 14px;
  }

  .mobile-log-title {
    margin-bottom: 8px;
  }

  .mobile-log-entry {
    display: grid;
    grid-template-columns: 64px minmax(0, 1fr);
    gap: 8px;
    padding: 7px 0;
    border-top: 1px solid var(--color-border-light);
  }

  .mobile-log-entry span {
    color: var(--color-text-muted);
    font-size: 11px;
    font-variant-numeric: tabular-nums;
  }

  .mobile-log-entry p {
    margin: 0;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 1.45;
  }

  .mobile-log-entry.success p {
    color: var(--state-success-text);
  }

  .mobile-log-entry.error p {
    color: var(--state-error-text);
  }

  .main-content {
    padding: 20px;
  }

  .input-state {
    width: 100%;
    padding-top: 12px;
  }

  .input-card {
    padding: 24px;
  }

  .settings-grid {
    grid-template-columns: 1fr;
  }

  .create-actions {
    grid-template-columns: 1fr;
  }

  .create-btn.ant-btn {
    width: 100%;
  }
}

@media (max-width: 520px) {
  .mobile-support-panels {
    padding: 14px 14px 0;
  }

  .mobile-context-grid {
    grid-template-columns: 1fr;
  }

  .main-content {
    padding: 14px;
  }

  .input-card {
    padding: 18px;
  }

  .input-title {
    font-size: 24px;
  }

  .field-header,
  .section-header {
    gap: 8px;
  }

  .topic-section,
  .setting-panel,
  .create-actions {
    padding: 14px;
  }

  .style-group,
  .methods-group {
    gap: 8px;
  }

  .style-group :deep(.ant-radio-wrapper),
  .methods-group :deep(.ant-checkbox-wrapper) {
    min-height: 36px;
    padding: 6px 10px;
  }
}

/* 质量门报告面板 */
.quality-report-panel {
  margin-bottom: 20px;

  .quality-title {
    font-weight: 600;
    font-size: 15px;
  }

  .quality-score {
    margin-left: 8px;
    font-family: var(--font-mono);
    font-size: 18px;
    font-weight: 700;
  }

  .quality-tag {
    margin-left: 8px;
  }

  .quality-violations {
    margin-top: 8px;

    .violations-label {
      font-weight: 600;
      color: var(--color-text-secondary);
    }

    ul {
      margin: 6px 0 0 16px;
      padding: 0;
      line-height: 1.8;

      li {
        color: var(--color-text-secondary);
        font-size: 13px;
      }
    }
  }
}
</style>
