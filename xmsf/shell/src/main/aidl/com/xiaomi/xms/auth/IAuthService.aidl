package com.xiaomi.xms.auth;

import android.os.Bundle;
import com.xiaomi.xms.auth.IAuthServiceCallback;

interface IAuthService {
    void auth(in Bundle bundle, IAuthServiceCallback callback);
    Bundle syncAuth(in Bundle bundle);
}
