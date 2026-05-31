/**
 * SC-002 — BOM Explosion Load Test
 *
 * Precondition: a fixture script must seed a 10-level BOM with 50 total components
 * via the API and export the BOM ID as BOM_ID env var before running this test.
 *
 * Scenario: 20 concurrent VUs each requesting
 *   GET /api/v1/boms/{bomId}/explosion?format=indented
 * for the pre-seeded BOM for 90 s (no ramp — concurrency is held constant).
 *
 * Thresholds (per T172):
 *   http_req_duration p(95) < 2000 ms
 *   http_req_failed   < 1%
 *
 * Usage:
 *   BASE_URL=http://localhost:8082 \
 *   ENGINEER_TOKEN=<jwt> \
 *   BOM_ID=<uuid-of-seeded-bom> \
 *   k6 run bom-explosion-load-test.js
 *
 * To seed the BOM first:
 *   node specs/008-item-master-bom-management/performance/seed-bom-fixture.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8082';
const token   = __ENV.ENGINEER_TOKEN || '';
const bomId   = __ENV.BOM_ID || '';

export const options = {
  vus:      20,
  duration: '90s',
  thresholds: {
    'http_req_duration': ['p(95)<2000'],
    'http_req_failed':   ['rate<0.01'],
  },
};

export function setup() {
  if (!bomId) {
    throw new Error('BOM_ID env var is required. Run the seed fixture first.');
  }
}

export default function () {
  const headers = {
    'Authorization': `Bearer ${token}`,
  };

  const res = http.get(
    `${baseUrl}/api/v1/boms/${bomId}/explosion?format=indented`,
    { headers },
  );

  check(res, {
    'explosion status is 200':          (r) => r.status === 200,
    'response contains nodes':          (r) => {
      try {
        const body = r.json();
        return Array.isArray(body) && body.length > 0;
      } catch (_) {
        return false;
      }
    },
  });

  sleep(0.05);
}
