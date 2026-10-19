/**
 * 类ERP平台（模拟第三方系统）前端逻辑（原生 JS，无第三方依赖）
 * 通过 /api/open/** 与图书借阅管理系统交互：
 *   1) 单点登录校验：POST /api/open/sso/validate?ticket=...
 *   2) 待处理/已处理表单：GET  /api/open/forms?ticket=...&status=0|1
 *   3) 办理表单（回传待办+消息）：POST /api/open/forms/{id}/handle
 *   4) 发起新表单（回传待办+消息）：POST /api/open/form/submit
 * 本地接口（本 Node 服务提供）：
 *   5) OA 推送的采购表单：GET /erp/forms 列表，POST /erp/forms 收单（由流程引擎调用）
 *      页面上的「同意 / 不同意」通过 POST /api/open/forms/{formId}/handle + agreed 回执给流程引擎
 */
(function () {
  'use strict'

  var SESSION_KEY = 'erp-session'
  var session = null
  var pendingCache = []
  var oaFormsCache = []
  var oaPollTimer = null

  var $ = function (id) { return document.getElementById(id) }

  /* ------------------------------ 基础工具 ------------------------------ */

  function api(path, options) {
    options = options || {}
    return fetch(path, {
      method: options.method || 'GET',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body: options.body ? JSON.stringify(options.body) : undefined
    }).then(function (res) {
      return res.json().catch(function () {
        throw new Error('响应解析失败（HTTP ' + res.status + '）')
      })
    }).then(function (json) {
      if (!json || json.code !== 200) {
        throw new Error((json && json.message) || '请求失败')
      }
      return json.data
    })
  }

  function escapeHtml(value) {
    if (value === null || value === undefined) return ''
    return String(value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
  }

  function toast(message, ok) {
    var el = $('toast')
    el.textContent = message
    el.className = 'toast ' + (ok === false ? 'toast-error' : 'toast-ok')
    setTimeout(function () { el.className = 'toast hidden' }, 4000)
  }

  function fmtTime(value) {
    if (!value) return '-'
    return String(value).replace('T', ' ').slice(0, 19)
  }

  function saveSession() {
    try { sessionStorage.setItem(SESSION_KEY, JSON.stringify(session)) } catch (e) { /* ignore */ }
  }

  function loadSession() {
    try {
      var raw = sessionStorage.getItem(SESSION_KEY)
      return raw ? JSON.parse(raw) : null
    } catch (e) { return null }
  }

  /* ------------------------------ 登录 / 视图 ------------------------------ */

  function showLogin(statusText, isError) {
    $('app-view').className = 'hidden'
    $('login-view').className = ''
    var bar = $('login-status')
    bar.textContent = statusText || '请从图书借阅管理系统单点登录进入'
    bar.className = 'status-bar ' + (isError ? 'error' : '')
  }

  function showApp() {
    $('login-view').className = 'hidden'
    $('app-view').className = ''
    $('app-name').textContent = session.appName || ('应用#' + session.appId)
    $('user-name').textContent = (session.nickname || session.username || ('用户#' + session.userId))
  }

  function login(ticket) {
    if (!ticket) {
      showLogin('缺少单点登录票据，请从图书借阅管理系统左侧「集成应用」栏目进入', true)
      return
    }
    api('/api/open/sso/validate?ticket=' + encodeURIComponent(ticket), { method: 'POST' })
      .then(function (data) {
        session = {
          appId: data.appId,
          appName: data.appName,
          userId: data.userId,
          username: data.username,
          nickname: data.nickname,
          ticket: ticket
        }
        saveSession()
        showApp()
        loadData()
      })
      .catch(function (err) {
        showLogin(isTicketError(err) ? ticketHint(err) : '单点登录失败：' + err.message, true)
      })
  }

  /* ------------------------------ 票据一次性消费 ------------------------------ */

  var TICKET_USED_HINT = '本次票据已使用，请重新从图书借阅系统点击进入'
  var TICKET_EXPIRED_HINT = '本次票据已失效，请重新从图书借阅系统点击进入'

  function isTicketError(err) {
    var msg = (err && err.message) || ''
    return msg.indexOf('票据已使用') >= 0 ||
      msg.indexOf('票据无效') >= 0 ||
      msg.indexOf('票据已过期') >= 0
  }

  function ticketHint(err) {
    var msg = (err && err.message) || ''
    return msg.indexOf('票据已使用') >= 0 ? TICKET_USED_HINT : TICKET_EXPIRED_HINT
  }

  function clearSession() {
    session = null
    pendingCache = []
    try { sessionStorage.removeItem(SESSION_KEY) } catch (e) { /* ignore */ }
  }

  /**
   * 接口因票据被拒（已使用 / 无效 / 过期）：清掉本地会话并回到登录页，
   * 在页面上给出明确提示，不留白屏或静默失败。
   * @returns {boolean} 是否属于票据类错误（已处理）
   */
  function failByTicket(err) {
    if (!isTicketError(err)) return false
    clearSession()
    showLogin(ticketHint(err), true)
    return true
  }

  /**
   * 写操作成功后票据已被服务端消费：回到登录页提示重新进入，
   * 避免继续用同一票据请求其它接口而触发「票据已使用」。
   */
  function backToLoginAfterConsume(doneText) {
    clearSession()
    showLogin(doneText + '；' + TICKET_USED_HINT, false)
  }

  /* ------------------------------ 数据加载 ------------------------------ */

  function loadData() {
    if (!session) return
    // OA 推送的采购表单来自本端内存仓库，不走票据
    loadOaForms()
    var base = '/api/open/forms?ticket=' + encodeURIComponent(session.ticket)
    api(base + '&status=0').then(function (list) {
      pendingCache = list || []
      renderPending()
    }).catch(function (err) {
      if (failByTicket(err)) return
      $('pending-list').innerHTML = '<p class="empty">加载失败：' + escapeHtml(err.message) + '</p>'
    })
    api(base + '&status=1').then(function (list) {
      renderHandled(list || [])
    }).catch(function (err) {
      if (failByTicket(err)) return
      $('handled-list').innerHTML = '<p class="empty">加载失败</p>'
    })
  }

  function renderPending() {
    var box = $('pending-list')
    if (!pendingCache.length) {
      box.innerHTML = '<p class="empty">暂无待处理表单。可在「图书采购管理」中提交采购单并下发到本应用。</p>'
      return
    }
    var html = pendingCache.map(function (f) {
      return '' +
        '<div class="row">' +
          '<div class="row-main">' +
            '<div class="row-title">' + escapeHtml(f.title) + '</div>' +
            '<div class="row-meta">类型：' + escapeHtml(f.formType) + '　发起人：' + escapeHtml(f.senderName || '-') +
              '　发起时间：' + fmtTime(f.createTime) + '</div>' +
            '<div class="row-content">' + escapeHtml(f.content || '') + '</div>' +
          '</div>' +
          '<div class="row-actions">' +
            '<button class="primary small" data-handle="' + f.id + '">办理</button>' +
          '</div>' +
        '</div>'
    }).join('')
    box.innerHTML = html

    Array.prototype.forEach.call(box.querySelectorAll('[data-handle]'), function (btn) {
      btn.addEventListener('click', function () {
        openHandleModal(Number(btn.getAttribute('data-handle')))
      })
    })
  }

  function renderHandled(list) {
    var box = $('handled-list')
    if (!list.length) {
      box.innerHTML = '<p class="empty">暂无已处理表单。</p>'
      return
    }
    box.innerHTML = list.map(function (f) {
      return '' +
        '<div class="row">' +
          '<div class="row-main">' +
            '<div class="row-title">' + escapeHtml(f.title) + '</div>' +
            '<div class="row-meta">类型：' + escapeHtml(f.formType) + '　发起人：' + escapeHtml(f.senderName || '-') +
              '　处理时间：' + fmtTime(f.handleTime) + '</div>' +
            '<div class="row-content">处理意见：' + escapeHtml(f.handleComment || '-') + '</div>' +
          '</div>' +
          '<div class="row-actions"><span class="tag-ok">已处理</span></div>' +
        '</div>'
    }).join('')
  }

  /* --------------------------- OA 推送的采购表单 --------------------------- */

  /**
   * 拉取本平台收到的 OA 推送采购表单（GET /erp/forms，不校验票据，仅本端内存数据）。
   */
  function loadOaForms() {
    return fetch('/erp/forms', { method: 'GET' })
      .then(function (res) { return res.json() })
      .then(function (json) {
        if (!json || json.code !== 200) {
          throw new Error((json && json.message) || '获取 OA 推送表单失败')
        }
        oaFormsCache = json.data || []
        renderOaForms()
      })
      .catch(function (err) {
        var box = $('oa-forms-list')
        if (box) box.innerHTML = '<p class="empty">加载失败：' + escapeHtml(err.message) + '</p>'
      })
  }

  function renderOaForms() {
    var box = $('oa-forms-list')
    if (!box) return
    if (!oaFormsCache.length) {
      box.innerHTML = '<p class="empty">暂无推送表单。在图书借阅管理系统提交采购单 → 提交申请 → 审批通过后，表单会推送至此。</p>'
      return
    }
    box.innerHTML = oaFormsCache.map(function (f) {
      var amount = f.totalPrice === null || f.totalPrice === undefined ? '-' : f.totalPrice
      // OA 侧流程终止（取消 / 超时打回）后由流程引擎主动撤单：标记已撤销并隐藏审批按钮
      var canceled = String(f.state || '').toUpperCase() === 'CANCELED'
      var cancelLine = canceled
        ? '<div class="row-meta">撤销原因：' + escapeHtml(f.cancelReason || 'OA 侧流程已终止') +
          '　撤销时间：' + fmtTime(f.updateTime) + '</div>'
        : ''
      var commentInput = canceled
        ? ''
        : '<label class="modal-label">审批意见' +
            '<input type="text" data-oa-comment="' + escapeHtml(String(f.erpFormId)) + '" value="已核实，同意采购" maxlength="500" />' +
          '</label>'
      var actions = canceled
        ? '<span class="tag-cancel">已撤销</span>'
        : '<button class="primary small" data-oa-agree="' + escapeHtml(String(f.formId)) + '">同意</button>' +
          '<button class="ghost small" data-oa-reject="' + escapeHtml(String(f.formId)) + '">不同意</button>'
      return '' +
        '<div class="row">' +
          '<div class="row-main">' +
            '<div class="row-title">采购单 ' + escapeHtml(f.purchaseNo || '-') + '：' + escapeHtml(f.bookName || '-') + '</div>' +
            '<div class="row-meta">ERP表单号：' + escapeHtml(String(f.erpFormId)) +
              '　OA表单ID：' + escapeHtml(String(f.formId)) +
              '　申请人：' + escapeHtml(f.applicantName || '-') +
              '　数量：' + escapeHtml(String(f.quantity === undefined ? '-' : f.quantity)) +
              '　金额：' + escapeHtml(String(amount)) +
              '　收单时间：' + fmtTime(f.receivedAt) + '</div>' +
            '<div class="row-meta">ISBN：' + escapeHtml(f.isbn || '-') +
              '　超级节点：' + escapeHtml(f.superNodeName || '-') + '</div>' +
            cancelLine +
            commentInput +
          '</div>' +
          '<div class="row-actions">' + actions + '</div>' +
        '</div>'
    }).join('')

    bindOaDecision(box, 'data-oa-agree', true)
    bindOaDecision(box, 'data-oa-reject', false)
  }

  /**
   * 绑定同意 / 不同意按钮：回执 POST /api/open/forms/{formId}/handle（body 带 agreed）。
   *
   * @param {Element} box        列表容器
   * @param {string}  attr       按钮的 data 属性名
   * @param {boolean} agreed     审批结论
   */
  function bindOaDecision(box, attr, agreed) {
    Array.prototype.forEach.call(box.querySelectorAll('[' + attr + ']'), function (btn) {
      btn.addEventListener('click', function () {
        var row = btn.closest('.row')
        var input = row ? row.querySelector('[data-oa-comment]') : null
        decideOaForm(btn.getAttribute(attr), agreed, input ? input.value.trim() : '')
      })
    })
  }

  /**
   * 提交 ERP 审批结论给流程引擎。票据为写操作一次性，成功后必须重新从图书借阅系统进入。
   *
   * @param {string}  formId  OA 侧集成表单ID
   * @param {boolean} agreed  true-同意 false-不同意
   * @param {string}  comment 审批意见
   */
  function decideOaForm(formId, agreed, comment) {
    if (!session) { toast('请先登录后再办理', false); return }
    api('/api/open/forms/' + encodeURIComponent(formId) + '/handle', {
      method: 'POST',
      body: { ticket: session.ticket, agreed: agreed, handleComment: comment }
    }).then(function () {
      // 写操作成功后票据已被服务端消费，需重新从图书借阅系统进入
      backToLoginAfterConsume(agreed
        ? '已回执「同意」，图书借阅管理系统将完成流程并自动入库'
        : '已回执「不同意」，图书借阅管理系统将把采购单打回')
    }).catch(function (err) {
      if (failByTicket(err)) return
      toast('回执失败：' + err.message, false)
    })
  }

  /* ------------------------------ 办理表单 ------------------------------ */

  function openHandleModal(formId) {
    var form = pendingCache.filter(function (f) { return f.id === formId })[0] || {}
    var mask = document.createElement('div')
    mask.className = 'modal-mask'
    mask.innerHTML = '' +
      '<div class="modal">' +
        '<h3>办理表单</h3>' +
        '<p class="modal-sub">' + escapeHtml(form.title || '') + '</p>' +
        '<label class="modal-label">处理意见' +
          '<textarea id="modal-comment" rows="3" maxlength="500">已审批通过，交采购部执行</textarea>' +
        '</label>' +
        '<div class="modal-actions">' +
          '<button class="ghost" data-cancel>取消</button>' +
          '<button class="primary" data-confirm>确认办理并回传</button>' +
        '</div>' +
      '</div>'
    document.body.appendChild(mask)

    function close() { document.body.removeChild(mask) }
    mask.querySelector('[data-cancel]').addEventListener('click', close)
    mask.querySelector('[data-confirm]').addEventListener('click', function () {
      var comment = mask.querySelector('#modal-comment').value
      api('/api/open/forms/' + formId + '/handle', {
        method: 'POST',
        body: { ticket: session.ticket, handleComment: comment }
      }).then(function () {
        close()
        // 写操作成功后票据已被服务端消费，需重新从图书借阅系统进入
        backToLoginAfterConsume('办理完成，已回传待办与消息')
      }).catch(function (err) {
        if (failByTicket(err)) { close(); return }
        toast('办理失败：' + err.message, false)
      })
    })
  }

  /* ------------------------------ 发起新表单 ------------------------------ */

  function bindSubmitForm() {
    $('submit-form').addEventListener('submit', function (e) {
      e.preventDefault()
      if (!session) { toast('请先登录', false); return }
      var quantity = Number($('f-quantity').value) || 1
      api('/api/open/form/submit', {
        method: 'POST',
        body: {
          ticket: session.ticket,
          title: $('f-title').value,
          formType: $('f-type').value,
          content: JSON.stringify({ quantity: quantity, source: '类ERP平台' }),
          handleComment: $('f-comment').value
        }
      }).then(function () {
        // 写操作成功后票据已被服务端消费，需重新从图书借阅系统进入
        backToLoginAfterConsume('已回传，图书借阅管理系统将生成一条待办与一条消息')
      }).catch(function (err) {
        if (failByTicket(err)) return
        toast('提交失败：' + err.message, false)
      })
    })
  }

  /* ------------------------------ 启动 ------------------------------ */

  function boot() {
    $('self-port').textContent = location.port || '80'
    bindSubmitForm()
    $('refresh-btn').addEventListener('click', loadData)
    $('oa-refresh-btn').addEventListener('click', loadOaForms)
    // 轮询 OA 推送的采购表单：流程引擎下发后无需手动刷新即可看到
    if (oaPollTimer === null) {
      oaPollTimer = setInterval(loadOaForms, 5000)
    }
    $('logout-btn').addEventListener('click', function () {
      try { sessionStorage.removeItem(SESSION_KEY) } catch (e) { /* ignore */ }
      session = null
      showLogin('已退出，请重新从图书借阅管理系统单点登录进入')
    })
    $('manual-login-btn').addEventListener('click', function () {
      login($('ticket-input').value.trim())
    })

    var params = new URLSearchParams(location.search)
    var ticket = params.get('ticket')
    if (ticket) {
      login(ticket)
      return
    }
    var cached = loadSession()
    if (cached && cached.ticket) {
      session = cached
      showApp()
      loadData()
      return
    }
    showLogin('请从图书借阅管理系统左侧「集成应用」栏目点击进入本平台', false)
  }

  document.addEventListener('DOMContentLoaded', boot)
})()
