package org.tagammu.gharbia;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CheckWorker extends Worker {
    static final String URL_DATA = "https://raw.githubusercontent.com/elsayedabosama1-cmyk/koddi-infographic/tagammu-app/data/public-data.json";
    static final String CH = "news";

    public CheckWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    // returns {count of posts newer than since, max ts}
    static void scan(Object o, long since, long[] r) {
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) scan(a.opt(i), since, r);
        } else if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            long ts = j.optLong("ts", 0);
            if (ts > 0) {
                if (ts > r[1]) r[1] = ts;
                if (ts > since) r[0]++;
            }
            java.util.Iterator<String> it = j.keys();
            while (it.hasNext()) scan(j.opt(it.next()), since, r);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(URL_DATA + "?t=" + System.currentTimeMillis()).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONObject db = new JSONObject(bo.toString("UTF-8"));
            SharedPreferences sp = getApplicationContext().getSharedPreferences("notif", Context.MODE_PRIVATE);
            long last = sp.getLong("last", -1);
            long[] r = new long[]{0, 0};
            if (last < 0) {
                scan(db, Long.MAX_VALUE, r);
                sp.edit().putLong("last", Math.max(r[1], System.currentTimeMillis())).apply();
                return Result.success();
            }
            scan(db, last, r);
            if (r[0] > 0) {
                sp.edit().putLong("last", r[1]).apply();
                notifyNew(getApplicationContext(), (int) r[0]);
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    static void notifyNew(Context ctx, int count) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(new NotificationChannel(CH, "جديد في التطبيق", NotificationManager.IMPORTANCE_DEFAULT));
        Intent i = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String txt = count == 1 ? "فيه حاجة جديدة نزلت في التطبيق" : "فيه " + count + " حاجات جديدة نزلت في التطبيق";
        nm.notify(1, new NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("التجمع – الغربية")
                .setContentText(txt)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }
}
