import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ShootingGameClient extends JPanel implements ActionListener, KeyListener {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientId;
    private String currentMapId; // 현재 맵 ID

    private javax.swing.Timer timer;

    private Map<String, Player> players = new ConcurrentHashMap<>();
    private List<Missile> missiles = new CopyOnWriteArrayList<>();
    private List<Obstacle> obstacles = new CopyOnWriteArrayList<>();

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
    private CardLayout cardLayout; // CardLayout 참조 변수 추가

    private boolean timerStarted = false;

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

        cardLayout = new CardLayout(); // CardLayout 초기화
        mainFrame.setLayout(cardLayout); // CardLayout을 JFrame에 설정
    }

    private void showMapSelection() {
        mapSelectionPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // 배경 이미지 설정
                Image background = new ImageIcon("images/map_selection_background.png").getImage();
                g.drawImage(background, 0, 0, getWidth(), getHeight(), this);
            }
        };

        mapSelectionPanel.setLayout(new BorderLayout());

        // 제목 레이블
        JLabel titleLabel = new JLabel("Select a Map", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 32));
        titleLabel.setForeground(Color.WHITE); // 텍스트 색상
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        mapSelectionPanel.add(titleLabel, BorderLayout.NORTH);

        // 미리보기와 선택 영역
        JPanel centerPanel = new JPanel();
        centerPanel.setOpaque(false); // 배경 투명
        centerPanel.setLayout(new BorderLayout());

        // 미리보기 레이블
        JLabel previewLabel = new JLabel("", JLabel.CENTER);
        previewLabel.setPreferredSize(new Dimension(300, 200));
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        previewLabel.setOpaque(true);
        previewLabel.setBackground(Color.BLACK);

        // 맵 선택 버튼 영역
        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false);
        buttonPanel.setLayout(new GridLayout(1, 3, 10, 10));

        String[] maps = {"Map1", "Map2", "Map3"};
        String[] mapPreviewPaths = {"images/map1_preview.png", "images/map2_preview.png", "images/map3_preview.png"};

        // 선택된 맵 상태 저장 변수 및 초기값 설정
        final String[] selectedMap = {"Map1"};
        JButton[] mapButtons = new JButton[maps.length]; // 맵 버튼 배열

        // 기본 미리보기 이미지 설정
        previewLabel.setIcon(new ImageIcon(mapPreviewPaths[0]));

        for (int i = 0; i < maps.length; i++) {
            String map = maps[i];
            String previewPath = mapPreviewPaths[i];

            JButton mapButton = new JButton(map);
            mapButton.setFont(new Font("Arial", Font.BOLD, 18));
            mapButton.setBackground(new Color(50, 150, 250)); // 기본 색상
            mapButton.setForeground(Color.WHITE);
            mapButton.setFocusPainted(false);
            mapButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            mapButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

            // 초기 상태: MAP1 버튼 강조
            if (map.equals("Map1")) {
                mapButton.setBackground(new Color(255, 100, 100)); // 선택된 상태 색상
                mapButton.setForeground(Color.BLACK); // 선택된 텍스트 색상
            }

            // 버튼 클릭 이벤트
            mapButton.addActionListener(e -> {
                // 선택된 맵 업데이트
                selectedMap[0] = map;

                // 미리보기 업데이트
                ImageIcon previewIcon = new ImageIcon(previewPath);
                previewLabel.setIcon(previewIcon);
                previewLabel.setText("");
                previewLabel.setBackground(null);

                // Start 버튼 활성화
                startButton.setEnabled(true);

                // 선택된 버튼 강조
                for (JButton btn : mapButtons) {
                    btn.setBackground(new Color(50, 150, 250)); // 기본 상태로 되돌림
                    btn.setForeground(Color.WHITE);
                }
                mapButton.setBackground(new Color(255, 100, 100)); // 선택된 버튼 강조 색상
                mapButton.setForeground(Color.BLACK); // 텍스트 색상 변경
            });

            mapButtons[i] = mapButton;
            buttonPanel.add(mapButton);
        }

        centerPanel.add(previewLabel, BorderLayout.CENTER);
        centerPanel.add(buttonPanel, BorderLayout.SOUTH);

        mapSelectionPanel.add(centerPanel, BorderLayout.CENTER);

        // Start 버튼
        startButton = new JButton("Start");
        startButton.setFont(new Font("Arial", Font.BOLD, 24));
        startButton.setBackground(new Color(50, 200, 100));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        startButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        startButton.setEnabled(true); // 초기 상태에서는 Map1이 기본값이므로 활성화

        startButton.addActionListener(e -> {
            if (selectedMap[0] != null) {
                out.println("MAPSELECT " + selectedMap[0]); // 선택된 맵 전달
                out.println("READY"); // 준비 상태 전달

                // 화면 전환: 맵 선택 -> 게임 화면
                cardLayout.show(mainFrame.getContentPane(), "GAME_SCREEN");
                inGame = true;

                // 게임 화면에 포커스 설정
                setFocusable(true);
                requestFocusInWindow();
            }
        });

        JPanel bottomPanel = new JPanel();
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        bottomPanel.add(startButton);

        mapSelectionPanel.add(bottomPanel, BorderLayout.SOUTH);

        // 패널 추가
        mainFrame.add(mapSelectionPanel, "MAP_SELECTION");
        mainFrame.add(this, "GAME_SCREEN");
        mainFrame.setVisible(true);
    }

    private Image loadImage(String path) {
        try {
            Image img = new ImageIcon(path).getImage();
            if (img == null) throw new IOException("Image not found: " + path);
            return img;
        } catch (Exception e) {
            System.err.println("Failed to load image: " + path);
            e.printStackTrace();
            return null;
        }
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
                    System.out.println("Background image set to: " + backgroundImagePath);
                    break;
                case "PLAYER_IMAGE":
                    playerImagePath = tokens[++i];
                    playerImage = loadImage(playerImagePath);
                    break;
                case "MISSILE_IMAGE":
                    String missileImagePath = tokens[++i];
                    missileImage = scaleImage(loadImage(missileImagePath), missileWidth, missileHeight);
                    System.out.println("Missile image set to: " + missileImagePath);
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

        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        } else {
            System.err.println("Background image is null.");
        }

        synchronized (players) {

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
            for (Player player : players.values()) {
                drawPlayer(g, player);
            }
        }

        synchronized (missiles) {
            // 미사일 그리기
            for (Missile missile : missiles) {
                drawMissile(g, missile);
            }
        }

        synchronized (obstacles) {
            // 장애물 그리기
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
        if (obstacle.getImage() == null) {
            System.err.println("Obstacle image is null for obstacle at: " + obstacle.getX() + ", " + obstacle.getY());
            return;
        }
        g.drawImage(obstacle.getImage(), obstacle.getX(), obstacle.getY(),
                obstacle.getWidth(), obstacle.getHeight(), this);
    }

    public void actionPerformed(ActionEvent e) {
        if (gameOver || !isGameStarted) return;

        if (clientId == null) {
            System.err.println("Error: clientId is null.");
            return;
        }

        Player player = players.get(clientId);
        if (player == null) {
            System.err.println("Error: Player object is null for clientId: " + clientId);
            return;
        }

        int dx = 0, dy = 0;

        // 방향키 입력에 따른 이동 계산
        if (keys[KeyEvent.VK_A]) dx -= 10;
        if (keys[KeyEvent.VK_D]) dx += 10;
        if (keys[KeyEvent.VK_W]) dy -= 10;
        if (keys[KeyEvent.VK_S]) dy += 10;

        int newX = player.getX() + dx;
        int newY = player.getY() + dy;

        // 화면 경계 조건
        if (newX < 0) newX = 0;
        if (newX + playerWidth > getWidth()) newX = getWidth() - playerWidth;

        // 세로 경계 조건: 플레이어는 화면 아래 절반에서만 움직일 수 있음
        int screenHalfHeight = getHeight() / 2;
        if (player.getId().equals(clientId)) {
            // 플레이어 1: 화면 아래쪽 절반에서만 이동 가능
            if (newY < screenHalfHeight) newY = screenHalfHeight;
            if (newY + playerHeight > getHeight()) newY = getHeight() - playerHeight;
        } else {
            // 상대 플레이어: 화면 위쪽 절반에서만 이동 가능
            if (newY < 0) newY = 0;
            if (newY + playerHeight > screenHalfHeight) newY = screenHalfHeight - playerHeight;
        }

        // 새로운 위치 설정 및 서버에 전송
        player.setPosition(newX, newY);
//        System.out.println("Sending MOVE: " + newX + ", " + newY); // 디버깅 출력
        out.println("MOVE " + newX + " " + newY);
        repaint();
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
                            break;
                        case "SETTINGS":
                            parseSettings(Arrays.copyOfRange(tokens, 1, tokens.length));
//                            updateScaledImages();
                            break;
                        case "GAMESTATE":
                            parseGameState(tokens);
                            break;
                        case "DEFEAT":
                            gameOver = true;
                            winner = "패배";
                            showCustomDialog("Game Over", "You have been defeated!", false);
                            resetClient(); // 클라이언트 초기화
                            break;

                        case "VICTORY":
                            gameOver = true;
                            winner = "승리";
                            showCustomDialog("Victory", "Congratulations! You won!", true);
                            resetClient(); // 클라이언트 초기화
                            break;
                        case "CONNECTED":
                            handleConnectedMessage(tokens);
                            break;
                        case "MAPFULL":
                            JOptionPane.showMessageDialog(mainFrame,
                                    "The selected map is full. Please choose another map.",
                                    "Map Full", JOptionPane.WARNING_MESSAGE);
                            resetClient(); // 초기화면으로 돌아가기
                            break;
                        default:
                            System.err.println("Unknown command: " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void resetClient() {
            inGame = false; // 게임 상태 초기화
            isGameStarted = false;
            gameOver = false;

            players.clear(); // 플레이어 정보 초기화
            missiles.clear(); // 미사일 정보 초기화
            obstacles.clear(); // 장애물 정보 초기화

            clientId = null; // 클라이언트 ID 초기화
            repaint(); // 화면 갱신

            // 초기 화면으로 전환
            SwingUtilities.invokeLater(() -> {
                cardLayout.show(mainFrame.getContentPane(), "MAP_SELECTION");
            });
        }

        private void handleConnectedMessage(String[] tokens) {
            if (tokens.length < 2) {
                System.err.println("Invalid CONNECTED message: " + Arrays.toString(tokens));
                return;
            }

            clientId = tokens[1];
            System.out.println("Connected with clientId: " + clientId);

            // 타이머를 처음으로 시작
            if (!timerStarted) {
                timer.start();
                timerStarted = true;
            }
        }
        private void showCustomDialog(String title, String message, boolean isVictory) {
            JDialog dialog = new JDialog(mainFrame, title, true);
            dialog.setSize(400, 400);
            dialog.setLocationRelativeTo(mainFrame);
            dialog.setLayout(new BorderLayout());

            // 상단 메시지 패널
            JPanel messagePanel = new JPanel();
            messagePanel.setBackground(Color.WHITE); // 배경 흰색
            messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
            messagePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            JLabel titleLabel = new JLabel(isVictory ? "Victory!" : "Defeat", JLabel.CENTER);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 32));
            titleLabel.setForeground(Color.BLACK); // 글씨 검은색

            JLabel messageLabel = new JLabel(message, JLabel.CENTER);
            messageLabel.setFont(new Font("Arial", Font.PLAIN, 18));
            messageLabel.setForeground(Color.BLACK); // 글씨 검은색

            messagePanel.add(titleLabel);
            messagePanel.add(Box.createVerticalStrut(10)); // 간격
            messagePanel.add(messageLabel);

            // 하단 배경 및 버튼 패널
            JPanel backgroundPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    // 승리와 패배에 따른 배경 이미지 설정
                    String bgImagePath = isVictory ? "images/victory_bg.png" : "images/defeat_bg.png";
                    Image bgImage = new ImageIcon(bgImagePath).getImage();
                    g.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
                }
            };
            backgroundPanel.setLayout(new BorderLayout());

            // 버튼 패널
            JPanel buttonPanel = new JPanel();
            buttonPanel.setOpaque(false); // 배경 투명
            JButton okButton = new JButton("OK");
            okButton.setFont(new Font("Arial", Font.BOLD, 18));
            okButton.setBackground(new Color(50, 200, 100)); // 녹색 버튼
            okButton.setForeground(Color.WHITE);
            okButton.setFocusPainted(false);
            okButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            okButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            okButton.addActionListener(e -> dialog.dispose());
            buttonPanel.add(okButton);

            backgroundPanel.add(buttonPanel, BorderLayout.SOUTH);

            // 다이얼로그 레이아웃에 추가
            dialog.add(messagePanel, BorderLayout.NORTH); // 상단에 메시지
            dialog.add(backgroundPanel, BorderLayout.CENTER); // 하단에 배경 및 버튼
            dialog.setUndecorated(true); // 기본 윈도우 테두리 제거
            dialog.setVisible(true);
        }

    }

    public void parseGameState(String[] tokens) {
        synchronized (players) {
            players.clear();
        }
        synchronized (missiles) {
            missiles.clear();
        }
        synchronized (obstacles) {
            obstacles.clear();
        }

        int i = 1;
        try {
            while (i < tokens.length) {
                if (tokens[i].equals("PLAYER")) {
                    String id = tokens[i + 1];
                    int x = Integer.parseInt(tokens[i + 2]);
                    int y = Integer.parseInt(tokens[i + 3]);
                    int health = Integer.parseInt(tokens[i + 4]);
                    String imagePath = tokens[i + 5];

                    synchronized (players) {
                        players.put(id, new Player(id, x, y, health, imagePath));
                    }
                    i += 6;
                } else if (tokens[i].equals("MISSILE")) {
                    String ownerId = tokens[i + 1];
                    int x = Integer.parseInt(tokens[i + 2]);
                    int y = Integer.parseInt(tokens[i + 3]);

                    synchronized (missiles) {
                        missiles.add(new Missile(ownerId, x, y));
                    }
                    i += 4;
                } else if (tokens[i].equals("OBSTACLE")) {
                    int x = Integer.parseInt(tokens[i + 1]);
                    int y = Integer.parseInt(tokens[i + 2]);
                    int width = Integer.parseInt(tokens[i + 3]);
                    int height = Integer.parseInt(tokens[i + 4]);
                    boolean movingRight = Boolean.parseBoolean(tokens[i + 5]);
                    String imagePath = tokens[i + 6]; // 이미지 경로 추가

                    synchronized (obstacles) {
                        Obstacle obstacle = new Obstacle(x, y, width, height, movingRight);
                        obstacle.setImagePath(imagePath); // 이미지 경로 설정
                        obstacles.add(obstacle);
                    }
                    i += 7;
                } else {
                    System.err.println("Unknown token type: " + tokens[i]);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing GAMESTATE: " + Arrays.toString(tokens));
            e.printStackTrace();
        }

        if (!timerStarted) {
            timer.start();
            timerStarted = true;
        }

        repaint();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Shooting Game Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        new ShootingGameClient("localhost", 12345); // 클라이언트 초기화
    }
}