import http from 'k6/http';
import { check } from 'k6';
import { config } from '../lib/config.js';
import { expectedSuccessResponse, parseJson, recordCheckoutOutcome } from '../lib/api.js';
import { getCheckout, userIdForIteration } from '../lib/booking.js';

http.setResponseCallback(expectedSuccessResponse);

const baseTps = 50;
// Checkout 평시 부하 유지 시간.
const duration = '2m';
// k6가 미리 확보할 VU 수.
const preAllocatedVUs = 100;
// k6가 동적으로 늘릴 수 있는 최대 VU 수.
const maxVUs = 500;
// 목표 TPS를 놓친 반복 허용 개수.
const maxDroppedIterations = 0;

export const options = {
    scenarios: {
        checkout_baseline: {
            executor: 'constant-arrival-rate',
            rate: baseTps,
            timeUnit: '1s',
            duration,
            preAllocatedVUs,
            maxVUs,
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        checkout_unexpected_response_rate: ['rate<0.01'],
        dropped_iterations: [`count<=${maxDroppedIterations}`],
        http_req_duration: ['p(95)<1000', 'p(99)<3000'],
    },
    summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
    const userId = userIdForIteration();
    const res = getCheckout(userId);
    const ok = recordCheckoutOutcome(res);
    const body = parseJson(res);

    check(res, {
        'checkout returns 200 response wrapper': () => ok,
        'checkout product option matches test option': () =>
            body && body.data && body.data.product.productOptionId === config.productOptionId,
    });
}
