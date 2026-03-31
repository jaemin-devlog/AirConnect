package univ.airconnect.groupmatching.domain.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GTemporaryTeamRoomTest {

    @Test
    void assignInviteCode_allowsPublicRoomInOpenState() {
        GTemporaryTeamRoom teamRoom = createRoom(1L, GTeamGender.M, GGenderFilter.ANY, GTeamVisibility.PUBLIC);

        assertThat(teamRoom.getInviteCode()).isNull();

        teamRoom.assignInviteCode("PUBLIC1234");

        assertThat(teamRoom.getInviteCode()).isEqualTo("PUBLIC1234");
    }

    @Test
    void leaveQueue_returnsRoomToReadyCheck() {
        GTemporaryTeamRoom teamRoom = createQueueWaitingRoom(1L, GTeamGender.M, GGenderFilter.F);

        teamRoom.leaveQueue();

        assertThat(teamRoom.getStatus()).isEqualTo(GTemporaryTeamRoomStatus.READY_CHECK);
        assertThat(teamRoom.getQueueToken()).isNull();
        assertThat(teamRoom.getQueuedAt()).isNull();
    }

    @Test
    void canMatchWith_returnsFalseWhenTeamGenderIsSame() {
        GTemporaryTeamRoom first = createQueueWaitingRoom(1L, GTeamGender.M, GGenderFilter.ANY);
        GTemporaryTeamRoom second = createQueueWaitingRoom(2L, GTeamGender.M, GGenderFilter.ANY);

        assertThat(first.canMatchWith(second)).isFalse();
    }

    @Test
    void canMatchWith_returnsTrueWhenTeamGenderDiffersEvenIfFiltersWouldBlock() {
        GTemporaryTeamRoom first = createQueueWaitingRoom(1L, GTeamGender.M, GGenderFilter.M);
        GTemporaryTeamRoom second = createQueueWaitingRoom(2L, GTeamGender.F, GGenderFilter.F);

        assertThat(first.canMatchWith(second)).isTrue();
    }

    private GTemporaryTeamRoom createRoom(
            Long leaderId,
            GTeamGender teamGender,
            GGenderFilter opponentGenderFilter,
            GTeamVisibility visibility
    ) {
        GTemporaryTeamRoom teamRoom = GTemporaryTeamRoom.create(
                leaderId,
                "team-" + leaderId,
                teamGender,
                GTeamSize.TWO,
                opponentGenderFilter,
                visibility,
                1000L + leaderId
        );
        ReflectionTestUtils.setField(teamRoom, "id", leaderId);
        return teamRoom;
    }

    private GTemporaryTeamRoom createQueueWaitingRoom(
            Long leaderId,
            GTeamGender teamGender,
            GGenderFilter opponentGenderFilter
    ) {
        GTemporaryTeamRoom teamRoom = createRoom(leaderId, teamGender, opponentGenderFilter, GTeamVisibility.PUBLIC);
        teamRoom.addMember();
        teamRoom.enterReadyCheck(leaderId);
        teamRoom.startQueue(leaderId, true, "queue-" + leaderId);
        return teamRoom;
    }
}
