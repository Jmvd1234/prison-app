package com.example.myapplication;

public class profileModel {

    private int profileID;

    private String firstName;
    private String lastName;

    private String leaArrestDate;

    private String commitmentDate;
    private boolean notCommittedToJail;

    private String minTimeRemaining;
    private String maxTimeRemaining;

    private long minRemainingDays;
    private long maxRemainingDays;

    private String suggestedAction;

    private int image;


    public profileModel(
            int profileID,
            String firstName,
            String lastName,
            String leaArrestDate,
            String commitmentDate,
            boolean notCommittedToJail,
            String minTimeRemaining,
            String maxTimeRemaining,
            long minRemainingDays,
            long maxRemainingDays,
            String suggestedAction,
            int image) {

        this.profileID = profileID;

        this.firstName = firstName;
        this.lastName = lastName;

        this.leaArrestDate = leaArrestDate;

        this.commitmentDate = commitmentDate;
        this.notCommittedToJail = notCommittedToJail;

        this.minTimeRemaining = minTimeRemaining;
        this.maxTimeRemaining = maxTimeRemaining;

        this.minRemainingDays = minRemainingDays;
        this.maxRemainingDays = maxRemainingDays;

        this.suggestedAction = suggestedAction;

        this.image = image;
    }


    public int getProfileID() {
        return profileID;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getLEAArrestDate() {
        return leaArrestDate;
    }

    public String getCommitmentDate() {
        return commitmentDate;
    }

    public boolean isNotCommittedToJail() {
        return notCommittedToJail;
    }

    public String getMinTimeRemaining() {
        return minTimeRemaining;
    }

    public String getMaxTimeRemaining() {
        return maxTimeRemaining;
    }

    public long getMinRemainingDays() {
        return minRemainingDays;
    }

    public long getMaxRemainingDays() {
        return maxRemainingDays;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public int getImage() {
        return image;
    }
}