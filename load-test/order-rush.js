// 선착순 주문 몰림 부하 테스트
//
// MODE=hot    : 인기 상품 1개(재고 100)에 500명이 동시에 주문 → 한 행에 락 경합 집중
// MODE=spread : 상품 10개(재고 각 10)에 500명이 나눠서 주문 → 락 경합 분산
//
// 실행: k6 run -e BASE_URL=http://localhost:8080 -e MODE=hot load-test/order-rush.js

import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = __ENV.MODE || 'hot';
const USERS = Number(__ENV.USERS || 500);
const VUS = Number(__ENV.VUS || 100);
const TOTAL_STOCK = 100;
const PRODUCT_COUNT = MODE === 'spread' ? 10 : 1;
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@eventcart.dev';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin1234!';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

const orderSuccess = new Counter('order_success');   // 201
const orderSoldOut = new Counter('order_sold_out');  // 409 OUT_OF_STOCK
const orderError = new Counter('order_error');       // 그 밖의 응답
const orderDuration = new Trend('order_duration', true);
const successDuration = new Trend('order_duration_success', true);   // 주문 성공한 요청만
const soldOutDuration = new Trend('order_duration_sold_out', true);  // 품절 안내 받은 요청만
// 주문 구간의 시작·끝 시각. setup(회원가입) 시간을 빼고 순수 주문 구간 TPS를 계산하는 데 쓴다.
const orderStartTs = new Trend('order_start_ts');
const orderEndTs = new Trend('order_end_ts');

export const options = {
    setupTimeout: '5m',
    scenarios: {
        rush: {
            executor: 'shared-iterations',
            vus: VUS,
            iterations: USERS,
            maxDuration: '3m',
        },
    },
    thresholds: {
        order_success: [`count==${TOTAL_STOCK}`], // 정확히 재고만큼만 팔려야 한다 (초과 판매 0)
        order_error: ['count==0'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

function post(path, body, token) {
    const headers = token ? { ...JSON_HEADERS, Authorization: `Bearer ${token}` } : JSON_HEADERS;
    return http.post(`${BASE_URL}${path}`, JSON.stringify(body), { headers, tags: { name: path } });
}

function login(email, password) {
    const res = post('/api/auth/login', { email, password });
    if (res.status !== 200) fail(`login failed ${email}: ${res.status} ${res.body}`);
    return res.json('accessToken');
}

export function setup() {
    // 1) 관리자: 이벤트 + 상품 등록
    const admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    const now = new Date();
    const fmt = (d) => d.toISOString().slice(0, 19);
    const event = post('/api/events', {
        name: `부하테스트-${MODE}-${now.getTime()}`,
        // 서버 시간대(UTC/KST)와 상관없이 진행 중이도록 앞뒤 하루씩
        startAt: fmt(new Date(now.getTime() - 86400_000)),
        endAt: fmt(new Date(now.getTime() + 86400_000)),
    }, admin).json();

    const eventProductIds = [];
    for (let i = 0; i < PRODUCT_COUNT; i++) {
        const product = post('/api/products', { name: `한정상품-${i}`, price: 10000 }, admin).json();
        const ep = post(`/api/events/${event.id}/products`, {
            productId: product.id,
            eventPrice: 5000,
            totalQuantity: TOTAL_STOCK / PRODUCT_COUNT,
            perMemberLimit: 1,
        }, admin);
        if (ep.status !== 201) fail(`event product failed: ${ep.status} ${ep.body}`);
        eventProductIds.push(ep.json('eventProductId'));
    }

    // 2) 회원 USERS명 가입 + 로그인 (50명씩 병렬)
    const tokens = [];
    const prefix = `${MODE}-${now.getTime()}`;
    for (let start = 0; start < USERS; start += 50) {
        const idx = [];
        for (let i = start; i < Math.min(start + 50, USERS); i++) idx.push(i);
        const signups = idx.map((i) => ['POST', `${BASE_URL}/api/members`,
            JSON.stringify({ email: `${prefix}-${i}@load.test`, name: `user${i}`, password: 'password123' }),
            { headers: JSON_HEADERS }]);
        http.batch(signups).forEach((r) => { if (r.status !== 201) fail(`signup failed: ${r.status} ${r.body}`); });
        const logins = idx.map((i) => ['POST', `${BASE_URL}/api/auth/login`,
            JSON.stringify({ email: `${prefix}-${i}@load.test`, password: 'password123' }),
            { headers: JSON_HEADERS }]);
        http.batch(logins).forEach((r) => tokens.push(r.json('accessToken')));
    }
    return { eventId: event.id, eventProductIds, tokens };
}

export default function (data) {
    const i = exec.scenario.iterationInTest;     // 0 ~ USERS-1, 회원 한 명당 한 번
    const eventProductId = data.eventProductIds[i % data.eventProductIds.length];

    orderStartTs.add(Date.now());
    const res = post('/api/orders', { eventProductId, quantity: 1 }, data.tokens[i]);
    orderEndTs.add(Date.now());
    orderDuration.add(res.timings.duration);

    if (res.status === 201) {
        orderSuccess.add(1);
        successDuration.add(res.timings.duration);
    } else if (res.status === 409 && res.json('code') === 'OUT_OF_STOCK') {
        orderSoldOut.add(1);
        soldOutDuration.add(res.timings.duration);
    }
    else orderError.add(1, { status: String(res.status) });

    check(res, { '201 또는 409(품절)': (r) => r.status === 201 || r.status === 409 });
}

export function teardown(data) {
    // 남은 재고가 모두 0인지 확인 (덜 팔리거나 더 팔리면 실패)
    const event = http.get(`${BASE_URL}/api/events/${data.eventId}`).json();
    const remaining = event.products.reduce((sum, p) => sum + p.remainingQuantity, 0);
    check(remaining, { '남은 재고 0': (r) => r === 0 });
    if (remaining !== 0) fail(`remaining stock = ${remaining}`);
}

export function handleSummary(data) {
    const m = data.metrics;
    const v = (name, stat) => (m[name] ? m[name].values[stat] : 0);
    const orders = v('order_success', 'count') + v('order_sold_out', 'count') + v('order_error', 'count');
    const windowMs = v('order_end_ts', 'max') - v('order_start_ts', 'min');
    const tps = windowMs > 0 ? orders / (windowMs / 1000) : 0;
    const ms = (x) => `${x.toFixed(1)} ms`;

    const result = {
        mode: MODE, users: USERS, vus: VUS, products: PRODUCT_COUNT, stock: TOTAL_STOCK,
        success: v('order_success', 'count'), soldOut: v('order_sold_out', 'count'), error: v('order_error', 'count'),
        windowSec: windowMs / 1000, tps,
        avg: v('order_duration', 'avg'), p50: v('order_duration', 'med'), p95: v('order_duration', 'p(95)'),
        p99: v('order_duration', 'p(99)'), max: v('order_duration', 'max'),
        successP50: v('order_duration_success', 'med'), successP95: v('order_duration_success', 'p(95)'),
        soldOutP50: v('order_duration_sold_out', 'med'), soldOutP95: v('order_duration_sold_out', 'p(95)'),
    };

    const md = [
        `### ${MODE === 'hot' ? '인기 상품 1개 집중 (hot)' : '상품 10개 분산 (spread)'}`,
        '',
        '| 항목 | 값 |',
        '|---|---|',
        `| 주문 요청 | ${orders}건 (VU ${VUS}) |`,
        `| 성공 / 품절 / 오류 | ${result.success} / ${result.soldOut} / ${result.error} |`,
        `| 주문 구간 | ${result.windowSec.toFixed(2)} 초 |`,
        `| 처리량 | **${tps.toFixed(1)} 건/초** |`,
        `| 응답 시간 평균 / p50 | ${ms(result.avg)} / ${ms(result.p50)} |`,
        `| 응답 시간 p95 / p99 / max | ${ms(result.p95)} / ${ms(result.p99)} / ${ms(result.max)} |`,
        `| 성공 요청 p50 / p95 | ${ms(result.successP50)} / ${ms(result.successP95)} |`,
        `| 품절 요청 p50 / p95 | ${ms(result.soldOutP50)} / ${ms(result.soldOutP95)} |`,
        '',
    ].join('\n');

    const outDir = __ENV.OUT_DIR || '.';
    return {
        stdout: `\n===== RESULT ${MODE} =====\n${md}\nRESULT_JSON ${JSON.stringify(result)}\n`,
        [`${outDir}/result-${MODE}.md`]: md,
        [`${outDir}/result-${MODE}.json`]: JSON.stringify(result, null, 2),
    };
}
