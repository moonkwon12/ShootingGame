import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class ShootingGameServer {
    private static final int PORT = 12345;
    private static ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<>();
    private static List<Missile> missiles = Collections.synchronizedList(new ArrayList<>());
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        System.out.println("Server is running...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // 스케줄러 시작
            scheduler.scheduleAtFixedRate(() -> {
                updateMissiles();
                checkCollisions();
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
                missile.setY(missile.getY() - 5); // 미사일을 위로 이동
                if (missile.getY() < 0) {
                    toRemove.add(missile); // 화면 밖으로 나간 미사일 제거
                }
            }
            missiles.removeAll(toRemove); // 제거된 미사일 삭제
        }
    }

    private static void checkCollisions() {
        List<Missile> toRemove = new ArrayList<>();
        synchronized (missiles) {
            for (Missile missile : missiles) {
                for (Player player : players.values()) {
                    if (!missile.getOwnerId().equals(player.getId())) { // 적 미사일만 확인
                        if (missile.getX() >= player.getX() && missile.getX() <= player.getX() + 50 &&
                                missile.getY() >= player.getY() && missile.getY() <= player.getY() + 50) {
                            player.reduceHealth(10); // 충돌 시 체력 감소
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
                            toRemove.add(missile); // 충돌한 미사일 제거
                            break;
                        }
                    }
                }
            }
            missiles.removeAll(toRemove); // 충돌한 미사일 제거
        }
        sendGameStateToAll(); // 게임 상태 업데이트
    }

    private static void sendGameStateToAll() {
        StringBuilder state = new StringBuilder("GAMESTATE ");
        synchronized (players) {
            for (Player player : players.values()) {
                state.append("PLAYER ")
                        .append(player.getId()).append(" ")
                        .append(player.getX()).append(" ")
                        .append(player.getY()).append(" ")
                        .append(player.getHealth()).append(" ")
                        .append(player.getImagePath()).append(" ")
                        .append("images/back2.png "); // backgroundImage 추가
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

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                synchronized (players) {
                    clientId = players.isEmpty() ? "Player1" : "Player2";
                    String playerImage = clientId.equals("Player1") ? "images/spaceship5.png" : "images/spaceship6.png";
                    String backgroundImage = "images/back2.png";
                    Player player = new Player(clientId, 180, clientId.equals("Player1") ? 700 : 100, 100, playerImage);
                    player.setOut(out); // PrintWriter 설정
                    players.put(clientId, player);
                }

                out.println("CONNECTED " + clientId);
                sendGameState();

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
                players.remove(clientId);
                System.out.println(clientId + " disconnected.");
                sendGameState(); // 연결 종료 후 상태 업데이트
            }
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

        private void sendGameState() {
            StringBuilder state = new StringBuilder("GAMESTATE ");
            synchronized (players) {
                for (Player player : players.values()) {
                    state.append("PLAYER ")
                            .append(player.getId()).append(" ")
                            .append(player.getX()).append(" ")
                            .append(player.getY()).append(" ")
                            .append(player.getHealth()).append(" ")
                            .append(player.getImagePath()).append(" ")
                            .append("images/back2.png "); // backgroundImagePath 추가
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

    private static class Player {
        private String id;
        private int x, y, health;
        private String imagePath;
        private PrintWriter out;

        public Player(String id, int x, int y, int health, String imagePath) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.health = health;
            this.imagePath = imagePath;
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

        public PrintWriter getOut() {
            return out;
        }

        public void setOut(PrintWriter out) {
            this.out = out;
        }

        public void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }

        // 추가: 체력을 감소시키는 메서드
        public void reduceHealth(int damage) {
            this.health -= damage;
            if (this.health < 0) {
                this.health = 0; // 체력은 0 이하로 내려가지 않도록 처리
            }
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

        public void setY(int y) {
            this.y = y;
        }
    }
}
