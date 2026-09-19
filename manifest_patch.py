#!/usr/bin/env python3
# Applies Xiaomi HyperOS "小米Widget" registration markers + compliant widget names.
# Run from the repo root: python3 manifest_patch.py
import io

BASE = "mipush/src/main"
MANIFEST = f"{BASE}/AndroidManifest.xml"
STR_EN = f"{BASE}/res/values/strings.xml"
STR_ZH = f"{BASE}/res/values-zh/strings.xml"

# ---------- 1. Manifest: application-level miuiWidgetVersion ----------
s = open(MANIFEST, encoding="utf-8").read()
old_app = '        tools:targetApi="tiramisu">'
new_app = (
    '        tools:targetApi="tiramisu">\n\n'
    '        <meta-data\n'
    '            android:name="miuiWidgetVersion"\n'
    '            android:value="1" />'
)
assert old_app in s, "application open tag not found"
s = s.replace(old_app, new_app, 1)

# ---------- 2. ConnectionStatus receiver ----------
conn_old = '''        <receiver
            android:name="io.github.magisk317.mipush.app.widget.ConnectionStatusWidgetProvider"
            android:exported="true"
            android:label="@string/widget_connection_status_title">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/mipush_widget_connection_status" />
        </receiver>'''
conn_new = '''        <receiver
            android:name="io.github.magisk317.mipush.app.widget.ConnectionStatusWidgetProvider"
            android:process=":widgetProvider"
            android:exported="true"
            android:label="@string/widget_connection_status_title">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="miui.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/mipush_widget_connection_status" />
            <meta-data
                android:name="miuiWidget"
                android:value="true" />
            <meta-data
                android:name="miuiWidgetRefresh"
                android:value="exposure" />
            <meta-data
                android:name="miuiWidgetRefreshMinInterval"
                android:value="20000" />
        </receiver>'''
assert conn_old in s, "connection receiver block not found"
s = s.replace(conn_old, conn_new, 1)

# ---------- 3. RecentEvents receiver ----------
rec_old = '''        <receiver
            android:name="io.github.magisk317.mipush.app.widget.RecentEventsWidgetProvider"
            android:exported="true"
            android:label="@string/widget_recent_events_title">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/mipush_widget_recent_events" />
        </receiver>'''
rec_new = '''        <receiver
            android:name="io.github.magisk317.mipush.app.widget.RecentEventsWidgetProvider"
            android:process=":widgetProvider"
            android:exported="true"
            android:label="@string/widget_recent_events_title">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="miui.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/mipush_widget_recent_events" />
            <meta-data
                android:name="miuiWidget"
                android:value="true" />
            <meta-data
                android:name="miuiWidgetRefresh"
                android:value="exposure" />
            <meta-data
                android:name="miuiWidgetRefreshMinInterval"
                android:value="20000" />
        </receiver>'''
assert rec_old in s, "recent receiver block not found"
s = s.replace(rec_old, rec_new, 1)

open(MANIFEST, "w", encoding="utf-8").write(s)

# ---------- 4. Widget names: 2-8 chars, != app name (spec 12.5) ----------
se = open(STR_EN, encoding="utf-8").read()
se = se.replace(
    '<string name="widget_connection_status_title">MiPush connection</string>',
    '<string name="widget_connection_status_title">Connection</string>',
)
se = se.replace(
    '<string name="widget_recent_events_title">MiPush records</string>',
    '<string name="widget_recent_events_title">Records</string>',
)
open(STR_EN, "w", encoding="utf-8").write(se)

sz = open(STR_ZH, encoding="utf-8").read()
sz = sz.replace(
    '<string name="widget_connection_status_title">MiPush 连接</string>',
    '<string name="widget_connection_status_title">连接状态</string>',
)
sz = sz.replace(
    '<string name="widget_recent_events_title">MiPush 记录</string>',
    '<string name="widget_recent_events_title">推送记录</string>',
)
open(STR_ZH, "w", encoding="utf-8").write(sz)

print("manifest_patch: OK")
