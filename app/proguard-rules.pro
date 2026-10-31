# Android's optimized defaults and AndroidX consumer rules handle framework,
# Room, ViewModel, Fragment, and XML-inflated view entry points.
# Keep only XML onClick callbacks that Android locates by method name.
-keepclassmembers,allowoptimization class com.example.myapplication.HomeActivity {
    public void open_dashboard(android.view.View);
    public void make_new_entry(android.view.View);
}
-keepclassmembers,allowoptimization class com.example.myapplication.DashboardActivity {
    public void goHome(android.view.View);
}
-keepclassmembers,allowoptimization class com.example.myapplication.newEntryActivity {
    public void goHomeNE(android.view.View);
}
-keepclassmembers,allowoptimization class com.example.myapplication.editProfileActivity {
    public void goHomeNE(android.view.View);
}
