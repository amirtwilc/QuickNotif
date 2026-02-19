package app.amir.quicknotif;

import static org.junit.Assert.*;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class QuickNotifWidgetServiceTest {

    private Context context;
    private Object factory; // QuickNotifRemoteViewsFactory instance
    private Class<?> factoryClass;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        NotifUtils.getPrefs(context).edit().clear().commit();

        factoryClass = Class.forName(
                "app.amir.quicknotif.QuickNotifWidgetService$QuickNotifRemoteViewsFactory");
        Constructor<?> ctor = factoryClass.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        factory = ctor.newInstance(context);
        factoryClass.getMethod("onCreate").invoke(factory);
    }

    private int getCount() throws Exception {
        return (int) factoryClass.getMethod("getCount").invoke(factory);
    }

    private void reload() throws Exception {
        factoryClass.getMethod("onDataSetChanged").invoke(factory);
    }

    private List<?> getNotifications() throws Exception {
        Field f = factoryClass.getDeclaredField("notifications");
        f.setAccessible(true);
        return (List<?>) f.get(factory);
    }

    private Object getNotificationAt(int idx) throws Exception {
        return getNotifications().get(idx);
    }

    private Object getField(Object notifData, String fieldName) throws Exception {
        Class<?> dataClass = notifData.getClass();
        Field f = dataClass.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(notifData);
    }

    private long futureTs() {
        return System.currentTimeMillis() + 3_600_000L;
    }

    // ─── Loading ──────────────────────────────────────────────────────────────

    @Test
    public void loadNotifications_emptyStorage_countZero() throws Exception {
        assertEquals(0, getCount());
    }

    @Test
    public void loadNotifications_disabledNotification_excluded() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_1_1");
        n.put("name", "Disabled");
        n.put("enabled", false);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        assertEquals(0, getCount());
    }

    @Test
    public void loadNotifications_zeroScheduledAt_excluded() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_2_2");
        n.put("name", "Zero ts");
        n.put("enabled", true);
        n.put("scheduledAt", 0L);
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        assertEquals(0, getCount());
    }

    @Test
    public void loadNotifications_enabledFutureNotification_included() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_3_3");
        n.put("name", "Future");
        n.put("enabled", true);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        assertEquals(1, getCount());
    }

    @Test
    public void loadNotifications_expiredButEnabled_included() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_4_4");
        n.put("name", "Expired but enabled");
        n.put("enabled", true);
        n.put("scheduledAt", System.currentTimeMillis() - 5_000L);
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        // Expired+enabled should still be shown in widget
        assertEquals(1, getCount());
    }

    // ─── Sort order ───────────────────────────────────────────────────────────

    @Test
    public void sortOrder_expiredBeforeFuture() throws Exception {
        long expiredTs = System.currentTimeMillis() - 5_000L;
        long futureTs  = futureTs();

        JSONArray arr = new JSONArray();
        JSONObject nFuture = new JSONObject();
        nFuture.put("id", "notification_10_1");
        nFuture.put("name", "Future");
        nFuture.put("enabled", true);
        nFuture.put("scheduledAt", futureTs);
        arr.put(nFuture);

        JSONObject nExpired = new JSONObject();
        nExpired.put("id", "notification_10_2");
        nExpired.put("name", "Expired");
        nExpired.put("enabled", true);
        nExpired.put("scheduledAt", expiredTs);
        arr.put(nExpired);

        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        assertEquals(2, getCount());
        Object first = getNotificationAt(0);
        assertTrue("First item should be expired", (Boolean) getField(first, "isExpired"));
    }

    @Test
    public void sortOrder_withinExpired_earliestFirst() throws Exception {
        long older  = System.currentTimeMillis() - 10_000L;
        long newer  = System.currentTimeMillis() - 2_000L;

        JSONArray arr = new JSONArray();
        JSONObject n1 = new JSONObject();
        n1.put("id", "notification_11_1");
        n1.put("name", "Newer expired");
        n1.put("enabled", true);
        n1.put("scheduledAt", newer);
        arr.put(n1);

        JSONObject n2 = new JSONObject();
        n2.put("id", "notification_11_2");
        n2.put("name", "Older expired");
        n2.put("enabled", true);
        n2.put("scheduledAt", older);
        arr.put(n2);

        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        assertEquals(2, getCount());
        Object first = getNotificationAt(0);
        assertEquals(older, getField(first, "scheduledAt"));
    }

    @Test
    public void sortOrder_withinFuture_earliestFirst() throws Exception {
        long sooner = System.currentTimeMillis() + 1_000_000L;
        long later  = System.currentTimeMillis() + 9_000_000L;

        JSONArray arr = new JSONArray();
        JSONObject n1 = new JSONObject();
        n1.put("id", "notification_12_1");
        n1.put("name", "Later");
        n1.put("enabled", true);
        n1.put("scheduledAt", later);
        arr.put(n1);

        JSONObject n2 = new JSONObject();
        n2.put("id", "notification_12_2");
        n2.put("name", "Sooner");
        n2.put("enabled", true);
        n2.put("scheduledAt", sooner);
        arr.put(n2);

        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        assertEquals(2, getCount());
        Object first = getNotificationAt(0);
        assertEquals(sooner, getField(first, "scheduledAt"));
    }

    // ─── ISO fallback parsing ─────────────────────────────────────────────────

    @Test
    public void loadNotifications_scheduledAtAsIsoString_fallbackParsed() throws Exception {
        // Use a far-future ISO string
        String isoFuture = "2099-06-15T10:30:00.000Z";
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_20_1");
        n.put("name", "ISO test");
        n.put("enabled", true);
        n.put("scheduledAt", isoFuture);
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        assertEquals("ISO-string scheduledAt should be included", 1, getCount());
    }

    // ─── Error handling ───────────────────────────────────────────────────────

    @Test
    public void loadNotifications_malformedJson_doesNotCrash() throws Exception {
        NotifUtils.saveNotificationsJson(context, "{ not valid [[[");
        // Should not throw
        reload();
        assertEquals(0, getCount());
    }

    // ─── Field population ─────────────────────────────────────────────────────

    @Test
    public void loadNotifications_notificationFields_correctlyPopulated() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_30_1");
        n.put("name", "Check fields");
        n.put("type", "relative");
        n.put("enabled", true);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        Object notifData = getNotificationAt(0);
        assertEquals("notification_30_1", getField(notifData, "id"));
        assertEquals("Check fields",      getField(notifData, "name"));
        assertEquals("relative",          getField(notifData, "type"));
    }

    @Test
    public void getCount_reflectsLoadedNotifications() throws Exception {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < 3; i++) {
            JSONObject n = new JSONObject();
            n.put("id", "notification_40_" + i);
            n.put("name", "Item " + i);
            n.put("enabled", true);
            n.put("scheduledAt", futureTs());
            arr.put(n);
        }
        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        assertEquals(3, getCount());
    }

    @Test
    public void getViewAt_doesNotThrow() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_50_1");
        n.put("name", "View test");
        n.put("enabled", true);
        n.put("scheduledAt", System.currentTimeMillis() - 1000L); // expired
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());
        reload();

        // Should not throw
        Object rv = factoryClass.getMethod("getViewAt", int.class).invoke(factory, 0);
        assertNotNull(rv);
    }

    @Test
    public void onDataSetChanged_reloadsFromPrefs() throws Exception {
        // Initially empty
        assertEquals(0, getCount());

        // Add a notification
        JSONArray arr = new JSONArray();
        JSONObject n = new JSONObject();
        n.put("id", "notification_60_1");
        n.put("name", "New notif");
        n.put("enabled", true);
        n.put("scheduledAt", futureTs());
        arr.put(n);
        NotifUtils.saveNotificationsJson(context, arr.toString());

        // onDataSetChanged should reload
        reload();
        assertEquals(1, getCount());
    }
}
