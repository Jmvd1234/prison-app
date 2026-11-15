package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileDao;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class DashboardActivity extends AppCompatActivity {

    private static final String TAG =
            "DashboardActivity";


    // Keep dropdown labels in constants so the dropdown
    // and sorting logic can never use different wording.
    private static final String SORT_FIRST_NAME =
            "First Name";

    private static final String SORT_LAST_NAME =
            "Last Name";

    private static final String SORT_MIN_TIME =
            "Min. Time Remaining";

    private static final String SORT_MAX_TIME =
            "Max. Time Remaining";

    private static final String SORT_ARREST_DATE =
            "LEA Arrest Date";


    ProfileDao profileDao;
    SharedPreferences sp;

    ArrayList<profileModel> profileModels =
            new ArrayList<>();

    Profile_RecyclerViewAdapter adapter;
    private EntryDeletionViewModel entryDeletion;
    private AlertDialog deleteConfirmation;
    private ProfileTransferViewModel transfer;
    private AlertDialog importConfirmation;
    private final androidx.activity.result.ActivityResultLauncher<String> exportFile=registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream"),uri->transfer.exportTo(uri));
    private final androidx.activity.result.ActivityResultLauncher<String[]> importFile=registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),uri->transfer.readFrom(uri));

    Spinner sortDropdown;
    Spinner orderDropdown;


    /*
     * Room loading + calculation happens away from the UI thread.
     */
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();


    /*
     * Used so an old asynchronous load cannot overwrite
     * a newer Dashboard refresh.
     */
    private int loadGeneration = 0;
    private final TodayDateObserver dateObserver = new TodayDateObserver(this, this::setUpProfileModels);


    @Override
    protected void onCreate(
            Bundle savedInstanceState) {

        super.onCreate(
                savedInstanceState
        );


        EdgeToEdge.enable(this);

        setContentView(
                R.layout.activity_dashboard
        );


        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {

                    Insets systemBars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );


                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );


                    return insets;
                }
        );


        // =====================================================
        // DATABASE + USER SESSION
        // =====================================================

        profileDao =
                AppDatabase
                        .getInstance(
                                getApplicationContext()
                        )
                        .ProfileDao();


        sp =
                getSharedPreferences(
                        "user_session",
                        Context.MODE_PRIVATE
                );


        // =====================================================
        // RECYCLER VIEW
        // =====================================================

        RecyclerView recyclerView =
                findViewById(
                        R.id.profileRecyclerView
                );


        adapter =
                new Profile_RecyclerViewAdapter(
                        this,
                        profileModels,
                        this::confirmDeleteEntry
                );


        recyclerView.setAdapter(
                adapter
        );


        recyclerView.setLayoutManager(
                new LinearLayoutManager(
                        this
                )
        );


        entryDeletion = new ViewModelProvider(this).get(EntryDeletionViewModel.class);
        entryDeletion.result.observe(this, success -> {
            if (success == null) return;
            entryDeletion.result.setValue(null);
            // Reload from Room so the current sort and every remaining card stay correct.
            setUpProfileModels();
            Toast.makeText(this, success ? R.string.entry_deleted : R.string.profile_delete_failed,
                    Toast.LENGTH_SHORT).show();
        });


        // =====================================================
        // SORT DROPDOWNS
        // =====================================================

        sortDropdown =
                findViewById(
                        R.id.sortDropdown
                );


        orderDropdown =
                findViewById(
                        R.id.orderDropdown
                );


        String[] sortOptions = {
                SORT_FIRST_NAME,
                SORT_LAST_NAME,
                SORT_MIN_TIME,
                SORT_MAX_TIME,
                SORT_ARREST_DATE
        };


        String[] orderOptions = {
                "Ascending",
                "Descending"
        };


        setSpinnerOptions(
                sortDropdown,
                sortOptions
        );


        setSpinnerOptions(
                orderDropdown,
                orderOptions
        );


        /*
         * Whenever either Spinner changes,
         * simply re-sort the existing model list.
         */

        AdapterView.OnItemSelectedListener sortListener =
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
                };


        sortDropdown.setOnItemSelectedListener(
                sortListener
        );


        orderDropdown.setOnItemSelectedListener(
                sortListener
        );
        setupTransfer();
        Edition.attach(this);
    }


    private void setupTransfer(){
        transfer=new ViewModelProvider(this).get(ProfileTransferViewModel.class);
        adapter.setExportSelection(new Profile_RecyclerViewAdapter.ExportSelection(){
            public boolean selecting(){return transfer.selecting();}
            public boolean selected(int id){return transfer.selected().contains(id);}
            public void toggle(int id,boolean selected){if(!transfer.isBusy()){transfer.select(id,selected);refreshTransfer();}}
        });
        findViewById(R.id.exportProfiles).setOnClickListener(v->{
            if(!transfer.selecting()){transfer.setSelecting(true);refreshTransfer();return;}
            if(transfer.selected().isEmpty())return;
            transfer.beginExport(sp.getLong("currentUserID",-1));
            exportFile.launch("Laya-profiles-"+LocalDate.now()+".laya");
        });
        findViewById(R.id.importProfiles).setOnClickListener(v->{
            transfer.beginImport(sp.getLong("currentUserID",-1));importFile.launch(new String[]{"*/*"});
        });
        findViewById(R.id.selectAllProfiles).setOnClickListener(v->{
            ArrayList<Integer> ids=new ArrayList<>();for(profileModel p:profileModels)ids.add(p.getProfileID());
            transfer.selectAll(ids);refreshTransfer();
        });
        findViewById(R.id.cancelExport).setOnClickListener(v->{transfer.setSelecting(false);refreshTransfer();});
        transfer.busy.observe(this,value->refreshTransfer());
        transfer.message.observe(this,value->{if(value==null)return;transfer.message.setValue(null);refreshTransfer();
            new MaterialAlertDialogBuilder(this).setMessage(value).setPositiveButton(android.R.string.ok,null).show();});
        transfer.imported.observe(this,value->{if(Boolean.TRUE.equals(value)){transfer.imported.setValue(null);setUpProfileModels();}});
        transfer.previewCount.observe(this,count->{
            if(count==null||isFinishing()||importConfirmation!=null)return;
            if (BuildConfig.FREE_EDITION && count > profileDao.availableProfileSlots()) {
                long available = profileDao.availableProfileSlots();
                transfer.discardPreview();
                new MaterialAlertDialogBuilder(this).setTitle("More profile slots needed")
                    .setMessage("This backup contains " + count + " profiles, but only " + available
                        + " slots are available. Unlock more slots, delete profiles you no longer need, or export a smaller backup. Nothing has been imported.")
                    .setPositiveButton("Unlock a slot", (d, w) -> Edition.unlock(this))
                    .setNegativeButton(android.R.string.cancel, null).show();
                return;
            }
            importConfirmation=new MaterialAlertDialogBuilder(this).setTitle("Import "+count+" profile"+(count==1?"":"s")+"?")
                .setMessage(BuildConfig.FREE_EDITION ? "These profiles will use available slots. Existing entries will stay unchanged. Importing the same file again creates additional copies." : "These profiles will be added to the current login profile. Existing entries will stay unchanged. Importing the same file again creates additional copies.")
                .setNegativeButton(android.R.string.cancel,(d,w)->transfer.discardPreview())
                .setPositiveButton("Import",(d,w)->transfer.confirmImport()).create();
            importConfirmation.setOnCancelListener(d->transfer.discardPreview());
            importConfirmation.setOnDismissListener(d->importConfirmation=null);importConfirmation.show();
        });
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){
            public void handleOnBackPressed(){
                if(transfer.isBusy()){Toast.makeText(DashboardActivity.this,"Please wait for the transfer to finish.",Toast.LENGTH_SHORT).show();return;}
                if(transfer.selecting()){transfer.setSelecting(false);refreshTransfer();}else finish();
            }
        });
        refreshTransfer();
    }
    private void refreshTransfer(){
        if(transfer==null)return;
        boolean selecting=transfer.selecting(),busy=transfer.isBusy();int count=transfer.selected().size();
        android.widget.TextView export=findViewById(R.id.exportProfiles);
        export.setText(selecting?"Export ("+count+")":getString(R.string.export_profiles));
        export.setEnabled(!busy&&(!selecting||count>0));
        findViewById(R.id.importProfiles).setVisibility(selecting?View.GONE:View.VISIBLE);
        findViewById(R.id.importProfiles).setEnabled(!busy);
        findViewById(R.id.exportSelectionControls).setVisibility(selecting?View.VISIBLE:View.GONE);
        findViewById(R.id.selectAllProfiles).setEnabled(!busy&&!profileModels.isEmpty());
        findViewById(R.id.cancelExport).setEnabled(!busy);
        findViewById(R.id.homeButton).setEnabled(!busy);
        findViewById(R.id.transferProgress).setVisibility(busy?View.VISIBLE:View.GONE);
        adapter.notifyDataSetChanged();
    }

    // =========================================================
    // SPINNER SETUP
    // =========================================================

    private void setSpinnerOptions(
            Spinner spinner,
            String[] options) {

        ArrayAdapter<String> spinnerAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        options
                );


        spinnerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinner.setAdapter(
                spinnerAdapter
        );
    }


    // =========================================================
    // HOME
    // =========================================================

    public void goHome(View v) {

        finish();
    }


    private void confirmDeleteEntry(profileModel selected) {
        if (entryDeletion.isBusy() || isFinishing() || isDestroyed()
                || (deleteConfirmation != null && deleteConfirmation.isShowing())) return;
        final int profileID = selected.getProfileID();
        final long userID = sp.getLong("currentUserID", -1L);
        if (userID < 0) return;
        String name = (selected.getFirstName() + " " + selected.getLastName()).trim();
        deleteConfirmation = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_entry_title)
                .setMessage(getString(R.string.delete_entry_message, name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete_entry, (dialog, which) -> {
                    if (sp.getLong("currentUserID", -1L) != userID) return;
                    ++loadGeneration; // Ignore any snapshot loaded before this deletion.
                    entryDeletion.delete(profileID, userID);
                })
                .create();
        deleteConfirmation.setOnDismissListener(dialog -> deleteConfirmation = null);
        deleteConfirmation.show();
        deleteConfirmation.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(getColor(R.color.laya_danger));
    }


    // =========================================================
    // LOAD + CALCULATE PROFILE CARDS
    // =========================================================

    private void setUpProfileModels() {
        final LocalDate calculationDate = LocalDate.now();

        long currentUserID =
                sp.getLong(
                        "currentUserID",
                        -1L
                );


        /*
         * Each load gets its own generation number.
         *
         * If the Activity refreshes again before this one
         * finishes, the older result is ignored.
         */

        final int generation =
                ++loadGeneration;


        if (currentUserID == -1L) {

            profileModels.clear();

            adapter.notifyDataSetChanged();


            Toast.makeText(
                    this,
                    "Please sign in again.",
                    Toast.LENGTH_LONG
            ).show();


            return;
        }


        executor.execute(() -> {

            try {

                List<Profile> storedProfiles =
                        profileDao.getProfilesByUserID(
                                currentUserID
                        );


                ArrayList<profileModel> loadedModels =
                        new ArrayList<>();


                for (Profile profile : storedProfiles) {

                    // =========================================
                    // CONVERT ROOM PROFILE → CALCULATOR INPUT
                    // =========================================

                    SentenceCalculator.Input input =
                            ProfileCalculationMapper.from(profile, calculationDate);


                    // =========================================
                    // RUN THE SINGLE CENTRAL CALCULATOR
                    // =========================================

                    SentenceCalculator.Result result =
                            SentenceCalculator.calculate(
                                    input
                            );


                    SequentialSentencePlan service=null;
                    if(result.available && profile.charges.size()>1){
                        service=new SequentialSentencePlan(profile);
                        result=service.calculateAtDate(calculationDate);
                    }
                    String serviceAction=service!=null && calculationDate.isBefore(service.capDate)
                            && service.stageAt(calculationDate).number<service.ordered.size()
                            ? (result.minRemainingDays<=0 ? "Continue to next charge" : "") : result.actionText();

                    boolean clemencyRecommended=result.available && ClemencyAssessment.create(profile,service)
                            .recommend(calculationDate,result,service);
                    if(clemencyRecommended)serviceAction=getString(R.string.clemency_action);

                    String minimumTimeRemaining =
                            "N/A";

                    String maximumTimeRemaining =
                            "N/A";


                    /*
                     * Only generate the formatted Y/M/D text
                     * when the calculator says that the result
                     * is actually available.
                     */

                    if (result.available) {

                        LocalDate observationDate = calculationDate;


                        minimumTimeRemaining =
                                result.minimumText(
                                        observationDate
                                );


                        maximumTimeRemaining =
                                result.maximumText(
                                        observationDate
                                );
                    }


                    // Use the same trigger meaning and recovered date as the form.

                    boolean showPrisonCommitmentLabel =
                            SentenceCalculator.usesPrisonCommitment(
                                    profile.notCommittedToJail, profile.disqualifiedForCPI);


                    String displayedCommitmentDate =
                            isBlank(
                                    ProfileCalculationMapper.commitmentDate(profile)
                            )
                                    ? "Not entered"
                                    : ProfileCalculationMapper.commitmentDate(profile);


                    // =========================================
                    // CREATE NORMAL RECYCLER VIEW MODEL
                    // =========================================

                    profileModel model =
                            new profileModel(

                                    profile.profileID,

                                    profile.firstName,
                                    profile.lastName,

                                    profile.leaArrestDate,

                                    displayedCommitmentDate,
                                    showPrisonCommitmentLabel,

                                    minimumTimeRemaining,
                                    maximumTimeRemaining,

                                    /*
                                     * When unavailable these are just
                                     * placeholders.
                                     *
                                     * The adapter checks calculationAvailable
                                     * before displaying them.
                                     */
                                    result.available
                                            ? result.minRemainingDays
                                            : 0L,

                                    result.available
                                            ? result.maxRemainingDays
                                            : 0L,

                                    serviceAction,

                                    profile.image_path
                            );


                    // =========================================
                    // ATTACH EXTRA CALCULATION DISPLAY DATA
                    // =========================================

                    model.setClemencyRecommended(clemencyRecommended);
                    model.setCalculationAvailable(
                            result.available
                    );


                    model.setMinimumExactDays(
                            result.available
                                    ? SentenceCalculator.exactDays(
                                    result.minRemainingFifths
                            )
                                    : "N/A"
                    );


                    model.setMaximumExactDays(
                            result.available
                                    ? SentenceCalculator.exactDays(
                                    result.maxRemainingFifths
                            )
                                    : "N/A"
                    );


                    model.setCalculationNotes(
                            joinNotes(
                                    result.notes
                            )
                    );


                    if(service!=null)model.setServiceChargeNumber(service.stageAt(calculationDate).number);

                    loadedModels.add(
                            model
                    );
                }


                // =============================================
                // UPDATE THE UI
                // =============================================

                runOnUiThread(() -> {

                    /*
                     * Do nothing if this Activity is gone
                     * or a newer load has already begun.
                     */

                    if (isFinishing()
                            || isDestroyed()
                            || generation != loadGeneration) {

                        return;
                    }


                    profileModels.clear();

                    profileModels.addAll(
                            loadedModels
                    );


                    updateSort();
                    refreshTransfer();
                });

            }

            catch (Exception e) {

                /*
                 * Don't put names/custody details into Logcat.
                 */

                Log.e(
                        TAG,
                        "Unable to load dashboard",
                        e
                );


                runOnUiThread(() -> {

                    if (isFinishing()
                            || isDestroyed()
                            || generation != loadGeneration) {

                        return;
                    }


                    profileModels.clear();

                    adapter.notifyDataSetChanged();


                    Toast.makeText(
                            this,
                            "Could not load profiles. Check the saved profile data.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }


    // =========================================================
    // NOTES → SINGLE DISPLAY STRING
    // =========================================================

    private String joinNotes(
            List<String> notes) {

        StringBuilder text =
                new StringBuilder();


        for (String note : notes) {

            if (text.length() > 0) {

                text.append("\n");
            }


            text.append(
                    note
            );
        }


        return text.toString();
    }


    private boolean isBlank(
            String value) {

        return value == null
                || value.trim().isEmpty();
    }


    // =========================================================
    // ACTIVITY RESUME
    // =========================================================

    @Override
    protected void onResume() {

        super.onResume();


        /*
         * Re-read Room and recalculate whenever the user
         * returns from creating/editing a profile.
         */

        setUpProfileModels();
        dateObserver.start();
    }

    @Override
    protected void onPause() {
        dateObserver.stop();
        if (deleteConfirmation != null) deleteConfirmation.dismiss();
        super.onPause();
    }


    // =========================================================
    // BUBBLE SORT
    // =========================================================

    private void sortProfileModels(
            String sortType,
            boolean ascending) {

        boolean sortingByTime =
                SORT_MIN_TIME.equals(
                        sortType
                )
                        || SORT_MAX_TIME.equals(
                        sortType
                );


        for (int i = 0;
             i < profileModels.size() - 1;
             i++) {


            for (int j = 0;
                 j < profileModels.size() - i - 1;
                 j++) {


                profileModel first =
                        profileModels.get(j);

                profileModel second =
                        profileModels.get(j + 1);


                boolean shouldSwap;


                /*
                 * A profile with unavailable calculations
                 * always stays at the bottom for time sorts.
                 *
                 * This prevents an N/A profile's placeholder
                 * zero value from sorting as if it were real.
                 */

                if (sortingByTime
                        && first.isCalculationAvailable()
                        != second.isCalculationAvailable()) {


                    shouldSwap =
                            !first.isCalculationAvailable();
                }

                else {

                    int comparisonResult =
                            compareProfiles(
                                    first,
                                    second,
                                    sortType
                            );


                    if (ascending) {

                        shouldSwap =
                                comparisonResult > 0;
                    }

                    else {

                        shouldSwap =
                                comparisonResult < 0;
                    }
                }


                if (shouldSwap) {

                    profileModel temporary =
                            profileModels.get(j);


                    profileModels.set(
                            j,
                            profileModels.get(j + 1)
                    );


                    profileModels.set(
                            j + 1,
                            temporary
                    );
                }
            }
        }
    }


    // =========================================================
    // PROFILE COMPARISON
    // =========================================================

    private int compareProfiles(
            profileModel first,
            profileModel second,
            String sortType) {


        switch (sortType) {


            case SORT_FIRST_NAME:

                return safe(
                        first.getFirstName()
                ).compareToIgnoreCase(
                        safe(
                                second.getFirstName()
                        )
                );


            case SORT_LAST_NAME:

                return safe(
                        first.getLastName()
                ).compareToIgnoreCase(
                        safe(
                                second.getLastName()
                        )
                );


            case SORT_MIN_TIME:

                /*
                 * If either calculation is unavailable,
                 * the caller already handles its position.
                 */

                if (!first.isCalculationAvailable()
                        || !second.isCalculationAvailable()) {

                    return 0;
                }


                /*
                 * Compare exact decimal day values.
                 *
                 * This matters when four-fifths CPI produces
                 * fractional credited-day balances.
                 */

                return new java.math.BigDecimal(
                        first.getMinimumExactDays()
                ).compareTo(
                        new java.math.BigDecimal(
                                second.getMinimumExactDays()
                        )
                );


            case SORT_MAX_TIME:

                if (!first.isCalculationAvailable()
                        || !second.isCalculationAvailable()) {

                    return 0;
                }


                return new java.math.BigDecimal(
                        first.getMaximumExactDays()
                ).compareTo(
                        new java.math.BigDecimal(
                                second.getMaximumExactDays()
                        )
                );


            case SORT_ARREST_DATE:

                LocalDate firstDate =
                        parseDateForSort(
                                first.getLEAArrestDate()
                        );


                LocalDate secondDate =
                        parseDateForSort(
                                second.getLEAArrestDate()
                        );


                if (firstDate == null
                        && secondDate == null) {

                    return 0;
                }


                if (firstDate == null) {

                    return 1;
                }


                if (secondDate == null) {

                    return -1;
                }


                return firstDate.compareTo(
                        secondDate
                );


            default:

                return 0;
        }
    }


    // =========================================================
    // DATE PARSING FOR SORT
    // =========================================================

    private LocalDate parseDateForSort(
            String value) {

        try {

            return SentenceCalculator.optionalDate(
                    value,
                    "Arrest date"
            );

        }

        catch (IllegalArgumentException e) {

            return null;
        }
    }


    private String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }


    // =========================================================
    // APPLY CURRENT SORT
    // =========================================================

    private void updateSort() {

        if (adapter == null
                || sortDropdown == null
                || orderDropdown == null
                || sortDropdown.getSelectedItem() == null
                || orderDropdown.getSelectedItem() == null) {

            return;
        }


        String sortType =
                sortDropdown
                        .getSelectedItem()
                        .toString();


        boolean ascending =
                "Ascending".equals(
                        orderDropdown
                                .getSelectedItem()
                                .toString()
                );


        sortProfileModels(
                sortType,
                ascending
        );


        adapter.notifyDataSetChanged();
    }


    // =========================================================
    // CLEANUP
    // =========================================================

    @Override
    protected void onDestroy() {
        dateObserver.stop();
        if(importConfirmation!=null)importConfirmation.dismiss();

        /*
         * Any result from an older load should no longer
         * be allowed to update this Activity.
         */

        ++loadGeneration;


        executor.shutdown();


        super.onDestroy();
    }
}
