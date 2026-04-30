import http from 'k6/http';
import { check } from 'k6';
import { expectedBookingResponse, recordBookingOutcome } from '../lib/api.js';
import { bookingPayload, creditCardPayment, postBooking, userIdForIteration } from '../lib/booking.js';

http.setResponseCallback(expectedBookingResponse);

const failureTps = 10;
// 결제 실패 요청을 유지하는 시간.
const duration = '30s';
// k6가 미리 확보할 VU 수.
const preAllocatedVUs = 50;
// k6가 동적으로 늘릴 수 있는 최대 VU 수.
const maxVUs = 200;
// 목표 TPS를 놓친 반복 허용 개수.
const maxDroppedIterations = 0;

export const options = {
    scenarios: {
        payment_failure: {
            executor: 'constant-arrival-rate',
            rate: failureTps,
            timeUnit: '1s',
            duration,
            preAllocatedVUs,
            maxVUs,
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

const payload = bookingPayload([creditCardPayment(undefined, 'exceeded_limit_k6')]);

export default function () {
    const res = postBooking(userIdForIteration(), payload, undefined, { probe: 'payment_failure' });
    const outcome = recordBookingOutcome(res);

    check(res, {
        'payment failure response classified': () => outcome !== 'UNEXPECTED',
        'payment failure is declined': () => outcome === 'PAYMENT_DECLINED',
    });
}
