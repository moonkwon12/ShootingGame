import javax.swing.*;
import java.awt.*;

public class Obstacle {
    private int x, y, width, height;
    private Image image; // 장애물 이미지를 저장할 변수
    private boolean movingRight; // 장애물의 현재 이동 방향 (true: 오른쪽, false: 왼쪽)

    public Obstacle(int x, int y, int width, int height, boolean movingRight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.image = new ImageIcon("images/obstacle.png").getImage(); // 이미지 로드
        this.movingRight = movingRight;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Image getImage() {
        return image;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }
    public boolean isMovingRight() {
        return movingRight;
    }
    public void setMovingRight(boolean movingRight) {
        this.movingRight = movingRight;
    }
}
