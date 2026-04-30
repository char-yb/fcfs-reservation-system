import http from 'k6/http';
import { Counter, Rate } from 'k6/metrics';

export const expectedSuccessResponse = http.expectedStatuses({ min: 200, max: 299 });
export const expectedBookingResponse = http.expectedStatuses({ min: 200, max: 299 }, 402, 409, 503);

export const checkoutUnexpectedResponseRate = new Rate('checkout_unexpected_response_rate');
export const bookingUnexpectedResponseRate = new Rate('booking_unexpected_response_rate');
export const bookingSuccessTotal = new Counter('booking_success_total');
export const stockSoldOutTotal = new Counter('stock_sold_out_total');
export const duplicateRequestTotal = new Counter('duplicate_request_total');
export const paymentDeclinedTotal = new Counter('payment_declined_total');
export const lockTimeoutTotal = new Counter('lock_timeout_total');
export const unexpectedResponseTotal = new Counter('unexpected_response_total');
export const responseParseErrorTotal = new Counter('response_parse_error_total');

export function parseJson(res) {
    if (!res.body) return null;

    try {
        return JSON.parse(res.body);
    } catch (e) {
        responseParseErrorTotal.add(1);
        return null;
    }
}

export function errorCode(res) {
    const body = parseJson(res);
    return body && body.data ? body.data.code : undefined;
}

export function recordCheckoutOutcome(res) {
    const body = parseJson(res);
    const ok =
        res.status === 200 &&
        body !== null &&
        body.success === true &&
        body.data &&
        body.data.product &&
        body.data.user;

    checkoutUnexpectedResponseRate.add(ok ? 0 : 1);
    if (!ok) unexpectedResponseTotal.add(1, { endpoint: 'checkout', status: String(res.status) });
    return ok;
}

export function recordBookingOutcome(res) {
    const body = parseJson(res);
    const code = body && body.data ? body.data.code : undefined;

    if (res.status === 200 && body !== null && body.success === true && body.data && body.data.status === 'CONFIRMED') {
        bookingSuccessTotal.add(1);
        bookingUnexpectedResponseRate.add(0);
        return 'CONFIRMED';
    }

    if (res.status === 409 && code === 'STOCK_SOLD_OUT') {
        stockSoldOutTotal.add(1);
        bookingUnexpectedResponseRate.add(0);
        return 'STOCK_SOLD_OUT';
    }

    if (res.status === 409 && code === 'DUPLICATE_REQUEST') {
        duplicateRequestTotal.add(1);
        bookingUnexpectedResponseRate.add(0);
        return 'DUPLICATE_REQUEST';
    }

    if (res.status === 402 && code === 'PAYMENT_DECLINED') {
        paymentDeclinedTotal.add(1);
        bookingUnexpectedResponseRate.add(0);
        return 'PAYMENT_DECLINED';
    }

    if (res.status === 503 && code === 'LOCK_ACQUISITION_FAILED') {
        lockTimeoutTotal.add(1);
        bookingUnexpectedResponseRate.add(0);
        return 'LOCK_ACQUISITION_FAILED';
    }

    bookingUnexpectedResponseRate.add(1);
    unexpectedResponseTotal.add(1, {
        endpoint: 'booking',
        status: String(res.status),
        code: code || 'UNKNOWN',
    });
    return 'UNEXPECTED';
}
