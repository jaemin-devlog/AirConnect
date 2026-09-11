package univ.airconnect.matching.domain;

public enum ConnectionStatus {
    PENDING,      // 대기 중
    ACCEPTED,     // 수락됨
    REJECTED,     // 거절됨
    CANCELLED,    // 요청 취소 또는 차단/탈퇴/제재로 종료됨
    EXPIRED       // 응답 기한이 지나 자동 만료됨
}

