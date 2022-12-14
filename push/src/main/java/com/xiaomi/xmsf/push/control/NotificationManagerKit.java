package com.xiaomi.xmsf.push.control;

import android.content.Context;

import com.oasisfeng.condom.CondomKit;


/**
 *
 * @author Trumeet
 * @date 2018/1/23
 */

public class NotificationManagerKit implements CondomKit {
    @Override
    public void onRegister(CondomKitRegistry registry) {
        registry.registerSystemService(Context.NOTIFICATION_SERVICE, new CondomKit.SystemServiceSupplier() {
            @Override
            public Object getSystemService(Context context, String name) {
                return null;
            }
        });
    }
}
