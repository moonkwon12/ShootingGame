import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class ShootingGameServer {
    private static final int PORT = 12345;

    private static ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<>();
    private static List<Missile> missiles = Collections.synchronizedList(new ArrayList<>());

    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final int serverWidth = 500; // 맵의 가로 크기
    private static final int serverHeight = 770; // 맵의 세로 크기

    private static final int PLAYER_WIDTH = 90;  // 플레이어 가로 크기
    private static final int PLAYER_HEIGHT = 90; // 플레이어 세로 크기
    private static final int MISSILE_WIDTH = 10; // 미사일 가로 크기
    private static final int MISSILE_HEIGHT = 30; // 미사일 세로 크기

    private static ObstacleManager obstacleManager;

    public static void main(String[] args) {
        System.out.println("Server is running...");

        obstacleManager = new ObstacleManager(serverWidth, serverHeight);


        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            // 스케줄러 시작
            scheduler.scheduleAtFixedRate(() -> {
                updateMissiles();
                checkMissileCollisions(); // 미사일 충돌 처리
                checkObstacleCollisions(); // 장애물 충돌 처리
                sendGameStateToAll();
            }, 0, 50, TimeUnit.MILLISECONDS); // 50ms마다 실행

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void updateMissiles() {
        List<Missile> toRemove = new ArrayList<>();
        synchronized (missiles) {
            for (Missile missile : missiles) {
                missile.setY(missile.getY() - 7); // 미사일을 위로 이동
                if (missile.getY() < 0) {
                    toRemove.add(missile); // 화면 밖으로 나간 미사일 제거
                }
            }
            missiles.removeAll(toRemove); // 제거된 미사일 삭제
        }
    }

    private static void checkObstacleCollisions() {
        List<Obstacle> obstacles = obstacleManager.getObstacles();
        for (Obstacle obstacle : obstacles) {
            for (Player player : players.values()) {
                // 플레이어와 장애물의 충돌 확인
                int playerX = player.getX();
                int playerY = player.getY();

                // 상대 플레이어의 경우 대칭된 좌표 계산
                if (!"Player1".equals(player.getId())) { // Player1 기준 대칭
                    playerX = serverWidth - playerX - PLAYER_WIDTH;
                    playerY = serverHeight - playerY - PLAYER_HEIGHT;
                }

                if (playerX < obstacle.getX() + obstacle.getWidth() &&
                        playerX + PLAYER_WIDTH > obstacle.getX() &&
                        playerY < obstacle.getY() + obstacle.getHeight() &&
                        playerY + PLAYER_HEIGHT > obstacle.getY()) {

                    player.reduceHealth(10); // 충돌 시 체력 감소
                    checkGameOver(player); // 게임 오버 처리
                    obstacleManager.getObstacles().remove(obstacle); // 충돌한 장애물 제거
                    break;
                }
            }
        }
    }


    // 미사일 판정 로직
    private static void checkMissileCollisions() {
        List<Missile> toRemove = new ArrayList<>();
        synchronized (missiles) {
            for (Missile missile : missiles) {
                for (Player player : players.values()) {
                    if (!missile.getOwnerId().equals(player.getId())) {
                        // 미사일 충돌 여부 확인
                        int mirroredX = serverWidth - player.getX() - PLAYER_WIDTH;
                        int mirroredY = serverHeight - player.getY() - PLAYER_HEIGHT;

                        if (missile.getX() >= mirroredX && missile.getX() <= mirroredX + PLAYER_WIDTH &&
                                missile.getY() >= mirroredY && missile.getY() <= mirroredY + PLAYER_HEIGHT) {

                            player.reduceHealth(10); // 충돌 시 체력 감소
                            checkGameOver(player); // 체력 확인 및 승패 처리
                            toRemove.add(missile); // 충돌한 미사일 제거
                            break;
                        }
                    }
                }
            }
            missiles.removeAll(toRemove);
        }
    }

    //승패 판정 로직
    private static void checkGameOver(Player player) {
        if (player.getHealth() <= 0) {
            System.out.println(player.getId() + " is defeated!");

            // 패배한 플레이어에게 DEFEAT 메시지 전송
            PrintWriter playerOut = player.getOut();
            if (playerOut != null) {
                playerOut.println("DEFEAT");
            }

            // 승리한 플레이어에게 VICTORY 메시지 전송
            String winnerId = player.getId().equals("Player1") ? "Player2" : "Player1";
            Player winner = players.get(winnerId);
            if (winner != null && winner.getOut() != null) {
                winner.getOut().println("VICTORY");
            }
        }
    }

    private static void sendGameStateToAll() {
        StringBuilder state = new StringBuilder("GAMESTATE ");

        synchronized (players) {
            for (Player player : players.values()) {
                state.append("PLAYER ")
                        .append(player.getId()).append(" ")
                        .append(player.getX()).append(" ")
                        .append(player.getY()).append(" ")
                        .append(player.getHealth()).append(" "); // backgroundImage 추가
            }
        }

        synchronized (missiles) {
            for (Missile missile : missiles) {
                state.append("MISSILE ")
                        .append(missile.getOwnerId()).append(" ")
                        .append(missile.getX()).append(" ")
                        .append(missile.getY()).append(" ");
            }
        }

        // 장애물 데이터 추가
        for (Obstacle obstacle : obstacleManager.getObstacles()) {
            state.append("OBSTACLE ")
                    .append(obstacle.getX()).append(" ")
                    .append(obstacle.getY()).append(" ")
                    .append(obstacle.getWidth()).append(" ")
                    .append(obstacle.getHeight()).append(" ")
                    .append(obstacle.isMovingRight()).append(" ");
        }

        broadcast(state.toString());
    }

    private static void broadcast(String message) {
        synchronized (players) {
            for (Player player : players.values()) {
                PrintWriter playerOut = player.getOut();
                if (playerOut != null) {
                    playerOut.println(message); // 클라이언트로 메시지 전송
                }
            }
        }
    }



    public static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientId;

        private static boolean obstaclesStarted = false; // 장애물 시작 여부

        private static Map<String, Boolean> restartStatus = new ConcurrentHashMap<>();

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                sendInitialSettings(out);

                synchronized (players) {
                    clientId = players.isEmpty() ? "Player1" : "Player2";
                    Player player = new Player(clientId, 180,600, 100);
                    player.setOut(out); // PrintWriter 설정
                    players.put(clientId, player);
                }

                out.println("CONNECTED " + clientId);
                sendGameState();

                // 두 명이 접속되면 게임 자동 시작
                if (players.size() == 2) {
                    if (!obstaclesStarted) {
                        obstaclesStarted = true;
                        obstacleManager.startSpawnThread(); // 장애물 생성 스레드 시작
                        obstacleManager.startMoveThread(); // 장애물 움직임 스레드 시작
                    }
                    broadcast("GAMESTART"); // 클라이언트들에게 게임 시작 알림
                }

                String message;
                while ((message = in.readLine()) != null) {
                    String[] tokens = message.split(" ");
                    switch (tokens[0]) {
                        case "MOVE":
                            handleMove(clientId, Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
                            break;
                        case "MISSILE":
                            handleMissile(clientId, Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
                            break;
                        case "DAMAGE":
                            handleDamage(clientId, Integer.parseInt(tokens[1]));
                            break;
                        case "RESTART":
                            handleReset(clientId);
                            break;
                        default:
                            System.out.println("Unknown command: " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // 클라이언트의 플레이어와 미사일 정보 삭제
                synchronized (players) {
                    System.out.println(clientId + " disconnected. Removing player and their missiles.");
                    players.remove(clientId);

                    // 클라이언트 소유 미사일 삭제
                    synchronized (missiles) {
                        missiles.removeIf(missile -> missile.getOwnerId().equals(clientId));
                    }
                }
                System.out.println(clientId + " disconnected.");
                sendGameState(); // 연결 종료 후 상태 업데이트
            }
        }

        private void sendInitialSettings(PrintWriter out) {
            out.println("SETTINGS PLAYER_WIDTH " + PLAYER_WIDTH +
                    " PLAYER_HEIGHT " + PLAYER_HEIGHT +
                    " MISSILE_WIDTH " + MISSILE_WIDTH +
                    " MISSILE_HEIGHT " + MISSILE_HEIGHT +
                    " BACKGROUND_IMAGE " + "images/back2.png" +
                    " PLAYER1_IMAGE " + "images/spaceship5.png" +
                    " PLAYER2_IMAGE " + "images/spaceship6.png");
        }

        private void handleMove(String clientId, int x, int y) {
            Player player = players.get(clientId);
            if (player != null) {
                player.setPosition(x, y);
                sendGameState();
            }
        }

        private void handleMissile(String clientId, int x, int y) {
            // 현재 클라이언트의 미사일만 추가
            missiles.add(new Missile(clientId, x, y));
            sendGameState();
        }

        private void handleDamage(String clientId, int damage) {
            System.out.println("handleDamage called with clientId: " + clientId + ", damage: " + damage); // 디버깅 로그
            Player opponent = players.get(clientId.equals("Player1") ? "Player2" : "Player1");

            if (opponent == null) {
                System.err.println("Opponent not found for clientId: " + clientId); // 디버깅 로그
                return;
            }

            opponent.reduceHealth(damage);
            System.out.println("Opponent's health after damage: " + opponent.getHealth()); // 디버깅 로그

            if (opponent.getHealth() <= 0) {
                System.out.println(opponent.getId() + " is defeated!");

                // 패배 메시지 전송
                PrintWriter opponentOut = opponent.getOut();
                if (opponentOut != null) {
                    opponentOut.println("DEFEAT");
                    System.out.println("DEFEAT message sent to " + opponent.getId()); // 디버깅 메시지
                } else {
                    System.err.println("Opponent's PrintWriter is null");
                }
            }

            sendGameState(); // 게임 상태 업데이트
        }

        // RESET 처리 메서드 추가
        private void handleReset(String clientId) {
            // 해당 클라이언트의 재시작 요청 상태 업데이트
            restartStatus.put(clientId, true);

            System.out.println(restartStatus.values().stream().allMatch(Boolean::booleanValue));
            // 두 클라이언트가 모두 준비되었는지 확인
            if (restartStatus.size() == players.size() && restartStatus.values().stream().allMatch(Boolean::booleanValue)) {
                // 모든 클라이언트가 재시작 요청한 경우
                restartStatus.clear(); // 상태 초기화

                synchronized (players) {
                    for (Player player : players.values()) {
                        player.setPosition(180, 600);
                        player.setHealth(100); // 체력 초기화
                    }
                }

                synchronized (missiles) {
                    missiles.clear(); // 미사일 정보 삭제
                }

                obstacleManager.resetObstacles(); // 장애물 초기화 (필요하면 추가)
                broadcast("GAMESTART"); // 모든 클라이언트에 게임 시작 메시지 전송
            } else {
                // 다른 클라이언트를 대기 중임을 알림
                broadcast("READY_TO_RESTART");
            }
        }

        private void sendGameState() {
            StringBuilder state = new StringBuilder("GAMESTATE ");
            synchronized (players) {
                for (Player player : players.values()) {
                    state.append("PLAYER ")
                            .append(player.getId()).append(" ")
                            .append(player.getX()).append(" ")
                            .append(player.getY()).append(" ")
                            .append(player.getHealth()).append(" "); // backgroundImagePath 추가
                }
            }
            synchronized (missiles) {
                for (Missile missile : missiles) {
                    state.append("MISSILE ")
                            .append(missile.getOwnerId()).append(" ")
                            .append(missile.getX()).append(" ")
                            .append(missile.getY()).append(" ");
                }
            }
            ShootingGameServer.broadcast(state.toString());
        }
    }
}
