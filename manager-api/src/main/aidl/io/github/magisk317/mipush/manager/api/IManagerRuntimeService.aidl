package io.github.magisk317.mipush.manager.api;

import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto;
import io.github.magisk317.mipush.manager.api.ManagerHandshake;

interface IManagerRuntimeService {
    ManagerHandshake handshake(int clientMajor, int clientMinor);
    ManagerConnectionSnapshotDto getConnectionSnapshot();
}
