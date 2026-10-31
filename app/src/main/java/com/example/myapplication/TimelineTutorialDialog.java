package com.example.myapplication;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Acknowledgement belongs to the signed-in local profile, not an individual entry. */
public class TimelineTutorialDialog extends DialogFragment {
    public static final String TAG="timeline_tutorial";
    public static final String DISMISSED="timeline_tutorial_dismissed";
    private TimelineDemoView demo;

    public static boolean acknowledged(Context context,long owner) {
        return context.getSharedPreferences("timeline_help",Context.MODE_PRIVATE)
                .getBoolean("acknowledged_"+owner,false);
    }
    public static TimelineTutorialDialog create(long owner) {
        TimelineTutorialDialog dialog=new TimelineTutorialDialog();
        Bundle args=new Bundle();args.putLong("owner",owner);dialog.setArguments(args);return dialog;
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        MaterialAlertDialogBuilder builder=new MaterialAlertDialogBuilder(requireContext());
        View content=getLayoutInflater().cloneInContext(builder.getContext())
                .inflate(R.layout.dialog_timeline_tutorial,null);
        demo=content.findViewById(R.id.timelineTutorialDemo);
        content.findViewById(R.id.timelineTutorialClose).setOnClickListener(v->dismiss());
        content.findViewById(R.id.timelineTutorialGotIt).setOnClickListener(v->{
            long owner=requireArguments().getLong("owner",-1);
            if(owner>=0)requireContext().getSharedPreferences("timeline_help",Context.MODE_PRIVATE)
                    .edit().putBoolean("acknowledged_"+owner,true).apply();
            dismiss();
        });
        return builder.setView(content).create();
    }
    @Override public void onStart(){super.onStart();if(demo!=null)demo.play();}
    @Override public void onStop(){if(demo!=null)demo.pause();super.onStop();}
    @Override public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if(isAdded()&&!requireActivity().isChangingConfigurations())
            getParentFragmentManager().setFragmentResult(DISMISSED,new Bundle());
    }
}
