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

    private Image backgroundImage, playerImage, missileImage;

    private String backgroundImagePath;
    private String playerImagePath;

    private int playerWidth = ShootingGameServer.PLAYER_WIDTH; // 기본값 설정
    private int playerHeight = ShootingGameServer.PLAYER_HEIGHT;
    private int missileWidth = ShootingGameServer.MISSILE_WIDTH;
    private int missileHeight = ShootingGameServer.MISSILE_HEIGHT;

    private boolean isReady = false;
    private boolean isGameStarted = false;

    private JFrame mainFrame; // 초기 화면 프레임
    private JPanel mapSelectionPanel; // 맵 선택 패널
    private JButton startButton;
    private JComboBox<String> mapSelector; // 맵 선택 드롭다운
    private boolean inGame = false; // 게임 중인지 여부

    public ShootingGameClient(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 서버 리스너 시작
            new Thread(new ServerListener()).start();

            // 메인 프레임 생성
            createMainFrame();

            // 초기 맵 선택 화면 표시
            showMapSelection();

            // 키 리스너 등록 및 포커스 설정
            setFocusable(true);
            addKeyListener(this); // KeyListener 등록
            requestFocusInWindow(); // 이 패널에 포커스 설정

            // 타이머 시작
            timer = new javax.swing.Timer(16, this); // 약 60FPS
            timer.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createMainFrame() {
        mainFrame = new JFrame("Shooting Game Client");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(500, 770);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setResizable(false);
        mainFrame.setLayout(new CardLayout()); // 카드 레이아웃 사용
    }

    private void showMapSelection() {
        mapSelectionPanel = new JPanel();
        mapSelectionPanel.setLayout(new GridLayout(3, 1));

        JLabel mapLabel = new JLabel("Select a Map:", JLabel.CENTER);
        mapSelector = new JComboBox<>(new String[]{"Map1", "Map2", "Map3"});
        startButton = new JButton("Start");

        // 시작 버튼 클릭 이벤트
        startButton.addActionListener(e -> {
            String selectedMap = (String) mapSelector.getSelectedItem();
            out.println("MAPSELECT " + selectedMap); // 서버로 선택한 맵 전달
            inGame = true;

            // 화면 전환: 맵 선택 -> 게임 화면
            CardLayout cl = (CardLayout) mainFrame.getContentPane().getLayout();
            cl.next(mainFrame.getContentPane());

            setFocusable(true);
            requestFocusInWindow();
        });

        mapSelectionPanel.add(mapLabel);
        mapSelectionPanel.add(mapSelector);
        mapSelectionPanel.add(startButton);

        mainFrame.add(mapSelectionPanel, "MAP_SELECTION");
        mainFrame.add(this, "GAME_SCREEN");
        mainFrame.setVisible(true);
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
                case "PLAYER_IMAGE":
                    playerImagePath = tokens[++i];
                    playerImage = loadImage(playerImagePath);
                    break;
                case "MISSILE_IMAGE":
                    missileImage = scaleImage(loadImage(tokens[++i]), missileWidth, missileHeight);
                    break;
            }
        }
        repaint();
    }

    private void updateScaledImages() {
        System.out.println("Scaling missile image: Width = " + missileWidth + ", Height = " + missileHeight);
        if (missileWidth > 0 && missileHeight > 0) {
            missileImage = scaleImage(loadImage("images/missile.png"), missileWidth, missileHeight);
        } else {
            System.err.println("Invalid missile dimensions for scaling.");
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (!inGame) return; // 게임 시작 전에는 그리지 않음

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
        Image image = loadImage(player.getImagePath()); // 이미지 로드

        Graphics2D g2d = (Graphics2D) g.create();

        if (!player.getId().equals(clientId)) {
            // 상대 플레이어 이미지 180도 회전
            g2d.rotate(Math.toRadians(180), x + playerWidth / 2.0, y + playerHeight / 2.0);
        }

        // 플레이어 이미지 그리기
        g2d.drawImage(image, x, y, playerWidth, playerHeight, this);
        g2d.dispose(); // Graphics2D 리소스 정리

        // 체력 바 그리기
        int healthBarX = x;
        int healthBarY = y - 15; // 체력 바는 플레이어 위에 표시
        int healthBarWidth = (int) ((player.getHealth() / 100.0) * playerWidth);

        g.setColor(Color.RED); // 체력 바 배경
        g.fillRect(healthBarX, healthBarY, playerWidth, 5);

        g.setColor(Color.GREEN); // 체력 바 현재 상태
        g.fillRect(healthBarX, healthBarY, healthBarWidth, 5);

        // 체력 숫자 표시
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.setColor(Color.WHITE);
        g.drawString("HP: " + player.getHealth(), healthBarX + 5, healthBarY - 2);
    }

    private void drawMissile(Graphics g, Missile missile) {
        int x = missile.getX();
        int y = missile.getY();

        Graphics2D g2d = (Graphics2D) g.create();

        // 상대 플레이어의 미사일 대칭 처리
        if (!missile.getOwnerId().equals(clientId)) {
            g2d.translate(getWidth(), getHeight()); // 화면 크기만큼 이동
            g2d.scale(-1, -1); // X축, Y축 대칭
            x = getWidth() - x - missileWidth; // 대칭된 좌표로 변환
            y = getHeight() - y - missileHeight; // 대칭된 좌표로 변환
        }

        // 미사일 이미지 그리기
        g2d.drawImage(missileImage, x, y, missileWidth, missileHeight, this);
        g2d.dispose(); // 리소스 정리
    }


    private void drawObstacle(Graphics g, Obstacle obstacle) {
        g.drawImage(obstacle.getImage(), obstacle.getX(), obstacle.getY(),
                obstacle.getWidth(), obstacle.getHeight(), this);
    }


    public void actionPerformed(ActionEvent e) {
        if (gameOver || !isGameStarted) return; // 게임이 시작되지 않았거나 종료되었으면 움직임 제한

        int dx = 0, dy = 0;

        // 방향키 입력에 따른 이동 계산
        if (keys[KeyEvent.VK_A]) dx -= 5; // 왼쪽
        if (keys[KeyEvent.VK_D]) dx += 5; // 오른쪽
        if (keys[KeyEvent.VK_W]) dy -= 5; // 위쪽
        if (keys[KeyEvent.VK_S]) dy += 5; // 아래쪽

        Player player = players.get(clientId);
        if (player != null) {
            int newX = player.getX() + dx;
            int newY = player.getY() + dy;

            // 화면 경계 조건
            if (newX < 0) newX = 0; // 왼쪽 경계
            if (newX + playerWidth > getWidth()) newX = getWidth() - playerWidth; // 오른쪽 경계
            if (newY + playerHeight > getHeight()) newY = getHeight() - playerHeight; // 아래쪽 경계

            // 화면 세로 절반 위로 이동 제한
            int halfHeight = getHeight() / 2;
            if (newY < halfHeight) newY = halfHeight; // 중앙선 위로 이동 제한

            // 새로운 위치 설정 및 서버에 전송
            player.setPosition(newX, newY);
            out.println("MOVE " + newX + " " + newY);
            repaint();
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
//                    System.out.println("Received message: " + message); // 디버그용 출력
                    String[] tokens = message.split(" ");
                    switch (tokens[0]) {
                        case "GAMESTART":
                            isGameStarted = true;
                            repaint();
                            break;
                        case "WAITING":
                            JOptionPane.showMessageDialog(mainFrame, "Waiting for another player...");
                            break;
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
        try {
            while (i < tokens.length) {
                if (tokens[i].equals("PLAYER")) {
                    String id = tokens[i + 1];
                    int x = Integer.parseInt(tokens[i + 2]);
                    int y = Integer.parseInt(tokens[i + 3]);
                    int health = Integer.parseInt(tokens[i + 4]);
                    String imagePath = tokens[i + 5];

                    players.put(id, new Player(id, x, y, health, imagePath));
                    i += 6;
                } else if (tokens[i].equals("MISSILE")) {
                    String ownerId = tokens[i + 1];
                    int x = Integer.parseInt(tokens[i + 2]);
                    int y = Integer.parseInt(tokens[i + 3]);

                    missiles.add(new Missile(ownerId, x, y));
                    i += 4;
                } else if (tokens[i].equals("OBSTACLE")) {
                    int x = Integer.parseInt(tokens[i + 1]);
                    int y = Integer.parseInt(tokens[i + 2]);
                    int width = Integer.parseInt(tokens[i + 3]);
                    int height = Integer.parseInt(tokens[i + 4]);
                    boolean movingRight = Boolean.parseBoolean(tokens[i + 5]);

                    obstacles.add(new Obstacle(x, y, width, height, movingRight));
                    i += 6;
                } else {
                    System.err.println("Unknown token type: " + tokens[i]);
                    break;
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            System.err.println("Error parsing GAMESTATE: " + Arrays.toString(tokens));
            e.printStackTrace();
        }

        repaint();
    }



    public static void main(String[] args) {
        JFrame frame = new JFrame("Shooting Game Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        new ShootingGameClient("localhost", 12345); // 클라이언트 초기화
//        frame.add(client);
//        frame.pack();
//        frame.setLocationRelativeTo(null);
//        frame.setResizable(false);
//        frame.setVisible(true);
//        client.requestFocusInWindow();
    }
}