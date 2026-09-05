package univ.airconnect.maintenance.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.maintenance.domain.entity.MaintenanceSetting;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.repository.MaintenanceSettingRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {
    @Mock private MaintenanceSettingRepository repository;

    @Test
    void getStatus_returnsVirtualVersionWithoutInitializingDatabase() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        var result = new MaintenanceService(repository).getStatus();

        assertThat(result.enabled()).isFalse();
        assertThat(result.version()).isEqualTo(-1);
        assertThat(result.title()).isEqualTo("서버 점검 중");
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).save(any());
    }

    @Test
    void changeState_createsMissingSingletonAndReturnsFlushedVersion() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            MaintenanceSetting setting = invocation.getArgument(0);
            assertThat(setting.getVersion()).isNull();
            ReflectionTestUtils.setField(setting, "version", 0L);
            return setting;
        });

        var result = new MaintenanceService(repository).changeState(999L, -1, true);

        assertThat(result.enabled()).isTrue();
        assertThat(result.startedAt()).isNotNull();
        assertThat(result.updatedByUserId()).isEqualTo(999L);
        assertThat(result.version()).isZero();
        assertThat(result.title()).isEqualTo("서버 점검 중");
    }

    @Test
    void updateContent_preservesEnabledAndStartTime() {
        var setting = stored(4);
        setting.changeState(true, 111L);
        var startedAt = setting.getStartedAt();
        when(repository.findById(1L)).thenReturn(Optional.of(setting));
        when(repository.saveAndFlush(setting)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(setting, "version", 5L);
            return setting;
        });

        var result = new MaintenanceService(repository).updateContent(999L, 4, " 새 제목 ", " 새 안내 ");

        assertThat(result.enabled()).isTrue();
        assertThat(result.startedAt()).isEqualTo(startedAt);
        assertThat(result.title()).isEqualTo("새 제목");
        assertThat(result.message()).isEqualTo("새 안내");
        assertThat(result.version()).isEqualTo(5);
    }

    @Test
    void staleContentAndStateFailBeforeMutatingEntityOrSaving() {
        var setting = stored(4);
        setting.changeState(true, 111L);
        var before = MaintenanceStatusResponse.from(setting);
        when(repository.findById(1L)).thenReturn(Optional.of(setting));
        var service = new MaintenanceService(repository);

        assertConflict(() -> service.updateContent(999L, 3, "stale", "stale"));
        assertConflict(() -> service.changeState(999L, 3, false));
        assertThat(MaintenanceStatusResponse.from(setting)).isEqualTo(before);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void missingSingletonRejectsStoredAndInvalidExpectedVersions() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        var service = new MaintenanceService(repository);

        assertConflict(() -> service.changeState(999L, 0, true));
        assertConflict(() -> service.updateContent(999L, -2, "title", "message"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void excessiveContentFailsBeforeDatabaseAccess() {
        var service = new MaintenanceService(repository);
        for (var content : new String[][] {{"x".repeat(121), "body"}, {"title", "x".repeat(501)}}) {
            assertThatThrownBy(() -> service.updateContent(999L, -1, content[0], content[1]))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void optimisticFlushFailureBecomesConflict() {
        var setting = stored(4);
        when(repository.findById(1L)).thenReturn(Optional.of(setting));
        when(repository.saveAndFlush(setting)).thenThrow(new OptimisticLockingFailureException("fixture race"));

        assertConflict(() -> new MaintenanceService(repository).changeState(999L, 4, true));
    }

    @Test
    void storedRowIntegrityFailureIsNotMisreportedAsVersionConflict() {
        var setting = stored(4);
        var failure = new DataIntegrityViolationException("fixture non-version integrity failure");
        when(repository.findById(1L)).thenReturn(Optional.of(setting));
        when(repository.saveAndFlush(setting)).thenThrow(failure);

        assertThatThrownBy(() -> new MaintenanceService(repository).changeState(999L, 4, true)).isSameAs(failure);
    }

    private static MaintenanceSetting stored(long version) {
        var setting = MaintenanceSetting.defaultValue();
        ReflectionTestUtils.setField(setting, "version", version);
        return setting;
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MAINTENANCE_CONFLICT));
    }
}
