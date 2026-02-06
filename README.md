# 🎮 Java Socket Shooting Game

Java Socket 통신을 기반으로 한 **실시간 1:1 멀티플레이 미사일 슈팅 게임**입니다.  
서버 중심(authoritative) 구조를 통해 게임 상태를 관리하며,  
두 플레이어는 **대칭 시점(Mirror View)** 으로 동일한 게임 환경을 경험합니다.

---

## 📌 프로젝트 개요

본 프로젝트는 TCP Socket 기반의 Client–Server 구조를 사용하여  
두 명의 플레이어가 실시간으로 대전하는 1:1 슈팅 게임을 구현한 프로젝트입니다.

- 맵 선택 기반 방 생성
- 맵당 최대 2명 입장
- 2명 모두 접속 시 자동 게임 시작
- 게임 중인 방은 추가 접속 불가
- 서버 기준 충돌 판정 및 상태 동기화

---

## 🛠 사용 기술

| 구분 | 기술 |
|----|----|
| Language | Java |
| Network | TCP Socket |
| UI | Java Swing |
| Concurrency | Thread, ScheduledExecutorService |
| Architecture | Client–Server |
| Rendering | Double Buffering |
| Game Logic | Server Authoritative |

---

## 📁 Project Structure

```text
ShootingGame/
 ┣ 📂 images/                 # 배경, 플레이어, 미사일, 장애물, 아이템 이미지
 ┣ 📂 src/
 ┃ ┣ Item.java                # 아이템 엔티티
 ┃ ┣ ItemManager.java         # 아이템 생성 및 이동 관리
 ┃ ┣ Missile.java             # 미사일 엔티티
 ┃ ┣ Obstacle.java            # 장애물 엔티티
 ┃ ┣ ObstacleManager.java     # 장애물 생성 및 이동 관리
 ┃ ┣ Player.java              # 플레이어 상태 및 정보
 ┃ ┣ ShootingGameClient.java  # 클라이언트 (렌더링, 입력 처리)
 ┃ ┣ ShootingGameServer.java  # 서버 실행 진입점
 ┃ ┣ ClientHandler.java       # 클라이언트 요청 처리 스레드
 ┃ ┣ MapInstance.java         # 맵 단위 게임 로직
 ┃ ┗ MapManager.java          # 맵 생성 및 플레이어 배정
 ┣ 📄 README.md
 ┣ 📄 .gitignore
 ┗ 📄 IDE 설정 파일

