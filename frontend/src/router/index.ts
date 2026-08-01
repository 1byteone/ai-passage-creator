import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: '主页',
      component: () => import('@/pages/HomePage.vue'),
    },
    {
      path: '/create',
      name: '创作文章',
      component: () => import('@/pages/article/ArticleCreatePage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/article/list',
      name: '文章列表',
      component: () => import('@/pages/article/ArticleListPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/article/:taskId',
      name: '文章详情',
      component: () => import('@/pages/article/ArticleDetailPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/skill',
      name: 'AI 技能中心',
      component: () => import('@/pages/skill/SkillCenterPage.vue'),
    },
    {
      path: '/skill/chain',
      name: 'Skill 链式编排',
      component: () => import('@/pages/skill/SkillChainPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/skill/:skillName',
      name: '执行 AI 技能',
      component: () => import('@/pages/skill/SkillExecutePage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: () => import('@/pages/user/UserLoginPage.vue'),
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: () => import('@/pages/user/UserRegisterPage.vue'),
    },
    {
      path: '/admin/userManage',
      name: '用户管理',
      component: () => import('@/pages/admin/UserManagePage.vue'),
    },
    {
      path: '/admin/statistics',
      name: '数据分析',
      component: () => import('@/pages/admin/StatisticsPage.vue'),
    },
    {
      path: '/vip',
      name: '会员购买',
      component: () => import('@/pages/VipPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
  ],
})

export default router
