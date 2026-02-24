
var username = generateRandomUsername();

document.getElementById('display-username').innerText = username;
document.getElementById('user-avatar').innerText = username.charAt(0);

function generateRandomUsername() {
    var adjectives = ["Quick", "Lazy", "Happy", "Sad", "Angry"];
    var nouns = ["Fox", "Dog", "Cat", "Mouse", "Bear"];
    var adjective = adjectives[Math.floor(Math.random() * adjectives.length)];
    var noun = nouns[Math.floor(Math.random() * nouns.length)];
    return adjective + noun + Math.floor(Math.random() * 1000);
}



const stompClient = new StompJs.Client({
    brokerURL: 'ws://localhost:8080/ws',
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
});

function connect() {
    stompClient.onConnect = async (frame) => {
        console.log('Connected: ' + frame);
        const groups = await loadGroups();
        if (groups && groups.length > 0) {
            setTimeout(() => switchGroup(groups[0].id, groups[0].name), 300);
        }
    };

    stompClient.onStompError = (frame) => { /* your error logs */ };

    stompClient.activate();
}
//function connect() {
//    console.log("starting stomp connection");
//    stompClient = new StompJs.Client({
//        brokerURL: 'ws://localhost:8080/ws',
//        onConnect: async (frame) => {
//            console.log('Connected: ' + frame);
//
//            const groups = await loadGroups();
//            if (groups && groups.length > 0){
//                switchGroup(groups[0].id, groups[0].name);
//            } else {
//                document.querySelector('.chat-header h2').innerText = "Create a group to start!";
//            }
//
////            stompClient.subscribe('/topic/messages', function (message) {
////                showMessage(JSON.parse(message.body));
////            });
//        }, onStompError: (frame) => {
//                       console.error('Broker reported error: ' + frame.headers['message']);
//                       console.error('Additional details: ' + frame.body);
//        }
//    });
//    stompClient.activate();
//    console.log("stomp client activated");
//}

let currentGroupId = null;
let currentSubscription = null;

async function loadGroups() {
    try {
        const response = await fetch('http://localhost:8080/api/groups');
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

        const groups = await response.json();
        const list = document.getElementById('group-list');
        list.innerHTML = '';

        groups.forEach(group => {
            const li = document.createElement('li');
            li.innerText = `# ${group.name}`;
            li.dataset.groupId = group.id;  // ✅ ADD THIS
            li.onclick = () => switchGroup(group.id, group.name);
            if (group.id === currentGroupId) li.classList.add('active');
            list.appendChild(li);
        });
        // for connect function
        return groups;
    } catch (error) {
        console.error("Failed to load groups:", error);
        // User feedback: Let them know the sidebar failed
        document.getElementById('group-list').innerHTML = '<li class="error">Failed to load channels</li>';
    }
}

// 2. Switch context to a new group
function switchGroup(groupId, groupName) {
    if (currentGroupId === groupId) return;

    currentGroupId = groupId;
    document.querySelector('.chat-header h2').innerText = `# ${groupName}`;

    // Clear old messages from view
    document.getElementById('messages').innerHTML = "";

    // Unsubscribe from the old group topic if it exists
    if (currentSubscription) {
        currentSubscription.unsubscribe();
        currentSubscription = null;
    }

    // Subscribe to the NEW group topic
    currentSubscription = stompClient.subscribe(`/topic/group/${groupId}`, (message) => {
            showMessage(JSON.parse(message.body));
    });
    // Load history for this specific group
    loadHistory(groupId);
    //
        document.querySelectorAll('#group-list li').forEach(li => {
            li.classList.toggle('active', li.dataset.groupId == groupId);
        });
}

async function promptNewGroup() {
    const name = prompt("Enter group name:");
    if (!name || name.trim() === "") return;

    try {
        const response = await fetch('http://localhost:8080/api/groups', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: name, adminUsername: username })
        });

        if (!response.ok) throw new Error("Could not create group");

        await loadGroups(); // Refresh list on success
    } catch (error) {
        console.error("Group creation failed:", error);
        alert("Oops! Could not create the group. Is the server running?");
    }
}

async function deleteCurrentGroup() {
    if (!currentGroupId) return;
    if (!confirm("Are you sure you want to delete this group?")) return;

    try {
        const response = await fetch(`http://localhost:8080/api/groups/${currentGroupId}?admin=${username}`, {
            method: 'DELETE'
        });

        if (!response.ok) throw new Error("Delete failed. Are you the admin?");

        // Cleanup UI after deletion
        currentGroupId = null;
        if (currentSubscription) currentSubscription.unsubscribe();
        document.getElementById('messages').innerHTML = "";
        document.querySelector('.chat-header h2').innerText = "Select a Channel";

        await loadGroups();
    } catch (error) {
        alert(error.message);
    }
}

async function loadHistory(groupId){
    if (!groupId) return;
    try {
        const response = await fetch(`http://localhost:8080/chat/history/${groupId}`);
        if (!response.ok) throw new Error('Failed retrieving chat history');
        const history = await response.json();
        document.getElementById('messages').innerHTML = "";
        history.reverse().forEach(msg => {
            showMessage(msg);
        })
    } catch (error) {
        console.error("Could not load history : ", error);
    }
}

function sendMessage() {
    var messageContent = document.getElementById('message-input').value;
        if (!currentGroupId) return alert("Select a group first!");

        stompClient.publish({
            destination: `/app/chat/${currentGroupId}`, // Match the Java @MessageMapping
            body: JSON.stringify({
                username: username,
                content: messageContent,
                groupId: currentGroupId
            })
        });
        document.getElementById('message-input').value = '';
}

function showMessage(message) {
    var messagesDiv = document.getElementById('messages');
    var messageElement = document.createElement('div');

    // handle cases where timestamp might be null
    var timeStr = message.timestamp ? new Date(message.timestamp).toLocaleTimeString() : "Just now";
    messageElement.appendChild(document.createTextNode(
        message.username + ": " + message.content + " (" + timeStr + ")"
    ));
    messagesDiv.appendChild(messageElement);

    // Auto-scroll to the bottom of the chat
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}


connect();


