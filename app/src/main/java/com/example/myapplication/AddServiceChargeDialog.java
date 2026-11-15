package com.example.myapplication;

import android.app.Dialog;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Choose the previous endpoint; the next charge always retains both terms. */
public final class AddServiceChargeDialog extends DialogFragment {
    public static final String TAG="add_service_charge", RESULT="service_charge_choice";
    public static AddServiceChargeDialog create(SequentialSentencePlan plan,java.time.LocalDate observation) {
        AddServiceChargeDialog dialog=new AddServiceChargeDialog(); Bundle args=new Bundle();
        com.example.myapplication.db.ProfileCharge next=plan.ordered.get(plan.active().number);
        args.putString("observation",observation.toString());
        args.putInt("number",plan.active().number+1);
        args.putString("minimum",length(next.minYears,next.minMonths,next.minDays));
        args.putString("maximum",length(next.maxYears,next.maxMonths,next.maxDays));
        args.putString("fromMin",plan.active().minimum.format(SentenceCalculator.DISPLAY_DATE));
        args.putString("fromMax",plan.active().maximum.format(SentenceCalculator.DISPLAY_DATE));
        args.putBoolean("canMin",plan.active().minimum.isBefore(plan.capDate));
        args.putBoolean("canMax",plan.active().maximum.isBefore(plan.capDate));
        args.putString("earliest",plan.active().start.toString());
        args.putString("latest",plan.capDate.minusDays(1).toString());
        args.putString("expected",plan.choices);
        dialog.setArguments(args);return dialog;
    }
    private static String length(int y,int m,int d){return y+"y, "+m+"m, "+d+"d";}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        Bundle args=requireArguments();
        androidx.fragment.app.Fragment restored=getChildFragmentManager().findFragmentByTag("custom_end_date");
        if(restored instanceof com.google.android.material.datepicker.MaterialDatePicker<?>)
            connectCustomPicker((com.google.android.material.datepicker.MaterialDatePicker<Long>)restored);
        LinearLayout body=new LinearLayout(requireContext());body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(12),dp(24),dp(16));
        LinearLayout heading=new LinearLayout(requireContext());heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title=new TextView(requireContext());title.setText(getString(R.string.service_add_number,args.getInt("number")));title.setTextSize(20);title.setTextColor(Color.rgb(36,55,65));
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=new TextView(requireContext());close.setText("×");close.setTextSize(28);close.setGravity(17);close.setContentDescription(getString(R.string.service_close));close.setOnClickListener(v->dismiss());close.setFocusable(true);heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(heading);
        TextView hint=new TextView(requireContext());hint.setText(R.string.service_choose_term);body.addView(hint);
        TextView min=new TextView(requireContext()),max=new TextView(requireContext());
        min.setId(R.id.serviceMinimumChoice);max.setId(R.id.serviceMaximumChoice);
        min.setText(getString(R.string.service_minimum_length,args.getString("minimum")));
        max.setText(getString(R.string.service_maximum_length,args.getString("maximum")));
        addLengthBox(body,min,Color.rgb(255,248,222),Color.rgb(255,224,138),Color.rgb(112,81,0));
        addLengthBox(body,max,Color.rgb(255,238,240),Color.rgb(239,176,183),Color.rgb(143,31,44));
        ((LinearLayout.LayoutParams)max.getLayoutParams()).bottomMargin=dp(12);
        MaterialButton fromMin=new MaterialButton(requireContext()),fromMax=new MaterialButton(requireContext());
        fromMin.setId(R.id.serviceAddToMinimum);fromMax.setId(R.id.serviceAddToMaximum);
        fromMin.setText(getString(R.string.service_add_minimum)+"\n"+args.getString("fromMin"));
        fromMax.setText(getString(R.string.service_add_maximum)+"\n"+args.getString("fromMax"));
        fromMin.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(255,204,51)));fromMin.setTextColor(Color.rgb(55,42,0));
        fromMax.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(182,30,43)));fromMax.setTextColor(Color.WHITE);
        fromMin.setAllCaps(false);fromMax.setAllCaps(false);body.addView(fromMin);body.addView(fromMax);
        fromMin.setEnabled(args.getBoolean("canMin"));fromMax.setEnabled(args.getBoolean("canMax"));
        fromMin.setOnClickListener(v->submit(false));fromMax.setOnClickListener(v->submit(true));
        MaterialButton custom=new MaterialButton(requireContext());
        custom.setId(R.id.serviceAddToCustom);custom.setText(R.string.service_add_custom);custom.setAllCaps(false);
        custom.setOnClickListener(v->showCustomPicker());body.addView(custom);
        TextView customHint=new TextView(requireContext());customHint.setText(R.string.service_custom_hint);
        customHint.setTextSize(12);body.addView(customHint);
        if(!args.getBoolean("canMax")){TextView cap=new TextView(requireContext());cap.setText(R.string.service_endpoint_at_limit);body.addView(cap);}
        android.widget.ScrollView scroll=new android.widget.ScrollView(requireContext());scroll.addView(body);
        return new MaterialAlertDialogBuilder(requireContext()).setView(scroll).create();
    }
    private void addLengthBox(LinearLayout body,TextView label,int fill,int border,int text){
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(fill);background.setCornerRadius(dp(10));background.setStroke(dp(1),border);
        label.setBackground(background);label.setTextColor(text);
        label.setPadding(dp(12),dp(12),dp(12),dp(12));
        label.setMinHeight(dp(56));label.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(12);body.addView(label,params);
    }

    private long millis(String date){return java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();}
    private void showCustomPicker(){
        if(getChildFragmentManager().findFragmentByTag("custom_end_date")!=null)return;
        long from=millis(requireArguments().getString("earliest")),to=millis(requireArguments().getString("latest"));
        long selection=Math.max(from,Math.min(to,millis(requireArguments().getString("observation"))));
        com.google.android.material.datepicker.CalendarConstraints constraints=new com.google.android.material.datepicker.CalendarConstraints.Builder()
                .setStart(from).setEnd(to).setOpenAt(selection)
                .setValidator(com.google.android.material.datepicker.CompositeDateValidator.allOf(java.util.Arrays.asList(
                        com.google.android.material.datepicker.DateValidatorPointForward.from(from),
                        com.google.android.material.datepicker.DateValidatorPointBackward.before(to)))).build();
        com.google.android.material.datepicker.MaterialDatePicker<Long> picker=com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.service_custom_end).setSelection(selection).setCalendarConstraints(constraints).build();
        connectCustomPicker(picker);picker.show(getChildFragmentManager(),"custom_end_date");
    }
    private void connectCustomPicker(com.google.android.material.datepicker.MaterialDatePicker<Long> picker){
        picker.clearOnPositiveButtonClickListeners();
        picker.addOnPositiveButtonClickListener(value->{
            if(value==null)return;
            if(value<millis(requireArguments().getString("earliest"))||value>millis(requireArguments().getString("latest")))return;
            Bundle result=new Bundle();result.putString("observation",requireArguments().getString("observation"));
            result.putString("customDate",java.time.Instant.ofEpochMilli(value).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString());
            result.putString("expected",requireArguments().getString("expected"));
            getParentFragmentManager().setFragmentResult(RESULT,result);dismiss();
        });
    }

    private void submit(boolean previousMax){

        Bundle result=new Bundle();result.putString("observation",requireArguments().getString("observation"));result.putBoolean("fromMaximum",previousMax);result.putString("expected",requireArguments().getString("expected"));
        getParentFragmentManager().setFragmentResult(RESULT,result);dismiss();
    }
}
