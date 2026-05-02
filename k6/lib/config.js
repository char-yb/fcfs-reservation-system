export const config = {
    // 부하테스트 대상 API 서버 주소.
    baseUrl: 'http://localhost:8080',
    // reset-local-data.sh가 준비하는 테스트 상품 옵션 ID.
    productOptionId: 1,
    // Booking 요청의 결제 금액. DB에 시드되는 상품 가격과 같아야 한다.
    productPrice: 100000,
    // 과제 기준 한정 수량. reset-local-data.sh의 stock과 맞춘다.
    stock: 10,
    // 부하테스트가 사용할 첫 번째 사용자 ID.
    userStartId: 1,
    // 반복 요청에서 순환 사용할 사용자 수.
    userCount: 1000,
    // 개별 HTTP 요청 timeout.
    timeout: '30s',
};
