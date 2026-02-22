var stompClient = null;

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

async function loadHistory(){
    try {
        const response = await fetch('http://localhost:8080/chat/history');
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

function connect() {
    console.log("starting stomp connection");
    stompClient = new StompJs.Client({
        brokerURL: 'ws://localhost:8080/ws',
        onConnect: () => {
            loadHistory();
            stompClient.subscribe('/topic/messages', function (message) {
                showMessage(JSON.parse(message.body));
            });
        }, onStompError: (frame) => {
                       console.error('Broker reported error: ' + frame.headers['message']);
                       console.error('Additional details: ' + frame.body);
        }
    });
    stompClient.activate();
    console.log("stomp client activated");
}

function sendMessage() {
    console.log("starting send msg func");
    var messageContent = document.getElementById('message-input').value;
    stompClient.publish({
        destination: '/app/chat',
        body: JSON.stringify({ username: username, content: messageContent })
    });
    document.getElementById('message-input').value = '';

    console.log("done sending message");
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