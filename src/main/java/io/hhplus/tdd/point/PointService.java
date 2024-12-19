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
        if (amount <= 0) {
            throw new IllegalArgumentException("충전금액은 0보다 커야 합니다.");
        }
        if (amount > 100000) {
            throw new IllegalArgumentException("충전금액은 100,000을 넘을 수 없습니다.");
        }

        UserPoint userPoint = userPointTable.selectById(id);

        long newBalance = userPoint.point() + amount;
        if (newBalance > 100000) {
            throw new IllegalArgumentException("충전한도는 100,000 까지 입니다. 충전에 실패하였습니다.");
        }

        if (userPoint.point() == 0) {
            userPoint = userPointTable.insertOrUpdate(id, amount);
        } else {
            userPoint = userPointTable.insertOrUpdate(id, newBalance);
        }

        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return userPoint;  // 업데이트된 포인트 정보 반환
    }



    /**
     * 포인트 사용
     */
    public UserPoint use(long id, long amount) {
        UserPoint userPoint = userPointTable.selectById(id);

        if (userPoint == null) {
            throw new IllegalArgumentException("유저 정보가 존재하지 않습니다.");
        }

        if (userPoint.point() < amount) {
            throw new IllegalArgumentException("포인트가 부족합니다.");
        }

        long newBalance = userPoint.point() - amount;
        userPoint = userPointTable.insertOrUpdate(id, newBalance);

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
