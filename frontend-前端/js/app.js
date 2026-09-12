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
            submitBtn.innerHTML = '<span class="loading-spinner"></span> 正在提交任务...';

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
                // 1. 提交任务：接口异步化后毫秒级返回受理凭证 { recordId, requestId, status }
                const accepted = await submitHealthForm(formData);
                if (accepted.code !== 200 || !accepted.data) {
                    throw new Error(accepted.message || '任务提交失败');
                }
                const recordId = accepted.data.recordId;
                this.showToast('任务已受理，AI 正在生成方案', 'success');

                // 2. 轮询任务状态：1待处理 2处理中 3已完成 4失败
                const plan = await this.pollPlanStatus(recordId, submitBtn);

                // 3. 渲染结果（打字机效果模拟流式输出）
                document.getElementById('formResult').scrollIntoView({ behavior: 'smooth' });
                await this.showFormResult(plan);
                this.showToast('AI计划已生成！', 'success');
            } catch (error) {
                console.error('表单提交失败:', error);
                this.showToast(error.message || '生成失败，请重试', 'error');
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
     * 显示表单结果（带打字机效果）
     *
     * 修正了两处字段名问题：
     * 1. 后端返回的是驼峰命名 trainingPlan，原先读 training_plan 导致「训练与饮食计划」
     *    区块从未渲染出来；
     * 2. bmi_info 字段后端并不存在，原先该区块也从未渲染；改为由身高体重直接计算。
     */
    async showFormResult(data) {
        const resultDiv = document.getElementById('formResult');
        const resultBody = document.getElementById('formResultBody');

        let html = '';

        if (data.assessment) {
            html += `<div class="result-section">
                <h3>📊 体能评估报告</h3>
                <div id="typeAssessment"></div>
            </div>`;
        }

        if (data.trainingPlan) {
            html += `<div class="result-section">
                <h3>🏋️‍♂️ 训练与饮食计划</h3>
                <div id="typeTrainingPlan"></div>
            </div>`;
        }

        if (data.height && data.weight) {
            const h = Number(data.height) / 100;
            const bmi = (Number(data.weight) / (h * h)).toFixed(1);
            html += `<div class="result-section">
                <p style="color: #64748b; font-size: 14px;">📏 参考数据: ${data.height}cm / ${data.weight}kg，BMI ${bmi}</p>
            </div>`;
        }

        resultBody.innerHTML = html;
        resultDiv.style.display = 'block';

        // 逐字渲染，模拟流式输出
        if (data.assessment) {
            await this.typeWriter(document.getElementById('typeAssessment'), data.assessment);
        }
        if (data.trainingPlan) {
            await this.typeWriter(document.getElementById('typeTrainingPlan'), data.trainingPlan);
        }
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
     * 轮询任务状态，直到完成或失败
     *
     * 这是"接口异步化"在客户端的对应实现：提交后不再阻塞等待，
     * 而是每 2 秒查询一次状态，并把等待过程可视化（已等待 Ns / 当前阶段）。
     *
     * @param {number} recordId 任务 id
     * @param {HTMLElement} submitBtn 用于展示进度的按钮
     * @returns {Promise<Object>} 完成后的任务记录
     */
    async pollPlanStatus(recordId, submitBtn) {
        const startAt = Date.now();

        for (let i = 0; i < API_CONFIG.pollMaxAttempts; i++) {
            const resp = await getPlanStatus(recordId);
            if (resp.code !== 200 || !resp.data) {
                throw new Error(resp.message || '查询任务状态失败');
            }

            const task = resp.data;
            const elapsed = Math.round((Date.now() - startAt) / 1000);

            if (task.status === 3) {
                return task;                                  // 已完成
            }
            if (task.status === 4) {
                throw new Error(task.errorMsg || 'AI 生成失败，请重试');   // 失败
            }

            // 状态 1 待处理 / 2 处理中：把等待变得可感知
            if (submitBtn) {
                const stage = task.status === 2 ? 'AI 正在生成方案' : '任务排队中';
                submitBtn.innerHTML =
                    `<span class="loading-spinner"></span> ${stage}... 已等待 ${elapsed}s`;
            }
            await this.wait(API_CONFIG.pollInterval);
        }

        throw new Error('任务处理超时，请稍后重试或查看历史记录');
    },

    /** 简单延时 */
    wait(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    },

    /**
     * 打字机效果：把完整文本逐字写入元素，模拟流式输出的视觉体验
     *
     * 说明：表单模式走 MQ 异步链路，结果是一次性返回的，无法做到 token 级真流式；
     * 这里用逐字渲染让用户看到"内容正在生成"的过程，兼顾体验与架构一致性。
     *
     * @param {HTMLElement} el 目标元素
     * @param {string} text 完整文本
     */
    typeWriter(el, text) {
        return new Promise(resolve => {
            if (!el || !text) {
                resolve();
                return;
            }
            // 文本较长时提高步长，把整体时长控制在数秒内
            const step = Math.max(1, Math.ceil(text.length / 600));
            let i = 0;

            const timer = setInterval(() => {
                i += step;
                if (i >= text.length) {
                    clearInterval(timer);
                    el.innerHTML = this.formatResultText(text);
                    resolve();
                    return;
                }
                el.innerHTML = this.formatResultText(text.slice(0, i));
            }, 16);
        });
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
