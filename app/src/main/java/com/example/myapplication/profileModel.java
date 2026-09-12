package com.example.myapplication;

public class profileModel {
    String firstName;
    String lastName;
    String charge;
    String arrestDate;
    String timeLeft;
    String timeOverdue;
    String suggestedAction;
    int profileID;
    //storing profile ID so can be used to edit it in the future
    int image;

    public profileModel(int profileID, String firstName, String lastName, String charge, String arrestDate, String timeLeft, String timeOverdue, String suggestedAction, int image) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.charge = charge;
        this.arrestDate = arrestDate;
        this.timeLeft = timeLeft;
        this.timeOverdue = timeOverdue;
        this.suggestedAction = suggestedAction;
        this.image = image;
        this.profileID = profileID;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getCharge() {
        return charge;
    }

    public String getArrestDate() {
        return arrestDate;
    }

    public String getTimeLeft() {
        return timeLeft;
    }

    public String getTimeOverdue() {
        return timeOverdue;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public int getImage() {
        return image;
    }

    public int getProfileID() { return profileID; }
}
