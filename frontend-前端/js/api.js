/**
 * API 通信模块
 * 负责与前端的 Python FastAPI 后端通信
 */

const API_CONFIG = {
    // Java 业务中台地址 (RocketMQ 消息网关, 默认端口 8080)
    baseURL: 'http://127.0.0.1:8080',
    // 请求超时时间 (ms)
    timeout: 150000,  // 2.5分钟，MQ + AI 生成需要时间
};

/**
 * 通用请求方法
 */
async function apiRequest(endpoint, options = {}) {
    const url = `${API_CONFIG.baseURL}${endpoint}`;
    const config = {
        headers: {
            'Content-Type': 'application/json',
        },
        ...options,
    };

    // 设置超时
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), API_CONFIG.timeout);
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
            throw new Error('请求超时，AI引擎处理时间较长，请稍后重试');
        }
        if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError')) {
            throw new Error('无法连接到AI服务，请确认后端服务已启动');
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
 * 表单模式：提交用户数据，获取AI生成计划
 * @param {Object} data - 用户身体数据
 */
async function submitHealthForm(data) {
    return await apiRequest('/api/health-plan', {
        method: 'POST',
        body: JSON.stringify(data),
    });
}

/**
 * 对话模式：发送聊天消息
 * @param {Object} params - 聊天参数
 */
async function sendChatMessage(params) {
    return await apiRequest('/api/chat', {
        method: 'POST',
        body: JSON.stringify(params),
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
