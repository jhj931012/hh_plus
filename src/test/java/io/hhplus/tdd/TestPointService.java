package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
   - 누적된 충전금액 > 100,000 일때 요청실패
   - 충전금액 > 100,000 일때 요청 실패
   - 잔액 < 사용금액 인 경우 요청실패
   - 모든 충전과 사용내역은 history에 기록
3. 유저마다 보유할 수 있는 잔액은 0 이상 10_000_000 이하이다.
   - 포인트 충전 시에 결과값이 10_000_000 이상일 경우, 요청은 실패한다.
   *
*/
@ExtendWith(MockitoExtension.class)
public class TestPointService {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @InjectMocks
    private PointService pointService;

    @Test
    @DisplayName("포인트 충전 성공케이스")
    void testCharge_successful(){
        // Given
        long userId = 1L;
        long amountToCharge = 500L;
        UserPoint existingUserPoint = new UserPoint(userId, 1000L, System.currentTimeMillis()); // 현재 포인트: 1000

        when(userPointTable.selectById(userId)).thenReturn(existingUserPoint);
        when(userPointTable.insertOrUpdate(userId, 1500L))
                .thenReturn(new UserPoint(userId, 1500L, System.currentTimeMillis())); // 업데이트된 포인트

        // When
        UserPoint updatedUserPoint = pointService.charge(userId, amountToCharge);

        // Then
        assertEquals(1500L, updatedUserPoint.point());
        verify(userPointTable).selectById(userId);
        verify(userPointTable).insertOrUpdate(userId, 1500L);
        verify(pointHistoryTable).insert(eq(userId), eq(amountToCharge), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("충전금액이 음수인 경우 예외발생")
    void testCharge_negativeAmount() {
        // Given
        long userId = 2L;
        long negativeAmount = -500L;

        // When & Then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            pointService.charge(userId, negativeAmount);
        });

        assertInstanceOf(IllegalArgumentException.class, exception);
        verifyNoInteractions(userPointTable);
        verifyNoInteractions(pointHistoryTable);
    }

    @Test
    @DisplayName("100,000을 초과하는 금액 충전 시 예외 발생")
    void testCharge_amountExceedsMaxCharge() {
        // Given
        long userId = 3L;
        long amountToCharge = 150000L;

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pointService.charge(userId, amountToCharge);
        });

        assertInstanceOf(IllegalArgumentException.class, exception);
        assertEquals("충전금액은 100,000을 넘을 수 없습니다.", exception.getMessage());
        verify(userPointTable, never()).selectById(userId);
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("충전 후 포인트가 100,000을 초과할 경우 예외 발생")
    void testCharge_exceedingMaxPoints() {
        // Given
        long userId = 3L;
        long amountToCharge = 60000L;
        UserPoint existingUserPoint = new UserPoint(userId, 50000L, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(existingUserPoint);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pointService.charge(userId, amountToCharge);
        });

        assertInstanceOf(IllegalArgumentException.class, exception);
        verify(userPointTable).selectById(userId);
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("포인트 사용 성공케이스")
    void testUse_successful() {
        // Given
        long userId = 1L;
        long initialPoints = 1000L;
        long useAmount = 500L;
        long currentTimeMillis = System.currentTimeMillis();

        UserPoint initialUserPoint = new UserPoint(userId, initialPoints, currentTimeMillis);
        UserPoint expectedUserPoint = new UserPoint(userId, initialPoints - useAmount, currentTimeMillis);

        when(userPointTable.selectById(userId)).thenReturn(initialUserPoint);
        when(userPointTable.insertOrUpdate(userId, initialPoints - useAmount))
                .thenReturn(expectedUserPoint);

        // When
        UserPoint result = pointService.use(userId, useAmount);

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(expectedUserPoint.point(), result.point()),
                () -> assertEquals(expectedUserPoint.updateMillis(), result.updateMillis()),
                () -> verify(userPointTable).selectById(userId),
                () -> verify(userPointTable).insertOrUpdate(userId, initialPoints - useAmount),
                () -> verify(pointHistoryTable).insert(
                        eq(userId),
                        eq(useAmount),
                        eq(TransactionType.USE),
                        anyLong()
                )
        );
    }

    @Test
    @DisplayName("포인트 잔액 부족 시 예외 발생")
    void testUse_insufficientBalance() {
        // Given
        long userId = 1L;
        long initialPoints = 500L;
        long useAmount = 1000L;
        long currentTimeMillis = System.currentTimeMillis();

        UserPoint initialUserPoint = new UserPoint(userId, initialPoints, currentTimeMillis);

        when(userPointTable.selectById(userId)).thenReturn(initialUserPoint);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> pointService.use(userId, useAmount),
                "포인트가 부족합니다.");

        verify(userPointTable).selectById(userId);
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("포인트 조회 성공 테스트")
    void testPoint_Success() {
        // Given
        long userId = 1L;
        long expectedPoints = 1000L;
        long currentTimeMillis = System.currentTimeMillis();
        UserPoint expectedUserPoint = new UserPoint(userId, expectedPoints, currentTimeMillis);

        when(userPointTable.selectById(userId)).thenReturn(expectedUserPoint);

        // When
        UserPoint result = pointService.point(userId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.id());
        assertEquals(expectedPoints, result.point());
        assertEquals(currentTimeMillis, result.updateMillis());
        verify(userPointTable, times(1)).selectById(userId);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 포인트 조회 테스트")
    void testPoint_UserNotFound() {
        // Given
        long nonExistentUserId = 999L;
        when(userPointTable.selectById(nonExistentUserId)).thenReturn(UserPoint.empty(nonExistentUserId));

        // When
        UserPoint result = pointService.point(nonExistentUserId);

        // Then
        assertNotNull(result);
        assertEquals(nonExistentUserId, result.id());
        assertEquals(0, result.point());
        verify(userPointTable, times(1)).selectById(nonExistentUserId);
    }

    @Test
    @DisplayName("포인트 사용/충전 내역 조회 성공 테스트")
    void testHistory_Success() {
        // Given
        long userId = 1L;
        List<PointHistory> expectedHistories = Arrays.asList(
                new PointHistory(1L, userId, 100L, TransactionType.CHARGE, System.currentTimeMillis()),
                new PointHistory(2L, userId, 50L, TransactionType.USE, System.currentTimeMillis())
        );

        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(expectedHistories);

        // When
        List<PointHistory> result = pointService.history(userId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(expectedHistories, result);
        verify(pointHistoryTable, times(1)).selectAllByUserId(userId);
    }

    @Test
    @DisplayName("내역이 없는 사용자의 포인트 사용/충전 내역 조회 테스트")
    void testHistory_EmptyHistory() {
        // Given
        long userId = 999L;
        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(List.of());

        // When
        List<PointHistory> result = pointService.history(userId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(pointHistoryTable, times(1)).selectAllByUserId(userId);
    }

    @Test
    @DisplayName("동시 포인트 사용 동시성 테스트")
    void testPointUse_Concurrency() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long userId = 1L;
        long initialPoints = 10000L;

        // 초기 포인트 설정
        pointService.charge(userId, initialPoints);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.use(userId, 100L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // 최종 포인트 검증
        UserPoint finalUserPoint = pointService.point(userId);
        assertEquals(initialPoints - (100L * threadCount), finalUserPoint.point());
    }


    @Test
    @DisplayName("동시 포인트 충전 동시성 테스트")
    void testPointCharge_Concurrency() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long userId = 1L;
        long chargeAmount = 100L;

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.charge(userId, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // 최종 포인트 검증
        UserPoint finalUserPoint = pointService.point(userId);
        assertEquals(chargeAmount * threadCount, finalUserPoint.point());
    }

}
