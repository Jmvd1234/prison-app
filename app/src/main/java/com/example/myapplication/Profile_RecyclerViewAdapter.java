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

public class Profile_RecyclerViewAdapter extends RecyclerView.Adapter<Profile_RecyclerViewAdapter.MyViewHolder> {
    Context context;
    ArrayList<profileModel> profileModels;

    public  Profile_RecyclerViewAdapter(Context context, ArrayList<profileModel> profileModels) {
        this.context = context;
        this.profileModels = profileModels;
    }

    @NonNull
    @Override
    public Profile_RecyclerViewAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        //Where you "inflate" the layout, give a look to your rows
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.recycler_view_row, parent, false);
        return new Profile_RecyclerViewAdapter.MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Profile_RecyclerViewAdapter.MyViewHolder holder, int position) {
        //Bind -> Assign values to each of your rows that you created in the layout file
        //based on position of recyclerview
        holder.imageView.setImageResource(profileModels.get(position).getImage());
        holder.firstName.setText("First Name: " + profileModels.get(position).getFirstName());
        holder.lastName.setText("Last Name: " + profileModels.get(position).getLastName());
        holder.charge.setText("Charge: " + profileModels.get(position).getCharge());
        holder.arrestDate.setText("Arrest Date: " + profileModels.get(position).getArrestDate());
        holder.timeLeft.setText("Time Left: " + profileModels.get(position).getTimeLeft());
        holder.timeOverdue.setText("Time Overdue: " + profileModels.get(position).getTimeOverdue());
        holder.suggestedAction.setText("Action: " + profileModels.get(position).getSuggestedAction());

        holder.imageView.setOnClickListener(v -> {
            Intent intent = new Intent(context, editProfileActivity.class);
            intent.putExtra("profileID", profileModels.get(position).getProfileID());
            context.startActivity(intent);
        });

    }

    @Override
    public int getItemCount() {
        return profileModels.size();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder{
        //Kinda like onCreate: grabs views from recycler_view_row file
        ImageView imageView;
        TextView firstName, lastName, charge, arrestDate, timeLeft, timeOverdue, suggestedAction;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

            imageView = itemView.findViewById(R.id.imageView);
            firstName = itemView.findViewById(R.id.firstNameBox);
            lastName = itemView.findViewById(R.id.lastNameBox);
            charge = itemView.findViewById(R.id.chargeBox);
            arrestDate = itemView.findViewById(R.id.arrestDateBox);
            timeLeft = itemView.findViewById(R.id.timeLeftBox);
            timeOverdue = itemView.findViewById(R.id.timeOverdueBox);
            suggestedAction = itemView.findViewById(R.id.actionBox);


        }
    }
}
