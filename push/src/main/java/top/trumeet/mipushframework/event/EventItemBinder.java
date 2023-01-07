package top.trumeet.mipushframework.event;

import static com.xiaomi.push.service.MIPushEventProcessor.buildContainer;

import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xiaomi.mipush.sdk.DecryptException;
import com.xiaomi.mipush.sdk.PushContainerHelper;
import com.xiaomi.push.service.MIPushEventProcessor;
import com.xiaomi.push.service.MyMIPushNotificationHelper;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.push.utils.Configurations;

import org.apache.thrift.TBase;
import org.apache.thrift.TException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import top.trumeet.common.event.Event;
import top.trumeet.common.event.type.EventType;
import top.trumeet.common.event.type.TypeFactory;
import top.trumeet.mipushframework.permissions.ManagePermissionsActivity;
import top.trumeet.mipushframework.utils.BaseAppsBinder;

/**
 * Created by Trumeet on 2017/8/26.
 * @see Event
 * @see EventFragment
 * @author Trumeet
 */

public class EventItemBinder extends BaseAppsBinder<Event> {
    private static Logger logger = XLog.tag(EventItemBinder.class.getSimpleName()).build();

    private boolean isSpecificApp = true;
    EventItemBinder(boolean isSpecificApp) {
        super();
        this.isSpecificApp = isSpecificApp;
    }

    @Override
    protected void onBindViewHolder(final @NonNull ViewHolder holder, final @NonNull Event item) {
        fillData(item.getPkg(), false, holder);
        final EventType type = TypeFactory.create(item, item.getPkg());
        holder.title.setText(type.getTitle(holder.itemView.getContext()));
        holder.summary.setText(type.getSummary(holder.itemView.getContext()));

        String status = "";
        switch (item.getResult()) {
            case Event.ResultType.OK :
                if (item.getPayload() != null) {
                    XmPushActionContainer container = MIPushEventProcessor.buildContainer(item.getPayload());
                    if (container.metaInfo.isSetPassThrough()) {
                        if (container.metaInfo.passThrough == 0) {
                            try {
                                Set<String> ops = Configurations.getInstance().handle(container.getPackageName(), container.getMetaInfo());
                                status = container.getMetaInfo().getExtra().get("channel_name");
                                if (!ops.isEmpty()) {
                                    status = ops + " " + status;
                                }
                            } catch (Throwable e) {
                                status = holder.itemView.getContext()
                                        .getString(R.string.message_type_notification);
                            }
                        } else if (container.metaInfo.passThrough == 1) {
                            status = holder.itemView.getContext()
                                    .getString(R.string.message_type_pass_through);
                        }
                    }
                }
                break;
            case Event.ResultType.DENY_DISABLED:
                status = holder.itemView.getContext()
                        .getString(R.string.status_deny_disable);
                break;
            case Event.ResultType.DENY_USER:
                status = holder.itemView.getContext()
                        .getString(R.string.status_deny_user);
                break;
            default:
                break;
        }

        Calendar calendarServer = Calendar.getInstance();
        calendarServer.setTime(new Date(item.getDate()));
        int zoneOffset = calendarServer.get(java.util.Calendar.ZONE_OFFSET);
        int dstOffset = calendarServer.get(java.util.Calendar.DST_OFFSET);
        calendarServer.add(java.util.Calendar.MILLISECOND, (zoneOffset + dstOffset));
        DateFormat formatter = SimpleDateFormat.getDateTimeInstance();

        holder.text2.setText(holder.itemView.getContext().getString(R.string.date_format_long,
                formatter.format(calendarServer.getTime())));
        holder.status.setText(status);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dialog dialog = createInfoDialog(type,
                        holder.itemView.getContext()); // "Developer info" dialog for event messages
                if (dialog != null && isSpecificApp) {
                    dialog.show();
                } else {
                    startManagePermissions(type, holder.itemView.getContext());
                }
            }
        });
    }

    @Nullable
    private Dialog createInfoDialog (final EventType type, final Context context) {
        XmPushActionContainer container = buildContainer(type.getPayload());
        Gson gson = new GsonBuilder()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .setExclusionStrategies(new ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(FieldAttributes f) {
                        String[] exclude = { "hb", "__isset_bit_vector" };
                        for (String field : exclude) {
                            if (f.getName().equals(field)) {
                                return true;
                            }
                        }
                        if (f.getDeclaredClass() == Map.class && f.getName().equals("internal"))
                        {
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        return false;
                    }
                })
                .create();
        JsonElement jsonElement = gson.toJsonTree(container);
        if (jsonElement.isJsonObject()) {
            JsonObject json = jsonElement.getAsJsonObject();
            String pushAction = "pushAction";
            try {
                TBase message = PushContainerHelper.getResponseMessageBodyFromContainer(context, container);
                json.add(pushAction, gson.toJsonTree(message));
            } catch (TException e) {
                logger.e(e.getLocalizedMessage(), e);
            } catch (DecryptException e) {
                json.add(pushAction, gson.toJsonTree(e));
            }
            jsonElement = json;
        }
        final CharSequence info = gson.toJson(jsonElement);
        if (info == null)
            return null;

        TextView showText = new TextView(context);
        showText.setText(info);
        showText.setTextSize(18);
        showText.setTextIsSelectable(true);
        showText.setTypeface(Typeface.MONOSPACE);

        final ScrollView scrollView = new ScrollView(context);
        scrollView.addView(showText);

        AlertDialog.Builder build = new AlertDialog.Builder(context)
                .setView(scrollView)
                .setTitle("Developer Info")
                .setNeutralButton(android.R.string.copy, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ClipboardManager clipboardManager = (ClipboardManager)
                                context.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboardManager.setText(info);
                    }
                })
                .setNegativeButton(R.string.action_edit_permission, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startManagePermissions(type, context);
                    }
                });
        if (type.getPayload() != null) {
            build.setPositiveButton(R.string.action_notify, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    MyMIPushNotificationHelper.notifyPushMessage(context,
                            buildContainer(type.getPayload()),
                            type.getPayload()
                    );
                }
            });
        }

        return build.create();
    }

    private static void startManagePermissions (EventType type, Context context) {
        // Issue: This currently allows overlapping opens.
        context.startActivity(new Intent(context,
                ManagePermissionsActivity.class)
                .putExtra(ManagePermissionsActivity.EXTRA_PACKAGE_NAME,
                        type.getPkg()));
    }
}
