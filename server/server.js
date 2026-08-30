/**
 * RIVEL Secure Gemini API Proxy Backend
 *
 * Production-ready backend for Render / Container Deployment.
 *
 * Provides secure HTTPS proxy endpoints for RIVEL Android client:
 * 1. POST /api/ai/chat
 * 2. POST /api/vision/verify-object
 * 3. GET  /api/health
 *
 * Security & Privacy:
 * - Gemini API key stays server-side only.
 * - No image or chat data is saved to disk.
 * - API keys and sensitive data are never returned to clients.
 * - Rate limiting and request size limits are enabled.
 */

require('dotenv').config();

const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');

const app = express();

const PORT = parseInt(process.env.PORT || '8080', 10);

/**
 * Render runs behind a reverse proxy.
 * This is required for express-rate-limit and X-Forwarded-For.
 */
app.set('trust proxy', 1);

/**
 * Prevent Express fingerprinting.
 */
app.disable('x-powered-by');


/* ============================================================
   GEMINI CONFIGURATION
============================================================ */

/**
 * Primary model.
 *
 * We keep gemini-3.7-flash as the configured default if available.
 * If it is temporarily overloaded or unavailable, the backend
 * automatically tries fallback models.
 */
const DEFAULT_GEMINI_MODEL = 'gemini-3.7-flash';

/**
 * Model fallback order.
 *
 * IMPORTANT:
 * The first available model that successfully responds will be used.
 */
const KNOWN_SUPPORTED_MODELS = [
  'gemini-3.7-flash',
  'gemini-2.5-flash',
  'gemini-2.5-pro'
];


/**
 * Normalize model names coming from Render environment variables.
 *
 * Examples:
 *
 * "gemini-2.5-flash"
 * " models/gemini-2.5-flash "
 *
 * Both become:
 *
 * "gemini-2.5-flash"
 */
function normalizeModelName(rawModel) {
  if (!rawModel || typeof rawModel !== 'string') {
    return DEFAULT_GEMINI_MODEL;
  }

  let model = rawModel.trim();

  if (model.startsWith('models/')) {
    model = model
      .substring('models/'.length)
      .trim();
  }

  /**
   * Invalid / empty / legacy values fall back safely.
   */
  if (
    !model ||
    model === 'undefined' ||
    model === 'null' ||
    model === 'gemini-2.0-flash' ||
    model === 'gemini-1.5-flash' ||
    model === 'gemini-1.5-pro'
  ) {
    return DEFAULT_GEMINI_MODEL;
  }

  return model;
}


const CONFIGURED_MODEL =
  normalizeModelName(process.env.GEMINI_MODEL);


/**
 * Object verification confidence threshold.
 */
const OBJECT_CONFIDENCE_THRESHOLD =
  parseFloat(
    process.env.OBJECT_CONFIDENCE_THRESHOLD || '0.80'
  );


/**
 * Timeout per Gemini model request.
 */
const GEMINI_TIMEOUT_MS =
  parseInt(
    process.env.GEMINI_TIMEOUT_MS || '25000',
    10
  );


/* ============================================================
   CORS
============================================================ */

const allowedOrigins =
  process.env.ALLOWED_ORIGINS
    ? process.env.ALLOWED_ORIGINS
        .split(',')
        .map(origin => origin.trim())
    : [];


app.use(
  cors({
    origin: (origin, callback) => {

      /**
       * Android apps and server requests often have no Origin header.
       */
      if (!origin) {
        return callback(null, true);
      }

      /**
       * Allow configured origins.
       */
      if (
        allowedOrigins.length === 0 ||
        allowedOrigins.includes(origin) ||
        origin.includes('localhost') ||
        origin.includes('127.0.0.1')
      ) {
        return callback(null, true);
      }

      return callback(
        new Error(
          'CORS policy: Not allowed by origin'
        )
      );
    },

    methods: [
      'GET',
      'POST',
      'OPTIONS'
    ],

    allowedHeaders: [
      'Content-Type',
      'Accept'
    ],

    maxAge: 86400
  })
);


/* ============================================================
   BODY PARSER
============================================================ */

/**
 * Maximum request size: 10MB.
 *
 * Camera images are sent as base64.
 */
app.use(
  express.json({
    limit: '10mb'
  })
);


/* ============================================================
   GLOBAL RATE LIMITING
============================================================ */

const globalLimiter =
  rateLimit({
    windowMs: 60 * 1000,

    max: 120,

    standardHeaders: true,

    legacyHeaders: false,

    message: {
      success: false,

      error:
        'Too many requests. Please try again in a minute.',

      status:
        'RATE_LIMITED'
    }
  });


app.use(globalLimiter);


/* ============================================================
   VISION RATE LIMITING
============================================================ */

const visionLimiter =
  rateLimit({
    windowMs: 60 * 1000,

    max: 30,

    standardHeaders: true,

    legacyHeaders: false,

    message: {
      status:
        'API_ERROR',

      detected:
        false,

      confidence:
        0.0,

      reason:
        'Verification rate limit exceeded. Please wait a moment before capturing again.'
    }
  });


/* ============================================================
   TEST / MOCK SUPPORT
============================================================ */

let mockGeminiHandler = null;


function setMockGeminiHandler(handler) {
  mockGeminiHandler = handler;
}


/* ============================================================
   GEMINI API CALL WITH FALLBACK
============================================================ */

/**
 * Calls Gemini API.
 *
 * IMPORTANT:
 *
 * If the first model returns:
 *
 * 404
 * 429
 * 500
 * 502
 * 503
 * 504
 *
 * OR times out,
 *
 * the backend automatically tries the next fallback model.
 */
async function callGeminiApi(
  payload,
  preferredModel = CONFIGURED_MODEL
) {

  /**
   * Mock support for automated tests.
   */
  if (
    mockGeminiHandler &&
    (
      process.env.NODE_ENV === 'test' ||
      process.env.TEST_MODE === 'true'
    )
  ) {
    return await mockGeminiHandler(payload);
  }


  const apiKey =
    process.env.GEMINI_API_KEY;


  /**
   * Validate API key.
   */
  if (
    !apiKey ||
    apiKey.trim() === '' ||
    apiKey === 'MY_GEMINI_API_KEY' ||
    apiKey === 'YOUR_GEMINI_API_KEY_HERE'
  ) {

    const error =
      new Error('NO_API_KEY');

    error.code =
      'NO_API_KEY';

    throw error;
  }


  /**
   * Build model attempt list.
   *
   * Example:
   *
   * Preferred:
   * gemini-3.7-flash
   *
   * Then:
   * gemini-2.5-flash
   * gemini-2.5-pro
   */
  const modelsToAttempt = [];


  /**
   * Preferred model first.
   */
  if (
    preferredModel &&
    !modelsToAttempt.includes(preferredModel)
  ) {
    modelsToAttempt.push(preferredModel);
  }


  /**
   * Reliable fallback order.
   */
  const fallbackModels = [
    'gemini-2.5-flash',
    'gemini-3.7-flash',
    'gemini-2.5-pro'
  ];


  for (
    const model of fallbackModels
  ) {

    if (
      !modelsToAttempt.includes(model)
    ) {
      modelsToAttempt.push(model);
    }
  }


  /**
   * These HTTP statuses can be retried
   * using another Gemini model.
   */
  const retryableStatuses = [
    404,
    429,
    500,
    502,
    503,
    504
  ];


  let lastResult = null;
  let lastError = null;


  /**
   * Try each model one by one.
   */
  for (
    let i = 0;
    i < modelsToAttempt.length;
    i++
  ) {

    const currentModel =
      modelsToAttempt[i];


    const apiVersion =
      'v1beta';


    const endpointPath =
      `https://generativelanguage.googleapis.com/${apiVersion}/models/${currentModel}:generateContent`;


    const fullUrl =
      `${endpointPath}?key=${apiKey.trim()}`;


    /**
     * Timeout controller.
     */
    const controller =
      new AbortController();


    const timeoutId =
      setTimeout(
        () => controller.abort(),
        GEMINI_TIMEOUT_MS
      );


    try {

      console.log(
        `[Gemini API] Attempt ${i + 1}/${modelsToAttempt.length} using model "${currentModel}"`
      );


      const response =
        await fetch(
          fullUrl,
          {
            method:
              'POST',

            headers: {
              'Content-Type':
                'application/json'
            },

            body:
              JSON.stringify(payload),

            signal:
              controller.signal
          }
        );


      clearTimeout(timeoutId);


      let data;


      /**
       * Safely parse JSON response.
       */
      try {
        data =
          await response.json();
      } catch {
        data = {};
      }


      lastResult = {
        status:
          response.status,

        data,

        modelUsed:
          currentModel
      };


      /**
       * SUCCESS
       */
      if (response.ok) {

        console.log(
          `[Gemini API] Success using model "${currentModel}"`
        );

        return lastResult;
      }


      /**
       * Get Gemini error safely.
       */
      const geminiErrMsg =
        data?.error?.message ||
        data?.error?.status ||
        'Unknown Gemini API error';


      console.warn(
        `[Gemini API] HTTP ${response.status} using "${currentModel}". Details: ${geminiErrMsg}`
      );


      const hasFallback =
        i <
        modelsToAttempt.length - 1;


      /**
       * IMPORTANT FIX:
       *
       * Retry another model for:
       *
       * 404
       * 429
       * 500
       * 502
       * 503
       * 504
       */
      if (
        retryableStatuses.includes(
          response.status
        ) &&
        hasFallback
      ) {

        console.warn(
          `[Gemini API] Switching to fallback model "${modelsToAttempt[i + 1]}"`
        );

        continue;
      }


      /**
       * No more fallback available.
       */
      return lastResult;

    } catch (err) {

      clearTimeout(timeoutId);


      const hasFallback =
        i <
        modelsToAttempt.length - 1;


      /**
       * IMPORTANT FIX:
       *
       * Timeout on one model should NOT
       * immediately fail the Android app.
       *
       * Try the next model first.
       */
      if (
        err.name === 'AbortError'
      ) {

        console.warn(
          `[Gemini API] Timeout using "${currentModel}"`
        );


        if (
          hasFallback
        ) {

          console.warn(
            `[Gemini API] Timeout fallback → "${modelsToAttempt[i + 1]}"`
          );

          continue;
        }


        const timeoutErr =
          new Error(
            'Gemini API call timed out'
          );


        timeoutErr.code =
          'TIMEOUT';


        throw timeoutErr;
      }


      /**
       * Other network / execution errors.
       */
      lastError =
        err;


      console.error(
        `[Gemini API] Network/execution error with model "${currentModel}":`,
        err.message
      );


      /**
       * Try next model before failing.
       */
      if (
        hasFallback
      ) {

        console.warn(
          `[Gemini API] Network fallback → "${modelsToAttempt[i + 1]}"`
        );

        continue;
      }


      throw lastError;
    }
  }


  return lastResult;
}


/* ============================================================
   HEALTH CHECK
============================================================ */

app.get(
  [
    '/',
    '/api/health'
  ],

  (req, res) => {

    const isConfigured =
      Boolean(
        process.env.GEMINI_API_KEY &&
        process.env.GEMINI_API_KEY.trim() !== '' &&
        process.env.GEMINI_API_KEY !== 'MY_GEMINI_API_KEY' &&
        process.env.GEMINI_API_KEY !== 'YOUR_GEMINI_API_KEY_HERE'
      );


    res.status(200).json({
      status:
        'healthy',

      service:
        'RIVEL Secure AI Backend Proxy',

      version:
        '1.0.0',

      geminiConfigured:
        isConfigured,

      model:
        CONFIGURED_MODEL,

      environment:
        process.env.NODE_ENV ||
        'production',

      timestamp:
        new Date().toISOString()
    });
  }
);


/* ============================================================
   AI CHAT ENDPOINT
============================================================ */

app.post(
  '/api/ai/chat',

  async (
    req,
    res
  ) => {

    try {

      const {
        message,
        studentContext,
        chatHistory
      } =
        req.body;


      /**
       * Validate message.
       */
      if (
        !message ||
        typeof message !== 'string' ||
        message.trim() === ''
      ) {

        return res
          .status(400)
          .json({
            success:
              false,

            error:
              'Invalid request: "message" is required and must be a non-empty string.'
          });
      }


      if (
        message.length > 3000
      ) {

        return res
          .status(400)
          .json({
            success:
              false,

            error:
              'Message exceeds maximum allowed length of 3000 characters.'
          });
      }


      const ctx =
        studentContext ||
        {};


      const studentName =
        ctx.studentName &&
        ctx.studentName.trim()
          ? ctx.studentName.trim()
          : '';


      const studentInfo =
        [

          studentName
            ? `Student Name: ${studentName}`
            : '',

          ctx.course
            ? `Course/Major: ${ctx.course} (${ctx.educationLevel || ''}, ${ctx.year || ''})`
            : '',

          Array.isArray(
            ctx.subjects
          ) &&
          ctx.subjects.length > 0
            ? `Enrolled Subjects: ${ctx.subjects.join(', ')}`
            : '',

          ctx.mainGoal
            ? `Main Goal: ${ctx.mainGoal}`
            : '',

          ctx.upcomingExam
            ? `Upcoming Exam / Deadline: ${ctx.upcomingExam}`
            : '',

          `Today's stats: ${ctx.todayCompletedMinutes || 0} min studied so far, ${ctx.activeStreak || 0} day streak.`

        ]
          .filter(Boolean)
          .join('\n');


      const systemInstruction =
`You are RIVEL AI, a friendly, intelligent, and natural AI study companion and productivity coach for students.

Student Profile & Context:
${studentInfo || 'General student'}

PERSONALITY & CONVERSATIONAL GUIDELINES:

1. NATURAL & CONVERSATIONAL:
Speak like a supportive, smart, encouraging mentor and study partner.
Be warm, human, and adaptable.
Never sound robotic or like a rigid automated script.

2. DYNAMIC INTENT ADAPTATION:

- GREETINGS & CASUAL CHAT:
Respond warmly and briefly.
Do not force an unsolicited study plan or lecture.

- LAZINESS & PROCRASTINATION:
Acknowledge the feeling with genuine empathy.
Remove guilt.
Explain that starting is often the hardest part.
Encourage one small, easy action.

- EXAM STRESS & URGENT DEADLINES:
Stay calm and reassuring.
Help prioritize important topics.

- ACADEMIC & TECHNICAL QUESTIONS:
Explain clearly with simple analogies and step-by-step reasoning.

- STUDY PLANS:
Ask short clarifying questions when needed.
Otherwise provide a realistic and clean plan.

3. NO RIGID TEMPLATES:
Do not force every answer into the same format.

4. CONTEXT & MEMORY:
Pay attention to recent conversation history.
Connect follow-up questions naturally.

5. CONCISE & READABLE:
Keep responses engaging and easy to read.
Use markdown only where useful.`;


      const contents =
        [];


      /**
       * Add recent conversation history.
       */
      if (
        Array.isArray(
          chatHistory
        )
      ) {

        const recent =
          chatHistory.slice(-12);


        for (
          const turn of recent
        ) {

          if (
            turn &&
            turn.role &&
            turn.text
          ) {

            contents.push({
              role:
                turn.role === 'USER' ||
                turn.role === 'user'
                  ? 'user'
                  : 'model',

              parts: [
                {
                  text:
                    String(
                      turn.text
                    )
                }
              ]
            });
          }
        }
      }


      /**
       * Current user message.
       */
      contents.push({
        role:
          'user',

        parts: [
          {
            text:
              message.trim()
          }
        ]
      });


      const geminiPayload = {

        contents,

        systemInstruction: {
          parts: [
            {
              text:
                systemInstruction
            }
          ]
        },

        generationConfig: {
          temperature:
            0.75,

          maxOutputTokens:
            1000
        }
      };


      /**
       * Call Gemini.
       *
       * Automatic model fallback happens inside this function.
       */
      const {
        status,
        data
      } =
        await callGeminiApi(
          geminiPayload
        );


      if (
        status !== 200
      ) {

        console.error(
          `[AI Chat] Gemini API returned error status: ${status}`
        );


        return res
          .status(502)
          .json({
            success:
              false,

            error:
              'AI service temporarily unavailable.',

            source:
              'ERROR'
          });
      }


      const candidate =
        data?.candidates?.[0];


      const replyText =
        candidate?.content?.parts?.[0]?.text;


      if (
        !replyText ||
        typeof replyText !== 'string' ||
        replyText.trim() === ''
      ) {

        return res
          .status(502)
          .json({
            success:
              false,

            error:
              'Empty response received from AI model.',

            source:
              'ERROR'
          });
      }


      /**
       * Suggested actions.
       */
      const lowerPrompt =
        message.toLowerCase();


      const lowerReply =
        replyText.toLowerCase();


      let suggestedActionType =
        null;

      let suggestedActionPayload =
        null;

      let suggestedActionLabel =
        null;


      if (
        lowerPrompt.includes('focus') ||
        lowerPrompt.includes('procrastinat') ||
        lowerPrompt.includes('start study') ||
        lowerReply.includes('25-minute focus') ||
        lowerReply.includes('focus sprint')
      ) {

        suggestedActionType =
          'START_FOCUS';

        suggestedActionPayload =
          '25';

        suggestedActionLabel =
          'Start 25-Min Focus Sprint';

      } else if (

        lowerPrompt.includes('plan') ||
        lowerPrompt.includes('schedule') ||
        lowerPrompt.includes('task') ||
        lowerPrompt.includes('exam')

      ) {

        suggestedActionType =
          'CREATE_TASK';

        suggestedActionPayload =
          'High Priority Study Task';

        suggestedActionLabel =
          "Add to Today's Plan";

      } else if (

        lowerPrompt.includes('alarm') ||
        lowerPrompt.includes('wake up') ||
        lowerPrompt.includes('morning')

      ) {

        suggestedActionType =
          'SET_ALARM';

        suggestedActionPayload =
          '06:30';

        suggestedActionLabel =
          'Set Morning Wake Alarm';
      }


      return res
        .status(200)
        .json({

          success:
            true,

          reply:
            replyText.trim(),

          source:
            'LIVE_GEMINI',

          suggestedActionType,

          suggestedActionPayload,

          suggestedActionLabel
        });


    } catch (
      err
    ) {

      /**
       * API key missing.
       */
      if (
        err.code === 'NO_API_KEY'
      ) {

        console.warn(
          '[AI Chat] Gemini API key not configured on server.'
        );


        return res
          .status(503)
          .json({

            success:
              false,

            error:
              'AI service is currently not configured on server.',

            source:
              'ERROR'
          });
      }


      /**
       * All model attempts timed out.
       */
      if (
        err.code === 'TIMEOUT'
      ) {

        console.error(
          '[AI Chat] Request timed out contacting Gemini API.'
        );


        return res
          .status(504)
          .json({

            success:
              false,

            error:
              'AI service request timed out.',

            source:
              'ERROR'
          });
      }


      console.error(
        '[AI Chat] Unexpected error:',
        err.message
      );


      return res
        .status(500)
        .json({

          success:
            false,

          error:
            'An unexpected internal error occurred.',

          source:
            'ERROR'
        });
    }
  }
);


/* ============================================================
   VISION OBJECT VERIFICATION
============================================================ */

app.post(

  '/api/vision/verify-object',

  visionLimiter,

  async (
    req,
    res
  ) => {

    try {

      const {
        targetObject,
        imageBase64
      } =
        req.body;


      /**
       * Validate target object.
       */
      if (
        !targetObject ||
        typeof targetObject !== 'string' ||
        targetObject.trim() === ''
      ) {

        return res
          .status(400)
          .json({

            status:
              'WRONG_OBJECT',

            detected:
              false,

            confidence:
              0.0,

            reason:
              'Invalid request: "targetObject" parameter is missing or empty.'
          });
      }


      /**
       * Validate image.
       */
      if (
        !imageBase64 ||
        typeof imageBase64 !== 'string' ||
        imageBase64.trim() === ''
      ) {

        return res
          .status(400)
          .json({

            status:
              'LOW_IMAGE_QUALITY',

            detected:
              false,

            confidence:
              0.0,

            reason:
              'Invalid request: "imageBase64" image data is missing.'
          });
      }


      /**
       * Remove data URL prefix if present.
       */
      const cleanBase64 =
        imageBase64
          .replace(
            /^data:image\/[a-zA-Z]+;base64,/,
            ''
          )
          .trim();


      /**
       * Reject extremely small image data.
       */
      if (
        cleanBase64.length < 100
      ) {

        return res
          .status(400)
          .json({

            status:
              'LOW_IMAGE_QUALITY',

            detected:
              false,

            confidence:
              0.0,

            reason:
              'Captured image data is corrupted or too small to evaluate.'
          });
      }


      /**
       * Validate base64.
       */
      const base64Regex =
        /^[A-Za-z0-9+/=]+$/;


      if (
        !base64Regex.test(
          cleanBase64.substring(
            0,
            Math.min(
              cleanBase64.length,
              500
            )
          )
        )
      ) {

        return res
          .status(400)
          .json({

            status:
              'LOW_IMAGE_QUALITY',

            detected:
              false,

            confidence:
              0.0,

            reason:
              'Captured image data is not valid base64 encoding.'
          });
      }


      const cleanTarget =
        targetObject.trim();


      /**
       * Gemini Vision prompt.
       */
      const prompt =
`You are a strict physical wake-up alarm verification AI.

Examine this photo taken by a waking student.

The target object required to turn off the alarm is:

"${cleanTarget}"

TASK:

1. Determine if the photo clearly shows a "${cleanTarget}" or a reasonable everyday variation.

Examples:
- book → textbook or notebook
- water bottle → tumbler or flask
- shoe → sneakers or shoes
- keys → keychain or keys
- pen → pen or pencil
- mug → coffee mug or cup

2. If the user photographed a completely different object, set detected to false.

3. If the photo is too dark, covered, unclear, or blurry to confidently identify, set detected to false.

You MUST respond ONLY with valid JSON in exactly this format:

{
  "detected": true,
  "confidence": 0.95,
  "reason": "A book is clearly visible in the photo."
}`;


      const geminiPayload = {

        contents: [

          {
            role:
              'user',

            parts: [

              {
                text:
                  prompt
              },

              {
                inlineData: {

                  mimeType:
                    'image/jpeg',

                  data:
                    cleanBase64
                }
              }
            ]
          }
        ],

        generationConfig: {

          responseMimeType:
            'application/json',

          temperature:
            0.1
        }
      };


      /**
       * Call Gemini Vision.
       *
       * 503 / timeout automatically tries fallback models.
       */
      const {
        status,
        data
      } =
        await callGeminiApi(
          geminiPayload
        );


      /**
       * Gemini returned an error after all fallbacks.
       */
      if (
        status !== 200
      ) {

        console.error(
          `[Vision Verification] Gemini Vision API returned error status: ${status}`
        );


        return res
          .status(502)
          .json({

            status:
              'API_ERROR',

            detected:
              false,

            confidence:
              0.0,

            reason:
              'Gemini Vision AI is temporarily unavailable. Please try again.'
          });
      }


      const candidate =
        data?.candidates?.[0];


      const outputJsonText =
        candidate?.content?.parts?.[0]?.text;


      if (
        !outputJsonText
      ) {

        return res
          .status(502)
          .json({

            status:
              'API_ERROR',

            detected:
              false,

            confidence:
              0.0,

            reason:
              'Unable to parse AI verification output.'
          });
      }


      /**
       * Parse AI JSON.
       */
      let parsedResult;


      try {

        parsedResult =
          JSON.parse(
            outputJsonText
          );

      } catch (
        parseErr
      ) {

        console.error(
          '[Vision Verification] Failed to parse JSON from model output:',
          outputJsonText
        );


        return res
          .status(502)
          .json({

            status:
              'API_ERROR',

            detected:
              false,

            confidence:
              0.0,

            reason:
              'Invalid response format from AI Vision model.'
          });
      }


      const detected =
        Boolean(
          parsedResult.detected
        );


      const confidence =
        typeof parsedResult.confidence === 'number'
          ? parsedResult.confidence
          : (
              detected
                ? 0.9
                : 0.0
            );


      const reason =
        typeof parsedResult.reason === 'string'
          ? parsedResult.reason
          : '';


      /**
       * VERIFIED
       */
      if (
        detected &&
        confidence >=
          OBJECT_CONFIDENCE_THRESHOLD
      ) {

        return res
          .status(200)
          .json({

            status:
              'VERIFIED',

            detected:
              true,

            confidence:
              parseFloat(
                confidence.toFixed(2)
              ),

            reason:
              reason ||
              `${cleanTarget} verified! Proof-of-wake confirmed.`
          });
      }


      /**
       * FAIL CLOSED.
       *
       * Confidence below threshold means not verified.
       */
      const explanation =
        !detected

          ? (
              reason ||
              `Could not detect "${cleanTarget}" in this photo. Please point camera directly at your ${cleanTarget}.`
            )

          : (
              `Confidence (${Math.round(confidence * 100)}%) is below the required ${Math.round(OBJECT_CONFIDENCE_THRESHOLD * 100)}% threshold.`
            );


      return res
        .status(200)
        .json({

          status:
            'WRONG_OBJECT',

          detected:
            false,

          confidence:
            parseFloat(
              confidence.toFixed(2)
            ),

          reason:
            explanation
        });


    } catch (
      err
    ) {

      /**
       * API key missing.
       */
      if (
        err.code === 'NO_API_KEY'
      ) {

        console.warn(
          '[Vision Verification] Gemini API key not configured on server.'
        );


        return res
          .status(503)
          .json({

            status:
              'NO_API_CONFIGURATION',

            detected:
              false,

            confidence:
              0.0,

            reason:
              'Gemini API key is not configured on the backend server.'
          });
      }


      /**
       * All fallback models timed out.
       */
      if (
        err.code === 'TIMEOUT'
      ) {

        console.error(
          '[Vision Verification] Gemini API timed out.'
        );


        return res
          .status(504)
          .json({

            status:
              'NETWORK_ERROR',

            detected:
              false,

            confidence:
              0.0,

            reason:
              'Vision analysis request timed out. Please try capturing again.'
          });
      }


      console.error(
        '[Vision Verification] Unexpected error:',
        err.message
      );


      return res
        .status(500)
        .json({

          status:
            'API_ERROR',

          detected:
            false,

          confidence:
            0.0,

          reason:
            'Internal vision verification error.'
        });
    }
  }
);


/* ============================================================
   ERROR HANDLING MIDDLEWARE
============================================================ */

app.use(
  (
    err,
    req,
    res,
    next
  ) => {

    /**
     * Payload too large.
     */
    if (
      err.type ===
      'entity.too.large'
    ) {

      return res
        .status(413)
        .json({

          success:
            false,

          status:
            'LOW_IMAGE_QUALITY',

          error:
            'Payload Too Large: The captured image exceeds the maximum permitted size of 10MB.',

          reason:
            'Photo is too large to process. Please try taking the photo again.'
        });
    }


    /**
     * Invalid JSON.
     */
    if (
      err instanceof SyntaxError &&
      err.status === 400 &&
      'body' in err
    ) {

      return res
        .status(400)
        .json({

          success:
            false,

          error:
            'Malformed JSON in request body.',

          status:
            'API_ERROR'
        });
    }


    console.error(
      '[Unhandled Middleware Error]',
      err.message
    );


    res
      .status(500)
      .json({

        success:
          false,

        error:
          'Internal server error occurred.',

        status:
          'API_ERROR'
      });
  }
);


/* ============================================================
   SERVER START
============================================================ */

let serverInstance =
  null;


if (
  require.main === module
) {

  const isKeyConfigured =
    Boolean(

      process.env.GEMINI_API_KEY &&

      process.env.GEMINI_API_KEY.trim() !== '' &&

      process.env.GEMINI_API_KEY !==
        'MY_GEMINI_API_KEY' &&

      process.env.GEMINI_API_KEY !==
        'YOUR_GEMINI_API_KEY_HERE'
    );


  serverInstance =
    app.listen(

      PORT,

      '0.0.0.0',

      () => {

        console.log(
          '[RIVEL Server] =========================================='
        );

        console.log(
          '[RIVEL Server] Secure Gemini Proxy Backend Started'
        );

        console.log(
          `[RIVEL Server] Binding: 0.0.0.0:${PORT}`
        );

        console.log(
          `[RIVEL Server] Environment: ${process.env.NODE_ENV || 'production'}`
        );

        console.log(
          `[RIVEL Server] Primary Model: ${CONFIGURED_MODEL}`
        );

        console.log(
          `[RIVEL Server] Fallback Models: ${KNOWN_SUPPORTED_MODELS.filter(
            model => model !== CONFIGURED_MODEL
          ).join(', ')}`
        );

        console.log(
          `[RIVEL Server] API Key Configured: ${isKeyConfigured ? 'YES (Active)' : 'NO (Missing/Placeholder)'}`
        );

        console.log(
          `[RIVEL Server] Confidence Threshold: ${OBJECT_CONFIDENCE_THRESHOLD * 100}%`
        );

        console.log(
          '[RIVEL Server] Trust Proxy: Enabled (hop count: 1 for Render reverse proxy)'
        );

        console.log(
          '[RIVEL Server] =========================================='
        );
      }
    );


  /* ==========================================================
     GRACEFUL SHUTDOWN
  ========================================================== */

  const handleShutdown =
    signal => {

      console.log(
        `[RIVEL Server] Received ${signal}. Starting graceful shutdown...`
      );


      if (
        serverInstance
      ) {

        serverInstance.close(
          () => {

            console.log(
              '[RIVEL Server] HTTP server closed cleanly.'
            );

            process.exit(0);
          }
        );


        /**
         * Force exit after 10 seconds.
         */
        setTimeout(
          () => {

            console.error(
              '[RIVEL Server] Forcefully terminating after timeout.'
            );

            process.exit(1);

          },
          10000
        );

      } else {

        process.exit(0);
      }
    };


  process.on(
    'SIGTERM',

    () =>
      handleShutdown(
        'SIGTERM'
      )
  );


  process.on(
    'SIGINT',

    () =>
      handleShutdown(
        'SIGINT'
      )
  );
}


/* ============================================================
   EXPORTS FOR TESTING
============================================================ */

module.exports = {

  app,

  setMockGeminiHandler
};
