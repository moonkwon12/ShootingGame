public class Missile {
    private String ownerId;
    private int x, y;

    public Missile(String ownerId, int x, int y) {
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}