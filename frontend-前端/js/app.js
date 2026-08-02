/**
 * 主应用逻辑
 * 管理视图切换、表单提交、全局事件等
 */

const App = {
    currentView: 'chat',

    /**
     * 初始化
     */
    async init() {
        this.bindViewNavigation();
        this.bindChatEvents();
        this.bindFormEvents();
        this.bindQuickActions();
        this.checkServerStatus();

        // 初始化聊天
        ChatManager.init();

        // 定期检查服务器状态
        setInterval(() => this.checkServerStatus(), 30000);
    },

    /**
     * 检查服务器连接状态
     */
    async checkServerStatus() {
        const dot = document.getElementById('statusDot');
        const text = document.getElementById('statusText');

        const online = await checkHealth();
        if (online) {
            dot.className = 'status-dot online';
            text.textContent = 'AI引擎在线';
        } else {
            dot.className = 'status-dot offline';
            text.textContent = 'AI引擎离线';
        }
    },

    /**
     * 视图导航
     */
    bindViewNavigation() {
        const navBtns = document.querySelectorAll('.nav-btn');
        navBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const viewName = btn.dataset.view;
                this.switchView(viewName);

                // 更新导航按钮状态
                navBtns.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
            });
        });
    },

    /**
     * 切换视图
     */
    switchView(viewName) {
        // 隐藏所有视图
        document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
        // 显示目标视图
        const targetView = document.getElementById(`view-${viewName}`);
        if (targetView) {
            targetView.classList.add('active');
            this.currentView = viewName;
        }
    },

    /**
     * 聊天事件绑定
     */
    bindChatEvents() {
        const chatInput = document.getElementById('chatInput');
        const sendBtn = document.getElementById('sendBtn');
        const resetBtn = document.getElementById('resetChatBtn');
        const sidebarToggle = document.getElementById('sidebarToggle');
        const charCount = document.getElementById('charCount');

        // 发送按钮点击
        sendBtn.addEventListener('click', () => {
            const message = chatInput.value.trim();
            if (message) {
                ChatManager.sendMessage(message);
                chatInput.value = '';
                this.updateCharCount();
            }
        });

        // 回车发送，Shift+回车换行
        chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                const message = chatInput.value.trim();
                if (message) {
                    ChatManager.sendMessage(message);
                    chatInput.value = '';
                    this.updateCharCount();
                }
            }
        });

        // 自动调整输入框高度
        chatInput.addEventListener('input', () => {
            chatInput.style.height = 'auto';
            chatInput.style.height = Math.min(chatInput.scrollHeight, 150) + 'px';
            this.updateCharCount();
        });

        // 字符计数
        const updateCharCountFn = () => this.updateCharCount();
        chatInput.addEventListener('input', updateCharCountFn);

        // 重置按钮
        resetBtn.addEventListener('click', () => {
            if (confirm('确定要重新开始对话吗？对话历史将被清除。')) {
                ChatManager.init();
                this.showToast('对话已重置', 'success');
            }
        });

        // 侧边栏折叠
        sidebarToggle.addEventListener('click', () => {
            const sidebar = document.getElementById('chatSidebar');
            sidebar.classList.toggle('collapsed');
            const icon = sidebarToggle.querySelector('i');
            if (sidebar.classList.contains('collapsed')) {
                icon.className = 'fa-solid fa-angles-right';
            } else {
                icon.className = 'fa-solid fa-angles-left';
            }
        });
    },

    /**
     * 快捷操作按钮
     */
    bindQuickActions() {
        const quickBtns = document.querySelectorAll('.quick-btn');
        quickBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const msg = btn.dataset.msg;
                if (msg) {
                    ChatManager.sendMessage(msg);
                }
                // 如果不在聊天视图，先切换过来
                if (this.currentView !== 'chat') {
                    this.switchView('chat');
                    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
                    document.querySelector('[data-view="chat"]').classList.add('active');
                }
            });
        });
    },

    /**
     * 更新字符计数
     */
    updateCharCount() {
        const chatInput = document.getElementById('chatInput');
        const charCount = document.getElementById('charCount');
        if (chatInput && charCount) {
            charCount.textContent = `${chatInput.value.length}/2000`;
        }
    },

    /**
     * 表单事件绑定
     */
    bindFormEvents() {
        const form = document.getElementById('healthForm');
        const submitBtn = document.getElementById('formSubmitBtn');

        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            // 禁用按钮
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="loading-spinner"></span> AI正在分析中，请稍候...';

            // 收集表单数据
            const formData = {
                height: parseFloat(document.getElementById('formHeight').value),
                weight: parseFloat(document.getElementById('formWeight').value),
                age: parseInt(document.getElementById('formAge').value),
                movement_type: document.getElementById('formMovement').value,
                current_1rm: parseFloat(document.getElementById('form1RM').value) || 0,
                primary_goal: document.getElementById('formGoal').value.trim(),
                dormitory_rules: document.getElementById('formLimits').value.trim() || null,
            };

            try {
                const response = await submitHealthForm(formData);

                if (response.code === 200 && response.data) {
                    this.showFormResult(response.data);
                    this.showToast('AI计划已生成！', 'success');

                    // 滚动到结果区域
                    document.getElementById('formResult').scrollIntoView({ behavior: 'smooth' });
                } else {
                    this.showToast(response.message || '生成失败，请重试', 'error');
                }
            } catch (error) {
                console.error('表单提交失败:', error);
                this.showToast(error.message || '网络错误，请检查后端服务', 'error');
            } finally {
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<i class="fa-solid fa-wand-magic-sparkles"></i> 生成我的专属计划';
            }
        });

        // 表单重置
        form.addEventListener('reset', () => {
            const result = document.getElementById('formResult');
            result.style.display = 'none';
        });
    },

    /**
     * 显示表单结果
     */
    showFormResult(data) {
        const resultDiv = document.getElementById('formResult');
        const resultBody = document.getElementById('formResultBody');

        let html = '';

        if (data.assessment) {
            html += `<div class="result-section">
                <h3>📊 体能评估报告</h3>
                <div>${this.formatResultText(data.assessment)}</div>
            </div>`;
        }

        if (data.training_plan) {
            html += `<div class="result-section">
                <h3>🏋️‍♂️ 训练与饮食计划</h3>
                <div>${this.formatResultText(data.training_plan)}</div>
            </div>`;
        }

        if (data.bmi_info) {
            html += `<div class="result-section">
                <p style="color: #64748b; font-size: 14px;">📏 BMI参考数据: ${data.bmi_info}</p>
            </div>`;
        }

        resultBody.innerHTML = html;
        resultDiv.style.display = 'block';
    },

    /**
     * 格式化结果文本
     */
    formatResultText(text) {
        if (!text) return '';
        return text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
            .replace(/\n/g, '<br>')
            .replace(/### (.+)/g, '<h3>$1</h3>')
            .replace(/## (.+)/g, '<h2>$1</h2>')
            .replace(/- (.+)/g, '<li>$1</li>');
    },

    /**
     * Toast 通知
     */
    showToast(message, type = 'success') {
        const container = document.getElementById('toastContainer');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;

        container.appendChild(toast);

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(100%)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    },
};

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', () => {
    App.init();
});
