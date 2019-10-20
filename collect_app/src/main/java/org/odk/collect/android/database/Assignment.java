package org.odk.collect.android.database;

public class Assignment {
	public int assignment_id;           // This is the task_id (However server uses assignment_id)
	public String assignee;
	public String manager;
	public String assignment_status;    // task_status
    public String task_comment;    // task_status
	public int dbId;
	public String uuid;             // The instance id of the submitted record associated with this task
}
