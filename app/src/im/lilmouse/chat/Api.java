package im.lilmouse.chat;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Api {
    public static class Env { public long id; public String payload; }

    private static HttpURLConnection open(String url, String method, String auth) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestMethod(method);
        if (auth != null) c.setRequestProperty("Authorization", "Bearer " + auth);
        return c;
    }

    private static String drain(HttpURLConnection c) throws Exception {
        InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
        }
        return new String(bo.toByteArray(), "UTF-8");
    }

    private static void check(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + drain(c));
    }

    public static String get(String url, String auth) throws Exception {
        HttpURLConnection c = open(url, "GET", auth);
        check(c);
        String s = drain(c);
        c.disconnect();
        return s;
    }

    public static String post(String url, String auth, String data) throws Exception {
        HttpURLConnection c = open(url, "POST", auth);
        c.setDoOutput(true);
        byte[] b = data.getBytes("UTF-8");
        c.setFixedLengthStreamingMode(b.length);
        c.setRequestProperty("Content-Type", "application/json");
        OutputStream os = c.getOutputStream();
        os.write(b);
        os.close();
        check(c);
        String s = drain(c);
        c.disconnect();
        return s;
    }

    public static byte[] getBytes(String url, String auth) throws Exception {
        HttpURLConnection c = open(url, "GET", auth);
        check(c);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        c.disconnect();
        return bo.toByteArray();
    }

    public static String putBytes(String url, String auth, byte[] data) throws Exception {
        HttpURLConnection c = open(url, "POST", auth);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(data.length);
        c.setRequestProperty("Content-Type", "application/octet-stream");
        OutputStream os = c.getOutputStream();
        os.write(data);
        os.close();
        check(c);
        String s = drain(c);
        c.disconnect();
        return s;
    }

    // ---------- high level ----------

    public static void register(String server, String acct, String token,
                                String name, String idKeyB64, String spkB64, JsonArray oneTime) throws Exception {
        JsonObject o = new JsonObject();
        o.add("accountId", acct);
        o.add("token", token);
        o.add("displayName", name);
        o.add("identityKey", idKeyB64);
        JsonObject spk = new JsonObject();
        spk.add("keyId", 1);
        spk.add("blob", spkB64);
        o.add("signedPrekey", spk);
        o.add("oneTimePrekeys", oneTime);
        post(server + "/v1/register", null, o.toString());
    }

    public static String prekeys(String server, String auth, String id) throws Exception {
        return get(server + "/v1/prekeys/" + id, auth);
    }

    public static void send(String server, String auth, List<String> dests, List<String> payloads) throws Exception {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        for (int i = 0; i < dests.size(); i++) {
            JsonObject d = new JsonObject();
            d.add("accountId", dests.get(i));
            d.add("payload", payloads.get(i));
            a.add(d);
        }
        o.add("dests", a);
        post(server + "/v1/messages", auth, o.toString());
    }

    public static List<Env> fetch(String server, String auth, long after) throws Exception {
        String s = get(server + "/v1/messages?after=" + after + "&limit=100", auth);
        List<Env> out = new ArrayList<Env>();
        JsonArray arr = Json.parse(s).asObject().get("envs").asArray();
        for (JsonValue v : arr) {
            JsonObject o = v.asObject();
            Env e = new Env();
            e.id = o.getLong("id", 0);
            e.payload = o.getString("payload", "");
            out.add(e);
        }
        return out;
    }

    public static void ack(String server, String auth, List<Long> ids) throws Exception {
        JsonArray a = new JsonArray();
        for (Long i : ids) a.add(i.longValue());
        JsonObject o = new JsonObject();
        o.add("ids", a);
        post(server + "/v1/ack", auth, o.toString());
    }

    public static String putBlob(String server, String auth, byte[] b) throws Exception {
        String s = putBytes(server + "/v1/blob", auth, b);
        return Json.parse(s).asObject().getString("blobId", "");
    }

    public static byte[] getBlob(String server, String auth, String id) throws Exception {
        return getBytes(server + "/v1/blob/" + id, auth);
    }

    // ---------- 群目录 ----------

    public static void groupReg(String server, String auth, String gid, String name) throws Exception {
        JsonObject o = new JsonObject();
        o.add("gid", gid).add("name", name);
        post(server + "/v1/groups/reg", auth, o.toString());
    }

    public static String groupSearch(String server, String auth, String q) throws Exception {
        return get(server + "/v1/groups/search?q=" + java.net.URLEncoder.encode(q, "UTF-8"), auth);
    }

    public static void groupDel(String server, String auth, String gid) throws Exception {
        JsonObject o = new JsonObject();
        o.add("gid", gid);
        post(server + "/v1/groups/del", auth, o.toString());
    }
}
