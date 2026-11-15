package com.example.myapplication;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;

/** Popup shell; table contents are shared through view_sentence_breakdown.xml. */
public class ProfileBreakdownDialogFragment extends DialogFragment {

    public static final String TAG = "profile_breakdown";

    // Keep the save-result API while sharing rendering with BreakdownActivity.
    public static Bundle createArguments(String minimum, String maximum,
            SentenceCalculator.Result result) {
        return BreakdownViewBinder.createArguments(minimum, maximum, result);
    }

    public static ProfileBreakdownDialogFragment newInstance(Bundle arguments) {
        ProfileBreakdownDialogFragment fragment = new ProfileBreakdownDialogFragment();
        fragment.setArguments(new Bundle(arguments));
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        // Keep the standard rounded dialog shape while matching the app's LAYA palette.
        if (builder.getBackground() instanceof MaterialShapeDrawable) {
            ((MaterialShapeDrawable) builder.getBackground()).setFillColor(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.laya_surface)));
        }
        View content = getLayoutInflater().cloneInContext(builder.getContext())
                .inflate(R.layout.dialog_profile_breakdown, null);

        BreakdownViewBinder.bind(content, requireArguments());
        content.findViewById(R.id.closeBreakdownButton).setOnClickListener(v -> dismiss());

        View details=content.findViewById(R.id.openDetailedBreakdown);
        int id=requireArguments().getInt(BreakdownActivity.EXTRA_PROFILE_ID,-1);
        details.setVisibility(id>0?View.VISIBLE:View.GONE);
        if(id>0){
            com.example.myapplication.db.Profile profile=com.example.myapplication.db.AppDatabase.getInstance(requireContext()).ProfileDao().getProfileByProfileID(id);
            long owner=requireContext().getSharedPreferences("user_session",0).getLong("currentUserID",-1);
            if(profile!=null&&profile.userID==owner)bindLimitSetting(content,profile);
        }
        details.setOnClickListener(v->{
            startActivity(new android.content.Intent(requireContext(),BreakdownActivity.class)
                    .putExtra(BreakdownActivity.EXTRA_PROFILE_ID,id));
            dismiss();
        });
        return builder.setView(content).create();
    }

    private void bindLimitSetting(View content,com.example.myapplication.db.Profile profile){
        ServiceLimitSettings.bind(requireActivity(),content,profile,()->{},loaded->{
            if(!isAdded())return;
            java.time.LocalDate date=java.time.LocalDate.now();
            SentenceCalculator.Result result=SequentialSentencePlan.initialResult(loaded,date);
            if(result.available){
                Bundle values=BreakdownViewBinder.createArguments(
                        getString(R.string.breakdown_balance_value,result.minimumText(date),result.minRemainingDays),
                        getString(R.string.breakdown_balance_value,result.maximumText(date),result.maxRemainingDays),result);
                requireArguments().putAll(values);
                BreakdownViewBinder.bind(content,values);
            }
            bindLimitSetting(content,loaded);
        });
    }

}
