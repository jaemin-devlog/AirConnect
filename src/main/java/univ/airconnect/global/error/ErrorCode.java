package univ.airconnect.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-001", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH-001", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH-002", "권한이 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-405", "허용되지 않은 요청 메서드입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404", "리소스를 찾을 수 없습니다."),

    TEAM_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "GMATCH-001", "임시 팀방을 찾을 수 없습니다."),
    TEAM_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "GMATCH-002", "임시 팀방 멤버를 찾을 수 없습니다."),
    READY_STATE_NOT_FOUND(HttpStatus.NOT_FOUND, "GMATCH-003", "준비 상태 정보를 찾을 수 없습니다."),
    INVALID_INVITE_CODE(HttpStatus.BAD_REQUEST, "GMATCH-004", "유효하지 않은 초대 코드입니다."),
    INVITE_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "GMATCH-005", "초대 코드는 필수입니다."),
    PUBLIC_ROOM_ONLY(HttpStatus.BAD_REQUEST, "GMATCH-006", "공개 방만 공개 입장이 가능합니다."),
    READY_CHECK_REQUIRED(HttpStatus.BAD_REQUEST, "GMATCH-007", "준비 확인 상태에서만 요청할 수 있습니다."),
    TEAM_ROOM_FULL(HttpStatus.BAD_REQUEST, "GMATCH-008", "팀방 정원이 가득 찼습니다."),
    TEAM_ROOM_JOIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "GMATCH-009", "현재 상태에서는 팀방에 입장할 수 없습니다."),
    TEAM_ROOM_TERMINATED(HttpStatus.BAD_REQUEST, "GMATCH-010", "종료된 팀방에는 입장할 수 없습니다."),
    LEADER_CANNOT_REJOIN(HttpStatus.BAD_REQUEST, "GMATCH-011", "방장은 자신이 만든 팀방에 다시 입장할 수 없습니다."),
    LEADER_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "GMATCH-012", "방장은 팀방 나가기 대신 방 해산을 사용해야 합니다."),
    ALREADY_TEAM_MEMBER(HttpStatus.BAD_REQUEST, "GMATCH-013", "이미 해당 팀방의 활성 멤버입니다."),
    ACTIVE_TEAM_ROOM_EXISTS(HttpStatus.CONFLICT, "GMATCH-014", "이미 참여 중인 활성 임시 팀방이 있습니다."),
    TEAM_MEMBER_FORBIDDEN(HttpStatus.FORBIDDEN, "GMATCH-015", "해당 임시 팀방의 활성 멤버가 아닙니다."),
    TEAM_ROOM_ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "GMATCH-016", "해당 임시 팀방에 접근할 권한이 없습니다."),
    TEAM_GENDER_REQUIRED(HttpStatus.BAD_REQUEST, "GMATCH-017", "팀 성별 정보가 필요합니다."),
    PROFILE_GENDER_REQUIRED(HttpStatus.BAD_REQUEST, "GMATCH-018", "프로필 성별을 먼저 설정해 주세요."),
    TEAM_GENDER_MISMATCH(HttpStatus.FORBIDDEN, "GMATCH-019", "팀 성별과 사용자 성별이 일치하지 않습니다."),
    TEAM_SIZE_REQUIRED(HttpStatus.BAD_REQUEST, "GMATCH-020", "teamSize는 필수입니다."),
    INVALID_TEAM_SIZE(HttpStatus.BAD_REQUEST, "GMATCH-021", "지원하지 않는 팀 인원 수입니다."),
    TEAM_ROOM_STATE_INVALID(HttpStatus.BAD_REQUEST, "GMATCH-022", "현재 팀방 상태에서는 요청을 처리할 수 없습니다."),
    LEADER_ONLY_ACTION(HttpStatus.FORBIDDEN, "GMATCH-023", "방장만 수행할 수 있습니다."),
    TEAM_ROOM_NOT_FULL(HttpStatus.BAD_REQUEST, "GMATCH-024", "정원이 모두 차야 요청할 수 있습니다."),
    TEAM_NOT_ALL_READY(HttpStatus.BAD_REQUEST, "GMATCH-025", "모든 팀원이 준비 완료 상태여야 합니다."),
    QUEUE_WAITING_REQUIRED(HttpStatus.BAD_REQUEST, "GMATCH-026", "매칭 대기 중인 방에서만 요청할 수 있습니다."),
    INVITE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "GMATCH-027", "초대 코드 생성에 실패했습니다."),
    TEAM_MEMBER_COUNT_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR, "GMATCH-028", "팀 인원 수와 실제 멤버 수가 일치하지 않습니다."),
    QUEUE_LOCK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "GMATCH-029", "큐 처리 잠금을 획득하지 못했습니다."),
    GROUP_MATCH_ARGUMENT_INVALID(HttpStatus.BAD_REQUEST, "GMATCH-030", "과팅 요청 값이 올바르지 않습니다."),
    MATCH_RESULT_STATE_INVALID(HttpStatus.BAD_REQUEST, "GMATCH-031", "매칭 결과 상태가 올바르지 않습니다."),
    FINAL_GROUP_ROOM_STATE_INVALID(HttpStatus.BAD_REQUEST, "GMATCH-032", "최종 그룹방 상태가 올바르지 않습니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-999", "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
