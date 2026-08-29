# RIVEL AI — Secure Backend Architecture & Security Documentation

## 1. Architectural Overview

### Previous Architecture (Insecure)
```
[Android App] ──(Direct REST with BuildConfig.GEMINI_API_KEY)──> [Google Gemini API]
  ⚠️ Risk: Gemini API key packaged inside Android APK, vulnerable to extraction/decompilation.
```

### New Phase 2 Architecture (Production-Grade Secure Proxy)
```
[Android App] 
     │ (HTTPS REST calls — ZERO Gemini API secrets on client)
     ▼
[RIVEL Secure Backend Proxy (Google Cloud Run / Express)]
     │ (Server-Side Env Secret: GEMINI_API_KEY)
     │ (Strict Input Validation, Rate Limiting, 10MB Payload Limits, 25s Timeout)
     ▼
[Google Gemini 2.5 Flash API]
```

---

## 2. API Endpoints

### 1. Health Check
- **Path:** `GET /api/health`
- **Response:**
  ```json
  {
    "status": "healthy",
    "service": "RIVEL Secure AI Backend Proxy",
    "version": "1.0.0",
    "geminiConfigured": true,
    "timestamp": "2026-08-26T02:00:00.000Z"
  }
  ```

### 2. AI Student Chat
- **Path:** `POST /api/ai/chat`
- **Request Body:**
  ```json
  {
    "message": "I have an exam in 2 days and I'm stressed.",
    "studentContext": {
      "studentName": "Alex",
      "course": "Computer Science",
      "educationLevel": "Undergraduate",
      "year": "Year 2",
      "mainGoal": "Ace Data Structures",
      "subjects": ["Algorithms", "OS"],
      "upcomingExam": "Midterm Algorithms",
      "todayCompletedMinutes": 45,
      "activeStreak": 4
    },
    "chatHistory": [
      { "role": "user", "text": "Hello coach" },
      { "role": "model", "text": "Hey Alex! Ready to tackle your study goals?" }
    ]
  }
  ```
- **Response Body:**
  ```json
  {
    "success": true,
    "reply": "For your Algorithms exam, switch to Active Recall immediately...",
    "source": "LIVE_GEMINI",
    "suggestedActionType": "START_FOCUS",
    "suggestedActionPayload": "25",
    "suggestedActionLabel": "Start 25-Min Focus Sprint"
  }
  ```

### 3. Proof-of-Wake Object Mission Verification
- **Path:** `POST /api/vision/verify-object`
- **Request Body:**
  ```json
  {
    "targetObject": "Book",
    "imageBase64": "<base64_encoded_jpeg_data>"
  }
  ```
- **Response Body (Verified):**
  ```json
  {
    "status": "VERIFIED",
    "detected": true,
    "confidence": 0.94,
    "reason": "A textbook is clearly visible on the desk."
  }
  ```
- **Response Body (Wrong Object / Rejection):**
  ```json
  {
    "status": "WRONG_OBJECT",
    "detected": false,
    "confidence": 0.22,
    "reason": "Could not detect 'Book' in this photo. Please clearly frame the requested item."
  }
  ```

---

## 3. Server-Side Secret Management

The Gemini API key is configured **exclusively on the server environment**:

- **Environment Variable:** `GEMINI_API_KEY`
- **Configuration (Local):** Place `GEMINI_API_KEY=AIzaSy...` in `server/.env`.
- **Configuration (Cloud Run):** Set via Google Cloud Secret Manager or Cloud Run Environment Variables:
  ```bash
  gcloud secrets create rivel-gemini-key --data-file=-
  gcloud run deploy rivel-backend \
    --image gcr.io/YOUR_PROJECT_ID/rivel-backend \
    --set-secrets GEMINI_API_KEY=rivel-gemini-key:latest \
    --allow-unauthenticated
  ```

---

## 4. How to Run Locally

### Running the Backend:
```bash
cd server
npm install
# Set your key in server/.env: GEMINI_API_KEY=AIzaSy...
npm start
```
The server will start on `http://0.0.0.0:8080`.

### Running Backend Unit & Integration Tests:
```bash
cd server
npm test
```

### Connecting Android to Local Backend:
In `com.example.ai.BackendConfig`, configure:
```kotlin
BackendConfig.useLocalDevelopmentServer(8080) // routes to http://10.0.2.2:8080 on Android emulator
```

---

## 5. Security & Abuse Protections

1. **Zero Client Secrets:** The Android app contains zero Gemini API keys, zero Gemini REST endpoints, and zero tokens.
2. **Payload Size Guard:** Request bodies capped at 10MB; oversized inputs rejected with `413 Payload Too Large`.
3. **Rate Limiting:**
   - Global rate limiter: 120 req/min/IP
   - Vision endpoint limiter: 30 req/min/IP
4. **Timeout Enforcer:** 25-second server-side timeout on all Gemini API calls to prevent connection stalling.
5. **No Data Retention / Zero Disk Logging:** In-memory stream processing only. No student photos, chat messages, or credentials are saved to disk, database, or console logs.
6. **Strict Verification Threshold:** Server enforces `confidence >= 0.80` before returning `VERIFIED`.
7. **Safe Offline Fallback for Chat / Fail-Closed for Alarms:**
   - AI Chat falls back to the on-device evidence-based coach (`LOCAL_FALLBACK`) if server is offline.
   - Object Wake verification strictly fails (`NETWORK_ERROR` / `API_ERROR`), preventing alarm dismissal without verified proof-of-wake.
