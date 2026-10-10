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

    // Whether the release calculation had enough information to produce a result
    private boolean calculationAvailable = false;

    // Exact day values as text.
// These can contain decimals later if CPI is credited at 4/5.
    private String minimumExactDays = "N/A";
    private String maximumExactDays = "N/A";

    // Extra explanation/warnings shown with the calculated result
    private String calculationNotes = "";

    private String suggestedAction;

    private String imagePath;


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
            String imagePath) {

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

        this.imagePath = imagePath;
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

    public boolean isCalculationAvailable() {
        return calculationAvailable;
    }

    public void setCalculationAvailable(boolean calculationAvailable) {
        this.calculationAvailable = calculationAvailable;
    }

    public String getMinimumExactDays() {
        return minimumExactDays;
    }

    public void setMinimumExactDays(String minimumExactDays) {
        this.minimumExactDays = minimumExactDays;
    }

    public String getMaximumExactDays() {
        return maximumExactDays;
    }

    public void setMaximumExactDays(String maximumExactDays) {
        this.maximumExactDays = maximumExactDays;
    }

    public String getCalculationNotes() {
        return calculationNotes;
    }

    public void setCalculationNotes(String calculationNotes) {
        this.calculationNotes = calculationNotes;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public String getImagePath() {
        return imagePath;
    }
}
