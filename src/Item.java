import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class Item {
    private int x, y, width, height;
    private Image image; // 아이템 이미지
    private String type; // 아이템 종류
    private boolean movingRight; // 이동 방향 (true: 오른쪽 →, false: 왼쪽 ←)
    private String imagePath; // 아이템 이미지 경로

    public Item(int x, int y, int width, int height, String type, boolean movingRight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
        this.movingRight = movingRight;
        this.image = new ImageIcon("images/item.png").getImage(); // 기본 이미지 설정
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
        try {
            this.image = new ImageIcon(imagePath).getImage();
            if (this.image == null) {
                throw new IOException("Image not found: " + imagePath);
            }
        } catch (Exception e) {
            System.err.println("Failed to load item image: " + imagePath);
            e.printStackTrace();
        }
    }

    public String getType() { return type; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Image getImage() { return image; }

    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public boolean isMovingRight() { return movingRight; }
    public void setMovingRight(boolean movingRight) { this.movingRight = movingRight; }
}
