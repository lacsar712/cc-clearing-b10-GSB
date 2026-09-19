<template>
  <div class="page">
    <h2 class="page-title">轧差任务队列</h2>
    <p class="page-desc">提交多个「交割日 + 币种」轧差任务，队列按提交顺序串行执行</p>

    <el-alert type="info" :closable="false" class="policy-alert">
      <template #title>失败策略：{{ policyNote || '加载中…' }}</template>
    </el-alert>

    <div class="card-panel">
      <div class="panel-head">
        <strong>提交任务</strong>
        <el-button size="small" :disabled="!auth.isOperator" @click="addRow">添加一项</el-button>
      </div>
      <div v-for="(row, i) in draft" :key="i" class="queue-row">
        <span class="queue-row-index">#{{ i + 1 }}</span>
        <el-date-picker v-model="row.settleDate" type="date" value-format="YYYY-MM-DD" placeholder="交割日" />
        <el-select v-model="row.currency" style="width: 120px">
          <el-option label="USD" value="USD" />
          <el-option label="CNY" value="CNY" />
          <el-option label="EUR" value="EUR" />
        </el-select>
        <el-button link type="danger" :disabled="draft.length <= 1" @click="draft.splice(i, 1)">移除</el-button>
      </div>
      <div class="toolbar" style="margin: 12px 0 0">
        <el-button type="primary" :disabled="!auth.isOperator" :loading="submitting" @click="submit">
          提交队列（{{ draft.length }} 项）
        </el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div class="card-panel" style="margin-top: 16px">
      <strong>队列状态</strong>
      <el-table :data="tasks" v-loading="loading" stripe style="margin-top: 12px">
        <el-table-column prop="seq" label="序号" width="70" />
        <el-table-column prop="settleDate" label="交割日" width="120" />
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="轧差批次" min-width="220">
          <template #default="{ row }">
            <router-link v-if="row.runId" class="mono" :to="`/netting-runs/${row.runId}`">{{ row.runId.slice(0, 8) }}…</router-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="180">
          <template #default="{ row }">{{ row.failureReason || '-' }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">{{ formatTime(row.finishedAt) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const today = new Date().toISOString().slice(0, 10)
const draft = ref([
  { settleDate: today, currency: 'USD' },
  { settleDate: today, currency: 'CNY' }
])
const tasks = ref([])
const policyNote = ref('')
const loading = ref(false)
const submitting = ref(false)
let timer = null

function addRow() {
  draft.value.push({ settleDate: today, currency: 'USD' })
}

function statusLabel(s) {
  return { QUEUED: '等待', RUNNING: '进行', COMPLETED: '完成', FAILED: '失败' }[s] || s
}

function statusType(s) {
  return { QUEUED: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[s] || 'info'
}

function formatTime(v) {
  return v ? new Date(v).toLocaleString() : '-'
}

async function load(silent = false) {
  if (!silent) {
    loading.value = true
  }
  try {
    const { data } = await api.get('/netting-tasks')
    tasks.value = data.tasks
    policyNote.value = data.failurePolicyNote
  } finally {
    loading.value = false
  }
}

async function submit() {
  submitting.value = true
  try {
    const { data } = await api.post('/netting-tasks', { items: draft.value })
    ElMessage.success(`已提交 ${data.length} 项任务，队列串行执行中`)
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  load()
  timer = setInterval(() => load(true), 1000)
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<style scoped>
.policy-alert {
  margin-bottom: 16px;
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.queue-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}
.queue-row-index {
  width: 32px;
  color: var(--muted);
  font-size: 13px;
}
</style>
