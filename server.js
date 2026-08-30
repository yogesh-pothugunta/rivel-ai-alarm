/**
 * RIVEL Secure Gemini API Proxy Backend
 * 
 * Production-ready backend for Google Cloud Run / Render Deployment.
 * 
 * Provides secure HTTPS proxy endpoints for RIVEL Android client:
 * 1. POST /api/ai/chat - AI Student Productivity Coach
 * 2. POST /api/vision/verify-object - Object Mission Proof-of-Wake Verification
 * 3. GET  /api/health - Health check endpoint
 * 
 * Security & Privacy:
 * - All Gemini API requests and API keys are strictly handled server-side.
 * - In-memory processing only: zero disk/database persistence of student photos, chat history, or credentials.
 * - Zero sensitive data, stack traces, or API keys exposed in error logs or client responses.
 * - Enforces rate limiting, payload size limits (10MB), request timeouts (25s), and strict input validation.
 */

require('dotenv').config();
const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');

const app = express();
const PORT = parseInt(process.env.PORT || '8080', 10);

// Trust first proxy hop (Render / Cloud Run / reverse proxies)
// Required for express-rate-limit to read X-Forwarded-For securely behind Render's load balancer
app.set('trust proxy', 1);

// Disable X-Powered-By header to prevent fingerprinting
app.disable('x-powered-by');

// Supported Gemini Models with automatic normalization and fallback
const DEFAULT_GEMINI_MODEL = 'gemini-2.5-flash';
const KNOWN_SUPPORTED_MODELS = [
  'gemini-2.5-flash',
  'gemini-3.7-flash',
  'gemini-2.5-pro'
];

function normalizeModelName(rawModel) {
  if (!rawModel || typeof rawModel !== 'string') return DEFAULT_GEMINI_MODEL;
  let model = rawModel.trim();
  if (model.startsWith('models/')) {
    model = model.substring('models/'.length).trim();
  }
  // If undefined string, empty, or legacy deprecated name, use default
  if (!model || model === 'undefined' || model === 'null' || model === 'gemini-2.0-flash' || model === 'gemini-1.5-flash' || model === 'gemini-1.5-pro') {
    return DEFAULT_GEMINI_MODEL;
  }
  return model;
}

const CONFIGURED_MODEL = normalizeModelName(process.env.GEMINI_MODEL);
const OBJECT_CONFIDENCE_THRESHOLD = parseFloat(process.env.OBJECT_CONFIDENCE_THRESHOLD || '0.80');
const GEMINI_TIMEOUT_MS = parseInt(process.env.GEMINI_TIMEOUT_MS || '25000', 10);

// Conservative CORS Configuration:
// Mobile apps (OkHttp) send requests without an Origin header, which is permitted.
// If an Origin header is present (e.g. web/testing), validate against configured allowed origins.
const allowedOrigins = process.env.ALLOWED_ORIGINS ? process.env.ALLOWED_ORIGINS.split(',').map(o => o.trim()) : [];
app.use(cors({
  origin: (origin, callback) => {
    // Allow requests with no origin (mobile apps, curl, server-to-server)
    if (!origin) return callback(null, true);
    if (allowedOrigins.length === 0 || allowedOrigins.includes(origin) || origin.includes('localhost') || origin.includes('127.0.0.1')) {
      return callback(null, true);
    }
    return callback(new Error('CORS policy: Not allowed by origin'));
  },
  methods: ['GET', 'POST', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Accept'],
  maxAge: 86400
}));

// Body parser: limit to 10MB to accommodate base64 camera frames while preventing memory exhaustion
app.use(express.json({ limit: '10mb' }));

// Global Rate Limiting: 120 requests per minute per IP
const globalLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 120,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    success: false,
    error: 'Too many requests. Please try again in a minute.',
    status: 'RATE_LIMITED'
  }
});
app.use(globalLimiter);

// Specific Rate Limiter for Vision (CPU/Quota intensive): 30 requests per minute per IP
const visionLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    status: 'API_ERROR',
    detected: false,
    confidence: 0.0,
    reason: 'Verification rate limit exceeded. Please wait a moment before capturing again.'
  }
});

// Test Hook / Mock Injection for deterministic testing
let mockGeminiHandler = null;
function setMockGeminiHandler(handler) {
  mockGeminiHandler = handler;
}

// Helper: Call Gemini API securely with timeout and multi-model fallback
async function callGeminiApi(payload, preferredModel = CONFIGURED_MODEL) {
  if (mockGeminiHandler && (process.env.NODE_ENV === 'test' || process.env.TEST_MODE === 'true')) {
    return await mockGeminiHandler(payload);
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey || apiKey.trim() === '' || apiKey === 'MY_GEMINI_API_KEY' || apiKey === 'YOUR_GEMINI_API_KEY_HERE') {
    const error = new Error('NO_API_KEY');
    error.code = 'NO_API_KEY';
    throw error;
  }

  // Candidate models to attempt: preferred model, then known stable fallback models
  const modelsToAttempt = [preferredModel];
  if (!modelsToAttempt.includes('gemini-2.5-flash')) modelsToAttempt.push('gemini-2.5-flash');
  if (!modelsToAttempt.includes('gemini-3.7-flash')) modelsToAttempt.push('gemini-3.7-flash');
  if (!modelsToAttempt.includes('gemini-2.5-pro')) modelsToAttempt.push('gemini-2.5-pro');

  const RETRIABLE_STATUS_CODES = [404, 429, 500, 502, 503, 504];
  const perModelTimeout = Math.max(12000, Math.floor(GEMINI_TIMEOUT_MS / 2));

  let lastResult = null;
  let lastError = null;

  for (let i = 0; i < modelsToAttempt.length; i++) {
    const currentModel = modelsToAttempt[i];
    const apiVersion = 'v1beta';
    const endpointPath = `https://generativelanguage.googleapis.com/${apiVersion}/models/${currentModel}:generateContent`;
    const fullUrl = `${endpointPath}?key=${apiKey.trim()}`;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), perModelTimeout);

    try {
      const response = await fetch(fullUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload),
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      const data = await response.json();
      lastResult = { status: response.status, data, modelUsed: currentModel };

      if (response.status === 200) {
        return lastResult;
      }

      // Safe error logging without exposing API key
      const geminiErrMsg = data?.error?.message || data?.error?.status || 'Unknown error response';
      console.warn(`[Gemini API] Request returned HTTP ${response.status} using model "${currentModel}" on endpoint "${apiVersion}/models/${currentModel}:generateContent". Details: ${geminiErrMsg}`);

      const isRetriable = RETRIABLE_STATUS_CODES.includes(response.status);
      if (isRetriable && i < modelsToAttempt.length - 1) {
        console.warn(`[Gemini API] Model "${currentModel}" returned HTTP ${response.status}. Automatically retrying with fallback model "${modelsToAttempt[i + 1]}"...`);
        continue;
      }

      return lastResult;
    } catch (err) {
      clearTimeout(timeoutId);
      if (err.name === 'AbortError') {
        lastError = new Error(`Gemini API model "${currentModel}" timed out`);
        lastError.code = 'TIMEOUT';
        console.warn(`[Gemini API] Request with model "${currentModel}" timed out.`);
      } else {
        lastError = err;
        console.error(`[Gemini API] Network/execution error with model "${currentModel}":`, err.message);
      }

      if (i < modelsToAttempt.length - 1) {
        console.warn(`[Gemini API] Retrying with fallback model "${modelsToAttempt[i + 1]}"...`);
        continue;
      }
      throw lastError;
    }
  }

  return lastResult;
}

// 1. Health check endpoint
app.get(['/', '/api/health'], (req, res) => {
  const isConfigured = Boolean(
    process.env.GEMINI_API_KEY &&
    process.env.GEMINI_API_KEY.trim() !== '' &&
    process.env.GEMINI_API_KEY !== 'MY_GEMINI_API_KEY' &&
    process.env.GEMINI_API_KEY !== 'YOUR_GEMINI_API_KEY_HERE'
  );

  res.status(200).json({
    status: 'healthy',
    service: 'RIVEL Secure AI Backend Proxy',
    version: '1.0.0',
    geminiConfigured: isConfigured,
    model: CONFIGURED_MODEL,
    environment: process.env.NODE_ENV || 'production',
    timestamp: new Date().toISOString()
  });
});

// 2. AI Chat Endpoint
app.post('/api/ai/chat', async (req, res) => {
  try {
    const { message, studentContext, chatHistory } = req.body;

    // Input validation
    if (!message || typeof message !== 'string' || message.trim() === '') {
      return res.status(400).json({
        success: false,
        error: 'Invalid request: "message" is required and must be a non-empty string.'
      });
    }

    if (message.length > 3000) {
      return res.status(400).json({
        success: false,
        error: 'Message exceeds maximum allowed length of 3000 characters.'
      });
    }

    const ctx = studentContext || {};
    const studentName = ctx.studentName && ctx.studentName.trim() ? ctx.studentName.trim() : '';
    const studentInfo = [
      studentName ? `Student Name: ${studentName}` : '',
      ctx.course ? `Course/Major: ${ctx.course} (${ctx.educationLevel || ''}, ${ctx.year || ''})` : '',
      Array.isArray(ctx.subjects) && ctx.subjects.length > 0 ? `Enrolled Subjects: ${ctx.subjects.join(', ')}` : '',
      ctx.mainGoal ? `Main Goal: ${ctx.mainGoal}` : '',
      ctx.upcomingExam ? `Upcoming Exam / Deadline: ${ctx.upcomingExam}` : '',
      `Today's stats: ${ctx.todayCompletedMinutes || 0} min studied so far, ${ctx.activeStreak || 0} day streak.`
    ].filter(Boolean).join('\n');

    const systemInstruction = `You are RIVEL AI, a friendly, intelligent, and natural AI study companion and productivity coach for students.

Student Profile & Context:
${studentInfo || 'General student'}

PERSONALITY & CONVERSATIONAL GUIDELINES:
1. NATURAL & CONVERSATIONAL: Speak like a supportive, smart, encouraging mentor and study partner. Be warm, human, and adaptable. Never sound robotic or like a rigid automated script.
2. DYNAMIC INTENT ADAPTATION:
   - GREETINGS & CASUAL CHAT ("hi", "hello", "how are you?", "hey"): Respond warmly and briefly (e.g. "Hey ${studentName || 'there'}! 😊 How's your day going? Ready to get some studying done or just checking in?"). Do NOT force an unsolicited 3-step action plan or lecture on a simple greeting.
   - LAZINESS & PROCRASTINATION ("I feel lazy", "I don't want to study", "distracted"): Acknowledge the feeling with genuine empathy, remove guilt, explain that starting is the hardest 5 minutes, and gently invite them to do a tiny low-friction step.
   - EXAM STRESS & URGENT DEADLINES ("exam tomorrow and haven't studied", "stressed"): Be calm, reassuring, and grounded. Help them triage high-yield topics instead of panicking.
   - ACADEMIC & TECHNICAL QUESTIONS ("explain Data Structures", "how does quicksort work", "chemistry formulas"): Explain concepts clearly using simple analogies, intuitive step-by-step logic, or the Feynman method.
   - STUDY PLANS ("give me a study plan"): Ask brief clarifying questions if needed, or provide a clean, realistic 2-3 step routine tailored to their subjects.
3. NO RIGID TEMPLATES: Do NOT force every single reply into a formulaic "Problem -> Solution -> Action" format. Vary your structure naturally according to what the student actually said.
4. CONTEXT & MEMORY: Pay close attention to recent conversation history. When the student asks follow-up questions (e.g. "what should I study first?", "can you give an example of that?"), seamlessly connect your response to what was previously discussed.
5. CONCISE & READABLE: Keep responses engaging and digestible. Use clean markdown formatting where helpful, but keep tone natural and inviting.`;

    const contents = [];

    // Add recent history if provided (up to 12 turns for rich context)
    if (Array.isArray(chatHistory)) {
      const recent = chatHistory.slice(-12);
      for (const turn of recent) {
        if (turn && turn.role && turn.text) {
          contents.push({
            role: turn.role === 'USER' || turn.role === 'user' ? 'user' : 'model',
            parts: [{ text: String(turn.text) }]
          });
        }
      }
    }

    // Add current user turn
    contents.push({
      role: 'user',
      parts: [{ text: message.trim() }]
    });

    const geminiPayload = {
      contents,
      systemInstruction: {
        parts: [{ text: systemInstruction }]
      },
      generationConfig: {
        temperature: 0.75,
        maxOutputTokens: 1000
      }
    };

    const { status, data } = await callGeminiApi(geminiPayload);

    if (status !== 200) {
      console.error(`[AI Chat] Gemini API returned error status: ${status}`);
      return res.status(502).json({
        success: false,
        error: 'AI service temporarily unavailable.',
        source: 'ERROR'
      });
    }

    const candidate = data?.candidates?.[0];
    const replyText = candidate?.content?.parts?.[0]?.text;

    if (!replyText || typeof replyText !== 'string' || replyText.trim() === '') {
      return res.status(502).json({
        success: false,
        error: 'Empty response received from AI model.',
        source: 'ERROR'
      });
    }

    // Extract suggested action based on user prompt and reply content
    const lowerPrompt = message.toLowerCase();
    const lowerReply = replyText.toLowerCase();

    let suggestedActionType = null;
    let suggestedActionPayload = null;
    let suggestedActionLabel = null;

    if (lowerPrompt.includes('focus') || lowerPrompt.includes('procrastinat') || lowerPrompt.includes('start study') || lowerReply.includes('25-minute focus') || lowerReply.includes('focus sprint')) {
      suggestedActionType = 'START_FOCUS';
      suggestedActionPayload = '25';
      suggestedActionLabel = 'Start 25-Min Focus Sprint';
    } else if (lowerPrompt.includes('plan') || lowerPrompt.includes('schedule') || lowerPrompt.includes('task') || lowerPrompt.includes('exam')) {
      suggestedActionType = 'CREATE_TASK';
      suggestedActionPayload = 'High Priority Study Task';
      suggestedActionLabel = "Add to Today's Plan";
    } else if (lowerPrompt.includes('alarm') || lowerPrompt.includes('wake up') || lowerPrompt.includes('morning')) {
      suggestedActionType = 'SET_ALARM';
      suggestedActionPayload = '06:30';
      suggestedActionLabel = 'Set Morning Wake Alarm';
    }

    return res.status(200).json({
      success: true,
      reply: replyText.trim(),
      source: 'LIVE_GEMINI',
      suggestedActionType,
      suggestedActionPayload,
      suggestedActionLabel
    });

  } catch (err) {
    if (err.code === 'NO_API_KEY') {
      console.warn('[AI Chat] Gemini API key not configured on server.');
      return res.status(503).json({
        success: false,
        error: 'AI service is currently not configured on server.',
        source: 'ERROR'
      });
    }

    if (err.code === 'TIMEOUT') {
      console.error('[AI Chat] Request timed out contacting Gemini API.');
      return res.status(504).json({
        success: false,
        error: 'AI service request timed out.',
        source: 'ERROR'
      });
    }

    console.error('[AI Chat] Unexpected error:', err.message);
    return res.status(500).json({
      success: false,
      error: 'An unexpected internal error occurred.',
      source: 'ERROR'
    });
  }
});

// 3. Vision Object Verification Endpoint
app.post('/api/vision/verify-object', visionLimiter, async (req, res) => {
  try {
    const { targetObject, imageBase64 } = req.body;

    // Validate inputs
    if (!targetObject || typeof targetObject !== 'string' || targetObject.trim() === '') {
      return res.status(400).json({
        status: 'WRONG_OBJECT',
        detected: false,
        confidence: 0.0,
        reason: 'Invalid request: "targetObject" parameter is missing or empty.'
      });
    }

    if (!imageBase64 || typeof imageBase64 !== 'string' || imageBase64.trim() === '') {
      return res.status(400).json({
        status: 'LOW_IMAGE_QUALITY',
        detected: false,
        confidence: 0.0,
        reason: 'Invalid request: "imageBase64" image data is missing.'
      });
    }

    // Clean base64 string if data URL prefix was sent
    const cleanBase64 = imageBase64.replace(/^data:image\/[a-zA-Z]+;base64,/, '').trim();

    if (cleanBase64.length < 100) {
      return res.status(400).json({
        status: 'LOW_IMAGE_QUALITY',
        detected: false,
        confidence: 0.0,
        reason: 'Captured image data is corrupted or too small to evaluate.'
      });
    }

    // Validate base64 charset
    const base64Regex = /^[A-Za-z0-9+/=]+$/;
    if (!base64Regex.test(cleanBase64.substring(0, Math.min(cleanBase64.length, 500)))) {
      return res.status(400).json({
        status: 'LOW_IMAGE_QUALITY',
        detected: false,
        confidence: 0.0,
        reason: 'Captured image data is not valid base64 encoding.'
      });
    }

    const cleanTarget = targetObject.trim();
    const prompt = `You are a strict physical wake-up alarm verification AI.
Examine this photo taken by a waking student.
The target object required to turn off the alarm is: "${cleanTarget}".

TASK:
1. Determine if the photo clearly shows a "${cleanTarget}" (or reasonable everyday variations such as textbook/notebook for book, tumbler/flask for water bottle, sneakers/shoes for shoe, keychain/keys for keys, pen/pencil for pen, coffee mug/cup for mug).
2. If the user photographed a completely different object (e.g. wall, pillow, laptop when looking for toothbrush), set detected to false.
3. If the photo is too dark, covered, or blurry to tell, set detected to false with an explanation.

You MUST respond ONLY with a valid JSON object in this exact schema:
{
  "detected": true,
  "confidence": 0.95,
  "reason": "A book is clearly visible in the photo."
}`;

    const geminiPayload = {
      contents: [
        {
          role: 'user',
          parts: [
            { text: prompt },
            {
              inlineData: {
                mimeType: 'image/jpeg',
                data: cleanBase64
              }
            }
          ]
        }
      ],
      generationConfig: {
        responseMimeType: 'application/json',
        temperature: 0.1
      }
    };

    const { status, data } = await callGeminiApi(geminiPayload);

    if (status !== 200) {
      console.error(`[Vision Verification] Gemini Vision API returned error status: ${status}`);
      return res.status(502).json({
        status: 'API_ERROR',
        detected: false,
        confidence: 0.0,
        reason: 'Gemini Vision AI is temporarily unavailable. Please try again.'
      });
    }

    const candidate = data?.candidates?.[0];
    const outputJsonText = candidate?.content?.parts?.[0]?.text;

    if (!outputJsonText) {
      return res.status(502).json({
        status: 'API_ERROR',
        detected: false,
        confidence: 0.0,
        reason: 'Unable to parse AI verification output.'
      });
    }

    let parsedResult;
    try {
      parsedResult = JSON.parse(outputJsonText);
    } catch (parseErr) {
      console.error('[Vision Verification] Failed to parse JSON from model output:', outputJsonText);
      return res.status(502).json({
        status: 'API_ERROR',
        detected: false,
        confidence: 0.0,
        reason: 'Invalid response format from AI Vision model.'
      });
    }

    const detected = Boolean(parsedResult.detected);
    const confidence = typeof parsedResult.confidence === 'number' ? parsedResult.confidence : (detected ? 0.9 : 0.0);
    const reason = typeof parsedResult.reason === 'string' ? parsedResult.reason : '';

    if (detected && confidence >= OBJECT_CONFIDENCE_THRESHOLD) {
      return res.status(200).json({
        status: 'VERIFIED',
        detected: true,
        confidence: parseFloat(confidence.toFixed(2)),
        reason: reason || `${cleanTarget} verified! Proof-of-wake confirmed.`
      });
    } else {
      const explanation = !detected
        ? (reason || `Could not detect "${cleanTarget}" in this photo. Please point camera directly at your ${cleanTarget}.`)
        : `Confidence (${Math.round(confidence * 100)}%) is below the required ${Math.round(OBJECT_CONFIDENCE_THRESHOLD * 100)}% threshold.`;

      return res.status(200).json({
        status: 'WRONG_OBJECT',
        detected: false,
        confidence: parseFloat(confidence.toFixed(2)),
        reason: explanation
      });
    }

  } catch (err) {
    if (err.code === 'NO_API_KEY') {
      console.warn('[Vision Verification] Gemini API key not configured on server.');
      return res.status(503).json({
        status: 'NO_API_CONFIGURATION',
        detected: false,
        confidence: 0.0,
        reason: 'Gemini API key is not configured on the backend server.'
      });
    }

    if (err.code === 'TIMEOUT') {
      console.error('[Vision Verification] Gemini API timed out.');
      return res.status(504).json({
        status: 'NETWORK_ERROR',
        detected: false,
        confidence: 0.0,
        reason: 'Vision analysis request timed out. Please try capturing again.'
      });
    }

    console.error('[Vision Verification] Unexpected error:', err.message);
    return res.status(500).json({
      status: 'API_ERROR',
      detected: false,
      confidence: 0.0,
      reason: 'Internal vision verification error.'
    });
  }
});

// Error handling middleware for oversized payloads and malformed JSON
app.use((err, req, res, next) => {
  if (err.type === 'entity.too.large') {
    return res.status(413).json({
      success: false,
      status: 'LOW_IMAGE_QUALITY',
      error: 'Payload Too Large: The captured image exceeds the maximum permitted size of 10MB.',
      reason: 'Photo is too large to process. Please try taking the photo again.'
    });
  }

  if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
    return res.status(400).json({
      success: false,
      error: 'Malformed JSON in request body.',
      status: 'API_ERROR'
    });
  }

  console.error('[Unhandled Middleware Error]', err.message);
  res.status(500).json({
    success: false,
    error: 'Internal server error occurred.',
    status: 'API_ERROR'
  });
});

// Start Server if run directly
let serverInstance = null;
if (require.main === module) {
  const isKeyConfigured = Boolean(
    process.env.GEMINI_API_KEY &&
    process.env.GEMINI_API_KEY.trim() !== '' &&
    process.env.GEMINI_API_KEY !== 'MY_GEMINI_API_KEY' &&
    process.env.GEMINI_API_KEY !== 'YOUR_GEMINI_API_KEY_HERE'
  );

  serverInstance = app.listen(PORT, '0.0.0.0', () => {
    console.log(`[RIVEL Server] ==========================================`);
    console.log(`[RIVEL Server] Secure Gemini Proxy Backend Started`);
    console.log(`[RIVEL Server] Binding: 0.0.0.0:${PORT}`);
    console.log(`[RIVEL Server] Environment: ${process.env.NODE_ENV || 'production'}`);
    console.log(`[RIVEL Server] Primary Model: ${CONFIGURED_MODEL}`);
    console.log(`[RIVEL Server] Fallback Models: ${KNOWN_SUPPORTED_MODELS.filter(m => m !== CONFIGURED_MODEL).join(', ')}`);
    console.log(`[RIVEL Server] API Key Configured: ${isKeyConfigured ? 'YES (Active)' : 'NO (Missing/Placeholder)'}`);
    console.log(`[RIVEL Server] Confidence Threshold: ${OBJECT_CONFIDENCE_THRESHOLD * 100}%`);
    console.log(`[RIVEL Server] Trust Proxy: Enabled (hop count: 1 for Render reverse proxy)`);
    console.log(`[RIVEL Server] ==========================================`);
  });

  // Graceful shutdown handling for Cloud Run container lifecycle
  const handleShutdown = (signal) => {
    console.log(`[RIVEL Server] Received ${signal}. Starting graceful shutdown...`);
    if (serverInstance) {
      serverInstance.close(() => {
        console.log('[RIVEL Server] HTTP server closed cleanly.');
        process.exit(0);
      });
      // Force exit after 10s if graceful shutdown hangs
      setTimeout(() => {
        console.error('[RIVEL Server] Forcefully terminating after timeout.');
        process.exit(1);
      }, 10000);
    } else {
      process.exit(0);
    }
  };

  process.on('SIGTERM', () => handleShutdown('SIGTERM'));
  process.on('SIGINT', () => handleShutdown('SIGINT'));
}

module.exports = {
  app,
  setMockGeminiHandler
};
