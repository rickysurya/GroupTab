// ── Config ───────────────────────────────────────────────────────────────────
const API_BASE = `${location.protocol}//${location.host}`;
const WS_URL   = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

// ── Auth State ────────────────────────────────────────────────────────────────
// Token and username are stored in sessionStorage so they survive page refresh
// but are cleared when the browser tab is closed
let token    = sessionStorage.getItem('token');
let username = sessionStorage.getItem('username');
let authMode = 'login'; // tracks which tab is active on the auth screen

// ── App State ─────────────────────────────────────────────────────────────────
let currentGroupId = null;
let currentSub     = null;

// ── STOMP Client ──────────────────────────────────────────────────────────────
// Defined here but only activated after login
const stompClient = new StompJs.Client({
    brokerURL:         WS_URL,
    reconnectDelay:    5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,

    // NEW: send JWT in STOMP CONNECT headers so WebSocketAuthInterceptor can validate it
    connectHeaders: () => ({ Authorization: `Bearer ${token}` }),

    onConnect:    onConnected,
    onStompError: frame => console.error('STOMP error:', frame.headers['message'], frame.body),
});

// ── Bootstrap ─────────────────────────────────────────────────────────────────
// If already logged in from a previous session, skip the auth screen
if (token && username) {
    showApp();
} else {
    showAuthScreen();
}

// ── Auth Screen ───────────────────────────────────────────────────────────────
function showAuthScreen() {
    document.getElementById('auth-screen').classList.remove('hidden');
    document.getElementById('app').classList.add('hidden');
}

function showApp() {
    document.getElementById('auth-screen').classList.add('hidden');
    document.getElementById('app').classList.remove('hidden');
    document.getElementById('display-username').textContent = username;
    document.getElementById('user-avatar').textContent      = username.charAt(0).toUpperCase();
    stompClient.activate();
}

// Switches between Login and Register tabs
function switchAuthTab(mode) {
    authMode = mode;
    document.querySelectorAll('.auth-tab').forEach((tab, i) => {
        tab.classList.toggle('active', (i === 0 && mode === 'login') || (i === 1 && mode === 'register'));
    });
    document.getElementById('auth-submit-btn').textContent = mode === 'login' ? 'Sign in' : 'Register';
    document.getElementById('auth-error').classList.add('hidden');
}

// Called when the submit button is clicked — routes to login or register
async function submitAuth() {
    const usernameVal = document.getElementById('auth-username').value.trim();
    const passwordVal = document.getElementById('auth-password').value;
    const btn         = document.getElementById('auth-submit-btn');
    const errorEl     = document.getElementById('auth-error');

    if (!usernameVal || !passwordVal) {
        showAuthError('Please fill in all fields');
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Please wait...';
    errorEl.classList.add('hidden');

    try {
        const endpoint = authMode === 'login' ? '/auth/login' : '/auth/register';
        const res = await fetch(`${API_BASE}${endpoint}`, {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify({ username: usernameVal, password: passwordVal }),
        });

        const data = await res.json();

        if (!res.ok) {
            // Show the error message from the server (e.g. "Username already taken")
            showAuthError(data.message || 'Something went wrong');
            return;
        }

        // Save token and username — used for all subsequent requests
        token    = data.token;
        username = data.username;
        sessionStorage.setItem('token', token);
        sessionStorage.setItem('username', username);

        showApp();
    } catch (err) {
        showAuthError('Could not reach the server');
        console.error(err);
    } finally {
        btn.disabled = false;
        btn.textContent = authMode === 'login' ? 'Sign in' : 'Register';
    }
}

function showAuthError(message) {
    const el = document.getElementById('auth-error');
    el.textContent = message;
    el.classList.remove('hidden');
}

// Clears session and returns to auth screen
function logout() {
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('username');
    token    = null;
    username = null;
    stompClient.deactivate();
    currentGroupId = null;
    currentSub     = null;
    showAuthScreen();
}

// ── Helpers: Authenticated fetch ──────────────────────────────────────────────
// All API calls go through this so the JWT is always attached
function authFetch(url, options = {}) {
    return fetch(url, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            ...options.headers,
        },
    });
}

// ── WebSocket ─────────────────────────────────────────────────────────────────
async function onConnected() {
    const groups = await fetchGroups();
    if (groups.length > 0) {
        switchGroup(groups[0].id, groups[0].name);
    } else {
        setTitle('Create a channel to get started');
    }
}

// ── Groups ────────────────────────────────────────────────────────────────────
async function fetchGroups() {
    try {
        // NEW: uses authFetch so JWT is sent in Authorization header
        const res = await authFetch(`${API_BASE}/api/groups`);
        if (!res.ok) throw new Error(`Status ${res.status}`);
        const groups = await res.json();
        renderGroupList(groups);
        return groups;
    } catch (err) {
        console.error('Failed to load groups:', err);
        document.getElementById('group-list').innerHTML =
            '<li class="error">⚠ Could not load channels</li>';
        return [];
    }
}

function renderGroupList(groups) {
    const list = document.getElementById('group-list');
    list.innerHTML = '';
    groups.forEach(g => {
        const li = document.createElement('li');
        li.textContent     = `# ${g.name}`;
        li.dataset.groupId = g.id;
        li.onclick         = () => switchGroup(g.id, g.name);
        if (g.id === currentGroupId) li.classList.add('active');
        list.appendChild(li);
    });
}

function switchGroup(groupId, groupName) {
    if (currentGroupId === groupId) return;
    currentGroupId = groupId;
    setTitle(`# ${groupName}`);
    document.getElementById('messages').innerHTML = '';
    currentSub?.unsubscribe();
    currentSub = stompClient.subscribe(`/topic/group.${groupId}`, msg => {
        appendMessage(JSON.parse(msg.body));
    });
    loadHistory(groupId);
    highlightActiveGroup(groupId);
}

function highlightActiveGroup(groupId) {
    document.querySelectorAll('#group-list li').forEach(li => {
        li.classList.toggle('active', Number(li.dataset.groupId) === groupId);
    });
}

async function promptNewGroup() {
    const name = prompt('Channel name:')?.trim();
    if (!name) return;

    try {
        // NEW: uses authFetch, removed adminUsername from body (server gets it from JWT)
        const res = await authFetch(`${API_BASE}/api/groups`, {
            method: 'POST',
            body:   JSON.stringify({ name }),
        });
        if (!res.ok) {
            const data = await res.json();
            throw new Error(data.message || 'Could not create channel');
        }
        await fetchGroups();
    } catch (err) {
        alert(err.message);
        console.error(err);
    }
}

async function deleteCurrentGroup() {
    if (!currentGroupId)                              return;
    if (!confirm('Delete this channel permanently?')) return;

    try {
        // NEW: uses authFetch, removed ?admin= query param (server gets identity from JWT)
        const res = await authFetch(`${API_BASE}/api/groups/${currentGroupId}`, {
            method: 'DELETE',
        });
        if (res.status === 403) throw new Error('Only the channel admin can delete it.');
        if (!res.ok)            throw new Error(`Delete failed (${res.status})`);

        currentSub?.unsubscribe();
        currentSub     = null;
        currentGroupId = null;
        document.getElementById('messages').innerHTML = '';
        setTitle('Select a channel');
        await fetchGroups();
    } catch (err) {
        alert(err.message);
    }
}

// ── Messages ──────────────────────────────────────────────────────────────────
async function loadHistory(groupId) {
    try {
        // NEW: uses authFetch
        const res = await authFetch(`${API_BASE}/chat/history/${groupId}`);
        if (!res.ok) throw new Error(`Status ${res.status}`);
        const history = await res.json();
        document.getElementById('messages').innerHTML = '';
        history.forEach(msg => appendMessage(msg));
    } catch (err) {
        console.error('Could not load history:', err);
    }
}

function sendMessage() {
    const input   = document.getElementById('message-input');
    const content = input.value.trim();
    if (!content)        return;
    if (!currentGroupId) { alert('Select a channel first!'); return; }

    stompClient.publish({
        destination: `/app/chat/${currentGroupId}`,
        body: JSON.stringify({
            username,               // from session, identifies who sent it in the UI
            content,
            groupId:     currentGroupId,
            messageType: 'CHAT',
        }),
    });

    input.value = '';
}

function appendMessage(msg) {
    const container = document.getElementById('messages');

    if (msg.messageType === 'JOIN' || msg.messageType === 'LEAVE') {
        const el = document.createElement('div');
        el.className   = 'msg-system';
        el.textContent = `${msg.username} ${msg.messageType === 'JOIN' ? 'joined' : 'left'}`;
        container.appendChild(el);
        scrollToBottom();
        return;
    }

    const isOwn = msg.username === username;
    const time  = msg.timestamp
        ? new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        : 'now';

    const msgEl = document.createElement('div');
    msgEl.className = `msg${isOwn ? ' own' : ''}`;
    msgEl.innerHTML = `
        <div class="msg-meta">
            <span class="msg-author">${escapeHtml(msg.username)}</span>
            <span class="msg-time">${time}</span>
        </div>
        <div class="msg-bubble">${escapeHtml(msg.content)}</div>
    `;

    container.appendChild(msgEl);
    scrollToBottom();
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function setTitle(text) {
    document.getElementById('chat-title').textContent = text;
}

function scrollToBottom() {
    const el = document.getElementById('messages');
    el.scrollTop = el.scrollHeight;
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}