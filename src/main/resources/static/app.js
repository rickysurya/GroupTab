// ── Config ───────────────────────────────────────────────────────────────────
const API_BASE = `${location.protocol}//${location.host}`;
const WS_URL   = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

// ── Auth State ────────────────────────────────────────────────────────────────
let token    = sessionStorage.getItem('token');
let username = sessionStorage.getItem('username');
let authMode = 'login';

// ── App State ─────────────────────────────────────────────────────────────────
let currentGroupId = null;
let currentSub     = null;

// ── STOMP Client ──────────────────────────────────────────────────────────────
let stompClient = null;

function createStompClient() {
    return new StompJs.Client({
        brokerURL:         WS_URL,
        reconnectDelay:    5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        connectHeaders:    { Authorization: `Bearer ${token}` },
        onConnect:    onConnected,
        onStompError: frame => {
            console.error('STOMP error:', frame.headers['message'], frame.body);
            showToast('Connection error — retrying...', 'error');
        },
    });
}

// ── Bootstrap ─────────────────────────────────────────────────────────────────
if (token && username) {
    showApp();
} else {
    showAuthScreen();
}

// ── Auth ──────────────────────────────────────────────────────────────────────
function showAuthScreen() {
    document.getElementById('auth-screen').classList.remove('hidden');
    document.getElementById('app').classList.add('hidden');
}

function showApp() {
    document.getElementById('auth-screen').classList.add('hidden');
    document.getElementById('app').classList.remove('hidden');
    document.getElementById('display-username').textContent = username;
    document.getElementById('user-avatar').textContent      = username.charAt(0).toUpperCase();
    stompClient = createStompClient();
    stompClient.activate();
}

function switchAuthTab(mode) {
    authMode = mode;
    document.querySelectorAll('.auth-tab').forEach((tab, i) => {
        tab.classList.toggle('active', (i === 0 && mode === 'login') || (i === 1 && mode === 'register'));
    });
    document.getElementById('auth-submit-btn').textContent = mode === 'login' ? 'Sign in' : 'Register';
    document.getElementById('auth-error').classList.add('hidden');
}

async function submitAuth() {
    const usernameVal = document.getElementById('auth-username').value.trim();
    const passwordVal = document.getElementById('auth-password').value;
    const btn         = document.getElementById('auth-submit-btn');
    const errorEl     = document.getElementById('auth-error');

    if (!usernameVal || !passwordVal) {
        showAuthError('Please fill in all fields');
        return;
    }

    btn.disabled    = true;
    btn.textContent = 'Please wait...';
    errorEl.classList.add('hidden');

    try {
        const endpoint = authMode === 'login' ? '/auth/login' : '/auth/register';
        const res  = await fetch(`${API_BASE}${endpoint}`, {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify({ username: usernameVal, password: passwordVal }),
        });
        const data = await res.json();

        if (!res.ok) {
            showAuthError(data.message || 'Something went wrong');
            return;
        }

        token    = data.token;
        username = data.username;
        sessionStorage.setItem('token', token);
        sessionStorage.setItem('username', username);
        // If redirected here from an invite link, go finish joining
        const pendingInvite = sessionStorage.getItem('pendingInvite');
        if (pendingInvite) {
            sessionStorage.removeItem('pendingInvite');
            location.href = `/join/${pendingInvite}`;
            return;
        }
        showApp();
    } catch (err) {
        showAuthError('Could not reach the server');
        console.error(err);
    } finally {
        btn.disabled    = false;
        btn.textContent = authMode === 'login' ? 'Sign in' : 'Register';
    }
}

function showAuthError(message) {
    const el = document.getElementById('auth-error');
    el.textContent = message;
    el.classList.remove('hidden');
}

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

// ── Authenticated fetch helper ────────────────────────────────────────────────
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

    // Hide delete banner when switching channels so it doesn't carry over
    hideDeleteConfirm();

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

// ── New Channel Inline Form ───────────────────────────────────────────────────
// REPLACED: prompt('Channel name') → inline form that appears in the sidebar

// Toggles the inline form open or closed when + is clicked
function toggleNewChannel() {
    const form     = document.getElementById('new-channel-form');
    const isHidden = form.classList.contains('hidden');

    if (isHidden) {
        // Show the form and focus the input so user can type immediately
        form.classList.remove('hidden');
        document.getElementById('new-channel-input').focus();
        // Change + to × so user knows clicking again will close it
        document.getElementById('new-channel-btn').textContent = '×';
    } else {
        cancelNewGroup();
    }
}

// Hides the form and resets its state
function cancelNewGroup() {
    document.getElementById('new-channel-form').classList.add('hidden');
    document.getElementById('new-channel-input').value = '';
    // Restore + button
    document.getElementById('new-channel-btn').textContent = '+';
}

// Called when user clicks Create or presses Enter inside the input
async function confirmNewGroup() {
    const name = document.getElementById('new-channel-input').value.trim();

    if (!name) {
        // REPLACED: alert('...') → toast so user gets feedback without a popup
        showToast('Channel name cannot be empty', 'error');
        return;
    }

    try {
        const res  = await authFetch(`${API_BASE}/api/groups`, {
            method: 'POST',
            body:   JSON.stringify({ name }),
        });

        console.log(`${API_BASE}/api/groups`);
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Could not create channel');

        cancelNewGroup();        // close and reset the form on success
        await fetchGroups();     // refresh the channel list
        // REPLACED: no feedback before → success toast
        showToast(`#${name} created`, 'success');
    } catch (err) {
        // REPLACED: alert(err.message) → error toast
        showToast(err.message, 'error');
        console.error(err);
    }
}

// ── Delete Confirmation Banner ────────────────────────────────────────────────
// REPLACED: confirm('Are you sure?') → inline banner below the chat header

// Shows the red confirmation banner when Delete button is clicked
function showDeleteConfirm() {
    if (!currentGroupId) {
        // REPLACED: alert('Select a channel first') → info toast
        showToast('Select a channel first', 'info');
        return;
    }
    // Remove hidden to show the banner — CSS animates it sliding down
    document.getElementById('delete-confirm').classList.remove('hidden');
}

// Hides the banner without doing anything — Cancel button
function hideDeleteConfirm() {
    document.getElementById('delete-confirm').classList.add('hidden');
}

// Called when user clicks "Yes, delete" inside the confirmation banner
async function confirmDelete() {
    hideDeleteConfirm(); // dismiss the banner immediately

    try {
        const res = await authFetch(`${API_BASE}/api/groups/${currentGroupId}`, {
            method: 'DELETE',
        });

        if (res.status === 403) throw new Error('Only the channel admin can delete it');
        if (!res.ok)            throw new Error(`Delete failed (${res.status})`);

        // Clean up state after successful deletion
        currentSub?.unsubscribe();
        currentSub     = null;
        currentGroupId = null;
        document.getElementById('messages').innerHTML = '';
        setTitle('Select a channel');
        await fetchGroups();
        // REPLACED: no feedback after delete → info toast
        showToast('Channel deleted', 'info');
    } catch (err) {
        // REPLACED: alert(err.message) → error toast
        showToast(err.message, 'error');
    }
}

// ── Messages ──────────────────────────────────────────────────────────────────
async function loadHistory(groupId) {
    try {
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
    if (!content) return;

    if (!currentGroupId) {
        // REPLACED: alert('Select a channel first') → info toast
        showToast('Select a channel first', 'info');
        return;
    }

    stompClient.publish({
        destination: `/app/chat/${currentGroupId}`,
        body: JSON.stringify({
            username,
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

// ── Toast ─────────────────────────────────────────────────────────────────────
// NEW: replaces all alert() calls — shows a non-blocking notification at the bottom
// type can be 'info' (black), 'success' (yellow), or 'error' (red)
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');

    // Create the toast element
    const toast = document.createElement('div');
    toast.className   = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);

    // After 3 seconds, play the exit animation then remove from DOM
    setTimeout(() => {
        toast.style.animation = 'toastOut 0.2s ease forwards';
        setTimeout(() => toast.remove(), 200); // wait for animation to finish before removing
    }, 3000);
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

// ── Invite ────────────────────────────────────────────────────────────────────
// Generates an invite link for the current channel and copies it to clipboard
async function copyInvite() {
    if (!currentGroupId) {
        showToast('Select a channel first', 'info');
        return;
    }

    try {
        const res  = await authFetch(`${API_BASE}/api/groups/${currentGroupId}/invite`, {
            method: 'POST',
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Could not generate invite');

        // Copy the invite URL to clipboard
        await navigator.clipboard.writeText(data.inviteUrl);
        showToast('Invite link copied', 'success');
    } catch (err) {
        showToast(err.message, 'error');
    }
}