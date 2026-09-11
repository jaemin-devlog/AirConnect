package univ.airconnect.groupmatching.domain.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GTemporaryTeamRoomTest {

    @Test
    void assignInviteCode_allowsInviteOnlyRoomInOpenState() {
        GTemporaryTeamRoom teamRoom = createRoom(1L, GTeamGender.M);

        assertThat(teamRoom.getInviteCode()).isNull();

        teamRoom.assignInviteCode("PUBLIC1234");

        assertThat(teamRoom.getInviteCode()).isEqualTo("PUBLIC1234");
    }

    @Test
    void leaveQueue_returnsRoomToOpen() {
        GTemporaryTeamRoom teamRoom = createQueueWaitingRoom(1L, GTeamGender.M);

        teamRoom.leaveQueue();

        assertThat(teamRoom.getStatus()).isEqualTo(GTemporaryTeamRoomStatus.OPEN);
        assertThat(teamRoom.getQueueToken()).isNull();
        assertThat(teamRoom.getQueuedAt()).isNull();
    }

    @Test
    void canMatchWith_returnsFalseWhenTeamGenderIsSame() {
        GTemporaryTeamRoom first = createQueueWaitingRoom(1L, GTeamGender.M);
        GTemporaryTeamRoom second = createQueueWaitingRoom(2L, GTeamGender.M);

        assertThat(first.canMatchWith(second)).isFalse();
    }

    @Test
    void canMatchWith_returnsTrueWhenTeamGenderDiffers() {
        GTemporaryTeamRoom first = createQueueWaitingRoom(1L, GTeamGender.M);
        GTemporaryTeamRoom second = createQueueWaitingRoom(2L, GTeamGender.F);

        assertThat(first.canMatchWith(second)).isTrue();
    }

    private GTemporaryTeamRoom createRoom(
            Long leaderId,
            GTeamGender teamGender
    ) {
        GTemporaryTeamRoom teamRoom = GTemporaryTeamRoom.createInviteOnly(leaderId, teamGender, GTeamSize.TWO);
        ReflectionTestUtils.setField(teamRoom, "id", leaderId);
        return teamRoom;
    }

    private GTemporaryTeamRoom createQueueWaitingRoom(
            Long leaderId,
            GTeamGender teamGender
    ) {
        GTemporaryTeamRoom teamRoom = createRoom(leaderId, teamGender);
        teamRoom.addMember();
        teamRoom.startQueue(leaderId, "queue-" + leaderId);
        return teamRoom;
    }
}
