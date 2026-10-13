package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


public class ReminderRepository {


    // ============================================================
    // DATABASE
    // ============================================================

    private final DatabaseHelper databaseHelper;
    private final String owner;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public ReminderRepository(
            DatabaseHelper databaseHelper
    ) {


        this.databaseHelper =
                databaseHelper;
        owner = databaseHelper.getActiveOwnerUid();
    }


    // ============================================================
    // ADD REMINDER
    // ============================================================

    public long addReminder(
            Integer gardenId,
            String title,
            String reminderType,
            long dueAt,
            String repeatRule,
            String note
    ) {
        if (!validGarden(gardenId) || safe(title).isEmpty() || !ReminderTime.valid(dueAt)
                || !ReminderTime.supported(repeatRule)) return -1;
        ContentValues values = fields(gardenId,title,reminderType,dueAt,repeatRule,note);
        values.put("is_done",0);values.put("created_at",System.currentTimeMillis());
        values.put("owner_uid",owner);
        values.put("cloud_id",owner.isEmpty()?"":java.util.UUID.randomUUID().toString());
        return databaseHelper.getWritableDatabase().insert("reminders",null,values);
    }


    // ============================================================
    // GET PENDING REMINDERS
    // ============================================================

    public Cursor getPendingReminders(
            int limit
    ) {
        return query("r.is_done=0",new String[0],"r.due_at ASC",String.valueOf(limit<=0?10:limit));
    }


    // ============================================================
    // GET ALL REMINDERS
    // ============================================================

    public Cursor getAllReminders() {
        return query("1=1",new String[0],"r.is_done ASC,r.due_at ASC",null);
    }


    // ============================================================
    // GET REMINDER
    // ============================================================

    public Cursor getReminder(
            long reminderId
    ) {
        return query("r.id=?",new String[]{String.valueOf(reminderId)},"r.id","1");
    }


    // ============================================================
    // GET GARDEN REMINDERS
    // ============================================================

    public Cursor getGardenReminders(
            long gardenId
    ) {
        return query("r.garden_id=?",new String[]{String.valueOf(gardenId)},"r.is_done ASC,r.due_at ASC",null);
    }


    // ============================================================
    // MARK DONE
    // ============================================================

    public int markReminderDone(
            long reminderId
    ) {
        ContentValues v=new ContentValues();v.put("is_done",1);return update(reminderId,v);
    }


    // ============================================================
    // MARK PENDING
    // ============================================================

    public int markReminderPending(
            long reminderId
    ) {
        ContentValues v=new ContentValues();v.put("is_done",0);return update(reminderId,v);
    }


    // ============================================================
    // UPDATE REMINDER
    // ============================================================

    public int updateReminder(
            long reminderId,
            Integer gardenId,
            String title,
            String reminderType,
            long dueAt,
            String repeatRule,
            String note
    ) {
        if (!validGarden(gardenId) || safe(title).isEmpty() || !ReminderTime.valid(dueAt)
                || !ReminderTime.supported(repeatRule)) return 0;
        return update(reminderId,fields(gardenId,title,reminderType,dueAt,repeatRule,note));
    }


    // ============================================================
    // DELETE REMINDER
    // ============================================================

    public boolean deleteReminder(
            long reminderId
    ) {
        SQLiteDatabase db=databaseHelper.getWritableDatabase();db.beginTransaction();
        try {
            if (!accessible(reminderId)) return false;
            databaseHelper.recordCloudDeletion("reminders",reminderId);
            boolean deleted=db.delete("reminders",mutationScope(),mutationArgs(reminderId))>0;
            if(deleted)db.setTransactionSuccessful();return deleted;
        } finally {db.endTransaction();}
    }


    // ============================================================
    // UPCOMING REMINDER COUNT
    // ============================================================

    public int getPendingReminderCount() {
        int n=0;try(Cursor c=getPendingReminders(Integer.MAX_VALUE)){while(c.moveToNext())n++;}return n;
    }


    // ============================================================
    // OVERDUE REMINDER COUNT
    // ============================================================

    public int getOverdueReminderCount() {
        int n=0;try(Cursor c=getOverdueReminders()){while(c.moveToNext())n++;}return n;
    }


    // ============================================================
    // GET OVERDUE REMINDERS
    // ============================================================

    public Cursor getOverdueReminders() {
        return query("r.is_done=0 AND r.due_at<?",new String[]{String.valueOf(System.currentTimeMillis())},"r.due_at ASC",null);
    }


    // ============================================================
    // GET UPCOMING REMINDERS
    // ============================================================

    public Cursor getUpcomingReminders(
            long fromTime,
            long toTime
    ) {
        return query("r.is_done=0 AND r.due_at>=? AND r.due_at<=?",new String[]{String.valueOf(fromTime),String.valueOf(toTime)},"r.due_at ASC",null);
    }


    // ============================================================
    // SEARCH REMINDERS
    // ============================================================

    public Cursor searchReminders(
            String query
    ) {
        String pattern="%"+safe(query)+"%";
        return query("(r.title LIKE ? OR r.reminder_type LIKE ? OR r.note LIKE ? OR g.name LIKE ?)",
                new String[]{pattern,pattern,pattern,pattern},"r.is_done ASC,r.due_at ASC",null);
    }


    // ============================================================
    // SAFE STRING
    // ============================================================

    private String safe(
            String value
    ) {


        if (value == null) {


            return "";
        }


        return value.trim();
    }

    // reminders has no season_year: due_at is an absolute date, independent of report season.
    // All reads and mutations share owner + owned-garden scope, including legacy null guest owners.
    private String scope() {
        return "COALESCE(r.owner_uid,'')=? AND (r.garden_id IS NULL OR COALESCE(g.owner_uid,'')=? AND g.id IS NOT NULL)";
    }
    private Cursor query(String condition,String[] args,String order,String limit) {
        java.util.ArrayList<String> bound=new java.util.ArrayList<>();
        bound.add(owner);bound.add(owner);java.util.Collections.addAll(bound,args);
        return databaseHelper.getReadableDatabase().rawQuery(
            "SELECT r.*,g.name AS garden_name FROM reminders r LEFT JOIN gardens g ON g.id=r.garden_id WHERE "
            +scope()+" AND "+(owner.equals(databaseHelper.getActiveOwnerUid())?"1=1":"1=0")
            +" AND ("+condition+") ORDER BY "+order+(limit==null?"":" LIMIT "+limit),bound.toArray(new String[0]));
    }
    public Cursor getSchedulable(long now) {
        return query("r.is_done=0 AND r.due_at>0 AND (r.due_at>? OR UPPER(TRIM(r.repeat_rule)) IN ('DAILY','WEEKLY','MONTHLY'))",
                new String[]{String.valueOf(now)},"r.due_at ASC",null);
    }
    public Cursor getOwnedGardens() {
        return databaseHelper.getReadableDatabase().rawQuery("SELECT id,name FROM gardens WHERE COALESCE(owner_uid,'')=? AND ?=? ORDER BY name,id",
                new String[]{owner,owner,databaseHelper.getActiveOwnerUid()});
    }
    private boolean validGarden(Integer id) {
        if(!owner.equals(databaseHelper.getActiveOwnerUid()))return false;
        if(id==null)return true;if(id<=0)return false;
        try(Cursor c=databaseHelper.getReadableDatabase().rawQuery("SELECT id FROM gardens WHERE id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(id),owner})){return c.moveToFirst();}
    }
    private boolean accessible(long id) {try(Cursor c=getReminder(id)){return c.moveToFirst();}}
    private String mutationScope() {return "id=? AND id IN (SELECT r.id FROM reminders r LEFT JOIN gardens g ON g.id=r.garden_id WHERE "+scope()+")";}
    private String[] mutationArgs(long id) {return new String[]{String.valueOf(id),owner,owner};}
    private int update(long id,ContentValues values) {
        if(!owner.equals(databaseHelper.getActiveOwnerUid()))return 0;
        values.put("updated_at",System.currentTimeMillis());
        return databaseHelper.getWritableDatabase().update("reminders",values,mutationScope(),mutationArgs(id));
    }
    public boolean advance(long id,long expectedDue,long next) {
        if(!ReminderTime.valid(next)||!owner.equals(databaseHelper.getActiveOwnerUid()))return false;
        ContentValues v=new ContentValues();v.put("due_at",next);v.put("updated_at",System.currentTimeMillis());
        return databaseHelper.getWritableDatabase().update("reminders",v,mutationScope()+" AND is_done=0 AND due_at=?",
                new String[]{String.valueOf(id),owner,owner,String.valueOf(expectedDue)})==1;
    }
    private ContentValues fields(Integer garden,String title,String type,long due,String repeat,String note) {
        ContentValues v=new ContentValues();if(garden==null)v.putNull("garden_id");else v.put("garden_id",garden);
        v.put("title",safe(title));v.put("reminder_type",safe(type));v.put("due_at",due);
        v.put("repeat_rule",ReminderTime.rule(repeat));v.put("note",safe(note));v.put("updated_at",System.currentTimeMillis());return v;
    }
}
