package io.github.magisk317.mipush.manager.api;

import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto;
import io.github.magisk317.mipush.manager.api.ManagerHandshake;
import io.github.magisk317.mipush.manager.api.ManagerApplicationDetailDto;
import io.github.magisk317.mipush.manager.api.ManagerApplicationDiagnosticsDto;
import io.github.magisk317.mipush.manager.api.ManagerApplicationPageDto;
import io.github.magisk317.mipush.manager.api.ManagerApplicationQueryDto;

interface IManagerRuntimeService {
    ManagerHandshake handshake(int clientMajor, int clientMinor);
    ManagerConnectionSnapshotDto getConnectionSnapshot();
    ManagerApplicationPageDto getApplicationPage(in ManagerApplicationQueryDto query);
    @nullable ManagerApplicationDetailDto getApplicationDetail(String packageName, boolean ignoreNotRegistered);
    ManagerApplicationDiagnosticsDto getApplicationDiagnostics(String packageName, int registeredType);
}
