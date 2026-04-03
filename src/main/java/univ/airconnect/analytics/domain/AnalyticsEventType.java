package univ.airconnect.analytics.domain;

/**
 * 서비스 사용 흐름을 추적하기 위한 통계 이벤트 타입 목록입니다.
 * 앱에서 직접 전송하는 이벤트와 서버가 내부적으로 적재하는 이벤트를 함께 사용합니다.
 */
public enum AnalyticsEventType {
    // 사용자가 로그인에 성공했을 때 적재합니다.
    USER_LOGGED_IN,

    // 회원가입 및 필수 초기 설정이 완료됐을 때 적재합니다.
    SIGN_UP_COMPLETED,

    // 앱 세션이 시작됐을 때 클라이언트가 전송합니다.
    APP_SESSION_STARTED,

    // 앱 세션이 종료됐을 때 클라이언트가 전송합니다.
    APP_SESSION_ENDED,

    // 앱 사용 중 활성 상태를 주기적으로 확인할 때 전송합니다.
    APP_HEARTBEAT,

    // 특정 화면 진입을 추적할 때 클라이언트가 전송합니다.
    SCREEN_VIEWED,

    // 친구 초대 공유가 발생했을 때 전송합니다.
    FRIEND_INVITE_SHARED,

    // 소개팅 추천 목록을 새로고침했을 때 적재합니다.
    MATCH_RECOMMENDATION_REFRESHED,

    // 소개팅 매칭 요청을 보냈을 때 적재합니다.
    MATCH_REQUEST_SENT,

    // 소개팅 매칭 요청을 수락했을 때 적재합니다.
    MATCH_REQUEST_ACCEPTED,

    // 과팅 임시방을 생성했을 때 적재합니다.
    TEAM_ROOM_CREATED,

    // 과팅 임시방에 입장했을 때 적재합니다.
    TEAM_ROOM_JOINED,

    // 과팅 임시방에서 나갔을 때 적재합니다.
    TEAM_ROOM_LEFT,

    // 과팅 준비 완료 또는 준비 취소 상태가 변경됐을 때 적재합니다.
    TEAM_READY_CHANGED,

    // 과팅 매칭 큐에 진입했을 때 적재합니다.
    GROUP_QUEUE_STARTED,

    // 과팅 최종 매칭이 성사됐을 때 적재합니다.
    GROUP_MATCH_COMPLETED,

    // 푸시 알림을 눌러 앱에 진입했을 때 클라이언트가 전송합니다.
    PUSH_NOTIFICATION_OPENED,

    // 티켓이 지급됐을 때 적재합니다.
    TICKET_GRANTED,

    // 결제가 완료됐을 때 적재합니다.
    PURCHASE_COMPLETED
}
