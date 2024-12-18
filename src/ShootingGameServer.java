import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class ShootingGameServer {
    private static final int PORT = 12345;
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static final int PLAYER_WIDTH = 90;
    public static final int PLAYER_HEIGHT = 90;
    public static final int MISSILE_WIDTH = 10;
    public static final int MISSILE_HEIGHT = 30;
    public static final int SERVER_WIDTH = 500;
    public static final int SERVER_HEIGHT = 770;

    public static MapManager mapManager = new MapManager(); // 접근 제어자를 public으로 변경

    public static void main(String[] args) {
        System.out.println("Server is running...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // 스케줄러 시작
            scheduler.scheduleAtFixedRate(() -> {
                for (MapInstance map : mapManager.getMaps().values()) {
                    map.updateMissiles();
                    map.checkCollisions();
                    map.broadcastGameState();
                }
            }, 0, 50, TimeUnit.MILLISECONDS);

            // 클라이언트 연결 처리
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// 맵 관리 클래스
class MapManager {
    private Map<String, MapInstance> maps = new ConcurrentHashMap<>();

    public Map<String, MapInstance> getMaps() {
        return maps;
    }

    public MapInstance createMap(String mapId) {
        MapInstance map = new MapInstance(mapId);
        maps.put(mapId, map);
        return map;
    }

    public MapInstance assignPlayerToMap(Player player) {
        MapInstance map = maps.values().stream()
                .filter(m -> m.getPlayers().size() < 2)
                .findFirst()
                .orElseGet(() -> createMap("Map" + (maps.size() + 1)));
        map.addPlayer(player);
        return map;
    }
}

// 맵 클래스
class MapInstance {
    private static final int PLAYER_COUNT_TO_START = 2; // 게임 시작에 필요한 플레이어 수
    private String mapId;
    private Map<String, Player> players = new ConcurrentHashMap<>();
    private List<Missile> missiles = Collections.synchronizedList(new ArrayList<>());
    private ObstacleManager obstacleManager = new ObstacleManager(ShootingGameServer.SERVER_WIDTH, ShootingGameServer.SERVER_HEIGHT);

    private boolean gameStarted = false; // 게임 시작 여부

    public MapInstance(String mapId) {
        this.mapId = mapId;
    }

    public String getMapId() {
        return mapId;
    }

    public Map<String, Player> getPlayers() {
        return players;
    }

    public List<Missile> getMissiles() {
        return missiles;
    }

    public ObstacleManager getObstacleManager() {
        return obstacleManager;
    }

    public void addPlayer(Player player) {
        players.put(player.getId(), player);
        player.setAssignedMap(this);
        checkAndStartGame(); // 플레이어 추가 후 게임 시작 조건 확인
    }

    private void checkAndStartGame() {
        if (players.size() == PLAYER_COUNT_TO_START && !gameStarted) {
            gameStarted = true;
            broadcast("GAMESTART"); // 모든 플레이어에게 게임 시작 메시지 전송
            System.out.println("Game started in map: " + mapId);
        }
    }

    public void broadcast(String message) {
        // 모든 플레이어에게 메시지를 전송
        for (Player player : players.values()) {
            PrintWriter out = player.getOut();
            if (out != null) {
                out.println(message); // 메시지를 전송
            }
        }
    }

    public void updateMissiles() {
        List<Missile> toRemove = new ArrayList<>();
        synchronized (missiles) {
            for (Missile missile : missiles) {
                missile.setY(missile.getY() - 7); // 미사일 위로 이동
                if (missile.getY() < 0) {
                    toRemove.add(missile);
                }
            }
            missiles.removeAll(toRemove);
        }
    }

    public void checkCollisions() {
        int serverWidth = ShootingGameServer.SERVER_WIDTH;
        int serverHeight = ShootingGameServer.SERVER_HEIGHT;

        // 미사일과 플레이어 충돌 확인
        List<Missile> toRemoveMissiles = new ArrayList<>();
        synchronized (missiles) {
            for (Missile missile : missiles) {
                for (Player player : players.values()) {
                    // 자신이 쏜 미사일은 충돌 대상에서 제외
                    if (missile.getOwnerId().equals(player.getId())) {
                        continue;
                    }

                    int playerX = player.getX();
                    int playerY = player.getY();

                    // 미사일 소유자가 상대 플레이어의 경우, 대칭 좌표 계산
                    if (!missile.getOwnerId().equals(player.getId())) {
                        playerX = serverWidth - playerX - ShootingGameServer.PLAYER_WIDTH;
                        playerY = serverHeight - playerY - ShootingGameServer.PLAYER_HEIGHT;
                    }

                    // 충돌 판정
                    if (missile.getX() >= playerX && missile.getX() <= playerX + ShootingGameServer.PLAYER_WIDTH &&
                            missile.getY() >= playerY && missile.getY() <= playerY + ShootingGameServer.PLAYER_HEIGHT) {
                        player.reduceHealth(10); // 체력 감소
                        toRemoveMissiles.add(missile); // 충돌한 미사일 제거
                        checkGameOver(player); // 게임 종료 여부 확인
                    }
                }
            }
            missiles.removeAll(toRemoveMissiles); // 충돌한 미사일 제거
        }

        // 장애물 충돌 확인 (기존 코드 유지)
        List<Obstacle> toRemoveObstacles = new ArrayList<>();
        for (Obstacle obstacle : obstacleManager.getObstacles()) {
            for (Player player : players.values()) {
                if (player.getX() < obstacle.getX() + obstacle.getWidth() &&
                        player.getX() + ShootingGameServer.PLAYER_WIDTH > obstacle.getX() &&
                        player.getY() < obstacle.getY() + obstacle.getHeight() &&
                        player.getY() + ShootingGameServer.PLAYER_HEIGHT > obstacle.getY()) {
                    player.reduceHealth(10);
                    toRemoveObstacles.add(obstacle);
                    checkGameOver(player);
                }
            }
        }
        obstacleManager.getObstacles().removeAll(toRemoveObstacles);
    }

    private void checkGameOver(Player player) {
        if (player.getHealth() <= 0) {
            System.out.println(player.getId() + " defeated in map " + mapId);

            // 패배자 알림
            player.getOut().println("DEFEAT");

            // 승리자 알림
            players.values().stream()
                    .filter(p -> !p.getId().equals(player.getId()))
                    .forEach(winner -> winner.getOut().println("VICTORY"));
        }
    }

    public void broadcastGameState() {
        if (!gameStarted) return; // 게임 시작 전에는 상태를 전송하지 않음

        StringBuilder state = new StringBuilder("GAMESTATE ");
        for (Player player : players.values()) {
            state.append("PLAYER ").append(player.getId()).append(" ")
                    .append(player.getX()).append(" ")
                    .append(player.getY()).append(" ")
                    .append(player.getHealth()).append(" ");
        }
        synchronized (missiles) {
            for (Missile missile : missiles) {
                state.append("MISSILE ").append(missile.getOwnerId()).append(" ")
                        .append(missile.getX()).append(" ")
                        .append(missile.getY()).append(" ");
            }
        }
        for (Obstacle obstacle : obstacleManager.getObstacles()) {
            state.append("OBSTACLE ").append(obstacle.getX()).append(" ")
                    .append(obstacle.getY()).append(" ")
                    .append(obstacle.getWidth()).append(" ")
                    .append(obstacle.getHeight()).append(" ");
        }
        players.values().forEach(player -> player.getOut().println(state.toString()));
    }
}

class ClientHandler extends Thread {
    private static final int PLAYER_WIDTH = 90;
    private static final int PLAYER_HEIGHT = 90;
    private static final int MISSILE_WIDTH = 10;
    private static final int MISSILE_HEIGHT = 30;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Player player;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // 플레이어 생성
            player = new Player(UUID.randomUUID().toString(), 180, 600, 100);
            player.setOut(out);

            // 플레이어를 맵에 할당
            MapInstance map = ShootingGameServer.mapManager.assignPlayerToMap(player);

            // 클라이언트 연결 로그 출력
            System.out.println("Client connected: " + player.getId() +
                    " assigned to map: " + map.getMapId());

            // 클라이언트에 연결 메시지 전송
            out.println("CONNECTED " + player.getId() + " " + map.getMapId());
            sendInitialSettings(out);

            // 메시지 수신 및 처리
            String message;
            while ((message = in.readLine()) != null) {
                String[] tokens = message.split(" ");
                switch (tokens[0]) {
                    case "MOVE":
                        handleMove(map, Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
                        break;
                    case "MISSILE":
                        handleMissile(map, Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
                        break;
                    default:
                        System.err.println("Unknown command: " + message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 클라이언트 종료 시 로그 출력
            System.out.println("Client disconnected: " + player.getId());
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ClientHandler 클래스 내부
    private void sendInitialSettings(PrintWriter out) {
        out.println("SETTINGS PLAYER_WIDTH " + ShootingGameServer.PLAYER_WIDTH +
                " PLAYER_HEIGHT " + ShootingGameServer.PLAYER_HEIGHT +
                " MISSILE_WIDTH " + ShootingGameServer.MISSILE_WIDTH +
                " MISSILE_HEIGHT " + ShootingGameServer.MISSILE_HEIGHT +
                " BACKGROUND_IMAGE images/back2.png" +  // 배경 이미지 경로
                " PLAYER1_IMAGE images/spaceship5.png" +  // 플레이어 1 이미지 경로
                " PLAYER2_IMAGE images/spaceship6.png" +  // 플레이어 2 이미지 경로
                " MISSILE_IMAGE images/missile.png");     // 미사일 이미지 경로
    }


    private void handleMove(MapInstance map, int x, int y) {
        player.setPosition(x, y);
        map.broadcastGameState();
    }

    private void handleMissile(MapInstance map, int x, int y) {
        map.getMissiles().add(new Missile(player.getId(), x, y));
        map.broadcastGameState();
    }
}