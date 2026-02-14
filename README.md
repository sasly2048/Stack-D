# Stack’d

> Put your phone down. Prove you did.

Stack’d is a real-time, multi-sensor presence engine built for the web.

Flip your phone face-down.  
Stay present.  
First one to move? Session ends.

---

## ⚡ What It Does

- 📱 Detects movement using gyroscope + accelerometer
- 🔗 Syncs participants in real-time (WebRTC via PeerJS)
- ⏱ Live session timer + group scoreboard
- 🏆 Zen points + badge system
- 📊 Local analytics dashboard
- 📦 Installable PWA

No backend required.  
No accounts.  
No tracking.

Just presence.

---

## 🧠 How It Works

Stack’d monitors:

- Device orientation
- Acceleration magnitude
- Rotation thresholds
- Tab visibility changes

Movement spike → break event → broadcast to all peers.

All sessions run peer-to-peer.

---

## 🚀 Test It

``` https://stack-d.netlify.app .
