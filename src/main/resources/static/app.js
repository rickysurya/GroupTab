// ── Config ──────────────────────────────────────────────────────────────────
const API_BASE   = `${location.protocol}//${location.host}`;
const WS_URL     = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

// ── State ────────────────────────────────────────────────────────────────────
const username        = generateUsername();
let   currentGroupId  = null;
let   currentSub      = null;

// ── STOMP Client ─────────────────────────────────────────────────────────────
const stompClient = new StompJs.Client({
    brokerURL:        WS_URL,
    reconnectDelay:   5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    onConnect:   onConnected,
    onStompError: frame => console.error('STOMP error:', frame.headers['message'], frame.body),
    onDisconnect: ()    => console.log('Disconnected'),
});

async function onConnected(frame) {
    console.log('Connected:', frame);
    const groups = await fetchGroups();
    if (groups.length > 0) {
        switchGroup(groups[0].id, groups[0].name);
    } else {
        setTitle('Create a channel to get started');
    }
}

// ── Bootstrap ────────────────────────────────────────────────────────────────
document.getElementById('display-username').textContent = username;
document.getElementById('user-avatar').textContent      = username.charAt(0).toUpperCase();
stompClient.activate();

// ── Groups ───────────────────────────────────────────────────────────────────
async function fetchGroups() {
    try {
        const res = await fetch(`${API_BASE}/api/groups`);
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

    // Unsubscribe old
    currentSub?.unsubscribe();

    // Subscribe new
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
        const res = await fetch(`${API_BASE}/api/groups`, {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify({ name, adminUsername: username }),
        });
        if (!res.ok) throw new Error(`Status ${res.status}`);
        await fetchGroups();
    } catch (err) {
        alert('Could not create channel. Is the server running?');
        console.error(err);
    }
}

async function deleteCurrentGroup() {
    if (!currentGroupId)                              return;
    if (!confirm('Delete this channel permanently?')) return;

    try {
        const res = await fetch(
            `${API_BASE}/api/groups/${currentGroupId}?admin=${encodeURIComponent(username)}`,
            { method: 'DELETE' }
        );
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

// ── Messages ─────────────────────────────────────────────────────────────────
async function loadHistory(groupId) {
    try {
        const res = await fetch(`${API_BASE}/chat/history/${groupId}`);
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
    if (!content)        { return; }
    if (!currentGroupId) { alert('Select a channel first!'); return; }

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

    // System messages (JOIN / LEAVE)
    if (msg.messageType === 'JOIN' || msg.messageType === 'LEAVE') {
        const el = document.createElement('div');
        el.className   = 'msg-system';
        el.textContent = `${msg.username} ${msg.messageType === 'JOIN' ? 'joined' : 'left'} the channel`;
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

function generateUsername() {
    const adj  = ['Quick', 'Lazy', 'Happy', 'Bold', 'Calm', 'Witty', 'Bright'];
    const noun = ['Fox', 'Bear', 'Wolf', 'Hawk', 'Lynx', 'Puma', 'Crow'];
    return adj[Math.floor(Math.random() * adj.length)]
         + noun[Math.floor(Math.random() * noun.length)]
         + Math.floor(Math.random() * 1000);
}