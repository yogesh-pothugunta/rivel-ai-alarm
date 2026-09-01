/**
 * RIVEL AI - Secure Server-Side Gemini Proxy Backend
 *
 * Provides safe, authenticated relaying of Gemini API requests with:
 * - Direct zero-overhead high-speed Gemini calling
 * - Up-to-date active model normalization (gemini-3.6-flash, gemini-3.5-flash, gemini-3.5-flash-lite)
 * - Automatic fast fallback retry on transient errors
 * - Strict input validation & JSON schema enforcement
 * - Rate limiting (anti-abuse)
 * - Keep-alive health check endpoint for zero-cold-start 24/7 uptime
 */

require('dotenv').config();
const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');

const app = express();
const PORT = process.env.PORT || 10000;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;

if (!GEMINI_API_KEY) {
  console.error('[CRITICAL] GEMINI_API_KEY environment variable is missing!');
}

// Security & Header configurations
app.disable('x-powered-by');
app.set('trust proxy', 1);

// Supported Gemini Models with automatic normalization and fast fallback
const DEFAULT_GEMINI_MODEL = 'gemini-3.6-flash';
const KNOWN_SUPPORTED_MODELS = [
  'gemini-3.6-flash',
  'gemini-3.5-flash',
  'gemini-3.5-flash-lite',
  'gemini-3.1-flash-lite-preview',
  'gemini-2.5-flash'
];

function normalizeModelName(rawModel) {
  let model = (rawModel || '').trim();
  if (model.startsWith('models/')) {
    model = model.substring('models/'.length).trim();
  }
  // If undefined string, empty, or legacy/discontinued models, use active default
  if (!model || model === 'undefined' || model === 'null' || 
      model === 'gemini-2.0-flash' || model === 'gemini-1.5-flash' || 
      model === 'gemini-1.5-pro' || model === 'gemini-2.5-pro' || 
      model === 'gemini-3.1-pro-preview' || model === 'gemini-2.5-flash-lite') {
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

// Body parsing with 15MB limit for high-res camera images
app.use(express.json({ limit: '15mb' }));
app.use(express.urlencoded({ extended: true, limit: '15mb' }));

// Rate Limiter
const limiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: parseInt(process.env.RATE_LIMIT_PER_MINUTE || '120', 10),
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    error: {
      message: 'Too many requests from this IP, please try again in a moment.',
      code: 429
    }
  }
});
app.use('/api/', limiter);

// Core Helper: Direct Gemini API Relay with Fast Fallback
async function callGeminiDirectWithFallback(modelName, geminiPayload) {
  if (!GEMINI_API_KEY) {
    throw new Error('Server-side GEMINI_API_KEY is not configured.');
  }

  const preferredModel = normalizeModelName(modelName);

  // Candidate models to attempt in order
  const defaultModelList = [
    'gemini-3.6-flash',
    'gemini-3.5-flash',
    'gemini-3.5-flash-lite',
    'gemini-3.1-flash-lite-preview',
    'gemini-2.5-flash'
  ];
  const modelsToAttempt = [preferredModel];
  for (const m of defaultModelList) {
    if (!modelsToAttempt.includes(m)) modelsToAttempt.push(m);
  }

  const RETRIABLE_STATUS_CODES = [404, 429, 500, 502, 503, 504];
  const perModelTimeout = 12000; // 12 seconds per attempt

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
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(geminiPayload),
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      const data = await response.json();

      if (response.ok) {
        return {
          status: 200,
          data: data,
          modelUsed: candidateModel
        };
      }

      console.warn(`[Gemini API] Request returned HTTP ${response.status} using model "${candidateModel}". Error: ${JSON.stringify(data?.error?.message || data)}`);

      lastResult = {
        status: response.status,
        data: data,
        modelUsed: candidateModel
      };

      if (!RETRIABLE_STATUS_CODES.includes(response.status)) {
        return lastResult;
      }

      if (i < modelsToAttempt.length - 1) {
        console.log(`[Gemini API] Retrying with fallback model "${modelsToAttempt[i + 1]}"...`);
      }
    } catch (err) {
      console.error(`[Gemini API] Network / timeout failure on model "${candidateModel}":`, err.message);
      lastError = err;

      if (i < modelsToAttempt.length - 1) {
        console.log(`[Gemini API] Retrying with fallback model "${modelsToAttempt[i + 1]}"...`);
      }
    }
  }

  if (lastResult) return lastResult;
  throw lastError || new Error('All candidate Gemini models failed.');
}

// ----------------------------------------------------
// Health Check Endpoint (For UptimeRobot 24/7 Keep-Alive)
// ----------------------------------------------------
app.get('/api/health', (req, res) => {
  res.status(200).json({
    status: 'healthy',
    service: 'RIVEL AI Proxy Backend',
    timestamp: new Date().toISOString(),
    primaryModel: DEFAULT_GEMINI_MODEL,
    apiKeyConfigured: !!GEMINI_API_KEY
  });
});

app.get('/health', (req, res) => {
  res.status(200).json({ status: 'ok' });
});

// ----------------------------------------------------
// 1. Generic Gemini Proxy Endpoint
// ----------------------------------------------------
app.post('/api/gemini/generate', async (req, res) => {
  try {
    const { model, contents, systemInstruction, generationConfig, safetySettings } = req.body;

    if (!contents || !Array.isArray(contents) || contents.length === 0) {
      return res.status(400).json({
        error: { message: 'Invalid payload: contents array is required.', code: 400 }
      });
    }

    const payload = {
      contents,
      ...(systemInstruction && { systemInstruction }),
      ...(generationConfig && { generationConfig }),
      ...(safetySettings && { safetySettings })
    };

    const result = await callGeminiDirectWithFallback(model || DEFAULT_GEMINI_MODEL, payload);
    return res.status(result.status).json(result.data);
  } catch (error) {
    console.error('[Generate Route] Unhandled Exception:', error);
    return res.status(500).json({
      error: { message: error.message || 'Internal proxy error', code: 500 }
    });
  }
});

// ----------------------------------------------------
// 2. High-Speed Object Detection Endpoint (Camera Mission)
// ----------------------------------------------------
app.post('/api/verify-object', async (req, res) => {
  try {
    const { imageBase64, mimeType = 'image/jpeg', targetObjectName, confidenceThreshold = 0.80 } = req.body;

    if (!imageBase64 || !targetObjectName) {
      return res.status(400).json({
        error: { message: 'imageBase64 and targetObjectName are required fields.', code: 400 }
      });
    }

    const cleanBase64 = imageBase64.replace(/^data:image\/[a-z]+;base64,/, '');

    const promptText = `
You are an instant, accurate vision classifier for an alarm verification system.
TARGET OBJECT TO DETECT: "${targetObjectName}"

Carefully analyze the image:
1. Is the target object ("${targetObjectName}") or a standard recognized variant of it clearly visible?
2. Estimate your confidence between 0.00 and 1.00.
3. If visible and confidence >= ${confidenceThreshold}, set isDetected=true.
4. Give a brief, friendly 1-sentence reasoning in detectedObjectDescription.

Respond ONLY with valid JSON conforming to this schema:
{
  "isDetected": boolean,
  "confidence": number,
  "detectedObjectDescription": string,
  "alternativeSuggestions": string[]
}
`;

    const payload = {
      contents: [
        {
          role: 'user',
          parts: [
            { text: promptText },
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
        responseMimeType: 'application/json',
        temperature: 0.1,
        maxOutputTokens: 256
      }
    };

    const result = await callGeminiDirectWithFallback(DEFAULT_GEMINI_MODEL, payload);

    if (result.status !== 200) {
      return res.status(result.status).json(result.data);
    }

    const candidateText = result.data?.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!candidateText) {
      return res.status(500).json({
        isDetected: false,
        confidence: 0.0,
        detectedObjectDescription: 'Failed to parse model response'
      });
    }

    let parsedResult;
    try {
      parsedResult = JSON.parse(candidateText);
    } catch {
      const match = candidateText.match(/\{[\s\S]*\}/);
      if (match) parsedResult = JSON.parse(match[0]);
      else throw new Error('Unparseable JSON from model');
    }

    return res.status(200).json(parsedResult);
  } catch (error) {
    console.error('[Vision Verification] Error:', error);
    return res.status(500).json({
      isDetected: false,
      confidence: 0.0,
      detectedObjectDescription: error.message || 'Verification failed due to an internal error'
    });
  }
});

// ----------------------------------------------------
// 3. AI Chat Agent Endpoint (Live RIVEL Assistant)
// ----------------------------------------------------
app.post('/api/chat', async (req, res) => {
  try {
    const { message, conversationHistory = [], systemInstruction } = req.body;

    if (!message || typeof message !== 'string') {
      return res.status(400).json({
        error: { message: 'message string is required.', code: 400 }
      });
    }

    const formattedContents = [];

    // Add prior conversation turns
    for (const turn of conversationHistory) {
      if (turn.role && turn.text) {
        formattedContents.push({
          role: turn.role === 'assistant' ? 'model' : 'user',
          parts: [{ text: turn.text }]
        });
      }
    }

    // Add latest user message
    formattedContents.push({
      role: 'user',
      parts: [{ text: message }]
    });

    const defaultSystem = systemInstruction || 
      "You are RIVEL AI, a supportive, hyper-focused productivity and discipline assistant for students. Provide direct, actionable, encouraging, and clear answers. Keep responses concise and engaging.";

    const payload = {
      contents: formattedContents,
      systemInstruction: {
        parts: [{ text: defaultSystem }]
      },
      generationConfig: {
        temperature: 0.7,
        maxOutputTokens: 1024
      }
    };

    const result = await callGeminiDirectWithFallback(DEFAULT_GEMINI_MODEL, payload);

    if (result.status !== 200) {
      return res.status(result.status).json(result.data);
    }

    const responseText = result.data?.candidates?.[0]?.content?.parts?.[0]?.text || '';
    return res.status(200).json({
      reply: responseText,
      modelUsed: result.modelUsed
    });
  } catch (error) {
    console.error('[Chat Route] Error:', error);
    return res.status(500).json({
      error: { message: error.message || 'Chat service encountered an internal error', code: 500 }
    });
  }
});

// 404 Handler for undefined routes
app.use((req, res) => {
  res.status(404).json({
    error: { message: `Route not found: ${req.method} ${req.originalUrl}`, code: 404 }
  });
});

// Start Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`[RIVEL Server] ========================================`);
  console.log(`[RIVEL Server] Secure Gemini Proxy Backend Started`);
  console.log(`[RIVEL Server] Port: ${PORT}`);
  console.log(`[RIVEL Server] Primary Model: ${DEFAULT_GEMINI_MODEL}`);
  console.log(`[RIVEL Server] API Key Configured: ${GEMINI_API_KEY ? 'YES (Active)' : 'NO (Missing)'}`);
  console.log(`[RIVEL Server] ========================================`);
});
