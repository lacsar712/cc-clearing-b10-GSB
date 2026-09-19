<template>
  <div class="page">
    <h2 class="page-title">轧差任务队列</h2>
    <p class="page-desc">批量提交「交割日 + 币种」轧差任务，后台按 # 顺序串行执行</p>

    <el-alert class="policy" type="info" :closable="false" show-icon>
      <template #title>
        执行策略：任务按 # 顺序串行执行，同一时刻仅运行一项；<strong>单项失败默认继续执行后续任务，不清空队列</strong>（continue-on-failure）。失败任务可在修正数据后重新提交。
      </template>
    </el-alert>

    <div class="card-panel" style="margin-top:16px">
      <div class="toolbar" style="justify-content:space-between;margin-bottom:12px">
        <strong>提交任务</strong>
        <el-button size="small" :disabled="!auth.isOperator" @click="addItem">添加一项</el-button>
      </div>
      <div v-for="(item, idx) in items" :key="idx" class="queue-row">
        <span class="queue-row-index">#{{ idx + 1 }}</span>
        <el-date-picker v-model="item.settleDate" type="date" value-format="YYYY-MM-DD" placeholder="交割日" />
        <el-select v-model="item.currency" style="width:120px">
          <el-option label="USD" value="USD" />
          <el-option label="CNY" value="CNY" />
          <el-option label="EUR" value="EUR" />
        </el-select>
        <el-button link type="danger" :disabled="items.length <= 1" @click="items.splice(idx, 1)">移除</el-button>
      </div>
      <div class="toolbar" style="margin-top:12px;margin-bottom:0">
        <el-button type="primary" :disabled="!auth.isOperator || !canSubmit" :loading="submitting" @click="submit">
          提交 {{ items.length }} 项任务
        </el-button>
        <span v-if="!auth.isOperator" class="hint">只读账号无法提交</span>
      </div>
    </div>

    <div class="card-panel" style="margin-top:16px">
      <div class="toolbar" style="justify-content:space-between">
        <div>
          <strong>任务队列</strong>
          <span class="summary">
            等待 {{ countOf('QUEUED') }} · 进行 {{ countOf('RUNNING') }} · 完成 {{ countOf('COMPLETED') }} · 失败 {{ countOf('FAILED') }}
          </span>
        </div>
        <el-button :loading="loading" @click="load()">刷新</el-button>
      </div>
      <el-table :data="tasks" v-loading="loading" stripe>
        <el-table-column prop="seq" label="#" width="70" />
        <el-table-column prop="settleDate" label="交割日" width="120" />
        <el-table-column prop="currency" label="币种" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联批次" min-width="220">
          <template #default="{ row }">
            <router-link v-if="row.runId" class="mono" :to="`/netting-runs/${row.runId}`">{{ row.runId }}</router-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="failureReason" label="失败原因" min-width="180">
          <template #default="{ row }">{{ row.failureReason || '-' }}</template>
        </el-table-column>
        <el-table-column label="开始时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.finishedAt) }}</template>
        </el-table-column>
        <template #empty>暂无任务，提交后此处按 # 顺序推进</template>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const today = new Date().toISOString().slice(0, 10)
const items = ref([{ settleDate: today, currency: 'USD' }])
const tasks = ref([])
const loading = ref(false)
const submitting = ref(false)
let pollTimer = null

const canSubmit = computed(() => items.value.every((i) => i.settleDate && i.currency))

function addItem() {
  items.value.push({ settleDate: today, currency: 'USD' })
}

function countOf(status) {
  return tasks.value.filter((t) => t.status === status).length
}

function statusType(s) {
  if (s === 'COMPLETED') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'RUNNING') return 'warning'
  return 'info'
}

function statusLabel(s) {
  return { QUEUED: '等待', RUNNING: '进行', COMPLETED: '完成', FAILED: '失败' }[s] || s
}

function formatTime(v) {
  return v ? new Date(v).toLocaleString() : '-'
}

function hasActive() {
  return tasks.value.some((t) => t.status === 'QUEUED' || t.status === 'RUNNING')
}

function schedulePoll() {
  clearTimeout(pollTimer)
  if (hasActive()) {
    pollTimer = setTimeout(() => load(true), 1000)
  }
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const { data } = await api.get('/netting-queue')
    tasks.value = data
  } finally {
    if (!silent) loading.value = false
    schedulePoll()
  }
}

async function submit() {
  submitting.value = true
  try {
    const { data } = await api.post('/netting-queue', { items: items.value })
    ElMessage.success(`已提交 ${data.length} 项任务，队列将按顺序执行`)
    items.value = [{ settleDate: today, currency: 'USD' }]
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
onUnmounted(() => clearTimeout(pollTimer))
</script>

<style scoped>
.policy {
  margin-top: 4px;
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
  font-weight: 600;
}
.summary {
  margin-left: 12px;
  color: var(--muted);
  font-size: 13px;
}
.hint {
  color: var(--muted);
  font-size: 13px;
}
</style>
