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

    public MapInstance assignPlayerToMap(Player player, String mapId) {
        if (mapId == null || player == null) {
            throw new IllegalArgumentException("Map ID and Player cannot be null");
        }

        // 맵이 없으면 새로 생성
        MapInstance map = maps.computeIfAbsent(mapId, id -> {
            System.out.println("Creating new map with ID: " + id);
            return new MapInstance(id);
        });

        // 기존에 동일한 ID의 플레이어가 있으면 삭제
        map.getPlayers().remove(player.getId());

        synchronized (map) {
            if (!map.canAddPlayer()) {
                throw new IllegalStateException("Map is full: " + mapId);
            }

            map.addPlayer(player);
        }

        return map;
    }
}

// 맵 클래스
class MapInstance {
    private static final int MAX_PLAYERS = 2; // 맵당 최대 플레이어 수
    private String mapId;
    private String backgroundImagePath; // 맵 배경 이미지 경로
    private String missileImagePath; // 맵에 따른 미사일 이미지 경로
    private Map<String, Player> players = new ConcurrentHashMap<>();
    private List<Missile> missiles = Collections.synchronizedList(new ArrayList<>());
    private ObstacleManager obstacleManager;

    private boolean gameStarted = false; // 게임 시작 여부

    public MapInstance(String mapId) {
        this.mapId = mapId;

        // 맵 ID에 따라 배경 이미지 설정
        switch (mapId) {
            case "Map1":
                this.backgroundImagePath = "images/back1.png";
                this.missileImagePath = "images/saliva.png"; // 사막 테마 미사일
                break;
            case "Map2":
                this.backgroundImagePath = "images/back2.png";
                this.missileImagePath = "images/missile.png"; // 우주 테마 미사일
                break;
            case "Map3":
                this.backgroundImagePath = "images/back3.png";
                this.missileImagePath = "images/ink.png"; // 바다 테마 미사일
                break;
            default:
                this.backgroundImagePath = "images/back1.png"; // 기본 맵 설정
                this.missileImagePath = "images/saliva.png"; // 사막 테마 미사일
                System.err.println("Invalid mapId: " + mapId + ", defaulting to back1.png");
        }

        this.obstacleManager = new ObstacleManager(ShootingGameServer.SERVER_WIDTH, ShootingGameServer.SERVER_HEIGHT);
    }

    public void startGame() {
        if (gameStarted) return; // 이미 시작된 경우 무시

        gameStarted = true;
        startObstacleThreads();
        broadcast("GAMESTART");
        System.out.println("Game started in map: " + mapId);
    }

    public String getMissileImagePath() {
        return missileImagePath;
    }

    public String getBackgroundImagePath() {
        return backgroundImagePath;
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

    public synchronized boolean canAddPlayer() {
        return players.size() < MAX_PLAYERS; // 플레이어 수가 제한 이하인지 확인
    }

    public synchronized void addPlayer(Player player) {
        if (players.size() >= MAX_PLAYERS) {
            throw new IllegalStateException("Cannot add more players to the map: " + mapId);
        }
        players.put(player.getId(), player);
        player.setAssignedMap(this);

        if (!gameStarted) {
            checkAndStartGame(); // 게임 시작 여부를 확인
        }
    }

    private void checkAndStartGame() {
        if (players.size() == 2 && !gameStarted) { // 플레이어가 2명이고 게임이 시작되지 않은 경우
            gameStarted = true;
            startObstacleThreads();
            broadcast("GAMESTART");
            System.out.println("Game started in map: " + mapId);
        }
    }

    private void startObstacleThreads() {
        obstacleManager.startSpawnThread();
        obstacleManager.startMoveThread();
        System.out.println("Obstacle threads started for map: " + mapId);
    }

    public boolean isGameStarted() {
        return gameStarted;
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

                    // 상대 플레이어의 좌표 대칭 처리
                    if (!missile.getOwnerId().equals(player.getId())) {
                        playerX = ShootingGameServer.SERVER_WIDTH - playerX - ShootingGameServer.PLAYER_WIDTH;
                        playerY = ShootingGameServer.SERVER_HEIGHT - playerY - ShootingGameServer.PLAYER_HEIGHT;
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

        // 장애물과 플레이어 충돌 확인
        List<Obstacle> toRemoveObstacles = new ArrayList<>();
        for (Obstacle obstacle : obstacleManager.getObstacles()) {
            for (Player player : players.values()) {
                int obstacleX = obstacle.getX();
                int obstacleY = obstacle.getY();

                // 플레이어 2의 경우 장애물 대칭 좌표를 사용
                if (!player.getId().equals(players.keySet().iterator().next())) {
                    obstacleX = ShootingGameServer.SERVER_WIDTH - obstacleX - obstacle.getWidth();
                    obstacleY = ShootingGameServer.SERVER_HEIGHT - obstacleY - obstacle.getHeight();
                }

                // 충돌 판정
                if (player.getX() < obstacleX + obstacle.getWidth() &&
                        player.getX() + ShootingGameServer.PLAYER_WIDTH > obstacleX &&
                        player.getY() < obstacleY + obstacle.getHeight() &&
                        player.getY() + ShootingGameServer.PLAYER_HEIGHT > obstacleY) {
                    player.reduceHealth(10); // 체력 감소
                    toRemoveObstacles.add(obstacle); // 충돌한 장애물 제거
                    checkGameOver(player); // 게임 종료 여부 확인
                }
            }
        }
        obstacleManager.getObstacles().removeAll(toRemoveObstacles);
    }

    public void checkGameOver(Player player) {
        if (player.getHealth() <= 0) {
            System.out.println(player.getId() + " defeated in map " + mapId);
            player.getOut().println("DEFEAT");

            players.values().stream()
                    .filter(p -> !p.getId().equals(player.getId()))
                    .forEach(winner -> winner.getOut().println("VICTORY"));

            // 맵 객체 삭제
            ShootingGameServer.mapManager.getMaps().remove(mapId);
            System.out.println("Map " + mapId + " has been deleted.");
        }
    }

    public void broadcastGameState() {
        if (!gameStarted) return;

        for (Player player : players.values()) {
            StringBuilder state = new StringBuilder("GAMESTATE ");

            // 플레이어 데이터
            for (Player otherPlayer : players.values()) {
                int x = otherPlayer.getX();
                int y = otherPlayer.getY();

                if (!player.getId().equals(otherPlayer.getId())) {
                    x = ShootingGameServer.SERVER_WIDTH - x - ShootingGameServer.PLAYER_WIDTH;
                    y = ShootingGameServer.SERVER_HEIGHT - y - ShootingGameServer.PLAYER_HEIGHT;
                }

                state.append("PLAYER ").append(otherPlayer.getId()).append(" ")
                        .append(x).append(" ").append(y).append(" ")
                        .append(otherPlayer.getHealth()).append(" ")
                        .append(otherPlayer.getImagePath()).append(" ");
            }

            // 미사일 데이터
            synchronized (missiles) {
                for (Missile missile : missiles) {
                    int x = missile.getX();
                    int y = missile.getY();

                    // 미사일 좌표 대칭
                    if (!missile.getOwnerId().equals(player.getId())) {
                        x = ShootingGameServer.SERVER_WIDTH - x - ShootingGameServer.MISSILE_WIDTH;
                        y = ShootingGameServer.SERVER_HEIGHT - y - ShootingGameServer.MISSILE_HEIGHT;
                    }

                    state.append("MISSILE ").append(missile.getOwnerId()).append(" ")
                            .append(x).append(" ").append(y).append(" ");
                }
            }

            // 장애물 데이터
            for (Obstacle obstacle : obstacleManager.getObstacles()) {
                int x = obstacle.getX();
                int y = obstacle.getY();

                // 현재 플레이어 기준으로 장애물 대칭
                if (!player.getId().equals(players.keySet().iterator().next())) {
                    x = ShootingGameServer.SERVER_WIDTH - x - obstacle.getWidth();
                    y = ShootingGameServer.SERVER_HEIGHT - y - obstacle.getHeight();
                }

                state.append("OBSTACLE ").append(x).append(" ")
                        .append(y).append(" ").append(obstacle.getWidth()).append(" ")
                        .append(obstacle.getHeight()).append(" ").append(obstacle.isMovingRight()).append(" ");
            }

            // 클라이언트에 전송
            PrintWriter out = player.getOut();
            if (out != null) {
                out.println(state.toString());
                System.out.println("Broadcasted state to player: " + player.getId());
            }
        }
    }
}

class ClientHandler extends Thread {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Player player;
    private String selectedMapId;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // 클라이언트 요청 처리
            String message;
            while ((message = in.readLine()) != null) {
                String[] tokens = message.split(" ");
                switch (tokens[0]) {
                    case "MAPSELECT":
                        handleMapSelect(tokens[1]); // 맵 선택 처리
                        break;
                    case "READY":
                        handleReady(); // 준비 상태 처리
                        break;
                    case "MOVE":
                        handleMove(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
                        break;
                    case "MISSILE":
                        handleMissile(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
                        break;
                    default:
                        System.err.println("Unknown command: " + message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 클라이언트 종료 시 로그 출력
            if (player != null) {
                System.out.println("Client disconnected: " + player.getId());

                MapInstance map = player.getAssignedMap();
                if (map != null) {
                    map.getPlayers().remove(player.getId()); // 맵에서 플레이어 제거

                    // 남아있는 플레이어가 있는 경우 승리 메시지 전송
                    if (!map.getPlayers().isEmpty()) {
                        Player remainingPlayer = map.getPlayers().values().iterator().next();
                        PrintWriter out = remainingPlayer.getOut();
                        if (out != null) {
                            out.println("VICTORY"); // 승리 메시지 전송
                        }
                    }

                    // 맵 객체 삭제
                    ShootingGameServer.mapManager.getMaps().remove(map.getMapId());
                    System.out.println("Map " + map.getMapId() + " has been deleted.");
                }
            } else {
                System.out.println("Client disconnected before map selection.");
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMapSelect(String mapId) {
        selectedMapId = mapId; // 선택된 맵 ID 저장

        // 새로운 Player 객체 생성
        // 맵에 따른 플레이어 이미지 설정
        String playerImagePath;
        switch (mapId) {
            case "Map1": // 사막
                playerImagePath = "images/camel" + (new Random().nextInt(5) + 1)  +".png";
                break;
            case "Map2": // 우주
                playerImagePath = "images/spaceship" + (new Random().nextInt(5) + 2) + ".png";
                break;
            case "Map3": // 바다
                playerImagePath = "images/octopus" + (new Random().nextInt(9) + 1) +".png";
                break;
            default:
                playerImagePath = "images/default_player.png"; // 기본 이미지
                break;
        }

        player = new Player(UUID.randomUUID().toString(), 180, 600, 100, playerImagePath);
        player.setOut(out);

        try {
            MapInstance map = ShootingGameServer.mapManager.assignPlayerToMap(player, selectedMapId);

            out.println("SETTINGS " +
                    "PLAYER_WIDTH " + ShootingGameServer.PLAYER_WIDTH + " " +
                    "PLAYER_HEIGHT " + ShootingGameServer.PLAYER_HEIGHT + " " +
                    "MISSILE_WIDTH " + ShootingGameServer.MISSILE_WIDTH + " " +
                    "MISSILE_HEIGHT " + ShootingGameServer.MISSILE_HEIGHT + " " +
                    "BACKGROUND_IMAGE " + map.getBackgroundImagePath() + " " +
                    "PLAYER_IMAGE " + player.getImagePath() + " " +
                    "MISSILE_IMAGE " + map.getMissileImagePath());

            out.println("CONNECTED " + player.getId());
        } catch (IllegalStateException e) {
            out.println("MAPFULL"); // 맵이 가득 찬 경우 클라이언트에 알림
        }
    }

    private void handleReady() {
        if (player != null) {
            player.setReady(true); // 현재 플레이어를 준비 상태로 설정
            MapInstance map = player.getAssignedMap();

            if (map != null) {
                System.out.println("Player " + player.getId() + " is ready in map: " + map.getMapId());

                // 맵의 모든 플레이어가 준비되었는지 확인
                if (map.getPlayers().size() == 2 && map.getPlayers().values().stream().allMatch(Player::isReady)) {
                    map.startGame(); // 게임 시작
                } else {
                    map.broadcast("WAITING"); // 아직 준비되지 않음
                }
            } else {
                System.err.println("Player is not assigned to any map.");
            }
        }
    }

    private void handleMove(int x, int y) {
        // 현재 플레이어의 맵 정보 가져오기
        MapInstance map = player.getAssignedMap();
        if (map != null) {
            System.out.println("Handling MOVE for player: " + player.getId() + " in map: " + map.getMapId());
            player.setPosition(x, y); // 플레이어 위치 업데이트
            map.broadcastGameState(); // 상태 전송
        } else {
            System.err.println("Player is not assigned to any map.");
        }
    }

    private void handleMissile(int x, int y) {
        // 현재 플레이어의 맵 정보 가져오기
        MapInstance map = player.getAssignedMap();
        if (map != null) {
            map.getMissiles().add(new Missile(player.getId(), x, y)); // 미사일 추가
            map.broadcastGameState(); // 상태 전송
        } else {
            System.err.println("Player is not assigned to any map.");
        }
    }
}