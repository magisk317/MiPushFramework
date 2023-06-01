package top.trumeet.mipush.provider.db;

import static top.trumeet.mipush.provider.DatabaseUtils.daoSession;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;

import androidx.annotation.Nullable;

import org.greenrobot.greendao.query.QueryBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import top.trumeet.common.Constants;
import top.trumeet.common.utils.DatabaseUtils;
import top.trumeet.common.utils.Utils;
import top.trumeet.mipush.provider.event.Event;
import top.trumeet.mipush.provider.event.EventType;
import top.trumeet.mipush.provider.gen.db.EventDao;

/**
 * @author Trumeet
 * @date 2017/12/23
 */

public class EventDb {
    public static final String AUTHORITY = "top.trumeet.mipush.providers.EventProvider";
    public static final String BASE_PATH = "EVENT";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);

    private static DatabaseUtils getInstance(Context context) {
        return new DatabaseUtils(CONTENT_URI, context.getContentResolver());
    }


    public static long insertEvent(Event event) {
        return daoSession.insert(event);
    }


    public static long insertEvent(@Event.ResultType int result,
                                  EventType type,
                                  Context context) {
        return insertEvent(type.fillEvent(new Event(null
                , type.getPkg()
                , type.getType()
                , Utils.getUTC().getTime()
                , result
                , type.getInfo()
                , null
                , null
                , type.getPayload()
                , Utils.getRegSec(type.getPkg())
        )));
    }


    public static List<Event> query(@Nullable Integer skip,
                                    @Nullable Integer limit,
                                    @Nullable Set<Integer> types,
                                    @Nullable String pkg,
                                    @Nullable String text,
                                    Context context,
                                    @Nullable CancellationSignal signal) {
        QueryBuilder<Event> query = daoSession.queryBuilder(Event.class)
                .orderDesc(EventDao.Properties.Date)
                .limit(limit)
                .offset(skip)
                ;
        if (pkg != null && !pkg.trim().isEmpty()) {
            query.where(EventDao.Properties.Pkg.eq(pkg));
        }
        if (types != null && !types.isEmpty()) {
            query.where(EventDao.Properties.Type.in(types));
        }
        if (text != null && !text.trim().isEmpty()) {
            query.where(EventDao.Properties.Info.like("%" + text + "%"));
        }
        return query.list();
    }


    public static void deleteHistory(Context context, CancellationSignal signal) {
        String data =  (Utils.getUTC().getTime() - 1000L * 3600L * 24 * 7) + "";
        QueryBuilder<Event> query = daoSession.queryBuilder(Event.class)
                .where(EventDao.Properties.Type.in(Event.Type.RECEIVE_PUSH, Event.Type.REGISTER, Event.Type.Command))
                .where(EventDao.Properties.Date.lt(data))
                ;
        query.buildDelete().executeDeleteWithoutDetachingEntities();
    }


    public static Set<String> queryRegistered(Context context, CancellationSignal signal) {

        QueryBuilder<Event> query = daoSession.queryBuilder(Event.class)
                .where(EventDao.Properties.Type.eq(Event.Type.RegistrationResult))
                .orderDesc(EventDao.Properties.Date)
                ;
        List<Event> registered = query.list();

        //fuck java6
        Map<String, Event> registeredMap = new HashMap<>();
        for (Event event : registered) {
            String pkg = event.getPkg();
            if (!registeredMap.containsKey(pkg)) {
                registeredMap.put(event.getPkg(), event);
            }
        }

        query = daoSession.queryBuilder(Event.class)
                .where(EventDao.Properties.Type.eq(Event.Type.UnRegistration))
                .orderDesc(EventDao.Properties.Date)
                ;
        List<Event> unregistered = query.list();

        Map<String, Event> unRegisteredMap = new HashMap<>();
        for (Event event : unregistered) {
            String pkg = event.getPkg();
            if (!unRegisteredMap.containsKey(pkg)) {
                unRegisteredMap.put(event.getPkg(), event);
            }
        }

        Set<String> pkgs = new HashSet<>();
        for (Event event : registeredMap.values()) {
            Event unRegisterEvent = unRegisteredMap.get(event.getPkg());
            if (unRegisterEvent != null && unRegisterEvent.getDate() > event.getDate()) {
                continue;
            }

            pkgs.add(event.getPkg());
        }

        return pkgs;
    }

}
