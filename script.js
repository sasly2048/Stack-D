let peer,
  connections = [];
let username,
  room,
  startTime,
  broken = false,
  isHost = false,
  isMonitoring = false;
let scores = {};

// Circle Progress Configuration
const radius = 90;
const circumference = 2 * Math.PI * radius;
const circle = document.getElementById("progressCircle");
if (circle) {
  circle.style.strokeDasharray = circumference;
  circle.style.strokeDashoffset = circumference;
}

function setProgress(percent) {
  if (!circle) return;
  const offset = circumference - (percent / 100) * circumference;
  circle.style.strokeDashoffset = offset;
}

function generateShortCode() {
  return Math.random().toString(36).substring(2, 8).toUpperCase();
}

async function initSession() {
  const btn = document.getElementById("connectBtn");
  username = document.getElementById("username").value.trim();
  let roomInput = document.getElementById("room").value.trim();
  if (!username) return alert("Please identify yourself.");

  if (btn) {
    btn.innerText = "Connecting...";
    btn.disabled = true;
  }

  // Requesting sensor permission
  if (
    typeof DeviceOrientationEvent !== "undefined" &&
    typeof DeviceOrientationEvent.requestPermission === "function"
  ) {
    try {
      await DeviceOrientationEvent.requestPermission();
    } catch (e) {
      console.error(e);
    }
  }

  isHost = !roomInput;
  room = isHost ? generateShortCode() : roomInput;
  peer = isHost ? new Peer(room) : new Peer();

  peer.on("open", () => {
    document.getElementById("setup").classList.add("hidden");
    startRitual();
  });

  peer.on("error", (err) => {
    alert("Peer Error: " + err.type);
    if (btn) {
      btn.innerText = "Connect";
      btn.disabled = false;
    }
  });
}

function startRitual() {
  const overlay = document.getElementById("ritualOverlay");
  const display = document.getElementById("ritualCountdown");
  overlay.classList.remove("hidden");

  let count = 3;
  const interval = setInterval(() => {
    count--;
    if (count > 0) {
      display.innerText = count;
      if (navigator.vibrate) navigator.vibrate(100);
    } else {
      clearInterval(interval);
      overlay.classList.add("hidden");
      document.getElementById("session").classList.remove("hidden");
      document.getElementById("roomTitle").innerText = "Protocol Key: " + room;
      if (isHost) setupHost();
      else setupClient(room);
      armSensors();
    }
  }, 1000);
}

function armSensors() {
  isMonitoring = true;
  window.addEventListener("deviceorientation", (e) => {
    if (!isMonitoring || broken) return;
    const isFaceDown = Math.abs(e.beta) > 170; // Detect face-down position
    if (isFaceDown && !startTime) {
      startTime = Date.now();
      updateTimer();
      if (navigator.vibrate) navigator.vibrate([100, 50, 100]);
    } else if (!isFaceDown && startTime) {
      broadcastBreak(username);
    }
  });
}

function updateTimer() {
  const interval = setInterval(() => {
    if (broken) return clearInterval(interval);
    const seconds = Math.floor((Date.now() - startTime) / 1000);
    document.getElementById("timer").innerText =
      `${Math.floor(seconds / 60)}:${(seconds % 60).toString().padStart(2, "0")}`;
    setProgress(Math.min((seconds / 300) * 100, 100)); // 5-minute visual goal
    connections.forEach((c) =>
      c.send({ type: "TIME", user: username, time: seconds }),
    );
  }, 1000);
}

function setupHost() {
  document.getElementById("roomBadge").innerText = "HOSTING";
  peer.on("connection", (conn) => {
    connections.push(conn);
    conn.on("data", (data) => {
      if (data.type === "TIME") {
        scores[data.user] = data.time;
        updateScoreboard();
      }
      if (data.type === "BREAK") broadcastBreak(data.user);
    });
  });
}

function setupClient(id) {
  document.getElementById("roomBadge").innerText = "CONNECTED";
  const conn = peer.connect(id);
  conn.on("open", () => {
    connections.push(conn);
    conn.on("data", (data) => {
      if (data.type === "BREAK")
        triggerSummary(data.user, document.getElementById("timer").innerText);
      if (data.type === "TIME") {
        scores[data.user] = data.time;
        updateScoreboard();
      }
    });
  });
}

function broadcastBreak(user) {
  broken = true;
  connections.forEach((c) => c.send({ type: "BREAK", user }));
  triggerSummary(user, document.getElementById("timer").innerText);
}

function updateScoreboard() {
  const list = document.getElementById("scoreList");
  if (!list) return;
  list.innerHTML = "";
  Object.entries(scores).forEach(([u, t]) => {
    const li = document.createElement("li");
    li.style =
      "display:flex; justify-content:space-between; padding:0.8rem; background:rgba(255,255,255,0.03); margin-bottom:0.5rem; border-radius:12px; font-size:0.8rem; border:1px solid var(--border);";
    li.innerHTML = `<span>${u}</span><span style="font-weight:800;">${t}s</span>`;
    list.appendChild(li);
  });
}
