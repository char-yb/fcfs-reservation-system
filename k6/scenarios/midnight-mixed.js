import http from 'k6/http';
import { check } from 'k6';
import { expectedBookingResponse, recordBookingOutcome, recordCheckoutOutcome } from '../lib/api.js';
import { bookingPayload, getCheckout, postBooking, userIdForIteration } from '../lib/booking.js';

http.setResponseCallback(expectedBookingResponse);

const baseTps = 50;
// Checkout은 00:00 전후로 일정하게 유지되는 주문서 진입 트래픽으로 본다.
const checkoutTps = 50;
// Booking은 00:00 정각 이후 목표 TPS. 500 또는 1000으로 바꿔 실행한다.
const peakTps = 1000;
// Checkout 시나리오 전체 실행 시간. Booking 총 시간과 맞춘다.
const mixedDuration = '6m31s';
// 00:00 직전 평시 Booking 트래픽 유지 시간.
const warmupDuration = '1m';
// 00:00 정각 급등 시간.
const rampDuration = '1s';
// 00:00 이후 Booking 피크 TPS 유지 시간.
const spikeDuration = '5m';
// Booking 피크 종료 후 트래픽을 0으로 낮추는 시간.
const cooldownDuration = '30s';
// Checkout 시나리오가 미리 확보할 VU 수.
const checkoutPreAllocatedVUs = 100;
// Booking 시나리오가 미리 확보할 VU 수.
const bookingPreAllocatedVUs = 800;
// 두 시나리오가 사용할 수 있는 최대 VU 수.
const maxVUs = 2500;
// 목표 TPS를 놓친 반복 허용 개수.
const maxDroppedIterations = 0;

export const options = {
    scenarios: {
        checkout_baseline: {
            executor: 'constant-arrival-rate',
            exec: 'checkoutFlow',
            rate: checkoutTps,
            timeUnit: '1s',
            duration: mixedDuration,
            preAllocatedVUs: checkoutPreAllocatedVUs,
            maxVUs,
        },
        booking_spike: {
            executor: 'ramping-arrival-rate',
            exec: 'bookingFlow',
            startRate: baseTps,
            timeUnit: '1s',
            preAllocatedVUs: bookingPreAllocatedVUs,
            maxVUs,
            stages: [
                // 00:00 직전 평시 Booking 트래픽.
                { duration: warmupDuration, target: baseTps },
                // 00:00 순간 Booking 피크 트래픽으로 급등.
                { duration: rampDuration, target: peakTps },
                // 00:00 이후 Booking 피크 유지 구간.
                { duration: spikeDuration, target: peakTps },
                { duration: cooldownDuration, target: 0 },
            ],
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        checkout_unexpected_response_rate: ['rate<0.01'],
        booking_unexpected_response_rate: ['rate<0.01'],
        dropped_iterations: [`count<=${maxDroppedIterations}`],
        http_req_duration: ['p(95)<5000', 'p(99)<10000'],
    },
    summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const payload = bookingPayload();

export function checkoutFlow() {
    const res = getCheckout(userIdForIteration());
    const ok = recordCheckoutOutcome(res);

    check(res, {
        'mixed checkout classified': () => ok,
    });
}

export function bookingFlow() {
    const res = postBooking(userIdForIteration(), payload);
    const outcome = recordBookingOutcome(res);

    check(res, {
        'mixed booking classified': () => outcome !== 'UNEXPECTED',
    });
}
