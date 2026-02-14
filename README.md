# Stack’d

> Put your phone down. Prove you did.

Stack’d is a real-time, multi-sensor presence engine built for the web.  
Flip your phone face-down. Stay present.  
First person to move? The session ends.

No accounts.  
No backend required.  
Just real presence.

---

## 🚀 Test It

👉 **Launch here:**  
https://stack-d.netlify.app/
---

## ⚡ What Stack’d Does

- 📱 Detects device movement using gyroscope + accelerometer
- 🔗 Syncs participants in real-time using WebRTC (PeerJS)
- ⏱ Runs live session timer + group scoreboard
- 🏆 Awards Zen points + badges
- 📊 Tracks presence analytics locally
- 📦 Installable as a Progressive Web App

---

## 🧠 How It Works

Stack’d monitors:

- Device orientation (face-down validation)
- Acceleration magnitude (movement spikes)
- Rotation thresholds (gyroscope)
- Tab visibility (switching apps ends session)

Movement spike → `BREAK` event → broadcast to all peers.

All sessions run peer-to-peer.
No central game server required.

---

## 🛠 Tech Stack

- HTML5
- CSS3 (custom SaaS design system)
- Vanilla JavaScript
- PeerJS (WebRTC abstraction)
- DeviceMotion & DeviceOrientation APIs
- LocalStorage analytics
- PWA (manifest + service worker)

---

🎯 Why It Exists

Because conversations shouldn’t compete with notifications.

