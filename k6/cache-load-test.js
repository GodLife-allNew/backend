/**
 * Redis 캐시 전/후 성능 비교 부하테스트
 *
 * [캐시 OFF] Redis FLUSHALL 후 실행:
 *   k6 run k6/cache-load-test.js
 *
 * [캐시 ON] 서버 정상 기동(워밍업 완료) 후 실행:
 *   k6 run k6/cache-load-test.js
 *
 * BASE_URL 환경변수로 대상 서버 변경 가능:
 *   k6 run -e BASE_URL=http://192.168.0.1:8080 k6/cache-load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// ─── 커스텀 메트릭 ────────────────────────────────────────────────────────────
const topMenuDuration  = new Trend('topMenu_duration',  true);
const jobDuration      = new Trend('job_duration',      true);
const targetDuration   = new Trend('target_duration',   true);
const challengeDuration = new Trend('challenge_duration', true);
const iconDuration     = new Trend('icon_duration',     true);
const errorCount       = new Counter('error_count');
// ──────────────────────────────────────────────────────────────────────────────

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9090';

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  stages: [
    { duration: '10s', target: 50 },  // ramp-up: 0 → 50 VU
    { duration: '30s', target: 50 },  // sustained: 50 VU 유지
    { duration: '10s', target: 0  },  // ramp-down: 50 → 0 VU
  ],
  thresholds: {
    // 전체 HTTP 요청의 95%가 500ms 이하여야 테스트 통과
    http_req_duration: ['p(95)<500'],
    // 에러율 1% 미만
    error_count: ['count<10'],
  },
};

// 각 엔드포인트 정의
const ENDPOINTS = [
  { name: 'topMenu',   url: `${BASE_URL}/api/categories/topMenu`,   metric: topMenuDuration   },
  { name: 'job',       url: `${BASE_URL}/api/categories/job`,        metric: jobDuration       },
  { name: 'target',    url: `${BASE_URL}/api/categories/target`,     metric: targetDuration    },
  { name: 'challenge', url: `${BASE_URL}/api/categories/challenge`,  metric: challengeDuration },
  { name: 'icon',      url: `${BASE_URL}/api/categories/icon`,       metric: iconDuration      },
];

export default function () {
  for (const ep of ENDPOINTS) {
    const res = http.get(ep.url, { tags: { endpoint: ep.name } });

    // 응답시간 기록
    ep.metric.add(res.timings.duration);

    // 상태 코드 검증
    const ok = check(res, {
      [`${ep.name} status 200`]: (r) => r.status === 200,
    });

    if (!ok) {
      errorCount.add(1);
      console.error(`[ERROR] ${ep.name} - status: ${res.status}, body: ${res.body}`);
    }
  }

  sleep(0.5); // VU당 요청 간격 (초)
}

/**
 * 테스트 종료 후 결과 요약 출력
 */
export function handleSummary(data) {
  const metrics = data.metrics;

  const fmt = (metric) => {
    if (!metric) return 'N/A';
    const v = metric.values;
    return `avg=${Math.round(v.avg)}ms  p90=${Math.round(v['p(90)'])}ms  p95=${Math.round(v['p(95)'])}ms  p99=${Math.round(v['p(99)'])}ms`;
  };

  const lines = [
    '',
    '═══════════════════════════════════════════════════════════',
    '                 캐시 부하테스트 결과 요약                 ',
    '═══════════════════════════════════════════════════════════',
    `  전체 요청 수   : ${metrics.http_reqs?.values?.count ?? 'N/A'}`,
    `  전체 에러 수   : ${metrics.error_count?.values?.count ?? 0}`,
    `  TPS (req/s)   : ${Math.round(metrics.http_reqs?.values?.rate ?? 0)}`,
    '───────────────────────────────────────────────────────────',
    `  topMenu       : ${fmt(metrics.topMenu_duration)}`,
    `  job           : ${fmt(metrics.job_duration)}`,
    `  target        : ${fmt(metrics.target_duration)}`,
    `  challenge     : ${fmt(metrics.challenge_duration)}`,
    `  icon          : ${fmt(metrics.icon_duration)}`,
    '═══════════════════════════════════════════════════════════',
    '',
  ];

  console.log(lines.join('\n'));

  // JSON 결과 파일로도 저장
  return {
    'k6/result.json': JSON.stringify(data, null, 2),
  };
}
