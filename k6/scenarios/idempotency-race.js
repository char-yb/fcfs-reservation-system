import http from 'k6/http';
import { check } from 'k6';
import { config } from '../lib/config.js';
import { expectedBookingResponse, recordBookingOutcome } from '../lib/api.js';
import { bookingPayload, postBooking } from '../lib/booking.js';
import { uuidv4 } from '../lib/uuid.js';

http.setResponseCallback(expectedBookingResponse);

const raceTps = 100;
// 동일 Idempotency-Key를 유지하며 동시 재시도 압력을 주는 시간.
const duration = '10s';
// k6가 미리 확보할 VU 수.
const preAllocatedVUs = 100;
// k6가 동적으로 늘릴 수 있는 최대 VU 수.
const maxVUs = 300;
// 목표 TPS를 놓친 반복 허용 개수.
const maxDroppedIterations = 0;

export const options = {
    scenarios: {
        idempotency_race: {
            executor: 'constant-arrival-rate',
            rate: raceTps,
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

const payload = bookingPayload();

export function setup() {
    return {
        idempotencyKey: uuidv4(),
    };
}

export default function (data) {
    const res = postBooking(config.userStartId, payload, data.idempotencyKey, { probe: 'idempotency_race' });
    const outcome = recordBookingOutcome(res);

    check(res, {
        'idempotency response classified': () => outcome !== 'UNEXPECTED',
        'idempotency duplicate is controlled': () =>
            ['CONFIRMED', 'DUPLICATE_REQUEST', 'STOCK_SOLD_OUT', 'LOCK_ACQUISITION_FAILED'].includes(outcome),
    });
}
