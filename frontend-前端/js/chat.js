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

        try {
            const response = await sendChatMessage({
                message: message,
                session_id: this.sessionId,
                history: this.messageHistory.slice(0, -1),  // 不包含当前消息
                ...this.userData,  // 已收集的数据
            });

            this.hideTyping();

            if (response.code === 200 && response.data) {
                const data = response.data;

                // 更新用户数据
                if (data.height) this.userData.height = data.height;
                if (data.weight) this.userData.weight = data.weight;
                if (data.age) this.userData.age = data.age;
                if (data.primary_goal) this.userData.primary_goal = data.primary_goal;

                // 显示AI回复
                if (data.reply) {
                    this.addMessage('bot', data.reply);
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
            } else {
                this.addMessage('bot', '抱歉，我遇到了一些问题，请稍后重试 😥');
                this.messageHistory.push({ role: 'assistant', content: '抱歉，遇到问题，请重试。' });
            }
        } catch (error) {
            this.hideTyping();
            console.error('发送消息失败:', error);
            this.addMessage('bot', `⚠️ ${error.message}\n\n请检查网络连接或后端服务是否正常运行。`);
            this.messageHistory.push({ role: 'assistant', content: `错误: ${error.message}` });
        } finally {
            this.isWaiting = false;
            sendBtn.disabled = false;
            chatInput.disabled = false;
            chatInput.focus();
        }
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
