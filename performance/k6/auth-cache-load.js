import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 로그인·워밍업을 제외한 실제 측정 요청 전용 지표
const authPingDuration = new Trend('auth_ping_duration', true);
const authPingRequests = new Counter('auth_ping_requests');
const authPingFailures = new Rate('auth_ping_failures');

function envNumber(name, defaultValue, minValue = 1) {
  const raw = __ENV[name];

  if (raw === undefined || raw === '') {
    return defaultValue;
  }

  const value = Number(raw);

  if (!Number.isFinite(value) || value < minValue) {
    fail(`${name} must be a number >= ${minValue}`);
  }

  return value;
}

function envString(name, defaultValue) {
  const raw = __ENV[name];
  return raw === undefined || raw === '' ? defaultValue : raw;
}

function joinUrl(baseUrl, path) {
  return `${baseUrl.replace(/\/+$/, '')}${path}`;
}

const RATE = envNumber('RATE', 100);
const DURATION = envString('DURATION', '2m');
const PRE_ALLOCATED_VUS = envNumber('PRE_ALLOCATED_VUS', 50);
const MAX_VUS = envNumber('MAX_VUS', 300);

export const options = {
  summaryTrendStats: [
    'avg',
    'med',
    'p(95)',
    'p(99)',
    'max',
  ],

  scenarios: {
    auth_load: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },

  thresholds: {
    auth_ping_failures: [
      'rate<0.01',
    ],
    auth_ping_duration: [
      'p(95)<200',
      'p(99)<500',
    ],
    auth_ping_requests: [
      'count>0',
    ],
    checks: [
      'rate>0.99',
    ],
    dropped_iterations: [
      'count==0',
    ],
  },
};

export function setup() {
  const baseUrl = envString('BASE_URL', 'http://localhost:8080');
  let accessToken = __ENV.ACCESS_TOKEN;

  // Access Token이 없으면 테스트 계정으로 로그인
  if (!accessToken) {
    const email = __ENV.EMAIL;
    const password = __ENV.PASSWORD;

    if (!email || !password) {
      fail('Set ACCESS_TOKEN or both EMAIL and PASSWORD');
    }

    const loginResponse = http.post(
      joinUrl(baseUrl, '/api/auth/login'),
      JSON.stringify({
        email,
        password,
        fcmToken: null,
      }),
      {
        headers: {
          'Content-Type': 'application/json',
        },
        tags: {
          endpoint: 'login',
        },
      },
    );

    if (loginResponse.status !== 200) {
      fail(
        `Login failed. status=${loginResponse.status} body=${loginResponse.body}`,
      );
    }

    let body;

    try {
      body = loginResponse.json();
    } catch (error) {
      fail(`Login response is not JSON: ${error.message}`);
    }

    accessToken = body.accessToken;

    if (!accessToken) {
      fail('Login response did not contain accessToken');
    }
  }

  // JVM·Security Filter 워밍업
  // perf-cache에서는 이 과정에서 memberAuthCache 생성
  const warmupRequests = envNumber('WARMUP_REQUESTS', 20, 0);

  const authHeaders = {
    Authorization: `Bearer ${accessToken}`,
  };

  for (let i = 0; i < warmupRequests; i += 1) {
    const warmupResponse = http.get(
      joinUrl(baseUrl, '/api/perf/ping'),
      {
        headers: authHeaders,
        tags: {
          endpoint: 'warmup_auth_ping',
        },
      },
    );

    if (warmupResponse.status !== 200) {
      fail(
        `Warmup failed. status=${warmupResponse.status} body=${warmupResponse.body}`,
      );
    }
  }

  return {
    baseUrl,
    accessToken,
  };
}

export default function (data) {
  const response = http.get(
    joinUrl(data.baseUrl, '/api/perf/ping'),
    {
      headers: {
        Authorization: `Bearer ${data.accessToken}`,
      },
      tags: {
        endpoint: 'auth_ping',
      },
    },
  );

  const statusOk = response.status === 200;

  let bodyIsPong = false;

  try {
    bodyIsPong = response.json('message') === 'pong';
  } catch (error) {
    bodyIsPong = false;
  }

  const success = statusOk && bodyIsPong;

  // 실제 본 측정 요청만 사용자 정의 지표에 기록
  authPingDuration.add(response.timings.duration);
  authPingRequests.add(1);
  authPingFailures.add(!success);

  check(response, {
    'auth ping status is 200': () => statusOk,
    'auth ping body is pong': () => bodyIsPong,
  });
}

function metric(data, name, field, defaultValue = 0) {
  return data.metrics[name]?.values?.[field] ?? defaultValue;
}

function thresholdsPassed(data) {
  return Object.values(data.metrics)
    .flatMap((metricData) =>
      Object.values(metricData.thresholds || {}),
    )
    .every((threshold) => threshold.ok !== false);
}

function fixed(value, digits = 2) {
  const number = Number(value);

  if (!Number.isFinite(number)) {
    return '0.00';
  }

  return number.toFixed(digits);
}

export function handleSummary(data) {
  const label = envString(
    'RESULT_LABEL',
    `auth-cache-${Date.now()}`,
  );

  const result = {
    label,

    profile: envString(
      'PROFILE',
      envString('SPRING_PROFILE', ''),
    ),

    settings: {
      rate: RATE,
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
      warmupRequests: envNumber(
        'WARMUP_REQUESTS',
        20,
        0,
      ),
    },

    metrics: {
      avgMs: metric(
        data,
        'auth_ping_duration',
        'avg',
      ),

      medMs: metric(
        data,
        'auth_ping_duration',
        'med',
      ),

      p95Ms: metric(
        data,
        'auth_ping_duration',
        'p(95)',
      ),

      p99Ms: metric(
        data,
        'auth_ping_duration',
        'p(99)',
      ),

      maxMs: metric(
        data,
        'auth_ping_duration',
        'max',
      ),

      requests: metric(
        data,
        'auth_ping_requests',
        'count',
      ),

      rps: metric(
        data,
        'auth_ping_requests',
        'rate',
      ),

      errorRate: metric(
        data,
        'auth_ping_failures',
        'rate',
      ),

      checkRate: metric(
        data,
        'checks',
        'rate',
      ),

      droppedIterations: metric(
        data,
        'dropped_iterations',
        'count',
      ),

      thresholdsPassed: thresholdsPassed(data),
    },
  };

  const summary = [
    '',
    `Auth cache load result: ${label}`,
    `profile=${result.profile || '(not set)'} rate=${RATE}/s duration=${DURATION}`,
    `requests=${result.metrics.requests} rps=${fixed(result.metrics.rps)} dropped=${result.metrics.droppedIterations}`,
    `avg=${fixed(result.metrics.avgMs)}ms med=${fixed(result.metrics.medMs)}ms p95=${fixed(result.metrics.p95Ms)}ms p99=${fixed(result.metrics.p99Ms)}ms max=${fixed(result.metrics.maxMs)}ms`,
    `errorRate=${fixed(result.metrics.errorRate * 100)}% checkRate=${fixed(result.metrics.checkRate * 100)}% thresholds=${result.metrics.thresholdsPassed ? 'PASS' : 'FAIL'}`,
    '',
].join('\n');

return {
    stdout: summary,

    [`performance/k6/results/${label}.json`]:
        JSON.stringify(result, null, 2),
};
}
