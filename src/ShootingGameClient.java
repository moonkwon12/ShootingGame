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
    private String currentMapId; // 현재 맵 ID

    private javax.swing.Timer timer;

    private Map<String, Player> players = new HashMap<>();
    private List<Missile> missiles = new ArrayList<>();
    private List<Obstacle> obstacles = new ArrayList<>();

    private boolean[] keys = new boolean[256];
    private boolean gameOver = false;
    private String winner = "";
    private boolean spacePressed = false;

    private Image backgroundImage, player1Image, player2Image, missileImage;

    private String backgroundImagePath;
    private String player1ImagePath;
    private String player2ImagePath;

    private int playerWidth;
    private int playerHeight;
    private int missileWidth;
    private int missileHeight;

    private boolean isReady = false;
    private boolean isGameStarted = false;

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
        updateScaledImages();
    }

    private void updateScaledImages() {
        missileImage = scaleImage(loadImage("images/missile.png"), missileWidth, missileHeight);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 배경 그리기
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);

        // 게임 시작 전 메시지
        if (!isGameStarted) {
            g.setFont(new Font("Arial", Font.BOLD, 30));
            g.setColor(Color.CYAN);
            if (isReady) {
                g.drawString("READY! Waiting for the game to start.", 30, (getHeight() / 2) - 20);
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
        int x = player.getX();
        int y = player.getY();
        Image image = player.getId().equals(clientId) ? player1Image : player2Image;

        g.drawImage(image, x, y, playerWidth, playerHeight, this);

        // 체력 표시
        g.setColor(Color.GREEN);
        g.drawString("HP: " + player.getHealth(), x, y - 10);
    }

    private void drawMissile(Graphics g, Missile missile) {
        g.drawImage(missileImage, missile.getX(), missile.getY(), missileWidth, missileHeight, this);
    }

    private void drawObstacle(Graphics g, Obstacle obstacle) {
        g.drawImage(obstacle.getImage(), obstacle.getX(), obstacle.getY(), obstacle.getWidth(), obstacle.getHeight(), this);
    }

    public void actionPerformed(ActionEvent e) {
        if (gameOver) return;

        int dx = 0, dy = 0;
        if (keys[KeyEvent.VK_A]) dx -= 5;
        if (keys[KeyEvent.VK_D]) dx += 5;
        if (keys[KeyEvent.VK_W]) dy -= 5;
        if (keys[KeyEvent.VK_S]) dy += 5;

        if (dx != 0 || dy != 0) {
            Player player = players.get(clientId);
            if (player != null) {
                player.setPosition(player.getX() + dx, player.getY() + dy);
                out.println("MOVE " + player.getX() + " " + player.getY());
                repaint();
            }
        }
    }

    public void keyPressed(KeyEvent e) {
        keys[e.getKeyCode()] = true;
        if (e.getKeyCode() == KeyEvent.VK_SPACE && !spacePressed) {
            spacePressed = true;
            Player player = players.get(clientId);
            if (player != null) {
                out.println("MISSILE " + (player.getX() + 40) + " " + (player.getY() - 20));
            }
        }
    }

    public void keyReleased(KeyEvent e) {
        keys[e.getKeyCode()] = false;
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            spacePressed = false;
        }
    }

    public void keyTyped(KeyEvent e) {}

    private class ServerListener implements Runnable {
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    String[] tokens = message.split(" ");
                    switch (tokens[0]) {
                        case "SETTINGS":
                            parseSettings(Arrays.copyOfRange(tokens, 1, tokens.length));
                            updateScaledImages();
                            break;
                        case "GAMESTATE":
                            parseGameState(tokens);
                            break;
                        case "DEFEAT":
                            gameOver = true;
                            winner = "패배";
                            repaint();
                            break;
                        case "VICTORY":
                            gameOver = true;
                            winner = "승리";
                            repaint();
                            break;
                        case "CONNECTED":
                            handleConnectedMessage(tokens);
                            break;
                        default:
                            System.err.println("Unknown command: " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleConnectedMessage(String[] tokens) {
            // 유효성 검사 추가
            if (tokens.length < 2) {
                System.err.println("Invalid CONNECTED message: " + Arrays.toString(tokens));
                return;
            }

            clientId = tokens[1]; // clientId 설정

            if (tokens.length >= 3) {
                currentMapId = tokens[2]; // mapId 설정 (있는 경우만)
            }

            System.out.println("Connected with clientId: " + clientId +
                    (currentMapId != null ? ", mapId: " + currentMapId : ""));
        }
    }

    private void parseGameState(String[] tokens) {
        players.clear();
        missiles.clear();
        obstacles.clear();

        int i = 1;
        while (i < tokens.length) {
            if (tokens[i].equals("PLAYER")) {
                players.put(tokens[i + 1], new Player(tokens[i + 1],
                        Integer.parseInt(tokens[i + 2]),
                        Integer.parseInt(tokens[i + 3]),
                        Integer.parseInt(tokens[i + 4])));
                i += 5;
            } else if (tokens[i].equals("MISSILE")) {
                missiles.add(new Missile(tokens[i + 1],
                        Integer.parseInt(tokens[i + 2]),
                        Integer.parseInt(tokens[i + 3])));
                i += 4;
            } else if (tokens[i].equals("OBSTACLE")) {
                obstacles.add(new Obstacle(
                        Integer.parseInt(tokens[i + 1]),
                        Integer.parseInt(tokens[i + 2]),
                        Integer.parseInt(tokens[i + 3]),
                        Integer.parseInt(tokens[i + 4]),
                        Boolean.parseBoolean(tokens[i + 5])));
                i += 6;
            } else {
                break;
            }
        }

        repaint();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Shooting Game Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ShootingGameClient client = new ShootingGameClient("localhost", 12345);
        frame.add(client);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);
        client.requestFocusInWindow();
    }
}
