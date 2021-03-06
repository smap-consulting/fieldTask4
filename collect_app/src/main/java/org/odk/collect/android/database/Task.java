package org.odk.collect.android.database;

import java.util.Date;

public class Task {
	public int id;
	public String title;
    public String name;
	public String url;
	public String form_id;
	public int form_version;
    public String update_id;
    public String initial_data_source;   // task || survey || none
	public String assignment_mode;
	public Date scheduled_at;
    public Date scheduled_finish;
	public String location_trigger;
	public boolean repeat;			// Task can be completed multiple times
	public String address;			// Key value pairs representing an unstructured address
	public String status;
	public int show_dist;

	// Local attributes - used only on device
    public String initial_data;
}
