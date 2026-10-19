/**
 * 类ERP平台（模拟第三方系统）前端逻辑（原生 JS，无第三方依赖）
 * 通过 /api/open/** 与图书借阅管理系统交互：
 *   1) 单点登录校验：POST /api/open/sso/validate?ticket=...
 *   2) 待处理/已处理表单：GET  /api/open/forms?ticket=...&status=0|1
 *   3) 办理表单（回传待办+消息）：POST /api/open/forms/{id}/handle
 *   4) 发起新表单（回传待办+消息）：POST /api/open/form/submit
 */
(function () {
  'use strict'

  var SESSION_KEY = 'erp-session'
  var session = null
  var pendingCache = []

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
        showLogin('单点登录失败：' + err.message, true)
      })
  }

  /* ------------------------------ 数据加载 ------------------------------ */

  function loadData() {
    if (!session) return
    var base = '/api/open/forms?ticket=' + encodeURIComponent(session.ticket)
    api(base + '&status=0').then(function (list) {
      pendingCache = list || []
      renderPending()
    }).catch(function (err) {
      $('pending-list').innerHTML = '<p class="empty">加载失败：' + escapeHtml(err.message) + '</p>'
    })
    api(base + '&status=1').then(function (list) {
      renderHandled(list || [])
    }).catch(function () {
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
        toast('办理完成，已回传待办与消息至图书借阅管理系统')
        loadData()
      }).catch(function (err) {
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
        toast('已回传，图书借阅管理系统将生成一条待办与一条消息')
        loadData()
      }).catch(function (err) {
        toast('提交失败：' + err.message, false)
      })
    })
  }

  /* ------------------------------ 启动 ------------------------------ */

  function boot() {
    $('self-port').textContent = location.port || '80'
    bindSubmitForm()
    $('refresh-btn').addEventListener('click', loadData)
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
