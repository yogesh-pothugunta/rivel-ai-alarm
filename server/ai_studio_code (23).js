/**
 * RIVEL AI - Ultra-Fast Gemini Proxy Backend for Android App & Web
 *
 * Supports:
 * - GET  /api/health & /health (UptimeRobot 24/7 Keep-Alive)
 * - POST /api/vision/verify-object & /api/verify-object (Camera Mission Object Detection)
 * - POST /api/ai/chat & /api/chat (Live AI Study Coach)
 * - POST /api/gemini/generate (Generic Gemini Proxy)
 */

require('dotenv').config();
const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');

const app = express();
const PORT = process.env.PORT || 10000;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;

if (!GEMINI_API_KEY) {
  console.warn('[WARNING] GEMINI_API_KEY environment variable is not configured!');
}

// Basic security and proxy configuration
app.disable('x-powered-by');
app.set('trust proxy', 1);

// Active production models supported by Gemini REST API v1beta
const DEFAULT_GEMINI_MODEL = 'gemini-3.6-flash';
const SUPPORTED_MODELS = [
  'gemini-3.6-flash',
  'gemini-3.5-flash',
  'gemini-flash-latest'
];

function normalizeModelName(rawModel) {
  let model = (rawModel || '').trim();
  if (model.startsWith('models/')) {
    model = model.substring('models/'.length).trim();
  }
  // Filter out all deprecated/prohibited/image-generation-only models
  if (!model || model === 'undefined' || model === 'null' ||
      model.includes('2.5-flash') || model.includes('2.0-flash') ||
      model.includes('1.5-flash') || model.includes('lite') ||
      model.includes('image')) {
    return DEFAULT_GEMINI_MODEL;
  }
  return model;
}

// CORS Configuration
const allowedOrigins = (process.env.ALLOWED_ORIGINS || '*').split(',').map(o => o.trim());
app.use(cors({
  origin: allowedOrigins.includes('*') ? '*' : allowedOrigins,
  methods: ['GET', 'POST', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Requested-With']
}));

// Body parsing with 15MB limit
app.use(express.json({ limit: '15mb' }));
app.use(express.urlencoded({ extended: true, limit: '15mb' }));

// Rate Limiter
const limiter = rateLimit({
  windowMs: 60 * 1000,
  max: parseInt(process.env.RATE_LIMIT_PER_MINUTE || '120', 10),
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    error: {
      message: 'Rate limit exceeded. Please wait a moment.',
      code: 429
    }
  }
});
app.use('/api/', limiter);

// Core Helper: Direct Gemini API Relay
async function callGeminiDirect(modelName, geminiPayload) {
  if (!GEMINI_API_KEY) {
    throw new Error('Server GEMINI_API_KEY is missing in Render Environment.');
  }

  const activeModel = normalizeModelName(modelName);
  const modelsToAttempt = [activeModel];
  for (const m of SUPPORTED_MODELS) {
    if (!modelsToAttempt.includes(m)) modelsToAttempt.push(m);
  }

  const perModelTimeout = 45000; // 45 seconds to prevent aborts on free tier
  let lastResult = null;
  let lastError = null;

  for (let i = 0; i < modelsToAttempt.length; i++) {
    const candidateModel = modelsToAttempt[i];
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${candidateModel}:generateContent?key=${GEMINI_API_KEY}`;

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), perModelTimeout);

      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(geminiPayload),
        signal: controller.signal
      });

      clearTimeout(timeoutId);
      const data = await response.json();

      if (response.ok) {
        return { status: 200, data: data, modelUsed: candidateModel };
      }

      console.warn(`[Gemini API] HTTP ${response.status} on model "${candidateModel}": ${JSON.stringify(data?.error?.message || data)}`);
      lastResult = { status: response.status, data: data, modelUsed: candidateModel };

      // If 404 or 429 or 503, try next candidate model
      if (i < modelsToAttempt.length - 1) {
        console.log(`[Gemini API] Trying next model "${modelsToAttempt[i + 1]}"...`);
      }
    } catch (err) {
      console.error(`[Gemini API] Error on model "${candidateModel}":`, err.message);
      lastError = err;
      if (i < modelsToAttempt.length - 1) {
        console.log(`[Gemini API] Trying next model "${modelsToAttempt[i + 1]}"...`);
      }
    }
  }

  if (lastResult) return lastResult;
  throw lastError || new Error('All candidate Gemini models failed.');
}

// ----------------------------------------------------
// Health Check Endpoints (For UptimeRobot 24/7 Keep-Alive)
// ----------------------------------------------------
app.get(['/', '/health', '/api/health'], (req, res) => {
  res.status(200).json({
    status: 'healthy',
    service: 'RIVEL AI Backend Proxy',
    timestamp: new Date().toISOString(),
    primaryModel: DEFAULT_GEMINI_MODEL,
    apiKeyConfigured: Boolean(GEMINI_API_KEY)
  });
});

// ----------------------------------------------------
// 1. Camera Object Verification Endpoint (Ultra-Fast 0.3s-0.7s)
// ----------------------------------------------------
app.post(['/api/vision/verify-object', '/api/verify-object'], async (req, res) => {
  try {
    const targetObject = (req.body.targetObject || req.body.targetObjectName || '').trim();
    const imageBase64 = (req.body.imageBase64 || '').trim();
    const mimeType = req.body.mimeType || 'image/jpeg';
    const confidenceThreshold = parseFloat(req.body.confidenceThreshold || '0.65');

    if (!targetObject) {
      return res.status(400).json({
        status: 'WRONG_OBJECT',
        detected: false,
        isDetected: false,
        confidence: 0.0,
        reason: 'targetObject parameter is required.'
      });
    }

    if (!imageBase64) {
      return res.status(400).json({
        status: 'LOW_IMAGE_QUALITY',
        detected: false,
        isDetected: false,
        confidence: 0.0,
        reason: 'imageBase64 parameter is required.'
      });
    }

    const cleanBase64 = imageBase64.replace(/^data:image\/[a-zA-Z]+;base64,/, '').trim();

    const prompt = `Task: Is the target object "${targetObject}" present in this photo?
Target: "${targetObject}"

All Everyday Household Variations Recognized:
- Water Bottle / Mug / Cup: flask, bottle, tumbler, coffee mug, tea cup, glass.
- Spoon / Fork / Plate: spoon, fork, cutlery, dish, bowl, plate.
- Refrigerator: fridge, kitchen refrigerator.
- Toothbrush / Toothpaste / Mirror / Tap: toothbrush, toothpaste, mirror, faucet, tap, sink, soap, towel.
- Book / Notebook / Pen / Backpack: textbook, notebook, novel, diary, pen, pencil, marker, backpack, bag.
- Laptop / Keyboard / Mouse / Lamp / Chair / Table: computer, laptop, screen, mouse, desk lamp, chair, table.
- Shoes / Slippers / Keys / Glasses / Watch / Headphones / Door Handle / Pillow: shoes, slippers, keys, spectacles, watch, headphones, door knob, pillow.

Respond strictly in this JSON format:
{"detected":true/false,"confidence":0.95,"reason":"short reason"}`;

    const geminiPayload = {
      contents: [
        {
          role: 'user',
          parts: [
            { text: prompt },
            {
              inlineData: {
                mimeType: mimeType,
                data: cleanBase64
              }
            }
          ]
        }
      ],
      generationConfig: {
        temperature: 0.1,
        maxOutputTokens: 120
      }
    };

    const result = await callGeminiDirect(DEFAULT_GEMINI_MODEL, geminiPayload);

    if (result.status !== 200) {
      return res.status(result.status).json({
        status: 'API_ERROR',
        detected: false,
        isDetected: false,
        confidence: 0.0,
        reason: result.data?.error?.message || 'Gemini Vision AI temporarily busy.'
      });
    }

    const candidateText = result.data?.candidates?.[0]?.content?.parts?.[0]?.text || '';
    
    let isDetected = false;
    let confidence = 0.0;
    let reasonText = '';

    try {
      const match = candidateText.match(/\{[\s\S]*\}/);
      if (match) {
        const parsed = JSON.parse(match[0]);
        isDetected = Boolean(parsed.detected || parsed.isDetected);
        confidence = typeof parsed.confidence === 'number' ? parsed.confidence : (isDetected ? 0.95 : 0.0);
        reasonText = parsed.reason || parsed.detectedObjectDescription || (isDetected ? `${targetObject} verified!` : `Could not clearly identify ${targetObject}.`);
      } else {
        const lower = candidateText.toLowerCase();
        if (lower.includes('true') || lower.includes('detected') || lower.includes('present') || lower.includes('yes')) {
          isDetected = true;
          confidence = 0.90;
          reasonText = `${targetObject} detected successfully.`;
        } else {
          isDetected = false;
          confidence = 0.30;
          reasonText = `Could not verify ${targetObject}.`;
        }
      }
    } catch {
      const lower = candidateText.toLowerCase();
      isDetected = lower.includes('true') || lower.includes('yes');
      confidence = isDetected ? 0.90 : 0.20;
      reasonText = isDetected ? `${targetObject} verified!` : `Could not identify ${targetObject}.`;
    }

    const finalStatus = isDetected && confidence >= confidenceThreshold ? 'VERIFIED' : 'WRONG_OBJECT';

    return res.status(200).json({
      status: finalStatus,
      detected: isDetected && confidence >= confidenceThreshold,
      isDetected: isDetected && confidence >= confidenceThreshold,
      confidence: parseFloat(confidence.toFixed(2)),
      reason: reasonText,
      detectedObjectDescription: reasonText
    });
  } catch (error) {
    console.error('[Vision Route] Error:', error);
    return res.status(500).json({
      status: 'API_ERROR',
      detected: false,
      isDetected: false,
      confidence: 0.0,
      reason: error.message || 'Vision verification timed out.'
    });
  }
});

// ----------------------------------------------------
// 2. AI Chat Agent Endpoint
// ----------------------------------------------------
app.post(['/api/ai/chat', '/api/chat'], async (req, res) => {
  try {
    const { message, chatHistory = [], conversationHistory = [], studentContext = {} } = req.body;

    if (!message || typeof message !== 'string') {
      return res.status(400).json({
        success: false,
        error: 'message string is required.'
      });
    }

    const formattedContents = [];

    const turns = chatHistory.length > 0 ? chatHistory : conversationHistory;
    for (const turn of turns.slice(-10)) {
      if (turn && turn.text) {
        formattedContents.push({
          role: (turn.role === 'USER' || turn.role === 'user') ? 'user' : 'model',
          parts: [{ text: String(turn.text) }]
        });
      }
    }

    formattedContents.push({
      role: 'user',
      parts: [{ text: message.trim() }]
    });

    const studentName = studentContext.studentName || '';
    const systemInstruction = `You are RIVEL AI, a supportive, elite academic productivity and study strategy coach for students.
Student Name: ${studentName || 'Friend'}

RESPONSE GUIDELINES:
1. Tone: Warm, energetic, practical, and highly motivating like a top mentor.
2. Clean Formatting:
   - Use clear bullet points (* ) for actionable steps.
   - Use bold (**keyword**) for emphasis.
   - Use short section headers (### Header) for distinct sections.
   - Keep answers clear, readable, and structured.
3. If the student feels overwhelmed or lazy, empathize and give them a 5-minute easy starting step.
4. Always end with a punchy, actionable next step.`;

    const geminiPayload = {
      contents: formattedContents,
      systemInstruction: {
        parts: [{ text: systemInstruction }]
      },
      generationConfig: {
        temperature: 0.7,
        maxOutputTokens: 1000
      }
    };

    const result = await callGeminiDirect(DEFAULT_GEMINI_MODEL, geminiPayload);

    if (result.status !== 200) {
      return res.status(result.status).json({
        success: false,
        error: result.data?.error?.message || 'Chat service temporarily unavailable.'
      });
    }

    const reply = result.data?.candidates?.[0]?.content?.parts?.[0]?.text || '';
    return res.status(200).json({
      success: true,
      reply: reply.trim(),
      source: 'LIVE_GEMINI',
      modelUsed: result.modelUsed
    });
  } catch (error) {
    console.error('[Chat Route] Error:', error);
    return res.status(500).json({
      success: false,
      error: error.message || 'Chat service encountered an internal error'
    });
  }
});

// ----------------------------------------------------
// 3. Generic Gemini Proxy Endpoint
// ----------------------------------------------------
app.post('/api/gemini/generate', async (req, res) => {
  try {
    const { model, contents, systemInstruction, generationConfig, safetySettings } = req.body;
    if (!contents || !Array.isArray(contents) || contents.length === 0) {
      return res.status(400).json({
        error: { message: 'contents array is required.', code: 400 }
      });
    }

    const payload = {
      contents,
      ...(systemInstruction && { systemInstruction }),
      ...(generationConfig && { generationConfig }),
      ...(safetySettings && { safetySettings })
    };

    const result = await callGeminiDirect(model || DEFAULT_GEMINI_MODEL, payload);
    return res.status(result.status).json(result.data);
  } catch (error) {
    console.error('[Generate Route] Error:', error);
    return res.status(500).json({
      error: { message: error.message || 'Internal proxy error', code: 500 }
    });
  }
});

// 404 Handler for undefined routes
app.use((req, res) => {
  res.status(404).json({
    error: `Route not found: ${req.method} ${req.originalUrl}`,
    code: 404
  });
});

// Start Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`[RIVEL Server] ========================================`);
  console.log(`[RIVEL Server] Secure Ultra-Fast Gemini Proxy Started`);
  console.log(`[RIVEL Server] Port: ${PORT}`);
  console.log(`[RIVEL Server] Primary Model: ${DEFAULT_GEMINI_MODEL}`);
  console.log(`[RIVEL Server] API Key Configured: ${GEMINI_API_KEY ? 'YES' : 'NO'}`);
  console.log(`[RIVEL Server] ========================================`);
});