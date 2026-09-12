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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class DashboardActivity extends AppCompatActivity {

    AppDatabase db;
    ProfileDao profileDao;
    SharedPreferences sp;

    ArrayList<profileModel> profileModels = new ArrayList<>();

    HashMap<String, String> monthNumber = new HashMap<String, String>();
    Profile_RecyclerViewAdapter adapter;

    Spinner sortDropdown;
    Spinner orderDropdown;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = AppDatabase.getInstance(this.getApplicationContext());
        profileDao = db.ProfileDao();

        sp = getSharedPreferences("user_session", Context.MODE_PRIVATE);

        RecyclerView recyclerView = findViewById(R.id.profileRecyclerView);

        sortDropdown = findViewById(R.id.sortDropdown);
        orderDropdown = findViewById(R.id.orderDropdown);

        String[] sortOptions = {
                "First Name",
                "Last Name",
                "Time Left",
                "Arrest Date"
        };
        String[] orderOptions = {
                "Ascending",
                "Descending"
        };

        //adapter so that the dropdown menus can make use of the list items. Basically for any complex UI element an adapter is needed
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                sortOptions
        );

        sortAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        sortDropdown.setAdapter(sortAdapter);

        ArrayAdapter<String> orderAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                orderOptions
        );

        orderAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        orderDropdown.setAdapter(orderAdapter);


        adapter = new Profile_RecyclerViewAdapter(this, profileModels);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        monthNumber.put("Jan", "01");
        monthNumber.put("Feb", "02");
        monthNumber.put("Mar", "03");
        monthNumber.put("Apr", "04");
        monthNumber.put("May", "05");
        monthNumber.put("Jun", "06");
        monthNumber.put("Jul", "07");
        monthNumber.put("Aug", "08");
        monthNumber.put("Sep", "09");
        monthNumber.put("Oct", "10");
        monthNumber.put("Nov", "11");
        monthNumber.put("Dec", "12");

        //listener to actually execute the sorting methods whenever the dropdowns change
        sortDropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSort();
            }

            //required by android even if in practice something is always selected
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        orderDropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSort();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

    }

    public void goHome(View v){
        finish();
    }

    private String getSuggestedAction(String[] timeValues) {
        if (timeValues[1].equals("N/A")) {
            return "Stand By";
        }
        else {
            return "Contact Authorities";
        }
    }

    private void setUpProfileModels() {
        List<Profile> profile_list = profileDao.getProfilesByUserID(sp.getLong("currentUserID", -1));
        for (int i = 0; i<profile_list.toArray().length; i++) {

            String[] timeValues = getTimeLeft(
                    profile_list.get(i).arrestDate,
                    profile_list.get(i).sentenceYears,
                    profile_list.get(i).sentenceMonths,
                    profile_list.get(i).sentenceDays,
                    profile_list.get(i).GCTA,
                    profile_list.get(i).TASTM,
                    profile_list.get(i).STAL
            );

            profileModels.add(new profileModel(
                    profile_list.get(i).profileID,
                    profile_list.get(i).firstName,
                    profile_list.get(i).lastName,
                    profile_list.get(i).chargeName,
                    profile_list.get(i).arrestDate,
                    //do time left logic later
                    timeValues[0],
                    timeValues[1],
                    getSuggestedAction(timeValues),
                    R.drawable.profile_placeholder
            ));
        }
    }

    private Date stringToDate(String arrest_date) {
        SimpleDateFormat formatter =
                new SimpleDateFormat("MMM d, yyyy", Locale.US);

        formatter.setTimeZone(TimeZone.getDefault());

        // Java requires error handling or it complains. In reality will never be used
        try {
            return formatter.parse(arrest_date);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    //method to get years, months, days, and hours between two dates. Uses Java time libraries in order to achieve this functionality
    private String dateDifference(ZonedDateTime start, ZonedDateTime end) {
        //get number of years between two days
        long years = ChronoUnit.YEARS.between(start, end);
        //add the number of years to the start date so the next unit can be retrieved
        //that would be the number of months between the new start date and the end date
        //repeat this for years, months, days, hours
        start = start.plusYears(years);

        long months = ChronoUnit.MONTHS.between(start, end);
        start = start.plusMonths(months);

        long days = ChronoUnit.DAYS.between(start, end);
        start = start.plusDays(days);

        long hours = ChronoUnit.HOURS.between(start, end);

        //String.format allows variables to be passed into the strings. %d is for passing integer number values (incl. longs)
        return String.format("%dy, %dm, %dd, %dh",years, months, days, hours);
    }

    private String[] getTimeLeft(String arrest_date, int sentYears, int sentMonths, int sentDays, int GCTA, int TASTM, int STAL) {
        String timeLeft = "N/A";
        String timeOverdue = "N/A";

        int deduction_hours = (GCTA + STAL + TASTM);


        Date start_date = stringToDate(arrest_date);
        //by default android gets the date at the current timezone of the device
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start_date);

        calendar.add(Calendar.YEAR, sentYears);
        calendar.add(Calendar.MONTH, sentMonths);
        calendar.add(Calendar.DAY_OF_MONTH, sentDays);
        calendar.add(Calendar.HOUR_OF_DAY, -deduction_hours);

        Calendar today = Calendar.getInstance();

        if (today.before(calendar)) {
            timeLeft = dateDifference(
                    //Need to convert calendar objects to ZonedDateTime objects for dateDifference to parse
                    today.toInstant()
                            //ensures that the ZonedDateTime object is at the correct timezone
                            .atZone(today.getTimeZone().toZoneId()),
                    calendar.toInstant()
                            .atZone(calendar.getTimeZone().toZoneId())
            );
        }
        else {
            timeOverdue = dateDifference(
                    //Need to convert calendar objects to ZonedDateTime objects for dateDifference to parse
                    calendar.toInstant()
                            //ensures that the ZonedDateTime object is at the correct timezone
                            .atZone(today.getTimeZone().toZoneId()),
                    today.toInstant()
                            .atZone(calendar.getTimeZone().toZoneId())
            );
        }

        return new String[] {timeLeft, timeOverdue};

    }
    //method to ensure that whenever there are changes to the dashboard data it is immediately updated
    protected void onResume() {
        super.onResume();

        profileModels.clear();
        setUpProfileModels();

        updateSort();
    }

    //Doing a bubble sort for the different attributes
    private ArrayList<profileModel> sortProfileModels(
            ArrayList<profileModel> profiles_list,
            String sortType,
            String order) {

        // TIME LEFT requires special handling because overdue time
        // is treated as negative time left
        if (sortType.equals("Time Left")) {

            ArrayList<profileModel> timeLeftList = new ArrayList<>();
            ArrayList<profileModel> timeOverdueList = new ArrayList<>();

            // Split profiles into those with time left and those overdue
            for (int i = 0; i < profiles_list.size(); i++) {

                if (profiles_list.get(i).getTimeLeft().equals("N/A")) {
                    timeOverdueList.add(profiles_list.get(i));
                }
                else {
                    timeLeftList.add(profiles_list.get(i));
                }
            }


            //sorting time left list in normal order
            for (int i = 0; i < timeLeftList.size() - 1; i++) {

                for (int j = 0; j < timeLeftList.size() - i - 1; j++) {

                    String firstTime = timeLeftList.get(j).getTimeLeft();
                    String secondTime = timeLeftList.get(j + 1).getTimeLeft();

                    String[] firstParts = firstTime.split(", ");
                    String[] secondParts = secondTime.split(", ");

                    int comparisonResult = 0;
                    //Basically the compare methods in java return a negative/positive based on if
                    //a comparison is true or false, which allows us to swap the order based on
                    //the order we want without writing the bubble sort algorithm multiple times

                    // Compare years, then months, then days, then hours
                    for (int k = 0; k < 4; k++) {

                        int firstValue = Integer.parseInt(
                                firstParts[k].replaceAll("[^0-9]", "")
                                //replaces everything that is not a number in the string
                                //allows us to work with the date numbers to compare them
                        );

                        int secondValue = Integer.parseInt(
                                secondParts[k].replaceAll("[^0-9]", "")
                        );
                        //essentially, what this method is doing is:
                        //if the first component is the same, do nothing, continue to the next componenent e.g. years --> months
                        //but if it is different we know what went before/after
                        if (firstValue < secondValue) {
                            comparisonResult = -1;
                            break;
                        }

                        else if (firstValue > secondValue) {
                            comparisonResult = 1;
                            break;
                        }
                    }

                    boolean shouldSwap;

                    if (order.equals("Ascending")) {
                        shouldSwap = comparisonResult > 0;
                    }
                    else {
                        shouldSwap = comparisonResult < 0;
                    }

                    if (shouldSwap) {
                        profileModel temp = timeLeftList.get(j);
                        timeLeftList.set(j, timeLeftList.get(j + 1));
                        timeLeftList.set(j + 1, temp);
                    }
                }
            }


            //We're treating overdue time as negative time left and then just appending it to the start/end of the list
            //so it should be sorted in reverse order from the normal time left list
            for (int i = 0; i < timeOverdueList.size() - 1; i++) {

                for (int j = 0; j < timeOverdueList.size() - i - 1; j++) {

                    String firstTime = timeOverdueList.get(j).getTimeOverdue();
                    String secondTime = timeOverdueList.get(j + 1).getTimeOverdue();

                    String[] firstParts = firstTime.split(", ");
                    String[] secondParts = secondTime.split(", ");

                    int comparisonResult = 0;

                    for (int k = 0; k < 4; k++) {

                        int firstValue = Integer.parseInt(
                                firstParts[k].replaceAll("[^0-9]", "")
                        );

                        int secondValue = Integer.parseInt(
                                secondParts[k].replaceAll("[^0-9]", "")
                        );

                        if (firstValue < secondValue) {
                            comparisonResult = -1;
                            break;
                        }

                        else if (firstValue > secondValue) {
                            comparisonResult = 1;
                            break;
                        }
                    }

                    boolean shouldSwap;

                    /*
                     * Overdue time represents NEGATIVE time.
                     *
                     * 2 years overdue = -2 years
                     * 1 month overdue = -1 month
                     *
                     * Therefore its ordering is reversed.
                     */

                    if (order.equals("Ascending")) {
                        shouldSwap = comparisonResult < 0;
                    }
                    else {
                        shouldSwap = comparisonResult > 0;
                    }

                    if (shouldSwap) {
                        profileModel temp = timeOverdueList.get(j);
                        timeOverdueList.set(j, timeOverdueList.get(j + 1));
                        timeOverdueList.set(j + 1, temp);
                    }
                }
            }



            // putting the two lists together
            profiles_list.clear();

            if (order.equals("Ascending")) {

                // Negative times first, then positive times
                profiles_list.addAll(timeOverdueList);
                profiles_list.addAll(timeLeftList);
            }

            else {

                // Positive times first, then negative times
                profiles_list.addAll(timeLeftList);
                profiles_list.addAll(timeOverdueList);
            }

            return profiles_list;
        }


        //normal bubble sort for other categories
        for (int i = 0; i < profiles_list.size() - 1; i++) {

            for (int j = 0; j < profiles_list.size() - i - 1; j++) {

                profileModel first = profiles_list.get(j);
                profileModel second = profiles_list.get(j + 1);

                int comparisonResult = 0;


                if (sortType.equals("First Name")) {

                    comparisonResult = first.getFirstName()
                            .compareToIgnoreCase(second.getFirstName());
                            //again, compare to returns negative/positive result to just let us swap it
                            //ignoring the case of the letters
                }

                else if (sortType.equals("Last Name")) {

                    comparisonResult = first.getLastName()
                            .compareToIgnoreCase(second.getLastName());
                }

                else if (sortType.equals("Arrest Date")) {

                    Date firstDate = stringToDate(first.getArrestDate());
                    Date secondDate = stringToDate(second.getArrestDate());

                    comparisonResult = firstDate.compareTo(secondDate);
                }


                boolean shouldSwap;

                //checking if the order is ascending or not (descending)
                if (order.equals("Ascending")) {
                    shouldSwap = comparisonResult > 0;
                }
                else {
                    shouldSwap = comparisonResult < 0;
                }


                if (shouldSwap) {
                    //if should swap is true, finally carry out the normal bubble sort)

                    profileModel temp = profiles_list.get(j);

                    profiles_list.set(j, profiles_list.get(j + 1));
                    profiles_list.set(j + 1, temp);
                }
            }
        }

        return profiles_list;
    }

    private void updateSort() {
        //basically triggering the actual updated sort based on the dropdown

        String sortType = sortDropdown.getSelectedItem().toString();
        String order = orderDropdown.getSelectedItem().toString();

        sortProfileModels(profileModels, sortType, order);

        adapter.notifyDataSetChanged();
    }
}

