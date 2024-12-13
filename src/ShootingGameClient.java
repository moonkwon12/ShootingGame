import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

public class ShootingGameClient extends JPanel implements ActionListener, KeyListener {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientId;

    private javax.swing.Timer timer;
    private Map<String, Player> players = new HashMap<>();
    private List<Missile> missiles = new ArrayList<>();
    private boolean[] keys = new boolean[256];
    private boolean gameOver = false;
    private String winner = "";
    private boolean spacePressed = false; // 스페이스바 눌림 상태

    private Image backgroundImage, player1Image, player2Image, missileImage;
    private String backgroundImagePath = ""; // 배경 이미지 경로

    public ShootingGameClient(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 이미지 로드
            backgroundImage = loadImage("images/back2.png");
            player1Image = loadImage("images/spaceship5.png");
            player2Image = loadImage("images/spaceship6.png");
            missileImage = loadImage("images/missile.png");
            backgroundImagePath = "images/back2.png";

            // 서버 리스너 시작
            new Thread(new ServerListener()).start();

            setFocusable(true);
            addKeyListener(this);
            setPreferredSize(new Dimension(400, 800));
            timer = new javax.swing.Timer(15, this);
            timer.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 이미지 로드 메서드
    private Image loadImage(String path) {
        return new ImageIcon(path).getImage();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);

        if (gameOver) {
            g.setFont(new Font("Arial", Font.BOLD, 30));
            g.setColor(Color.RED);
            if (winner.equals("패배")) {
                g.drawString("패배", 150, getHeight() / 2);
            } else if (winner.equals("승리")) {
                g.drawString("승리", 150, getHeight() / 2);
            }
            return;
        }

        Graphics2D g2d = (Graphics2D) g;

        synchronized (players) {
            for (Player player : players.values()) {
                int x = player.getX();
                int y = player.getY();
                Image image = "Player1".equals(player.getId()) ? player1Image : player2Image;

                // 상대방 플레이어 회전 처리
                if (!player.getId().equals(clientId)) {
                    double rotationAngle = Math.toRadians(180); // 180도 회전
                    g2d.rotate(rotationAngle, x + 25, y + 25); // 회전 중심을 이미지의 중심으로 설정
                    g2d.drawImage(image, x, y, this);
                    g2d.rotate(-rotationAngle, x + 25, y + 25); // 원상 복구
                } else {
                    g2d.drawImage(image, x, y, this); // 자신의 플레이어는 회전 없이 그리기
                }

                // 체력 표시
                g.setFont(new Font("Arial", Font.BOLD, 15));
                g.setColor(Color.GREEN);
                g.drawString("Health: " + player.getHealth(), x, y - 10);
            }
        }

        synchronized (missiles) {
            for (Missile missile : missiles) {
                int x = missile.getX();
                int y = missile.getY();

                // 상대방 미사일 회전 처리
                if (!missile.getOwnerId().equals(clientId)) {
                    double rotationAngle = Math.toRadians(180); // 180도 회전
                    g2d.rotate(rotationAngle, x + 10, y + 10); // 미사일 중심 기준으로 회전
                    g2d.drawImage(missileImage, x, y, this);
                    g2d.rotate(-rotationAngle, x + 10, y + 10); // 원상 복구
                } else {
                    g2d.drawImage(missileImage, x, y, this); // 자신의 미사일은 회전 없이 그리기
                }
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (gameOver) return; // 게임 종료 시 움직임 제한
        int moveX = 0, moveY = 0;
        if (keys[KeyEvent.VK_A]) moveX -= 5;
        if (keys[KeyEvent.VK_D]) moveX += 5;
        if (keys[KeyEvent.VK_W]) moveY -= 5;
        if (keys[KeyEvent.VK_S]) moveY += 5;
        if (moveX != 0 || moveY != 0) {
            Player player = players.get(clientId);
            if (player != null) {
                player.setPosition(player.getX() + moveX, player.getY() + moveY);
                out.println("MOVE " + player.getX() + " " + player.getY());
            }
        }
        repaint();
    }

    public void keyPressed(KeyEvent e) {
        if (gameOver) return; // 게임 종료 시 키 입력 무시
        keys[e.getKeyCode()] = true;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (!spacePressed) { // 스페이스바가 아직 눌리지 않은 상태
                spacePressed = true; // 눌림 상태로 변경
                Player player = players.get(clientId);
                if (player != null) {
                    int missileX = player.getX() + 20;
                    int missileY = player.getY();
                    out.println("MISSILE " + missileX + " " + missileY); // 미사일 발사
                }
            }
        }
    }

    public void keyReleased(KeyEvent e) {
        keys[e.getKeyCode()] = false;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            spacePressed = false; // 스페이스바 눌림 상태 해제
        }
    }

    public void keyTyped(KeyEvent e) {}

    private void parseGameState(String[] tokens) {
        if (gameOver) return; // 게임 종료 상태에서는 처리하지 않음

        players.clear();
        missiles.clear();
        int screenWidth = getWidth(); // 화면 너비 가져오기
        int screenHeight = getHeight(); // 화면 높이 가져오기
        int playerWidth = 50; // 플레이어 이미지 너비 (임의값)
        int playerHeight = 50; // 플레이어 이미지 높이 (임의값)
        int missileWidth = 20; // 미사일 이미지 너비 (임의값)
        int missileHeight = 20; // 미사일 이미지 높이 (임의값)

        int i = 1;

        while (i < tokens.length) {
            if ("PLAYER".equals(tokens[i])) {
                if (i + 6 >= tokens.length) {
                    System.err.println("Incomplete PLAYER data: " + Arrays.toString(tokens));
                    break;
                }
                String id = tokens[i + 1];
                int x = Integer.parseInt(tokens[i + 2]);
                int y = Integer.parseInt(tokens[i + 3]);
                int health = Integer.parseInt(tokens[i + 4]);
                String image = tokens[i + 5];
                String backgroundImage = tokens[i + 6];

                // 배경 이미지 업데이트
                if (!this.backgroundImagePath.equals(backgroundImage)) {
                    this.backgroundImage = loadImage(backgroundImage);
                    this.backgroundImagePath = backgroundImage;
                }

                // 상대편 데이터를 X축과 Y축 대칭 처리
                if (!id.equals(clientId)) {
                    x = screenWidth - x - playerWidth;  // X 대칭 변환
                    y = screenHeight - y - playerHeight; // Y 대칭 변환
                }

                players.put(id, new Player(id, x, y, health, image));
                i += 7;
            } else if ("MISSILE".equals(tokens[i])) {
                if (i + 3 >= tokens.length) {
                    System.err.println("Incomplete MISSILE data: " + Arrays.toString(tokens));
                    break;
                }
                String ownerId = tokens[i + 1];
                int x = Integer.parseInt(tokens[i + 2]);
                int y = Integer.parseInt(tokens[i + 3]);

                // 상대편 미사일 데이터를 X축과 Y축 대칭 처리
                if (!ownerId.equals(clientId)) {
                    x = screenWidth - x - missileWidth;  // X 대칭 변환
                    y = screenHeight - y - missileHeight; // Y 대칭 변환
                }

                missiles.add(new Missile(ownerId, x, y));
                i += 4;
            } else {
                System.err.println("Unknown token: " + tokens[i]);
                break;
            }
        }

        repaint();
    }

    private class ServerListener implements Runnable {
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    String[] tokens = message.split(" ");
                    if ("GAMESTATE".equals(tokens[0])) {
                        parseGameState(tokens);
                    } else if ("DEFEAT".equals(tokens[0])) {
                        handleDefeatMessage(); // 패배 메시지 처리
                    } else if ("VICTORY".equals(tokens[0])) {
                        handleVictoryMessage(); // 승리 메시지 처리
                    } else if ("CONNECTED".equals(tokens[0])) {
                        clientId = tokens[1];
                    } else {
                        System.err.println("Unknown message: " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleDefeatMessage() {
            System.out.println("DEFEAT message received for " + clientId);
            gameOver = true;
            winner = "패배";

            // 패배 메시지 모달 표시
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(
                        ShootingGameClient.this,
                        "게임에서 패배하셨습니다.",
                        "패배",
                        JOptionPane.INFORMATION_MESSAGE
                );
            });

            repaint();
        }

        private void handleVictoryMessage() {
            System.out.println("VICTORY message received for " + clientId);
            gameOver = true;
            winner = "승리";

            // 승리 메시지 모달 표시
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(
                        ShootingGameClient.this,
                        "게임에서 승리하셨습니다!",
                        "승리",
                        JOptionPane.INFORMATION_MESSAGE
                );
            });

            repaint();
        }
    }

    private static class Player {
        private String id;
        private int x, y, health;
        private String imagePath; // 이미지 경로를 저장할 필드 추가

        public Player(String id, int x, int y, int health, String imagePath) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.health = health;
            this.imagePath = imagePath; // 이미지 경로 초기화
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

        public String getImagePath() {
            return imagePath;
        }

        public void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class Missile {
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
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Shooting Game Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ShootingGameClient client = new ShootingGameClient("localhost", 12345);
        frame.add(client);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        client.requestFocusInWindow();
    }
}
