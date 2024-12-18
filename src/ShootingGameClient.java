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
    private List<Obstacle> obstacles = new ArrayList<>();

    private boolean[] keys = new boolean[256];
    private boolean gameOver = false;
    private String winner = "";
    private boolean spacePressed = false; // 스페이스바 눌림 상태

    private Image backgroundImage, player1Image, player2Image, missileImage;

    private String backgroundImagePath;
    private String player1ImagePath;
    private String player2ImagePath;

    private int playerWidth;
    private int playerHeight;
    private int missileWidth;
    private int missileHeight;

    private boolean isReady = false; // 두 명 연결 여부
    private boolean isGameStarted = false; // 게임 시작 여부

    public ShootingGameClient(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 서버 리스너 시작
            new Thread(new ServerListener()).start();

            setFocusable(true);
            addKeyListener(this);
            setPreferredSize(new Dimension(500, 770));

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

    private Image scaleImage(Image srcImg, int width, int height) {
        return srcImg.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    }

    private void parseSettings(String[] tokens) {
        for (int i = 0; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "PLAYER_WIDTH":
                    playerWidth = Integer.parseInt(tokens[++i]);
                    break;
                case "PLAYER_HEIGHT":
                    playerHeight = Integer.parseInt(tokens[++i]);
                    break;
                case "MISSILE_WIDTH":
                    missileWidth = Integer.parseInt(tokens[++i]);
                    break;
                case "MISSILE_HEIGHT":
                    missileHeight = Integer.parseInt(tokens[++i]);
                    break;
                case "BACKGROUND_IMAGE":
                    backgroundImagePath = tokens[++i];
                    backgroundImage = loadImage(backgroundImagePath);
                    break;
                case "PLAYER1_IMAGE":
                    player1ImagePath = tokens[++i];
                    player1Image = scaleImage(loadImage(player1ImagePath), playerWidth, playerHeight);
                    break;
                case "PLAYER2_IMAGE":
                    player2ImagePath = tokens[++i];
                    player2Image = scaleImage(loadImage(player2ImagePath), playerWidth, playerHeight);
                    break;
            }
        }
        updateScaledImages(); // 크기 조정된 이미지를 생성
    }


    private void updateScaledImages() {
        missileImage = scaleImage(loadImage("images/missile.png"), missileWidth, missileHeight);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        setDoubleBuffered(true); // 더블 버퍼링 활성화

        // 배경 그리기
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);

        // 게임 시작 전 메시지
        if (!isGameStarted) {
            g.setFont(new Font("Arial", Font.BOLD, 30));
            g.setColor(Color.CYAN);
            if (isReady) {
                g.drawString("READY! Press START to begin.", 30, (getHeight() / 2) - 20);
            } else {
                g.drawString("Waiting for another player...", 50, (getHeight() / 2) - 20);
            }
            return;
        }

        // 게임 종료 메시지
        if (gameOver) {
            g.setFont(new Font("Arial", Font.BOLD, 30));
            g.setColor(Color.RED);
            g.drawString(winner.equals("패배") ? "패배" : "승리", 150, getHeight() / 2);
            return;
        }

        // 플레이어 그리기
        synchronized (players) {
            for (Player player : players.values()) {
                drawPlayer(g, player);
            }
        }

        // 미사일 그리기
        synchronized (missiles) {
            for (Missile missile : missiles) {
                drawMissile(g, missile);
            }
        }

        // 장애물 그리기
        synchronized (obstacles) {
            for (Obstacle obstacle : obstacles) {
                drawObstacle(g, obstacle);
            }
        }
    }
    private void drawPlayer(Graphics g, Player player) {
        Graphics2D g2d = (Graphics2D) g;
        int x = player.getX();
        int y = player.getY();
        Image image = "Player1".equals(player.getId()) ? player1Image : player2Image;

        // 상대방 플레이어 회전 처리
        if (!player.getId().equals(clientId)) {
            double rotationAngle = Math.toRadians(180);
            g2d.rotate(rotationAngle, x + playerWidth / 2.0, y + playerHeight / 2.0);
            g2d.drawImage(image, x, y, playerWidth, playerHeight, this);
            g2d.rotate(-rotationAngle, x + playerWidth / 2.0, y + playerHeight / 2.0);
        } else {
            g2d.drawImage(image, x, y, playerWidth, playerHeight, this);
        }

        // 체력 표시
        g.setFont(new Font("Arial", Font.BOLD, 15));
        g.setColor(Color.GREEN);
        g.drawString("Health: " + player.getHealth(), x, y - 10);
    }
    private void drawObstacle(Graphics g, Obstacle obstacle) {
        g.drawImage(obstacle.getImage(), obstacle.getX(), obstacle.getY(), obstacle.getWidth(), obstacle.getHeight(), this);
    }
    private void drawMissile(Graphics g, Missile missile) {
        Graphics2D g2d = (Graphics2D) g;
        int x = missile.getX();
        int y = missile.getY();

        // 상대방 미사일 회전 처리
        if (!missile.getOwnerId().equals(clientId)) {
            double rotationAngle = Math.toRadians(180);
            g2d.rotate(rotationAngle, x + missileWidth / 2.0, y + missileHeight / 2.0);
            g2d.drawImage(missileImage, x, y, missileWidth, missileHeight, this);
            g2d.rotate(-rotationAngle, x + missileWidth / 2.0, y + missileHeight / 2.0);
        } else {
            g2d.drawImage(missileImage, x, y, missileWidth, missileHeight, this);
        }
    }
    private void updatePlayer(Player player) {
        repaint(new Rectangle(player.getX(), player.getY(), playerWidth, playerHeight));
    }
    private void updateObstacle(Obstacle obstacle) {
        synchronized (obstacles) {
            repaint(new Rectangle(obstacle.getX(), obstacle.getY(), obstacle.getWidth(), obstacle.getHeight()));
        }
    }
    private void updateMissile(Missile missile) {
        repaint(new Rectangle(missile.getX(), missile.getY(), missileWidth, missileHeight));
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
                int prevX = player.getX();
                int prevY = player.getY();

                player.setPosition(player.getX() + moveX, player.getY() + moveY);

                // 이전 위치와 새 위치 갱신
                repaint(new Rectangle(prevX, prevY, playerWidth, playerHeight));
                updatePlayer(player);

                // 서버에 새로운 위치 전송
                out.println("MOVE " + player.getX() + " " + player.getY());
            }
        }
    }

    public void keyPressed(KeyEvent e) {
        if (gameOver) return; // 게임 종료 시 키 입력 무시
        keys[e.getKeyCode()] = true;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (!spacePressed) { // 스페이스바가 아직 눌리지 않은 상태
                spacePressed = true; // 눌림 상태로 변경
                Player player = players.get(clientId);
                if (player != null) {
                    int missileX = player.getX() + 40;
                    int missileY = player.getY() - 20;

                    // 미사일 발사 위치가 y축의 절반 이상에서 발사되지 않도록 제한
                    if (missileY < getHeight() / 2) {
                        missileY = getHeight() / 2;
                    }

                    out.println("MISSILE " + missileX + " " + missileY);
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
        obstacles.clear(); // 장애물 초기화

        int screenWidth = getWidth(); // 화면 너비 가져오기
        int screenHeight = getHeight(); // 화면 높이 가져오기

        int i = 1;

        while (i < tokens.length) {
            if ("PLAYER".equals(tokens[i])) {
                if (i + 4 >= tokens.length) {
                    System.err.println("Incomplete PLAYER data: " + Arrays.toString(tokens));
                    break;
                }
                String id = tokens[i + 1];
                int x = Integer.parseInt(tokens[i + 2]);
                int y = Integer.parseInt(tokens[i + 3]);
                int health = Integer.parseInt(tokens[i + 4]);

                // 상대편 데이터를 X축과 Y축 대칭 처리
                if (!id.equals(clientId)) {
                    x = screenWidth - x - playerWidth;  // X 대칭 변환
                    y = screenHeight - y - playerHeight; // Y 대칭 변환
                }

                players.put(id, new Player(id, x, y, health));
                i += 5;
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
            } else if ("OBSTACLE".equals(tokens[i])) {
                synchronized (obstacles) {
                    int x = Integer.parseInt(tokens[i + 1]);
                    int y = Integer.parseInt(tokens[i + 2]);
                    int width = Integer.parseInt(tokens[i + 3]);
                    int height = Integer.parseInt(tokens[i + 4]);
                    boolean movingRight = Boolean.parseBoolean(tokens[i + 5]);

                    // 장애물의 좌표를 X축 및 Y축 대칭 처리
                    if (!"Player1".equals(clientId)) { // Player1 기준으로 대칭 처리
                        x = screenWidth - x - width; // X 대칭
                        y = screenHeight - y - height; // Y 대칭
                    }

                    obstacles.add(new Obstacle(x, y, width, height, movingRight));
                    i += 6;
                }
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
                    switch (tokens[0]) {
                        case "READY":
                            isReady = true;
                            repaint();
                            break;
                        case "READY_TO_RESTART":
                            isReady = true; // 다른 클라이언트도 준비 완료
                            repaint();
                            break;

                        case "GAMESTART":
                            isGameStarted = true; // 게임 시작
                            repaint();
                            break;
                        case "SETTINGS":
                            parseSettings(Arrays.copyOfRange(tokens, 1, tokens.length));
                            updateScaledImages();
                            break;
                        case "GAMESTATE":
                            parseGameState(tokens);
                            break;
                        case "DEFEAT":
                            handleDefeatMessage();
                            break;
                        case "VICTORY":
                            handleVictoryMessage();
                            break;
                        case "CONNECTED":
                            clientId = tokens[1];
                            break;
                        default:
                            System.err.println("Unknown message: " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleDefeatMessage() {
            if (gameOver) return; // 게임 종료 후 추가 처리 방지
            System.out.println("DEFEAT message received for " + clientId);
            gameOver = true;
            winner = "패배";

            SwingUtilities.invokeLater(() -> {
                int result = JOptionPane.showConfirmDialog(
                        ShootingGameClient.this,
                        "게임에서 패배하셨습니다. 다시 시작하시겠습니까?",
                        "패배",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE
                );
                if (result == JOptionPane.YES_OPTION) {
                    resetGame(); // 게임 리셋
                } else {
                    System.exit(0); // 창 닫기
                }
            });

            repaint();
        }

        private void handleVictoryMessage() {
            if (gameOver) return; // 게임 종료 후 추가 처리 방지
            System.out.println("VICTORY message received for " + clientId);
            gameOver = true;
            winner = "승리";

            SwingUtilities.invokeLater(() -> {
                int result = JOptionPane.showConfirmDialog(
                        ShootingGameClient.this,
                        "게임에서 승리하셨습니다! 다시 시작하시겠습니까?",
                        "승리",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE
                );
                if (result == JOptionPane.YES_OPTION) {
                    resetGame(); // 게임 리셋
                } else {
                    System.exit(0); // 창 닫기
                }
            });

            repaint();
        }

        private void resetGame() {
            // 클라이언트 데이터 초기화
            players.clear();
            missiles.clear();
            obstacles.clear();
            Arrays.fill(keys, false);
            gameOver = false;
            winner = "";

            // 서버에 재시작 요청
            out.println("RESTART");

            // 재시작 대기 메시지
            isReady = false; // 초기화
            isGameStarted = false; // 게임 상태를 대기 상태로 변경
            repaint();
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Shooting Game Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ShootingGameClient client = new ShootingGameClient("localhost", 12345);
        frame.add(client);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setResizable(false); // 창 크기 변경 불가능
        frame.setVisible(true);
        client.requestFocusInWindow();
    }
}