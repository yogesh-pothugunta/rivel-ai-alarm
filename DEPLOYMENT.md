# RIVEL AI — Production Cloud Run Deployment Guide

This guide details the complete, step-by-step procedure to deploy the **RIVEL Secure AI Backend Proxy** to **Google Cloud Run** with **Google Cloud Secret Manager** for zero-leakage Gemini API secret isolation.

---

## 1. Prerequisites

1. **Google Cloud Account** with active billing enabled.
2. **Google Cloud SDK (`gcloud` CLI)** installed:
   ```bash
   gcloud version
   ```
3. **Gemini API Key** from [Google AI Studio](https://aistudio.google.com/).

---

## 2. Google Cloud Authentication & Project Setup

### Step 2.1: Log in to Google Cloud CLI
```bash
gcloud auth login
```

### Step 2.2: Set or Create Your Google Cloud Project
```bash
# Set your desired PROJECT_ID (e.g. rivel-productivity-prod)
export PROJECT_ID="YOUR_GOOGLE_CLOUD_PROJECT_ID"
export REGION="us-central1" # Or your preferred region (e.g. asia-southeast1, europe-west1)

# Set the active project in gcloud
gcloud config set project $PROJECT_ID
```

---

## 3. Enable Required Google Cloud APIs

Enable Cloud Run, Artifact Registry, Secret Manager, and Cloud Build:

```bash
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  cloudbuild.googleapis.com
```

---

## 4. Secure Gemini API Secret Provisioning

> **CRITICAL SECURITY MANDATE:**
> Never commit your actual `GEMINI_API_KEY` to Git, Dockerfiles, or client-side Android source code.

### Step 4.1: Create Secret in Secret Manager
```bash
# Create the secret definition
gcloud secrets create rivel-gemini-key \
  --replication-policy="automatic"

# Add your Gemini API key value securely (replace with your actual key from AI Studio)
echo -n "AIzaSy_YOUR_ACTUAL_GEMINI_API_KEY_HERE" | gcloud secrets versions add rivel-gemini-key --data-file=-
```

### Step 4.2: Grant Secret Access to the Cloud Run Service Account
```bash
# Get your default compute service account
export PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")

gcloud secrets add-iam-policy-binding rivel-gemini-key \
  --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

---

## 5. Deploy Backend to Google Cloud Run

Deploy directly from the `/server` directory using Cloud Build:

```bash
cd server

gcloud run deploy rivel-backend \
  --source . \
  --region $REGION \
  --platform managed \
  --allow-unauthenticated \
  --set-secrets GEMINI_API_KEY=rivel-gemini-key:latest \
  --set-env-vars NODE_ENV=production,GEMINI_MODEL=gemini-2.5-flash,OBJECT_CONFIDENCE_THRESHOLD=0.80,GEMINI_TIMEOUT_MS=25000 \
  --min-instances 0 \
  --max-instances 10 \
  --memory 512Mi \
  --cpu 1 \
  --timeout 30s
```

---

## 6. Retrieve Your Cloud Run HTTPS Service URL

After deployment completes, retrieve the public HTTPS endpoint:

```bash
export SERVICE_URL=$(gcloud run services describe rivel-backend --platform managed --region $REGION --format 'value(status.url)')
echo "Your RIVEL Backend Service URL is: $SERVICE_URL"
```

Example output:
`https://rivel-backend-abc12345-uc.a.run.app`

---

## 7. Verifying Deployed Cloud Run Endpoints

Test your live Cloud Run deployment with `curl`:

### 7.1: Verify Health Endpoint
```bash
curl -X GET "$SERVICE_URL/api/health"
```
**Expected Response:**
```json
{
  "status": "healthy",
  "service": "RIVEL Secure AI Backend Proxy",
  "version": "1.0.0",
  "geminiConfigured": true,
  "timestamp": "2026-08-26T06:30:00.000Z"
}
```

### 7.2: Verify AI Student Chat Endpoint
```bash
curl -X POST "$SERVICE_URL/api/ai/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "I feel like procrastinating on my physics problem set.",
    "studentContext": {
      "studentName": "Alex",
      "course": "Physics",
      "mainGoal": "Finish Lab 3"
    }
  }'
```
**Expected Response:**
```json
{
  "success": true,
  "reply": "Hey Alex...",
  "source": "LIVE_GEMINI",
  "suggestedActionType": "START_FOCUS",
  "suggestedActionPayload": "25",
  "suggestedActionLabel": "Start 25-Min Focus Sprint"
}
```

### 7.3: Verify Vision Proof-of-Wake Object Mission Endpoint
```bash
# Create a test 1x1 white pixel base64 sample
TEST_BASE64="/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="

curl -X POST "$SERVICE_URL/api/vision/verify-object" \
  -H "Content-Type: application/json" \
  -d "{
    \"targetObject\": \"Book\",
    \"imageBase64\": \"$TEST_BASE64\"
  }"
```
**Expected Response:**
```json
{
  "status": "WRONG_OBJECT",
  "detected": false,
  "confidence": 0.0,
  "reason": "Could not detect \"Book\" in this photo..."
}
```

---

## 8. Connecting Android App to the Deployed Backend

1. Open `app/src/main/java/com/example/ai/BackendConfig.kt` in the Android project.
2. Update the `PRODUCTION_BASE_URL_PLACEHOLDER` or call `useProductionServer(...)`:

```kotlin
// In com/example/ai/BackendConfig.kt
const val PRODUCTION_BASE_URL_PLACEHOLDER = "https://rivel-backend-abc12345-uc.a.run.app"
```

3. Build and install your release/debug APK. All AI coaching and wake missions will securely query your production Cloud Run proxy!
