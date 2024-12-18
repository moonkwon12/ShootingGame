import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ObstacleManager {
    private List<Obstacle> obstacles = new CopyOnWriteArrayList<>(); // 스레드 안전한 리스트
    private final int serverWidth;
    private final int serverHeight;
    private boolean running = true; // 스레드 종료 플래그

    public ObstacleManager(int serverWidth, int serverHeight) {
        this.serverWidth = serverWidth;
        this.serverHeight = serverHeight;
    }

    public List<Obstacle> getObstacles() {
        return obstacles;
    }

    public void stop() {
        running = false;
    }

    // 장애물 초기화 메서드
    public void resetObstacles() {
        obstacles.clear(); // 기존 장애물 목록 초기화
    }


    // 장애물 생성 스레드
    public void startSpawnThread() {
        new Thread(() -> {
            Random random = new Random();
            while (running) {
                try {
                    if (obstacles.size() < 10) { // 최대 장애물 수 제한
                        spawnObstacle(random);
                    }
                    Thread.sleep(2000); // 2초마다 새로운 장애물 생성
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt(); // 스레드 상태 복구
                }
            }
        }).start();
    }

    // 장애물 움직임 스레드
    public void startMoveThread() {
        new Thread(() -> {
            while (running) {
                try {
                    updateObstacles();
                    Thread.sleep(50); // 50ms마다 장애물 이동
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt(); // 스레드 상태 복구
                }
            }
        }).start();
    }

    private void spawnObstacle(Random random) {
        int x;
        if (random.nextBoolean()) {
            x = 0; // 왼쪽 끝
        } else {
            x = serverWidth - 50; // 오른쪽 끝 (장애물의 가로 크기 고려)
        }
        int y = random.nextInt(serverHeight); // Y 좌표는 랜덤
        boolean moveRight = (x == 0); // 왼쪽에서 생성되면 오른쪽으로 이동, 오른쪽에서 생성되면 왼쪽으로 이동
        obstacles.add(new Obstacle(x, y, 50, 50, moveRight));
    }

    private void updateObstacles() {
        List<Obstacle> toRemove = new ArrayList<>(); // 제거할 장애물 리스트

        for (Obstacle obstacle : obstacles) {
            if (obstacle.isMovingRight()) {
                obstacle.setX(obstacle.getX() + 5); // 오른쪽으로 이동
                if (obstacle.getX() + obstacle.getWidth() >= serverWidth) {
                    toRemove.add(obstacle); // 오른쪽 끝까지 이동하면 제거
                }
            } else {
                obstacle.setX(obstacle.getX() - 5); // 왼쪽으로 이동
                if (obstacle.getX() <= 0) {
                    toRemove.add(obstacle); // 왼쪽 끝까지 이동하면 제거
                }
            }
        }

        // 제거
        obstacles.removeAll(toRemove);
    }
}
