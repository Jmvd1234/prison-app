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

import java.util.ArrayList;

public class Profile_RecyclerViewAdapter
        extends RecyclerView.Adapter<
        Profile_RecyclerViewAdapter.MyViewHolder> {

    Context context;
    ArrayList<profileModel> profileModels;


    public Profile_RecyclerViewAdapter(
            Context context,
            ArrayList<profileModel> profileModels) {

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



        //profile details
        holder.imageView.setImageResource(profile.getImage());

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
        holder.minTime.setText("Minimum Time Remaining: \n" + profile.getMinTimeRemaining() + " OR "  + profile.getMinRemainingDays() + " Days ");

        holder.maxTime.setText("Maximum Time Remaining: \n" + profile.getMaxTimeRemaining() + " OR "  + profile.getMaxRemainingDays() + " Days ");


        //suggested action
        holder.suggestedAction.setText("Action: " + profile.getSuggestedAction());



        //allowing you to edit the profile when you click the image
        //and sending the already present profileID to the new intent
        holder.imageView.setOnClickListener(v -> {

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


        /*
         * expandBreakdown currently has no click listener.
         *
         * Later:
         * View Breakdown ▼
         *
         * will expand/collapse the calculation details.
         */
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
        TextView expandBreakdown;


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

            exportPDF =
                    itemView.findViewById(
                            R.id.exportPDFBox
                    );

            expandBreakdown =
                    itemView.findViewById(
                            R.id.expandBreakdownBox
                    );
        }
    }
}