/**
 * 聊天功能模块
 * 管理聊天消息的显示、发送、历史记录等功能
 */

const ChatManager = {
    // 当前会话ID
    sessionId: 'session_' + Date.now(),
    // 消息历史（用于API请求）
    messageHistory: [],
    // 用户已收集的数据
    userData: {},
    // 是否正在等待AI回复
    isWaiting: false,

    /**
     * 初始化聊天
     */
    init() {
        this.sessionId = 'session_' + Date.now();
        this.messageHistory = [];
        this.userData = {};
        this.isWaiting = false;

        const chatMessages = document.getElementById('chatMessages');
        // 保留欢迎消息
        chatMessages.innerHTML = `
            <div class="message bot">
                <div class="message-avatar"><i class="fa-solid fa-robot"></i></div>
                <div class="message-bubble">
                    <div class="message-text">
                        <p>👋 你好！我是你的专属<strong>智能运动健康助手 FitAI</strong>。</p>
                        <p>我可以帮你：</p>
                        <ul>
                            <li>🏋️ 根据你的身体数据，生成<strong>专属训练计划</strong></li>
                            <li>🥗 提供<strong>科学饮食建议</strong>和营养配比</li>
                            <li>📊 评估你的<strong>体能状态</strong>和运动风险</li>
                            <li>💬 解答关于健身、减脂、增肌的各种问题</li>
                        </ul>
                        <p>让我们先认识一下你吧！请告诉我你的<strong>身高、体重、年龄</strong>和<strong>健身目标</strong>～</p>
                    </div>
                </div>
            </div>
        `;
        this.updateSidebar();
    },

    /**
     * 添加消息到聊天区
     */
    addMessage(role, content, isPlanCard = false) {
        const chatMessages = document.getElementById('chatMessages');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${role}`;

        const avatarIcon = role === 'user'
            ? '<i class="fa-solid fa-user"></i>'
            : '<i class="fa-solid fa-robot"></i>';

        messageDiv.innerHTML = `
            <div class="message-avatar">${avatarIcon}</div>
            <div class="message-bubble">
                <div class="message-text">${isPlanCard ? content : this.formatContent(content)}</div>
            </div>
        `;

        chatMessages.appendChild(messageDiv);
        this.scrollToBottom();
        return messageDiv;
    },

    /**
     * 显示打字动画
     */
    showTyping() {
        const chatMessages = document.getElementById('chatMessages');
        const typingDiv = document.createElement('div');
        typingDiv.className = 'message bot';
        typingDiv.id = 'typingIndicator';
        typingDiv.innerHTML = `
            <div class="message-avatar"><i class="fa-solid fa-robot"></i></div>
            <div class="message-bubble">
                <div class="typing-indicator">
                    <span></span><span></span><span></span>
                </div>
            </div>
        `;
        chatMessages.appendChild(typingDiv);
        this.scrollToBottom();
    },

    /**
     * 移除打字动画
     */
    hideTyping() {
        const typing = document.getElementById('typingIndicator');
        if (typing) typing.remove();
    },

    /**
     * 格式化消息内容
     */
    formatContent(text) {
        if (!text) return '';
        // 转义HTML
        let formatted = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            // Markdown加粗
            .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
            // 换行
            .replace(/\n/g, '<br>')
            // 处理标题
            .replace(/### (.+)/g, '<h4>$1</h4>')
            .replace(/## (.+)/g, '<h3>$1</h3>');

        return formatted;
    },

    /**
     * 添加计划卡片
     */
    addPlanCard(content) {
        const planHtml = `<div class="plan-card">${this.formatContent(content)}</div>`;
        return this.addMessage('bot', planHtml, true);
    },

    /**
     * 发送消息
     */
    async sendMessage(message) {
        if (this.isWaiting || !message.trim()) return;

        this.isWaiting = true;
        const sendBtn = document.getElementById('sendBtn');
        const chatInput = document.getElementById('chatInput');

        sendBtn.disabled = true;
        chatInput.disabled = true;

        // 显示用户消息
        this.addMessage('user', message);
        this.messageHistory.push({ role: 'user', content: message });

        // 显示打字动画
        this.showTyping();

        // 优先尝试流式链路（前端直连 Python AI 中台，SSE 逐 token）
        let handled = false;
        try {
            handled = await this.sendViaStream(message);
        } catch (error) {
            console.error('流式链路异常，降级为同步请求:', error);
            handled = false;
        }

        // 降级：流式链路不可用时，走 Java 中台的同步接口
        if (!handled) {
            try {
                const response = await sendChatMessage({
                    message: message,
                    session_id: this.sessionId,
                    history: this.messageHistory.slice(0, -1),  // 不包含当前消息
                    ...this.userData,  // 已收集的数据
                });

                this.hideTyping();

                if (response.code === 200 && response.data) {
                    this.applyChatData(response.data, 'typewriter');
                } else {
                    this.addMessage('bot', '抱歉，我遇到了一些问题，请稍后重试 😥');
                    this.messageHistory.push({ role: 'assistant', content: '抱歉，遇到问题，请重试。' });
                }
            } catch (error) {
                this.hideTyping();
                console.error('发送消息失败:', error);
                this.addMessage('bot', `⚠️ ${error.message}\n\n请检查网络连接或后端服务是否正常运行。`);
                this.messageHistory.push({ role: 'assistant', content: `错误: ${error.message}` });
            }
        }

        this.isWaiting = false;
        sendBtn.disabled = false;
        chatInput.disabled = false;
        chatInput.focus();
    },

    /**
     * 流式链路发送：前端直连 Python（SSE）。
     * 售后场景逐 token 渲染；售前（结构化输出无 token 流）或链路不可用时
     * 返回 false，由调用方降级为同步请求。
     */
    async sendViaStream(message) {
        this.streamRaw = '';
        this.streamBubble = null;
        let finalRes = null;
        let errRes = null;

        const ok = await sendChatMessageStream(
            {
                message: message,
                session_id: this.sessionId,
                history: this.messageHistory.slice(0, -1),
                ...this.userData,
            },
            {
                onToken: (t) => {
                    if (!this.streamBubble) {
                        this.hideTyping();
                        this.streamBubble = this.addMessage('bot', '');
                    }
                    this.streamRaw += t;
                    this.streamBubble.querySelector('.message-text')
                        .innerHTML = this.formatContent(this.streamRaw);
                    this.scrollToBottom();
                },
                onFinal: (res) => { finalRes = res; },
                onError: (e) => { errRes = e; },
            }
        );

        if (!ok) return false;   // 链路不可用 → 降级
        this.hideTyping();

        // 服务端已明确报错：链路是通的，不能降级重试（否则同一条消息会被后端处理两次）
        if (errRes) {
            this.addMessage('bot', `⚠️ ${errRes.message || 'AI 处理失败，请稍后重试'}`);
            this.messageHistory.push({ role: 'assistant', content: `错误: ${errRes.message || 'AI 处理失败'}` });
            return true;
        }

        if (finalRes && finalRes.code === 200 && finalRes.data) {
            const data = finalRes.data;
            const replyText = data.reply || this.streamRaw || '';
            data.reply = replyText;

            if (replyText && !this.streamRaw) {
                // 售前模式：没有 token 流 → 打字机渲染整段回复
                this.applyChatData(data, 'typewriter');
            } else {
                if (replyText !== this.streamRaw && this.streamBubble) {
                    // 以 final 载荷为准校正流式期间渲染的内容
                    this.streamBubble.querySelector('.message-text')
                        .innerHTML = this.formatContent(replyText);
                }
                this.applyChatData(data, 'none');
            }
            return true;
        }

        // final 缺失但已收到 token 内容：仍视为成功（部分结果好过报错）
        if (this.streamRaw) {
            this.messageHistory.push({ role: 'assistant', content: this.streamRaw });
            return true;
        }
        return false;
    },

    /**
     * 应用回复数据：更新已收集信息、计划卡片与侧边栏（流式/同步两条链路共用）
     * @param {Object} data - 与 /chat data 结构一致的载荷
     * @param {string} renderMode - 'plain' 直接渲染 | 'typewriter' 打字机 | 'none' 气泡已渲染
     */
    applyChatData(data, renderMode = 'plain') {
        // 更新用户数据
        if (data.height) this.userData.height = data.height;
        if (data.weight) this.userData.weight = data.weight;
        if (data.age) this.userData.age = data.age;
        if (data.primary_goal) this.userData.primary_goal = data.primary_goal;

        if (data.reply) {
            if (renderMode === 'plain') {
                this.addMessage('bot', data.reply);
            } else if (renderMode === 'typewriter') {
                this.typewriter(data.reply);
            }
            // 'none'：气泡已由流式/打字机渲染，不再重复添加
            this.messageHistory.push({ role: 'assistant', content: data.reply });
        }

        // 如果已生成计划，显示计划卡片
        if (data.plan_generated && data.training_plan) {
            setTimeout(() => {
                this.addPlanCard(data.training_plan);
            }, 500);
        }

        // 更新侧边栏
        this.updateSidebar();
    },

    /**
     * 打字机动画：整段文本逐字渲染。
     * 售前结构化输出没有 token 流，用它提供与流式一致的视觉体验。
     */
    typewriter(text, speed = 18) {
        const bubble = this.addMessage('bot', '');
        const target = bubble.querySelector('.message-text');
        let shown = 0;
        const timer = setInterval(() => {
            shown = Math.min(shown + 2, text.length);
            target.innerHTML = this.formatContent(text.slice(0, shown));
            this.scrollToBottom();
            if (shown >= text.length) clearInterval(timer);
        }, speed);
    },

    /**
     * 更新侧边栏用户信息
     */
    updateSidebar() {
        const data = this.userData;
        document.getElementById('infoHeight').textContent = data.height ? `${data.height} cm` : '未填写';
        document.getElementById('infoWeight').textContent = data.weight ? `${data.weight} kg` : '未填写';
        document.getElementById('infoAge').textContent = data.age ? `${data.age} 岁` : '未填写';
        document.getElementById('infoGoal').textContent = data.primary_goal || '未填写';

        // 计算并显示BMI
        if (data.height && data.weight) {
            const bmi = data.weight / ((data.height / 100) ** 2);
            const bmiCard = document.getElementById('bmiCard');
            bmiCard.style.display = 'block';
            document.getElementById('infoBMI').textContent = bmi.toFixed(1);

            // BMI颜色提示
            const bmiEl = document.getElementById('infoBMI');
            if (bmi < 18.5) bmiEl.style.color = '#f59e0b';
            else if (bmi < 24) bmiEl.style.color = '#10b981';
            else if (bmi < 28) bmiEl.style.color = '#f59e0b';
            else bmiEl.style.color = '#ef4444';
        }
    },

    /**
     * 滚动到底部
     */
    scrollToBottom() {
        const chatMessages = document.getElementById('chatMessages');
        setTimeout(() => {
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }, 100);
    },
};
