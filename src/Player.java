import java.io.PrintWriter;

public class Player {
    private String id;
    private int x, y, health;
    private PrintWriter out;
    private MapInstance assignedMap; // 플레이어가 할당된 맵
    private String imagePath; // 플레이어 이미지 경로
    private boolean isReady = false; // 준비 상태 추가

    public Player(String id, int x, int y, int health, String imagePath) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.health = health;
        this.imagePath = imagePath;
    }
    public boolean isReady() {
        return isReady;
    }

    public void setReady(boolean ready) {
        this.isReady = ready;
    }

    public String getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getHealth() {
        return health;
    }

    public PrintWriter getOut() {
        return out;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setOut(PrintWriter out) {
        this.out = out;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void reduceHealth(int damage) {
        this.health -= damage;
        if (this.health < 0) {
            this.health = 0; // 체력은 0 이하로 내려가지 않도록 처리
        }
    }

    public void setHealth(int health) {
        this.health = health;
    }

    // 새로 추가된 메서드
    public MapInstance getAssignedMap() {
        return assignedMap;
    }

    public void setAssignedMap(MapInstance assignedMap) {
        this.assignedMap = assignedMap;
    }
}
