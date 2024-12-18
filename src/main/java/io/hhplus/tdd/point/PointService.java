package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    /**
     * 포인트 충전
     */
    public UserPoint charge(long id, long amount) {
        // 유저 포인트 조회
        UserPoint userPoint = userPointTable.selectById(id);
        long newBalance = userPoint.point() + amount;  // 새 포인트 계산
        userPoint = userPointTable.insertOrUpdate(id, newBalance); // 업데이트

        // 포인트 히스토리 저장
        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return userPoint;  // 업데이트된 포인트 정보 반환
    }

    /**
     * 포인트 사용
     */
    public UserPoint use(long id, long amount) {
        UserPoint userPoint = userPointTable.selectById(id);

        if (userPoint.point() < amount) {  // 잔액 부족 검사
            throw new IllegalArgumentException("포인트가 부족합니다.");
        }

        long newBalance = userPoint.point() - amount;
        userPoint = userPointTable.insertOrUpdate(id, newBalance);

        // 포인트 히스토리 저장
        pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());

        return userPoint;  // 사용 후 업데이트된 포인트 정보 반환
    }

    /**
     * 포인트 조회
     */
    public UserPoint point(long id) {
        return userPointTable.selectById(id);
    }

    /**
     * 포인트 사용/충전 내역 조회
     */
    public List<PointHistory> history(long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }
}
