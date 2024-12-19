import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ItemManager {
    private List<Item> items = new CopyOnWriteArrayList<>(); // 스레드 안전한 리스트
    private final int serverWidth;
    private final int serverHeight;
    private boolean running = true; // 스레드 종료 플래그

    public ItemManager(int serverWidth, int serverHeight) {
        this.serverWidth = serverWidth;
        this.serverHeight = serverHeight;
    }

    public List<Item> getItems() {
        return items;
    }

    public void stop() {
        running = false;
    }

    // 아이템 생성 스레드
    public void startSpawnThread() {
        new Thread(() -> {
            Random random = new Random();
            while (running) {
                try {
                    spawnItem(random);
                    Thread.sleep(10000); // 10초마다 생성
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // 아이템 움직임 스레드
    public void startMoveThread() {
        new Thread(() -> {
            while (running) {
                try {
                    updateItems();
                    Thread.sleep(50); // 50ms마다 이동
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void spawnItem(Random random) {
        int x = random.nextBoolean() ? 0 : serverWidth - 50; // 좌우 랜덤
        int y = random.nextInt(serverHeight); // y 위치 랜덤
        boolean movingRight = x == 0;

        String type = "DOUBLE_MISSILE"; // 아이템 타입
        Item item = new Item(x, y, 40, 40, type, movingRight); // 크기 40x40
        item.setImagePath("images/double_missile_item.png"); // 이미지 경로 설정
        items.add(item);
    }

    private void updateItems() {
        List<Item> toRemove = new ArrayList<>();

        for (Item item : items) {
            if (item.isMovingRight()) {
                item.setX(item.getX() + 5); // 오른쪽으로 이동
                if (item.getX() > serverWidth) {
                    toRemove.add(item); // 화면 밖으로 나가면 제거
                }
            } else {
                item.setX(item.getX() - 5); // 왼쪽으로 이동
                if (item.getX() + item.getWidth() < 0) {
                    toRemove.add(item); // 화면 밖으로 나가면 제거
                }
            }
        }

        items.removeAll(toRemove); // 리스트에서 제거
    }
}
