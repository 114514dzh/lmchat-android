package im.lilmouse.chat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Splash extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(UI.primary(this));
        ImageView logo = new ImageView(this);
        try { logo.setImageResource(R.drawable.ic_launcher); } catch (Throwable t) {}
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int sz = UI.dp(this, 84);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(sz, sz);
        root.addView(logo, llp);
        TextView name = UI.label(this, "LM Chat", UI.onPrimary(this), 24, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, UI.dp(this, 12), 0, 0);
        root.addView(name, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(320).start();
        final Intent next;
        if (P.hasAccount(this)) next = new Intent(this, Conversations.class);
        else next = new Intent(this, Setup.class);
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        root.postDelayed(new Runnable() { public void run() {
            startActivity(next); finish();
        }}, 650);
    }
}
