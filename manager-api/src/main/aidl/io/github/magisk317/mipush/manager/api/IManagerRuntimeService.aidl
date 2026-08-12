package io.github.magisk317.mipush.manager.api;

import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto;
import io.github.magisk317.mipush.manager.api.ManagerHandshake;
import io.github.magisk317.mipush.manager.api.ManagerApplicationDetailDto;
import io.github.magisk317.mipush.manager.api.ManagerApplicationDiagnosticsDto;
import io.github.magisk317.mipush.manager.api.ManagerApplicationPageDto;
import io.github.magisk317.mipush.manager.api.ManagerApplicationQueryDto;
import io.github.magisk317.mipush.manager.api.ManagerEventQueryDto;
import io.github.magisk317.mipush.manager.api.ManagerEventPageDto;
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelQueryDto;
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelPageDto;
import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogDto;
import io.github.magisk317.mipush.manager.api.ManagerLogExportResultDto;
import io.github.magisk317.mipush.manager.api.ManagerRuntimePreferencesDto;
import io.github.magisk317.mipush.manager.api.ManagerMigrationSnapshotDto;
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadRequestDto;
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadResultDto;
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto;
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto;
import io.github.magisk317.mipush.manager.api.ManagerRuntimeEnvironmentSnapshotDto;

interface IManagerRuntimeService {
    ManagerHandshake handshake(int clientMajor, int clientMinor);
    ManagerConnectionSnapshotDto getConnectionSnapshot();
    ManagerApplicationPageDto getApplicationPage(in ManagerApplicationQueryDto query);
    @nullable ManagerApplicationDetailDto getApplicationDetail(String packageName, boolean ignoreNotRegistered);
    ManagerApplicationDiagnosticsDto getApplicationDiagnostics(String packageName, int registeredType);
    ManagerEventPageDto getEventPage(in ManagerEventQueryDto query);
    ManagerNotificationChannelPageDto getNotificationChannelPage(in ManagerNotificationChannelQueryDto query);
    ManagerConfigurationCatalogDto getConfigurationCatalog();
    ManagerLogExportResultDto exportRuntimeLogs();
    ManagerRuntimePreferencesDto getRuntimePreferences();
    ManagerMigrationSnapshotDto getManagerMigrationSnapshot();
    ManagerConfigurationUploadResultDto uploadConfiguration(in ManagerConfigurationUploadRequestDto request);
    ManagerWriteResultDto executeWrite(in ManagerWriteRequestDto request);
    ManagerRuntimeEnvironmentSnapshotDto getRuntimeEnvironmentSnapshot();
}
