import http from 'k6/http';
import { check } from 'k6';
import { expectedBookingResponse, recordBookingOutcome } from '../lib/api.js';
import { bookingPayload, postBooking, userIdForIteration } from '../lib/booking.js';

http.setResponseCallback(expectedBookingResponse);

const baseTps = 50;
// 00:00 정각 이후 목표 TPS. 500 또는 1000으로 바꿔 실행한다.
const peakTps = 1000;
// 00:00 직전 평시 트래픽 유지 시간.
const warmupDuration = '1m';
// 00:00 정각 급등 시간. 순간 급등을 가정하므로 1초로 둔다.
const rampDuration = '1s';
// 00:00 이후 피크 TPS 유지 시간.
const spikeDuration = '5m';
// 피크 종료 후 트래픽을 0으로 낮추는 시간.
const cooldownDuration = '30s';
// k6가 미리 확보할 VU 수. dropped_iterations가 생기면 늘린다.
const preAllocatedVUs = 800;
// k6가 동적으로 늘릴 수 있는 최대 VU 수.
const maxVUs = 2500;
// 목표 TPS를 놓친 반복 허용 개수. 용량 검증에서는 0을 기준으로 본다.
const maxDroppedIterations = 0;

export const options = {
    scenarios: {
        booking_spike: {
            executor: 'ramping-arrival-rate',
            startRate: baseTps,
            timeUnit: '1s',
            preAllocatedVUs,
            maxVUs,
            stages: [
                // 00:00 직전 평시 트래픽.
                { duration: warmupDuration, target: baseTps },
                // 00:00 순간 피크 트래픽으로 급등.
                { duration: rampDuration, target: peakTps },
                // 00:00 이후 피크 유지 구간.
                { duration: spikeDuration, target: peakTps },
                { duration: cooldownDuration, target: 0 },
            ],
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        booking_unexpected_response_rate: ['rate<0.01'],
        dropped_iterations: [`count<=${maxDroppedIterations}`],
        http_req_duration: ['p(95)<5000', 'p(99)<10000'],
    },
    summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const payload = bookingPayload();

export default function () {
    const userId = userIdForIteration();
    const res = postBooking(userId, payload);
    const outcome = recordBookingOutcome(res);

    check(res, {
        'booking response is classified': () => outcome !== 'UNEXPECTED',
        'booking success or expected fail': () =>
            ['CONFIRMED', 'STOCK_SOLD_OUT', 'DUPLICATE_REQUEST', 'PAYMENT_DECLINED', 'LOCK_ACQUISITION_FAILED'].includes(outcome),
    });
}
