/**
 * SC-001 — Item Master API Load Test
 *
 * Scenario: Ramp to 100 VUs over 30 s, sustain for 60 s.
 * Each VU: POST /api/v1/item-master (create with unique partNumber) then
 *          GET  /api/v1/item-master/{id} (retrieve by ID).
 *
 * Thresholds (per T171):
 *   http_req_duration p(95) < 500 ms
 *   http_req_failed   < 1%
 *
 * Usage:
 *   BASE_URL=http://localhost:8082 \
 *   ENGINEER_TOKEN=<jwt> \
 *   k6 run item-master-load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8082';
const token   = __ENV.ENGINEER_TOKEN || '';

export const options = {
  stages: [
    { duration: '30s', target: 100 },
    { duration: '60s', target: 100 },
    { duration: '10s', target: 0  },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<500'],
    'http_req_failed':   ['rate<0.01'],
  },
};

const createErrors = new Counter('create_errors');
const getErrors    = new Counter('get_errors');

export default function () {
  const headers = {
    'Content-Type':  'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // Unique part number per VU iteration to avoid 409 conflicts
  const partNumber = `LOAD-${__VU}-${__ITER}`;

  const createPayload = JSON.stringify({
    partNumber:         partNumber,
    revision:           'A',
    description:        `Load test item ${partNumber}`,
    unitOfMeasure:      'EA',
    classification:     'PURCHASED_PART',
    makeBuyCode:        'BUY',
    traceabilityMethod: 'NONE',
  });

  const createRes = http.post(
    `${baseUrl}/api/v1/item-master`,
    createPayload,
    { headers },
  );

  const createOk = check(createRes, {
    'create status is 201': (r) => r.status === 201,
  });

  if (!createOk) {
    createErrors.add(1);
    return;
  }

  const itemId = createRes.json('id');

  const getRes = http.get(
    `${baseUrl}/api/v1/item-master/${itemId}`,
    { headers },
  );

  const getOk = check(getRes, {
    'get status is 200':        (r) => r.status === 200,
    'get returns correct id':   (r) => r.json('id') === itemId,
    'get partNumber matches':    (r) => r.json('partNumber') === partNumber,
  });

  if (!getOk) {
    getErrors.add(1);
  }

  sleep(0.1);
}
