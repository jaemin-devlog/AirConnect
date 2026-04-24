package univ.airconnect.user.domain;

public enum UserRole {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
