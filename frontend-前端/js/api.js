/**
 * API 通信模块
 * 负责与 Java 业务中台通信（由 Java 再与 Python AI 服务交互）
 */

const API_CONFIG = {
    // Java 业务中台地址 (默认端口 8080)
    baseURL: 'http://127.0.0.1:8080',

    // 默认请求超时 (ms)
    // 说明：表单接口异步化改造后，"提交"与"查询状态"都是毫秒级返回，
    // 不再需要原来 150s 的超时；只有对话接口仍是同步等待，单独指定。
    timeout: 15000,

    // 对话接口超时 (ms)：对话模式保留同步等待
    chatTimeout: 90000,

    // 任务状态轮询参数
    pollInterval: 2000,      // 轮询间隔 2 秒
    pollMaxAttempts: 90,     // 最多轮询 90 次（约 3 分钟）
};

/**
 * 通用请求方法
 */
async function apiRequest(endpoint, options = {}) {
    const url = `${API_CONFIG.baseURL}${endpoint}`;
    const timeout = options.timeout || API_CONFIG.timeout;

    const config = {
        headers: {
            'Content-Type': 'application/json',
        },
        ...options,
    };
    delete config.timeout;   // timeout 仅用于前端控制，不传给 fetch

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);
    config.signal = controller.signal;

    try {
        const response = await fetch(url, config);
        clearTimeout(timeoutId);

        if (!response.ok) {
            const errorText = await response.text().catch(() => '未知错误');
            throw new Error(`HTTP ${response.status}: ${errorText}`);
        }

        return await response.json();
    } catch (error) {
        clearTimeout(timeoutId);

        if (error.name === 'AbortError') {
            throw new Error('请求超时，请稍后重试');
        }
        if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError')) {
            throw new Error('无法连接到服务，请确认后端服务已启动');
        }
        throw error;
    }
}

/**
 * 检查后端服务是否在线
 */
async function checkHealth() {
    try {
        const result = await apiRequest('/api/health', {
            method: 'GET',
            signal: new AbortController().signal,  // 使用独立controller避免超时
        });
        // Java 返回 {"code":200,...} 格式，请求成功即为在线
        return result.code === 200;
    } catch {
        return false;
    }
}

/**
 * 表单模式：提交用户数据（异步受理）
 *
 * 重要变化：接口不再返回 AI 生成结果，而是立即返回受理凭证
 * { recordId, requestId, status }。生成结果需通过 getPlanStatus 轮询获取。
 *
 * @param {Object} data - 用户身体数据
 */
async function submitHealthForm(data) {
    return await apiRequest('/api/health-plan', {
        method: 'POST',
        body: JSON.stringify(data),
    });
}

/**
 * 查询任务状态与结果（轮询）
 *
 * 返回的 UserHealthDO 中 status 取值：
 *   1 待处理 / 2 处理中 / 3 已完成 / 4 失败
 *
 * @param {number} recordId - 受理时返回的任务 id
 */
async function getPlanStatus(recordId) {
    return await apiRequest(`/api/health-plan/${recordId}`, {
        method: 'GET',
    });
}

/**
 * 对话模式：发送聊天消息（同步等待回复）
 * @param {Object} params - 聊天参数
 */
async function sendChatMessage(params) {
    return await apiRequest('/api/chat', {
        method: 'POST',
        body: JSON.stringify(params),
        timeout: API_CONFIG.chatTimeout,
    });
}

/**
 * 获取会话历史
 * @param {string} sessionId - 会话ID
 */
async function getChatHistory(sessionId) {
    return await apiRequest(`/api/chat/history?session_id=${encodeURIComponent(sessionId)}`, {
        method: 'GET',
    });
}
