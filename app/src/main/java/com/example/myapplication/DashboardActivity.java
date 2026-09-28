package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileDao;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.temporal.ChronoUnit;


public class DashboardActivity extends AppCompatActivity {

    AppDatabase db;
    ProfileDao profileDao;
    SharedPreferences sp;

    ArrayList<profileModel> profileModels =
            new ArrayList<>();

    Profile_RecyclerViewAdapter adapter;

    Spinner sortDropdown;
    Spinner orderDropdown;


    // Format used by the dates stored from newEntryActivity
    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern(
                    "MMM d, uuuu",
                    Locale.US
            );


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });


        db = AppDatabase.getInstance(getApplicationContext());

        profileDao = db.ProfileDao();

        sp = getSharedPreferences("user_session", Context.MODE_PRIVATE);



        //setting up the recycler view
        RecyclerView recyclerView =
                findViewById(
                        R.id.profileRecyclerView
                );

        adapter =
                new Profile_RecyclerViewAdapter(
                        this,
                        profileModels
                );

        recyclerView.setAdapter(adapter);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );


        //setting up dropdowns
        sortDropdown =
                findViewById(
                        R.id.sortDropdown
                );

        orderDropdown =
                findViewById(
                        R.id.orderDropdown
                );


        String[] sortOptions = {
                "First Name",
                "Last Name",
                "Min.Time Remaining",
                "Max. Time Remaining",
                "LEA Arrest Date"
        };


        String[] orderOptions = {
                "Ascending",
                "Descending"
        };


        ArrayAdapter<String> sortAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        sortOptions
                );

        sortAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        sortDropdown.setAdapter(
                sortAdapter
        );


        ArrayAdapter<String> orderAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        orderOptions
                );

        orderAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        orderDropdown.setAdapter(
                orderAdapter
        );


        // Re-sort immediately whenever either dropdown changes
        sortDropdown.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        updateSort();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                }
        );


        orderDropdown.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        updateSort();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                }
        );
    }


    public void goHome(View v) {
        finish();
    }


    //setting up profile list

    private void setUpProfileModels() {

        List<Profile> profileList = profileDao.getProfilesByUserID(sp.getLong("currentUserID", -1));


        for (Profile profile : profileList) {


            //dates
            LocalDate observationDate =
                    stringToDate(
                            profile.observationDate
                    );

            LocalDate leaArrestDate =
                    stringToDate(
                            profile.leaArrestDate
                    );

            LocalDate commitmentDate =
                    stringToDate(
                            profile.commitmentDate
                    );

            long leaDays =
                    ChronoUnit.DAYS.between(
                            leaArrestDate,
                            commitmentDate
                    );
            //this is to get exact days between the two dates
            //so that there's no weird leap year stuff


            // BASE RELEASE DATES
            // Uses the anniversary method:
            // years -> months -> days

            LocalDate minimumBaseReleaseDate =
                    calculateBaseReleaseDate(
                            commitmentDate,
                            leaDays,
                            profile.minYears,
                            profile.minMonths,
                            profile.minDays
                    );


            LocalDate maximumBaseReleaseDate =
                    calculateBaseReleaseDate(
                            commitmentDate,
                            leaDays,
                            profile.maxYears,
                            profile.maxMonths,
                            profile.maxDays
                    );


            //calc whole months elapsed (for GCTA and TASTM)
            long wholeMonthsElapsed =
                    getWholeMonthsElapsed(
                            commitmentDate,
                            observationDate
                    );


            //calculating deductions
            long tastmDays = calculateTASTM(wholeMonthsElapsed);

            long gctaDays = calculateGCTA(wholeMonthsElapsed);


            long totalAllowanceDays = tastmDays + gctaDays;


            //Days added back
            // These increase the amount of time before release.

            long addedDays = (long) profile.deductionDays + profile.timeGapDays;


            //adjusted release dates
            LocalDate minimumReleaseDate =
                    minimumBaseReleaseDate
                            // Allowances shorten the sentence
                            .minusDays(
                                    totalAllowanceDays
                            )
                            // These extend the sentence
                            .plusDays(
                                    addedDays
                            );

            LocalDate maximumReleaseDate =
                    maximumBaseReleaseDate
                            //same calculations here
                            .minusDays(
                                    totalAllowanceDays
                            )
                            .plusDays(
                                    addedDays
                            );


            //get the time remaining for both measurements
            String minimumTimeRemaining =
                    getTimeRemaining(
                            observationDate,
                            minimumReleaseDate
                    );


            String maximumTimeRemaining =
                    getTimeRemaining(
                            observationDate,
                            maximumReleaseDate
                    );


            //using days remaining to sort easier later
            long minimumRemainingDays =
                    getRemainingDays(
                            observationDate,
                            minimumReleaseDate
                    );


            long maximumRemainingDays =
                    getRemainingDays(
                            observationDate,
                            maximumReleaseDate
                    );


            //getting suggested action based on number of days
            String suggestedAction =
                    getSuggestedAction(
                            minimumRemainingDays,
                            maximumRemainingDays
                    );


            // -------------------------
            // CREATE RECYCLER VIEW MODEL
            // -------------------------

            profileModels.add(
                    new profileModel(

                            profile.profileID,

                            profile.firstName,
                            profile.lastName,

                            profile.leaArrestDate,

                            profile.commitmentDate,
                            profile.notCommittedToJail,

                            minimumTimeRemaining,
                            maximumTimeRemaining,

                            minimumRemainingDays,
                            maximumRemainingDays,

                            suggestedAction,

                            R.drawable.profile_placeholder
                    )
            );
        }
    }


    // =========================================================
    // DATE CONVERSION
    // =========================================================

    private LocalDate stringToDate(
            String date) {

        return LocalDate.parse(
                date,
                dateFormatter
        );
    }


    // =========================================================
    // BASE RELEASE DATE
    // =========================================================

    private LocalDate calculateBaseReleaseDate(
            LocalDate commitmentDate,
            long leaDays,
            int years,
            int months,
            int days) {

        /*
         * Anniversary method.
         *
         * Example:
         *
         * Sep 20, 2026 + 1 year
         * becomes Sep 20, 2027,
         *
         * rather than simply adding 365 days.
         */

        return commitmentDate
                .plusYears(years)
                .plusMonths(months)
                .plusDays(days)
                .minusDays(leaDays);
    }


    // =========================================================
    // WHOLE MONTHS ELAPSED
    // =========================================================

    private long getWholeMonthsElapsed(
            LocalDate commitmentDate,
            LocalDate observationDate) {

        /*
         * If the observation date is before commitment,
         * no allowance months have elapsed yet.
         */

        if (observationDate.isBefore(
                commitmentDate)) {

            return 0;
        }


        /*
         * First calculate the rough difference
         * in calendar months.
         */

        long months =
                (
                        observationDate.getYear()
                                - commitmentDate.getYear()
                ) * 12L

                        + observationDate.getMonthValue()
                        - commitmentDate.getMonthValue();


        /*
         * Then check whether the anniversary for
         * that month has actually occurred.
         *
         * Example:
         *
         * Jan 15 -> Feb 14
         * = 0 complete months
         *
         * Jan 15 -> Feb 15
         * = 1 complete month
         */

        LocalDate anniversary =
                commitmentDate.plusMonths(
                        months
                );


        if (anniversary.isAfter(
                observationDate)) {

            months--;
        }


        return Math.max(
                months,
                0
        );
    }


    // =========================================================
    // TASTM
    // =========================================================

    private long calculateTASTM(
            long wholeMonthsElapsed) {

        /*
         * Current app model:
         *
         * 15 days of TASTM for every complete
         * qualifying month.
         */

        return wholeMonthsElapsed * 15;
    }


    // =========================================================
    // GCTA
    // =========================================================

    private long calculateGCTA(
            long wholeMonthsElapsed) {

        long monthsRemaining =
                wholeMonthsElapsed;

        long gctaDays = 0;


        // -------------------------
        // MONTHS 1 - 24
        // 20 days per month
        // -------------------------

        long firstTier =
                Math.min(
                        monthsRemaining,
                        24
                );

        gctaDays +=
                firstTier * 20;

        monthsRemaining -=
                firstTier;


        // -------------------------
        // MONTHS 25 - 60
        // 23 days per month
        // -------------------------

        if (monthsRemaining > 0) {

            // Months 25 through 60
            // = 36 months total

            long secondTier =
                    Math.min(
                            monthsRemaining,
                            36
                    );

            gctaDays +=
                    secondTier * 23;

            monthsRemaining -=
                    secondTier;
        }


        // -------------------------
        // MONTHS 61 - 120
        // 25 days per month
        // -------------------------

        if (monthsRemaining > 0) {

            // Months 61 through 120
            // = 60 months total

            long thirdTier =
                    Math.min(
                            monthsRemaining,
                            60
                    );

            gctaDays +=
                    thirdTier * 25;

            monthsRemaining -=
                    thirdTier;
        }


        // -------------------------
        // MONTH 121 ONWARDS
        // 30 days per month
        // -------------------------

        if (monthsRemaining > 0) {

            gctaDays +=
                    monthsRemaining * 30;
        }


        return gctaDays;
    }


    // =========================================================
    // DISPLAYED SIGNED TIME REMAINING
    // =========================================================

    private String getTimeRemaining(
            LocalDate observationDate,
            LocalDate releaseDate) {

        boolean overdue =
                releaseDate.isBefore(
                        observationDate
                );


        Period difference;


        /*
         * Period.between() works best when the
         * earlier date is supplied first.
         *
         * We therefore reverse the dates for
         * overdue profiles and add the negative
         * sign ourselves.
         */

        if (overdue) {

            difference =
                    Period.between(
                            releaseDate,
                            observationDate
                    );
        }

        else {

            difference =
                    Period.between(
                            observationDate,
                            releaseDate
                    );
        }


        String sign =
                overdue ? "-" : "";


        return sign
                + difference.getYears() + "y, "
                + difference.getMonths() + "m, "
                + difference.getDays() + "d";
    }


    // =========================================================
    // NUMERIC SIGNED TIME REMAINING
    // =========================================================

    private long getRemainingDays(
            LocalDate observationDate,
            LocalDate releaseDate) {

        /*
         * ChronoUnit returns:
         *
         * positive -> future release
         * zero     -> release today
         * negative -> overdue
         */

        return ChronoUnit.DAYS.between(
                observationDate,
                releaseDate
        );
    }


    // =========================================================
    // SUGGESTED ACTION
    // =========================================================

    private String getSuggestedAction(
            long minimumRemainingDays,
            long maximumRemainingDays) {

        /*
         * These are workflow suggestions only.
         *
         * Maximum <= 0:
         * both estimated dates have passed.
         *
         * Minimum <= 0 but Maximum > 0:
         * profile is somewhere between the two
         * calculated limits.
         *
         * Otherwise:
         * neither calculated date has arrived.
         */

        if (maximumRemainingDays <= 0) {

            return "Contact Authorities";
        }

        else if (minimumRemainingDays <= 0) {

            return "Review Case";
        }

        else {

            return "Stand By";
        }
    }


    // =========================================================
    // ACTIVITY RESUME
    // =========================================================

    @Override
    protected void onResume() {

        super.onResume();


        /*
         * Reload the Room data every time the user
         * comes back to Dashboard.
         */

        profileModels.clear();

        setUpProfileModels();


        /*
         * Reapply whatever sorting options
         * are currently selected.
         */

        updateSort();
    }


    // =========================================================
    // BUBBLE SORT
    // =========================================================

    private ArrayList<profileModel> sortProfileModels(
            ArrayList<profileModel> profilesList,
            String sortType,
            String order) {


        /*
         * Bubble sort:
         *
         * repeatedly compare adjacent items and
         * swap them if they are in the wrong order.
         */

        for (int i = 0;
             i < profilesList.size() - 1;
             i++) {


            for (int j = 0;
                 j < profilesList.size() - i - 1;
                 j++) {


                profileModel first =
                        profilesList.get(j);

                profileModel second =
                        profilesList.get(j + 1);


                /*
                 * All comparison types are converted
                 * into the same convention:
                 *
                 * negative -> first comes before second
                 * zero     -> equal
                 * positive -> first comes after second
                 */

                int comparisonResult = 0;


                // -------------------------
                // FIRST NAME
                // -------------------------

                if (sortType.equals(
                        "First Name")) {

                    comparisonResult =
                            first.getFirstName()
                                    .compareToIgnoreCase(
                                            second.getFirstName()
                                    );
                }


                // -------------------------
                // LAST NAME
                // -------------------------

                else if (sortType.equals(
                        "Last Name")) {

                    comparisonResult =
                            first.getLastName()
                                    .compareToIgnoreCase(
                                            second.getLastName()
                                    );
                }


                // -------------------------
                // MINIMUM TIME REMAINING
                // -------------------------

                else if (sortType.equals(
                        "Minimum Time Remaining")) {

                    comparisonResult =
                            Long.compare(
                                    first.getMinRemainingDays(),
                                    second.getMinRemainingDays()
                            );
                }


                // -------------------------
                // MAXIMUM TIME REMAINING
                // -------------------------

                else if (sortType.equals(
                        "Maximum Time Remaining")) {

                    comparisonResult =
                            Long.compare(
                                    first.getMaxRemainingDays(),
                                    second.getMaxRemainingDays()
                            );
                }


                // -------------------------
                // LEA ARREST DATE
                // -------------------------

                else if (sortType.equals(
                        "LEA Arrest Date")) {

                    LocalDate firstDate =
                            stringToDate(
                                    first.getLEAArrestDate()
                            );


                    LocalDate secondDate =
                            stringToDate(
                                    second.getLEAArrestDate()
                            );


                    comparisonResult =
                            firstDate.compareTo(
                                    secondDate
                            );
                }


                // -------------------------
                // ASCENDING / DESCENDING
                // -------------------------

                boolean shouldSwap;


                if (order.equals(
                        "Ascending")) {

                    shouldSwap =
                            comparisonResult > 0;
                }

                else {

                    shouldSwap =
                            comparisonResult < 0;
                }


                // -------------------------
                // SWAP
                // -------------------------

                if (shouldSwap) {

                    profileModel temp =
                            profilesList.get(j);

                    profilesList.set(
                            j,
                            profilesList.get(j + 1)
                    );

                    profilesList.set(
                            j + 1,
                            temp
                    );
                }
            }
        }


        return profilesList;
    }


    // =========================================================
    // APPLY CURRENT SORT
    // =========================================================

    private void updateSort() {

        /*
         * Both dropdown selections are read every
         * time either Spinner changes.
         */

        String sortType =
                sortDropdown
                        .getSelectedItem()
                        .toString();


        String order =
                orderDropdown
                        .getSelectedItem()
                        .toString();


        sortProfileModels(
                profileModels,
                sortType,
                order
        );


        /*
         * The same ArrayList object has been
         * reordered, so tell RecyclerView to redraw.
         */

        adapter.notifyDataSetChanged();
    }
}