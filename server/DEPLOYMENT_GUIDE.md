# 🚀 RIVEL Backend Deployment & Physical Phone Setup Guide

This guide details how to host your secure RIVEL Node.js backend for **100% FREE** with **ZERO credit cards or billing required**, and connect your real Android phone.

---

## ⚡ Option 1: Free Cloud Hosting via Render.com (Recommended - 5 Minutes)

Render provides free HTTPS hosting with zero credit card required.

### Steps:
1. **Push your code to GitHub** (or fork your repository).
2. Go to [https://render.com](https://render.com) and create a free account.
3. Click **New +** → **Web Service**.
4. Connect your GitHub repository.
5. Configure the service:
   - **Root Directory**: `server`
   - **Environment**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `node server.js`
   - **Plan**: Free
6. Under **Environment Variables**, add:
   - `GEMINI_API_KEY`: `<Your Gemini API Key>`
   - `GEMINI_MODEL`: `gemini-2.0-flash`
   - `NODE_ENV`: `production`
7. Click **Deploy Web Service**.
8. Render will provide an HTTPS URL like:
   `https://rivel-backend-xxxx.onrender.com`

---

## ⚡ Option 2: Instant Local Testing on Real Phone via ngrok (1 Minute)

If you want to test on your real physical Android phone immediately from your laptop:

1. In the `server` directory on your computer, start the backend:
   ```bash
   export GEMINI_API_KEY="your_api_key_here"
   node server.js
   ```
   *(On Windows PowerShell: `$env:GEMINI_API_KEY="your_api_key_here"; node server.js`)*

2. In a second terminal window, run a free public HTTPS tunnel:
   ```bash
   npx ngrok http 8080
   ```
   *(Or using localtunnel: `npx localtunnel --port 8080`)*

3. ngrok will give you an HTTPS forwarding URL, e.g.:
   `https://abc1234.ngrok-free.app`

---

## 📱 How to Connect Your Real Android Phone to the Backend

1. Open the **RIVEL** app on your Android phone.
2. Tap the **Profile / Settings** icon in the top corner.
3. In the **AI Backend URL** field, enter your public HTTPS URL:
   - Example: `https://rivel-backend-xxxx.onrender.com`
   - Or: `https://abc1234.ngrok-free.app`
4. Tap **Save**.

The URL is saved permanently on your device in SharedPreferences. All Proof-of-Wake Object Verifications and AI Coach conversations will now route directly through your live backend!
