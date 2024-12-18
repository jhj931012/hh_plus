package io.hhplus.tdd;

import org.springframework.boot.test.context.SpringBootTest;

/*
* PATCH  `/point/{id}/charge` : 포인트를 충전한다.
- PATCH `/point/{id}/use` : 포인트를 사용한다.
- GET `/point/{id}` : 포인트를 조회한다.
- GET `/point/{id}/histories` : 포인트 내역을 조회한다.
- 잔고가 부족할 경우, 포인트 사용은 실패하여야 합니다.
- 동시에 여러 건의 포인트 충전, 이용 요청이 들어올 경우 순차적으로 처리되어야 합니다.
*
* **분석**
1. 각 유저별 포인트 사용/충전/조회는 동시에 실행될 수 있다
2. 동일한 유저에 대한 포인트 사용/충전/조회는 한번에 하나만 실행되어야 한다
   - 동일한 유저에 요청이 한번 더 들어가면 기존의 포인트 + 새로운 포인트
   - 충전 금액이 음수인 경우 요청실패
   - 잔액 < 사용금액 인 경우 요청실패
   - 모든 충전과 사용내역은 history에 기록
3. 유저마다 보유할 수 있는 잔액은 0 이상 10_000_000 이하이다.
   - 포인트 충전 시에 결과값이 10_000_000 이상일 경우, 요청은 실패한다.
*/

@SpringBootTest
public class TestPointService {
}
