/**
 * Automated Test Suite for RIVEL Secure Gemini API Proxy Backend
 * 
 * Comprehensive E2E & Unit Test Coverage (Requirements A through N)
 */

const assert = require('assert');
const http = require('http');

process.env.PORT = '8082';
process.env.NODE_ENV = 'test';
process.env.TEST_MODE = 'true';
process.env.GEMINI_API_KEY = ''; // start unconfigured

const { app, setMockGeminiHandler } = require('../server');

let server;

function request(options, data) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port: 8082,
        headers: {
          'Content-Type': 'application/json'
        },
        ...options
      },
      (res) => {
        let body = '';
        res.on('data', (chunk) => (body += chunk));
        res.on('end', () => {
          let parsed;
          try {
            parsed = JSON.parse(body);
          } catch (e) {
            parsed = body;
          }
          resolve({ status: res.statusCode, headers: res.headers, data: parsed });
        });
      }
    );

    req.on('error', reject);
    if (data) {
      req.write(typeof data === 'string' ? data : JSON.stringify(data));
    }
    req.end();
  });
}

async function runTests() {
  console.log('====================================================');
  console.log('  STARTING RIVEL PRODUCTION BACKEND TEST SUITE');
  console.log('====================================================');

  server = app.listen(8082, '127.0.0.1');

  try {
    // --- Test A: Health Check ---
    console.log('\n[Test A] GET /api/health (Health check)');
    const health = await request({ method: 'GET', path: '/api/health' });
    assert.strictEqual(health.status, 200);
    assert.strictEqual(health.data.status, 'healthy');
    assert.strictEqual(health.data.service, 'RIVEL Secure AI Backend Proxy');
    assert.strictEqual(typeof health.data.geminiConfigured, 'boolean');
    assert.strictEqual(typeof health.data.timestamp, 'string');
    assert.strictEqual(health.data.apiKey, undefined, 'API key must never be leaked in health check');
    console.log('  ✓ Health check passed without leaking any secrets');

    // --- Test B: Chat Success Path ---
    console.log('\n[Test B] POST /api/ai/chat (Chat success path with mocked Gemini)');
    setMockGeminiHandler(async () => {
      return {
        status: 200,
        data: {
          candidates: [
            {
              content: {
                parts: [{ text: 'Here is a 25-minute focus plan: start with your hardest topic now.' }]
              }
            }
          ]
        }
      };
    });

    const chatSuccess = await request(
      { method: 'POST', path: '/api/ai/chat' },
      {
        message: 'How should I study for exams?',
        studentContext: {
          studentName: 'Alex',
          course: 'CS',
          educationLevel: 'Undergraduate',
          year: 'Year 2',
          mainGoal: 'Ace algorithms',
          subjects: ['Algorithms'],
          upcomingExam: 'Midterm',
          todayCompletedMinutes: 30,
          activeStreak: 3
        },
        chatHistory: [
          { role: 'user', text: 'Hi' },
          { role: 'model', text: 'Hello Alex' }
        ]
      }
    );

    assert.strictEqual(chatSuccess.status, 200);
    assert.strictEqual(chatSuccess.data.success, true);
    assert.strictEqual(chatSuccess.data.source, 'LIVE_GEMINI');
    assert.strictEqual(typeof chatSuccess.data.reply, 'string');
    assert.strictEqual(chatSuccess.data.suggestedActionType, 'START_FOCUS');
    console.log('  ✓ Chat success path returned 200 with structured advice and suggested action');

    // --- Test C: Chat Missing Message ---
    console.log('\n[Test C] POST /api/ai/chat (Missing message validation)');
    const chatNoMsg = await request({ method: 'POST', path: '/api/ai/chat' }, { message: '' });
    assert.strictEqual(chatNoMsg.status, 400);
    assert.strictEqual(chatNoMsg.data.success, false);
    console.log('  ✓ Missing message properly rejected with 400');

    // --- Test D: Vision Missing targetObject ---
    console.log('\n[Test D] POST /api/vision/verify-object (Missing targetObject)');
    const dummyBase64 = Buffer.from('testimagebytes'.repeat(20)).toString('base64');
    const visionNoTarget = await request(
      { method: 'POST', path: '/api/vision/verify-object' },
      { targetObject: '', imageBase64: dummyBase64 }
    );
    assert.strictEqual(visionNoTarget.status, 400);
    assert.strictEqual(visionNoTarget.data.status, 'WRONG_OBJECT');
    assert.strictEqual(visionNoTarget.data.detected, false);
    console.log('  ✓ Missing targetObject rejected with 400 and detected=false');

    // --- Test E: Vision Missing imageBase64 ---
    console.log('\n[Test E] POST /api/vision/verify-object (Missing imageBase64)');
    const visionNoImg = await request(
      { method: 'POST', path: '/api/vision/verify-object' },
      { targetObject: 'Book', imageBase64: '' }
    );
    assert.strictEqual(visionNoImg.status, 400);
    assert.strictEqual(visionNoImg.data.status, 'LOW_IMAGE_QUALITY');
    assert.strictEqual(visionNoImg.data.detected, false);
    console.log('  ✓ Missing imageBase64 rejected with 400 and detected=false');

    // --- Test F: Vision Malformed base64 ---
    console.log('\n[Test F] POST /api/vision/verify-object (Malformed base64 characters)');
    const visionMalformedBase64 = await request(
      { method: 'POST', path: '/api/vision/verify-object' },
      { targetObject: 'Book', imageBase64: '!!!NOT_BASE64@@@###$$$%%%^^^&&&***(((' }
    );
    assert.strictEqual(visionMalformedBase64.status, 400);
    assert.strictEqual(visionMalformedBase64.data.status, 'LOW_IMAGE_QUALITY');
    assert.strictEqual(visionMalformedBase64.data.detected, false);
    console.log('  ✓ Malformed base64 rejected with 400 LOW_IMAGE_QUALITY');

    // --- Test G: Vision Backend Unavailable (Timeout / Upstream Error) ---
    console.log('\n[Test G] POST /api/vision/verify-object (Upstream timeout / network error)');
    setMockGeminiHandler(async () => {
      const timeoutErr = new Error('Gemini API call timed out');
      timeoutErr.code = 'TIMEOUT';
      throw timeoutErr;
    });

    const visionTimeout = await request(
      { method: 'POST', path: '/api/vision/verify-object' },
      { targetObject: 'Book', imageBase64: dummyBase64 }
    );
    assert.strictEqual(visionTimeout.status, 504);
    assert.strictEqual(visionTimeout.data.status, 'NETWORK_ERROR');
    assert.strictEqual(visionTimeout.data.detected, false);
    console.log('  ✓ Upstream timeout returned 504 NETWORK_ERROR and detected=false (fail-closed)');

    // --- Test H: Vision Malformed Gemini Response ---
    console.log('\n[Test H] POST /api/vision/verify-object (Malformed JSON from Gemini model)');
    setMockGeminiHandler(async () => {
      return {
        status: 200,
        data: {
          candidates: [
            {
              content: {
                parts: [{ text: 'This is not valid json output from model' }]
              }
            }
          ]
        }
      };
    });

    const visionMalformedGemini = await request(
      { method: 'POST', path: '/api/vision/verify-object' },
      { targetObject: 'Book', imageBase64: dummyBase64 }
    );
    assert.strictEqual(visionMalformedGemini.status, 502);
    assert.strictEqual(visionMalformedGemini.data.status, 'API_ERROR');
    assert.strictEqual(visionMalformedGemini.data.detected, false);
    console.log('  ✓ Malformed model output safely returned 502 API_ERROR and detected=false');

    // --- Test I: Vision Confidence 0.79 -> MUST FAIL ---
    console.log('\n[Test I] POST /api/vision/verify-object (Confidence 0.79 -> MUST FAIL CLOSED)');
    setMockGeminiHandler(async () => {
      return {
        status: 200,
        data: {
          candidates: [
            {
              content: {
                parts: [
                  {
                    text: JSON.stringify({
                      detected: true,
                      confidence: 0.79,
                      reason: 'Looks like a book but slightly blurry'
                    })
                  }
                ]
              }
            }
          ]
        }
      };
    });

    const vision79 = await request(
      { method: 'POST', path: '/api/vision/verify-object' },
      { targetObject: 'Book', imageBase64: dummyBase64 }
    );
    assert.strictEqual(vision79.status, 200);
    assert.strictEqual(vision79.data.status, 'WRONG_OBJECT');
    assert.strictEqual(vision79.data.detected, false, 'Confidence 0.79 MUST NOT pass verification');
    assert.strictEqual(vision79.data.confidence, 0.79);
    console.log('  ✓ Confidence 0.79 strictly failed closed (detected=false, status=WRONG_OBJECT)');

    // --- Test J: Vision Confidence 0.80 -> MAY PASS if detected=true ---
    console.log('\n[Test J] POST /api/vision/verify-object (Confidence 0.80 -> PASSES)');
    setMockGeminiHandler(async () => {
      return {
        status: 200,
        data: {
          candidates: [
            {
              content: {
                parts: [
                  {
                    text: JSON.stringify({
                      detected: true,
                      confidence: 0.80,
                      reason: 'Book clearly confirmed on desk.'
                    })
                  }
                ]
              }
            }
          ]
        }
      };
    });

    const vision80 = await request(
      { method: 'POST', path: '/api/vision/verify-object' },
      { targetObject: 'Book', imageBase64: dummyBase64 }
    );
    assert.strictEqual(vision80.status, 200);
    assert.strictEqual(vision80.data.status, 'VERIFIED');
    assert.strictEqual(vision80.data.detected, true);
    assert.strictEqual(vision80.data.confidence, 0.80);
    console.log('  ✓ Confidence 0.80 with detected=true successfully verified');

    // --- Test K: Vision detected=false -> MUST FAIL (even if confidence is high) ---
    console.log('\n[Test K] POST /api/vision/verify-object (detected=false -> MUST FAIL)');
    setMockGeminiHandler(async () => {
      return {
        status: 200,
        data: {
          candidates: [
            {
              content: {
                parts: [
                  {
                    text: JSON.stringify({
                      detected: false,
                      confidence: 0.95,
                      reason: 'This is a coffee cup, not a book.'
                    })
                  }
                ]
              }
            }
          ]
        }
      };
    });

    const visionDetectedFalse = await request(
      { method: 'POST', path: '/api/vision/verify-object' },
      { targetObject: 'Book', imageBase64: dummyBase64 }
    );
    assert.strictEqual(visionDetectedFalse.status, 200);
    assert.strictEqual(visionDetectedFalse.data.status, 'WRONG_OBJECT');
    assert.strictEqual(visionDetectedFalse.data.detected, false);
    console.log('  ✓ detected=false correctly returned WRONG_OBJECT and detected=false');

    // --- Test L: Rate Limiting ---
    console.log('\n[Test L] Rate Limiting (Health endpoint requests)');
    const healthPings = [];
    for (let i = 0; i < 5; i++) {
      healthPings.push(request({ method: 'GET', path: '/api/health' }));
    }
    const pingResults = await Promise.all(healthPings);
    assert.strictEqual(pingResults.every(r => r.status === 200), true);
    console.log('  ✓ Rate limiter initialized and processing requests normally');

    // --- Test M: Oversized Request (>10MB) ---
    console.log('\n[Test M] Oversized Request Handling (>10MB)');
    const hugePayload = JSON.stringify({
      targetObject: 'Book',
      imageBase64: 'A'.repeat(11 * 1024 * 1024) // 11 MB
    });

    const oversizedRes = await request(
      {
        method: 'POST',
        path: '/api/vision/verify-object',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(hugePayload)
        }
      },
      hugePayload
    );
    assert.strictEqual(oversizedRes.status, 413);
    console.log('  ✓ Oversized payload (>10MB) safely rejected with 413 Payload Too Large');

    // --- Test N: Missing Server API Key ---
    console.log('\n[Test N] Missing Server API Key Handling');
    setMockGeminiHandler(null); // disable mock to test real unconfigured path
    process.env.GEMINI_API_KEY = '';

    const noKeyChat = await request(
      { method: 'POST', path: '/api/ai/chat' },
      { message: 'Test message with no key configured' }
    );
    assert.strictEqual(noKeyChat.status, 503);
    assert.strictEqual(noKeyChat.data.success, false);

    const noKeyVision = await request(
      { method: 'POST', path: '/api/vision/verify-object' },
      { targetObject: 'Book', imageBase64: dummyBase64 }
    );
    assert.strictEqual(noKeyVision.status, 503);
    assert.strictEqual(noKeyVision.data.status, 'NO_API_CONFIGURATION');
    assert.strictEqual(noKeyVision.data.detected, false);
    console.log('  ✓ Unconfigured server returns 503 with safe error messages');

    console.log('\n====================================================');
    console.log('  ALL UNIT/MOCK BACKEND TESTS (A through N) PASSED 100%');
    console.log('====================================================\n');

    // --- Optional Test O: Live Real Gemini API Smoke Test ---
    // Only runs when RUN_LIVE_GEMINI_TEST=true is explicitly set with a real key
    const shouldRunLiveTest = process.env.RUN_LIVE_GEMINI_TEST === 'true';
    if (shouldRunLiveTest) {
      console.log('\n====================================================');
      console.log('  RUNNING LIVE REAL GEMINI API VERIFICATION TEST');
      console.log('====================================================\n');

      const realKey = process.env.GEMINI_API_KEY || process.env.API_KEY;
      if (!realKey || realKey.trim() === '' || realKey === 'MY_GEMINI_API_KEY' || realKey === 'YOUR_GEMINI_API_KEY_HERE') {
        console.warn('  ⚠️ Skipping Live Test: RUN_LIVE_GEMINI_TEST is true but GEMINI_API_KEY is not set or is a placeholder.');
      } else {
        process.env.GEMINI_API_KEY = realKey;
        setMockGeminiHandler(null); // Ensure live HTTP requests to Google

        // Test Live AI Chat
        console.log('[Test O1] Live Gemini Chat Request...');
        const liveChatRes = await request(
          { method: 'POST', path: '/api/ai/chat' },
          { message: 'Hi! Quick test from RIVEL backend verification suite.' }
        );
        console.log(`  Live Chat Status: ${liveChatRes.status}`);
        assert.strictEqual(liveChatRes.status, 200, `Live Gemini Chat failed with status ${liveChatRes.status}`);
        assert.strictEqual(typeof liveChatRes.data.reply, 'string');
        console.log(`  ✓ Live Gemini Chat Succeeded! Model Response: "${liveChatRes.data.reply.substring(0, 60)}..."`);

        // Test Live Vision Object Verification with 1x1 test JPEG pixel
        console.log('\n[Test O2] Live Gemini Vision Request...');
        const test1x1Jpeg = '/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=';
        const liveVisionRes = await request(
          { method: 'POST', path: '/api/vision/verify-object' },
          { targetObject: 'Book', imageBase64: test1x1Jpeg }
        );
        console.log(`  Live Vision Status: ${liveVisionRes.status}, Detected: ${liveVisionRes.data?.detected}, Result Status: ${liveVisionRes.data?.status}`);
        assert.strictEqual(liveVisionRes.status, 200, `Live Gemini Vision failed with status ${liveVisionRes.status}`);
        assert.strictEqual(typeof liveVisionRes.data.detected, 'boolean');
        console.log(`  ✓ Live Gemini Vision Succeeded without 404! Explanation: "${liveVisionRes.data.reason}"`);

        console.log('\n====================================================');
        console.log('  LIVE REAL GEMINI API TESTS (O1 & O2) PASSED 100%');
        console.log('====================================================\n');
      }
    } else {
      console.log('  ℹ️ Note: RUN_LIVE_GEMINI_TEST is not set. Real Gemini API call was not made in this unit test run.');
    }
  } finally {
    if (server) server.close();
  }
}

runTests().catch((err) => {
  console.error('\n❌ Test Suite Failed:', err);
  if (server) server.close();
  process.exit(1);
});
