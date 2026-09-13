package im.lilmouse.chat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class Search extends Activity {
    private EditText input;
    private ListView list;
    private ResultAdapter ad;
    private List<Object[]> data = new ArrayList<Object[]>();
    private final Handler h = new Handler();
    private Runnable pending;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UI.background(this));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(UI.surface(this));
        int hp = UI.dp(this, 10);
        head.setPadding(UI.dp(this, 12), hp, UI.dp(this, 12), hp);
        TextView back = UI.label(this, "←", UI.textMain(this), 22, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(UI.rippleOnly());
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        head.addView(back, new LinearLayout.LayoutParams(UI.dp(this, 40), UI.dp(this, 40)));
        input = new EditText(this);
        input.setTextSize(15);
        input.setHint("搜索聊天记录");
        input.setTextColor(UI.textMain(this));
        input.setHintTextColor(UI.textSub(this));
        input.setBackground(UI.round(this, UI.background(this), 20));
        int ip = UI.dp(this, 10);
        input.setPadding(ip, ip, ip, ip);
        input.setSingleLine(true);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1);
        ilp.leftMargin = UI.dp(this, 8);
        head.addView(input, ilp);
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        list = new ListView(this);
        list.setDivider(null);
        ad = new ResultAdapter();
        list.setAdapter(ad);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                Object[] r = data.get(pos);
                Intent it = new Intent(Search.this, Chat.class);
                it.putExtra("conv", (String) r[3]);
                it.putExtra("name", (String) r[4]);
                it.putExtra("type", (Integer) r[5]);
                startActivity(it);
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, -1, 1));
        setContentView(UI.wrap(this, root, UI.surface(this), UI.background(this)));

        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            public void afterTextChanged(Editable s) {
                if (pending != null) h.removeCallbacks(pending);
                final String q = s.toString().trim();
                if (q.length() == 0) { data.clear(); ad.notifyDataSetChanged(); return; }
                pending = new Runnable() { public void run() { doSearch(q); } };
                h.postDelayed(pending, 300);
            }
        });
    }

    private void doSearch(final String q) {
        P.POOL.execute(new Runnable() {
            public void run() {
                final List<Object[]> r = Db.get(Search.this).search(q);
                runOnUiThread(new Runnable() { public void run() {
                    data = r;
                    ad.notifyDataSetChanged();
                }});
            }
        });
    }

    private class ResultAdapter extends BaseAdapter {
        public int getCount() { return data.size(); }
        public Object getItem(int p) { return data.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int p, View cv, ViewGroup pg) {
            Object[] r = data.get(p);
            String body = (String) r[0];
            long ts = (Long) r[2];
            String convId = (String) r[3];
            String convName = (String) r[4];

            LinearLayout row = new LinearLayout(Search.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UI.rippleOnly());
            row.setPadding(UI.dp(Search.this, 14), UI.dp(Search.this, 8), UI.dp(Search.this, 12), UI.dp(Search.this, 8));
            row.addView(UI.avatar(Search.this, convName, convId, 40), new LinearLayout.LayoutParams(-2, -2));

            LinearLayout mid = new LinearLayout(Search.this);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView n = UI.label(Search.this, convName, UI.textMain(Search.this), 15, true);
            TextView b = UI.label(Search.this, body, UI.textSub(Search.this), 13, false);
            b.setSingleLine(true);
            mid.addView(n, new LinearLayout.LayoutParams(-2, -2));
            mid.addView(b, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1);
            mlp.leftMargin = UI.dp(Search.this, 10);
            row.addView(mid, mlp);

            TextView t = UI.label(Search.this, UI.timeStr(ts), UI.textSub(Search.this), 11, false);
            row.addView(t, new LinearLayout.LayoutParams(-2, -2));
            return row;
        }
    }
}
