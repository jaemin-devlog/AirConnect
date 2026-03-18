package univ.airconnect.user.domain;

public enum MilestoneType {
    PROFILE_IMAGE_UPLOADED("프로필 이미지 업로드"),
    EMAIL_VERIFIED("이메일 인증");

    private final String description;

    MilestoneType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

