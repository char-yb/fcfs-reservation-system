import http from 'k6/http';
import exec from 'k6/execution';
import { config } from './config.js';
import { expectedBookingResponse, expectedSuccessResponse } from './api.js';
import { uuidv4 } from './uuid.js';

export function userIdForIteration(offset = 0) {
    return config.userStartId + ((exec.scenario.iterationInTest + offset) % config.userCount);
}

export function checkoutUrl(userId) {
    return `${config.baseUrl}/api/v1/checkout/${userId}?productId=${config.productId}`;
}

export function bookingUrl(userId) {
    return `${config.baseUrl}/api/v1/booking/${userId}`;
}

export function creditCardPayment(amount = config.productPrice, token = 'card-token-k6') {
    return {
        method: 'CREDIT_CARD',
        amount,
        attributes: {
            cardToken: token,
        },
    };
}

export function yPayPayment(amount = config.productPrice, token = 'pay-token-k6') {
    return {
        method: 'Y_PAY',
        amount,
        attributes: {
            payToken: token,
        },
    };
}

export function yPointPayment(amount = config.productPrice) {
    return {
        method: 'Y_POINT',
        amount,
    };
}

export function bookingPayload(payments = [creditCardPayment()]) {
    const totalAmount = payments.reduce((sum, payment) => sum + payment.amount, 0);
    return JSON.stringify({
        productId: config.productId,
        totalAmount,
        payments,
    });
}

export function getCheckout(userId) {
    return http.get(checkoutUrl(userId), {
        timeout: config.timeout,
        tags: {
            endpoint: 'checkout',
        },
        responseCallback: expectedSuccessResponse,
    });
}

export function postBooking(userId, payload = bookingPayload(), idempotencyKey = uuidv4(), extraTags = {}) {
    return http.post(bookingUrl(userId), payload, {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
        },
        timeout: config.timeout,
        tags: {
            endpoint: 'booking',
            ...extraTags,
        },
        responseCallback: expectedBookingResponse,
    });
}

