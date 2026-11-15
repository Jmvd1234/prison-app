package com.example.myapplication;

import android.app.Activity;
import android.view.View;
import android.widget.Toast;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Shared, per-entry cutoff setting in the summary and detailed breakdown. */
final class ServiceLimitSettings {
    private static final ExecutorService SAVES=Executors.newSingleThreadExecutor();

    static void bind(Activity host,View content,Profile profile,Runnable onSaving,Consumer<Profile> onSaved){
        content.findViewById(R.id.serviceLimitSettings).setVisibility(View.VISIBLE);
        MaterialSwitch toggle=content.findViewById(R.id.considerAllowancesInServiceLimit);
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(profile.considerAllowancesInServiceLimit);
        toggle.setEnabled(true);
        toggle.setOnCheckedChangeListener((button,enabled)->{
            toggle.setEnabled(false);onSaving.run();
            SAVES.execute(()->{
                Profile saved=null;
                try{saved=AppDatabase.getInstance(host).ProfileDao().setServiceLimitMode(profile.profileID,profile.userID,enabled);}
                catch(RuntimeException ignored){}
                final Profile completed=saved;
                host.runOnUiThread(()->{
                    if(host.isFinishing()||host.isDestroyed())return;
                    if(host.getSharedPreferences("user_session",0).getLong("currentUserID",-1)!=profile.userID)return;
                    if(completed==null)Toast.makeText(host,R.string.service_limit_mode_failed,Toast.LENGTH_LONG).show();
                    onSaved.accept(completed==null?profile:completed);
                });
            });
        });
    }
}
