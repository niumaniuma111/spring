<template>
  <div class="assistant-page">
    <!-- 顶部导航 -->
    <header class="navbar">
      <div class="nav-inner">
        <div class="logo" @click="$router.push('/')">🏞️ <span>知行山水</span></div>
        <div class="spacer"></div>
        <el-button link type="primary" @click="$router.push('/')">返回景点列表</el-button>
        <el-tag type="success" effect="plain" round>🤖 AI 行程助手</el-tag>
      </div>
    </header>

    <div class="layout">
      <!-- 左侧会话列表 -->
      <aside class="session-panel">
        <el-button type="primary" class="new-btn" round @click="newSession">＋ 新建对话</el-button>
        <div class="session-list">
          <div v-for="s in sessions" :key="s.id"
               class="session-item" :class="{ active: s.id === currentId }"
               @click="openSession(s.id)">
            <span class="s-title">{{ s.title }}</span>
            <el-icon class="s-del" @click.stop="removeSession(s.id)"><Delete /></el-icon>
          </div>
          <el-empty v-if="sessions.length === 0" description="暂无对话" :image-size="60" />
        </div>
      </aside>

      <!-- 右侧聊天区 -->
      <main class="chat-panel">
        <div class="messages" ref="msgBox">
          <template v-if="messages.length === 0">
            <div class="welcome">
              <h2>👋 我是 AI 行程助手</h2>
              <p>我可以基于本站收录的真实景点，帮你规划行程、解答旅行问题。</p>
              <div class="quick">
                <el-button round @click="quickFill('帮我规划一趟云南三日游，预算2000元，两个人')">🗓️ 帮我规划一趟行程</el-button>
                <el-button round @click="quickFill('四川有哪些5A级景点适合亲子游玩？')">💬 问答模式：问景点</el-button>
              </div>
            </div>
          </template>

          <div v-for="(m, i) in messages" :key="i"
               class="bubble-row" :class="m.role">
            <div class="avatar" :class="m.role === 'user' ? 'avatar-user' : 'avatar-ai'">
              {{ m.role === 'user' ? '我' : 'AI' }}
            </div>
            <div class="bubble" :class="{ streaming: m.streaming }">
              <div v-if="m.role === 'assistant'" class="md" v-html="render(m.content)"></div>
              <template v-else>{{ m.content }}</template>
              <span v-if="m.streaming" class="cursor">▌</span>
            </div>
          </div>
        </div>

        <div class="input-bar">
          <el-input v-model="input" type="textarea" :rows="2" resize="none"
                    placeholder="输入目的地 / 天数…，例如：帮我规划成都三日游"
                    @keydown.enter.exact.prevent="send" :disabled="sending" />
          <el-button type="primary" round :loading="sending" @click="send">
            {{ sending ? '思考中…' : '发送' }}
          </el-button>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import api, { chatStream } from '../api'

// Markdown 渲染（markdown-it 默认转义 HTML，防注入）
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const render = (text) => md.render(text || '')

const user = JSON.parse(localStorage.getItem('user') || 'null')

const sessions = ref([])
const currentId = ref(null)
const messages = ref([])
const input = ref('')
const sending = ref(false)
const msgBox = ref(null)

const scrollToBottom = () => nextTick(() => {
  if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
})

const loadSessions = async () => {
  try { sessions.value = await api.sessions() } catch (e) { /* 拦截器已提示 */ }
}

const openSession = async (id) => {
  if (sending.value) return
  currentId.value = id
  try { messages.value = await api.sessionMessages(id) } catch (e) { messages.value = [] }
  scrollToBottom()
}

const newSession = () => {
  if (sending.value) return
  currentId.value = null
  messages.value = []
  input.value = ''
}

const removeSession = async (id) => {
  if (sending.value) return
  try {
    await ElMessageBox.confirm('删除后聊天记录不可恢复，确定删除？', '提示', { type: 'warning' })
  } catch (e) { return }
  await api.deleteSession(id)
  if (currentId.value === id) newSession()
  loadSessions()
  ElMessage.success('已删除')
}

const quickFill = (text) => { input.value = text }

const send = async () => {
  const text = input.value.trim()
  if (!text || sending.value) return
  sending.value = true

  try {
    // 无会话则先创建（标题暂用"新对话"，后端收到首条消息后自动命名）
    if (!currentId.value) {
      const s = await api.createSession()
      currentId.value = s.id
    }
    messages.value.push({ role: 'user', content: text })
    input.value = ''
    const aiMsg = { role: 'assistant', content: '', streaming: true }
    messages.value.push(aiMsg)
    scrollToBottom()

    let errored = false
    await chatStream(currentId.value, text, {
      onDelta: (delta) => { aiMsg.content += delta; scrollToBottom() },
      onDone: () => { aiMsg.streaming = false },
      onError: (msg) => {
        errored = true
        aiMsg.streaming = false
        if (!aiMsg.content) aiMsg.content = '⚠️ ' + msg
        ElMessage.error(msg)
      }
    })
    aiMsg.streaming = false
    if (errored && !aiMsg.content) messages.value.pop()
    loadSessions()
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

onMounted(loadSessions)
</script>

<style scoped>
.assistant-page { min-height: 100vh; display: flex; flex-direction: column; }
.navbar { background: #fff; border-bottom: 1px solid #e8eaf0; }
.nav-inner { max-width: 1200px; margin: 0 auto; padding: 12px 24px; display: flex; align-items: center; gap: 16px; }
.logo { font-size: 18px; font-weight: 500; cursor: pointer; }
.spacer { flex: 1; }
.layout { max-width: 1200px; width: 100%; margin: 16px auto; padding: 0 24px; flex: 1;
  display: flex; gap: 16px; min-height: 0; }

/* 左侧会话 */
.session-panel { width: 240px; flex-shrink: 0; background: #fff; border-radius: 12px;
  padding: 12px; display: flex; flex-direction: column; height: calc(100vh - 140px); }
.new-btn { width: 100%; margin-bottom: 12px; }
.session-list { overflow-y: auto; flex: 1; }
.session-item { display: flex; align-items: center; padding: 10px 12px; border-radius: 8px;
  cursor: pointer; margin-bottom: 4px; font-size: 13px; }
.session-item:hover { background: #f5f7fa; }
.session-item.active { background: #ecf5ff; color: #409eff; }
.s-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.s-del { color: #c0c4cc; }
.s-del:hover { color: #f56c6c; }

/* 右侧聊天 */
.chat-panel { flex: 1; background: #fff; border-radius: 12px; display: flex;
  flex-direction: column; height: calc(100vh - 140px); min-width: 0; }
.messages { flex: 1; overflow-y: auto; padding: 20px; }
.welcome { text-align: center; margin-top: 80px; color: #606266; }
.welcome h2 { font-size: 20px; margin-bottom: 8px; color: #303133; font-weight: 500; }
.quick { margin-top: 20px; display: flex; gap: 12px; justify-content: center; }

.bubble-row { display: flex; gap: 10px; margin-bottom: 16px; }
.bubble-row.user { flex-direction: row-reverse; }
.avatar { width: 38px; height: 38px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; color: #fff;
  border: 2px solid #fff; box-shadow: 0 2px 8px rgba(15, 52, 96, .18); }
.avatar-ai { background: linear-gradient(135deg, #1f6feb, #0aa37f); letter-spacing: 1px; }
.avatar-user { background: linear-gradient(135deg, #ff9a44, #fc6076); }
.bubble { max-width: 75%; padding: 10px 14px; border-radius: 12px; font-size: 14px;
  line-height: 1.7; word-break: break-word; }
.bubble-row.user .bubble { background: #409eff; color: #fff; border-top-right-radius: 4px; }
.bubble-row.assistant .bubble { background: #f5f7fa; color: #303133; border-top-left-radius: 4px; }
.bubble.streaming .cursor { animation: blink 1s step-start infinite; color: #409eff; }
@keyframes blink { 50% { opacity: 0; } }

/* Markdown 内容样式 */
.md :deep(h1), .md :deep(h2), .md :deep(h3) { margin: 12px 0 6px; font-weight: 500; font-size: 15px; }
.md :deep(p) { margin: 6px 0; }
.md :deep(ul), .md :deep(ol) { padding-left: 20px; margin: 6px 0; }
.md :deep(table) { border-collapse: collapse; margin: 8px 0; font-size: 13px; }
.md :deep(th), .md :deep(td) { border: 1px solid #dcdfe6; padding: 5px 10px; }
.md :deep(code) { background: #eceef3; padding: 1px 5px; border-radius: 4px; font-size: 13px; }

/* 输入区 */
.input-bar { border-top: 1px solid #e8eaf0; padding: 12px 16px; display: flex;
  gap: 12px; align-items: flex-end; }
.input-bar .el-button { height: 54px; }
</style>
