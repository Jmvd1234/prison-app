package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

public class Profile_RecyclerViewAdapter
        extends RecyclerView.Adapter<
        Profile_RecyclerViewAdapter.MyViewHolder> {

    interface OnDeleteEntryListener { void onDeleteEntry(profileModel profile); }
    private final OnDeleteEntryListener deleteEntryListener;

    interface ExportSelection { boolean selecting(); boolean selected(int id); void toggle(int id,boolean selected); }
    private ExportSelection exportSelection;
    void setExportSelection(ExportSelection value){exportSelection=value;}

    Context context;
    ArrayList<profileModel> profileModels;


    public Profile_RecyclerViewAdapter(
            Context context,
            ArrayList<profileModel> profileModels) {

        this(context, profileModels, null);
    }

    public Profile_RecyclerViewAdapter(Context context, ArrayList<profileModel> profileModels,
            OnDeleteEntryListener deleteEntryListener) {
        this.deleteEntryListener = deleteEntryListener;
        this.context = context;
        this.profileModels = profileModels;
    }


    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        //inflater turns the xml row layout into a view that can be dynamically displayed by java
        LayoutInflater inflater =
                LayoutInflater.from(context);

        View view = inflater.inflate(
                R.layout.recycler_view_row,
                parent,
                false
        );

        return new MyViewHolder(view);
    }


    @Override
    public void onBindViewHolder(
            @NonNull MyViewHolder holder,
            int position) {

        profileModel profile =
                profileModels.get(position);



        boolean selecting=exportSelection!=null&&exportSelection.selecting();
        boolean selected=selecting&&exportSelection.selected(profile.getProfileID());
        holder.itemView.findViewById(R.id.profileCardFace).setAlpha(selecting&&!selected?0.5f:1f);
        android.widget.CheckBox checkbox=holder.itemView.findViewById(R.id.exportProfileCheck);
        checkbox.setOnCheckedChangeListener(null);checkbox.setVisibility(selecting?View.VISIBLE:View.GONE);
        checkbox.setChecked(selected);checkbox.setContentDescription(context.getString(R.string.select_export_profile,profile.getFirstName(),profile.getLastName()));
        checkbox.setOnCheckedChangeListener((button,checked)->{if(exportSelection!=null)exportSelection.toggle(profile.getProfileID(),checked);});
        holder.itemView.setOnClickListener(selecting?v->checkbox.setChecked(!checkbox.isChecked()):null);
        holder.editEntry.setEnabled(!selecting);holder.expandBreakdown.setEnabled(!selecting);
        //profile details
        ProfilePhotoLoader.show(holder.imageView, profile.getImagePath(), R.drawable.profile_placeholder);
        holder.imageView.setContentDescription(context.getString(R.string.profile_photo_description,
                profile.getFirstName(), profile.getLastName()));

        holder.firstName.setText("First Name: " + profile.getFirstName());

        holder.lastName.setText("Last Name: " + profile.getLastName());


        //dates:
        holder.leaArrestDate.setText("LEA Arrest Date: " + profile.getLEAArrestDate());


        if (profile.isNotCommittedToJail()) {
            holder.commitmentDate.setText("Prison Commitment Date: \n" + profile.getCommitmentDate());

        }
        else {

            holder.commitmentDate.setText("Jail Commitment Date: \n" + profile.getCommitmentDate());
        }


        //time remaining
        // Only display the exact number of days if the calculation
        // had enough information to produce a valid result.
        String minRaw = profile.isCalculationAvailable()
                ? " OR " + profile.getMinimumExactDays() + " days"
                : "";

        String maxRaw = profile.isCalculationAvailable()
                ? " OR " + profile.getMaximumExactDays() + " days"
                : "";


        holder.minTime.setText(
                (profile.getServiceChargeNumber()>0?"Charge #"+profile.getServiceChargeNumber()+" · Minimum balance:\n":"Minimum estimated balance:\n")
                        + profile.getMinTimeRemaining()
                        + minRaw
        );

        holder.maxTime.setText(
                (profile.getServiceChargeNumber()>0?"Charge #"+profile.getServiceChargeNumber()+" · Maximum balance:\n":"Maximum estimated balance:\n")
                        + profile.getMaxTimeRemaining()
                        + maxRaw
        );


        // Keep cards compact; calculation notes are not appended to each profile.
        holder.suggestedAction.setText("Action: " + profile.getSuggestedAction());
        holder.suggestedAction.setTextColor(androidx.core.content.ContextCompat.getColor(context,
                profile.isClemencyRecommended()?R.color.laya_danger:R.color.laya_text));
        holder.suggestedAction.setVisibility(profile.getSuggestedAction().isEmpty()
                ? View.GONE : View.VISIBLE);



        holder.deleteEntry.setVisibility(selecting||deleteEntryListener == null ? View.GONE : View.VISIBLE);
        holder.deleteEntry.setContentDescription(context.getString(
                R.string.delete_profile_entry, profile.getFirstName(), profile.getLastName()));
        holder.deleteEntry.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION || deleteEntryListener == null) return;
            deleteEntryListener.onDeleteEntry(profileModels.get(currentPosition));
        });

        holder.editEntry.setContentDescription(context.getString(
                R.string.edit_profile_entry, profile.getFirstName(), profile.getLastName()));
        // Resolve the current row at click time, including after sorting/recycling.
        holder.editEntry.setOnClickListener(v -> {

            int currentPosition =
                    holder.getBindingAdapterPosition();

            if (currentPosition
                    == RecyclerView.NO_POSITION) {
                return;
            }

            profileModel clickedProfile =
                    profileModels.get(currentPosition);

            Intent intent =
                    new Intent(
                            context,
                            editProfileActivity.class
                    );

            intent.putExtra(
                    "profileID",
                    clickedProfile.getProfileID()
            );

            context.startActivity(intent);
        });


        holder.expandBreakdown.setContentDescription(context.getString(
                R.string.view_profile_breakdown, profile.getFirstName(), profile.getLastName()));
        holder.expandBreakdown.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return;

            // Resolve the current row at click time, including after sorting/recycling.
            Intent intent = new Intent(context, BreakdownActivity.class);
            intent.putExtra(BreakdownActivity.EXTRA_PROFILE_ID,
                    profileModels.get(currentPosition).getProfileID());
            context.startActivity(intent);
        });
    }


    @Override
    public void onViewRecycled(@NonNull MyViewHolder holder) {
        ProfilePhotoLoader.show(holder.imageView, null, R.drawable.profile_placeholder);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return profileModels.size();
    }


    public static class MyViewHolder
            extends RecyclerView.ViewHolder {

        ImageView imageView;

        TextView firstName;
        TextView lastName;

        TextView leaArrestDate;
        TextView commitmentDate;

        TextView minTime;
        TextView maxTime;

        TextView suggestedAction;

        TextView exportPDF;
        View deleteEntry;
        MaterialButton editEntry;
        MaterialButton expandBreakdown;


        //holds all the different views for one recycler view row
        public MyViewHolder(
                @NonNull View itemView) {

            super(itemView);


            imageView =
                    itemView.findViewById(
                            R.id.imageView
                    );

            firstName =
                    itemView.findViewById(
                            R.id.firstNameBox
                    );

            lastName =
                    itemView.findViewById(
                            R.id.lastNameBox
                    );

            leaArrestDate =
                    itemView.findViewById(
                            R.id.leaArrestDateBox
                    );

            commitmentDate =
                    itemView.findViewById(
                            R.id.commitmentDateBox
                    );

            minTime =
                    itemView.findViewById(
                            R.id.minTimeBox
                    );

            maxTime =
                    itemView.findViewById(
                            R.id.maxTimeBox
                    );

            suggestedAction =
                    itemView.findViewById(
                            R.id.actionBox
                    );


            deleteEntry = itemView.findViewById(R.id.deleteProfileEntryButton);

            editEntry = itemView.findViewById(R.id.editEntryButton);

            expandBreakdown =
                    itemView.findViewById(
                            R.id.expandBreakdownBox
                    );
        }
    }
}
