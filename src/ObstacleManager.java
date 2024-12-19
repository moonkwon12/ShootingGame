import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ObstacleManager {
    private List<Obstacle> obstacles = new CopyOnWriteArrayList<>(); // 스레드 안전한 리스트
    private final int serverWidth;
    private final int serverHeight;
    private boolean running = true; // 스레드 종료 플래그
    private String obstacleImagePath; // 맵별 장애물 이미지 경로

    public ObstacleManager(int serverWidth, int serverHeight) {
        this.serverWidth = serverWidth;
        this.serverHeight = serverHeight;
    }

    public void setObstacleImagePath(String obstacleImagePath) {
        this.obstacleImagePath = obstacleImagePath;
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
            while (true) { // 임의의 조건으로 루프
                try {
                    // 장애물 생성
                    spawnObstacle(random);
                    Thread.sleep(2000); // 2초마다 생성
                } catch (InterruptedException e) {
                    e.printStackTrace();
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
        int x = random.nextBoolean() ? 0 : serverWidth - 50; // 좌우 랜덤
        int y = random.nextInt(serverHeight); // y 위치 랜덤
        boolean moveRight = x == 0; // 이동 방향 결정

        // 장애물 생성 및 이미지 설정
        Obstacle obstacle = new Obstacle(x, y, 50, 50, moveRight);
        obstacle.setImagePath(obstacleImagePath); // 맵의 이미지 경로 사용
        obstacles.add(obstacle); // 장애물을 리스트에 추가
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
