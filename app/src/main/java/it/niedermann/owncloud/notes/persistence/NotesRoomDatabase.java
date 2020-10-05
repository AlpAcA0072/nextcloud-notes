package it.niedermann.owncloud.notes.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ShortcutManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.nextcloud.android.sso.model.SingleSignOnAccount;

import java.util.Collections;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.persistence.dao.CategoryDao;
import it.niedermann.owncloud.notes.persistence.dao.LocalAccountDao;
import it.niedermann.owncloud.notes.persistence.dao.NoteDao;
import it.niedermann.owncloud.notes.persistence.entity.CategoryEntity;
import it.niedermann.owncloud.notes.persistence.entity.LocalAccountEntity;
import it.niedermann.owncloud.notes.persistence.entity.NoteEntity;
import it.niedermann.owncloud.notes.shared.model.Capabilities;
import it.niedermann.owncloud.notes.shared.model.CloudNote;
import it.niedermann.owncloud.notes.shared.model.DBNote;
import it.niedermann.owncloud.notes.shared.model.DBStatus;
import it.niedermann.owncloud.notes.shared.util.ColorUtil;

import static it.niedermann.owncloud.notes.shared.util.NoteUtil.generateNoteExcerpt;
import static it.niedermann.owncloud.notes.widget.notelist.NoteListWidget.updateNoteListWidgets;
import static it.niedermann.owncloud.notes.widget.singlenote.SingleNoteWidget.updateSingleNoteWidgets;

@Database(
        entities = {
                LocalAccountEntity.class,
                NoteEntity.class
        }, version = 18
)
public abstract class NotesRoomDatabase extends RoomDatabase {

    private static final String TAG = NotesRoomDatabase.class.getSimpleName();
    private static final String NOTES_DB_NAME = "OWNCLOUD_NOTES";
    private static NotesRoomDatabase instance;
    private static Context context;
    private static NoteServerSyncHelper syncHelper;

    public static NotesRoomDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = create(context);
            NotesRoomDatabase.context = context;
            syncHelper = NoteServerSyncHelper.getInstance(NotesDatabase.getInstance(context), instance);
        }
        return instance;
    }

    private static NotesRoomDatabase create(final Context context) {
        return Room.databaseBuilder(
                context,
                NotesRoomDatabase.class,
                NOTES_DB_NAME)
                .addMigrations(OLD_STUFF)
                .fallbackToDestructiveMigration()
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                        Log.v(TAG, NotesRoomDatabase.class.getSimpleName() + " created.");
                    }
                })
                .allowMainThreadQueries() // FIXME remove
                .build();
    }

    private static final Migration OLD_STUFF = new Migration(17, 18) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {

        }
    };

    public abstract LocalAccountDao getLocalAccountDao();

    public abstract CategoryDao getCategoryDao();

    public abstract NoteDao getNoteDao();

    @SuppressWarnings("UnusedReturnValue")
    public long addAccount(@NonNull String url, @NonNull String username, @NonNull String accountName, @NonNull Capabilities capabilities) {
        final LocalAccountEntity entity = new LocalAccountEntity();
        entity.setUrl(url);
        entity.setUsername(username);
        entity.setAccountName(accountName);
        entity.setCapabilities(capabilities);
        return getLocalAccountDao().insert(entity);
    }

    public void updateBrand(long accountId, @NonNull Capabilities capabilities) throws IllegalArgumentException {
        validateAccountId(accountId);

        String color;
        try {
            color = ColorUtil.formatColorToParsableHexString(capabilities.getColor()).substring(1);
        } catch (Exception e) {
            color = "0082C9";
        }

        String textColor;
        try {
            textColor = ColorUtil.formatColorToParsableHexString(capabilities.getTextColor()).substring(1);
        } catch (Exception e) {
            textColor = "FFFFFF";
        }

        getLocalAccountDao().updateBrand(accountId, color, textColor);
    }


    void deleteNote(long id, @NonNull DBStatus forceDBStatus) {
        getNoteDao().deleteByCardId(id, forceDBStatus);
        getCategoryDao().removeEmptyCategory(id);
    }


    /**
     * Get the category if with the given category title
     * The method does not support fuzzy search.
     * Because the category title in database is unique, there will not at most one result.
     * If there is no such category, database will create it if create flag is set.
     * Otherwise this method will return -1 as default value.
     *
     * @param accountId     The user {@link LocalAccountEntity} Id
     * @param categoryTitle The category title which will be search in the db
     * @return -1 if there is no such category else the corresponding id
     */
    @NonNull
    @WorkerThread
    private Long getOrCreateCategoryIdByTitle(long accountId, @NonNull String categoryTitle) {
        validateAccountId(accountId);
        Long categoryId = getCategoryDao().getCategoryIdByTitle(accountId, categoryTitle);
        if (categoryId > 0) {
            return categoryId;
        } else {
            CategoryEntity entity = new CategoryEntity();
            entity.accountId = accountId;
            entity.title = categoryTitle;
            return getCategoryDao().addCategory(entity);
        }
    }

    public void moveNoteToAnotherAccount(SingleSignOnAccount ssoAccount, long oldAccountId, DBNote note, long newAccountId) {
        // Add new note
        addNoteAndSync(ssoAccount, newAccountId, new CloudNote(0, note.getModified(), note.getTitle(), note.getContent(), note.isFavorite(), note.getCategory(), null));
        deleteNoteAndSync(ssoAccount, note.getId());

        notifyWidgets();
        syncHelper.scheduleSync(ssoAccount, true);
    }



    /**
     * Marks a Note in the Database as Deleted. In the next Synchronization it will be deleted
     * from the Server.
     *
     * @param id long - ID of the Note that should be deleted
     */
    public void deleteNoteAndSync(SingleSignOnAccount ssoAccount, long id) {
        getNoteDao().updateStatus(id, DBStatus.LOCAL_DELETED);
        notifyWidgets();
        syncHelper.scheduleSync(ssoAccount, true);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
            if (shortcutManager != null) {
                shortcutManager.getPinnedShortcuts().forEach((shortcut) -> {
                    String shortcutId = id + "";
                    if (shortcut.getId().equals(shortcutId)) {
                        Log.v(TAG, "Removing shortcut for " + shortcutId);
                        shortcutManager.disableShortcuts(Collections.singletonList(shortcutId), context.getResources().getString(R.string.note_has_been_deleted));
                    }
                });
            } else {
                Log.e(TAG, ShortcutManager.class.getSimpleName() + "is null.");
            }
        }
    }

    /**
     * Creates a new Note in the Database and adds a Synchronization Flag.
     *
     * @param note Note
     */
    public long addNoteAndSync(SingleSignOnAccount ssoAccount, long accountId, CloudNote note) {
        DBNote dbNote = new DBNote(0, 0, note.getModified(), note.getTitle(), note.getContent(), note.isFavorite(), note.getCategory(), note.getEtag(), DBStatus.LOCAL_EDITED, accountId, generateNoteExcerpt(note.getContent(), note.getTitle()), 0);
        long id = addNote(accountId, dbNote);
        notifyWidgets();
        syncHelper.scheduleSync(ssoAccount, true);
        return id;
    }

    /**
     * Inserts a note directly into the Database.
     * No Synchronisation will be triggered! Use addNoteAndSync()!
     *
     * @param note Note to be added. Remotely created Notes must be of type CloudNote and locally created Notes must be of Type DBNote (with DBStatus.LOCAL_EDITED)!
     */
    long addNote(long accountId, CloudNote note) {
        NoteEntity entity = new NoteEntity();
        if (note instanceof DBNote) {
            DBNote dbNote = (DBNote) note;
            if (dbNote.getId() > 0) {
                entity.id = dbNote.getId();
            }
            entity.status = dbNote.getStatus().getTitle();
            entity.accountId = dbNote.getAccountId();
            entity.excerpt = dbNote.getExcerpt();
        } else {
            entity.status = DBStatus.VOID.getTitle();
            entity.accountId = accountId;
            entity.excerpt = generateNoteExcerpt(note.getContent(), note.getTitle());
        }
        if (note.getRemoteId() > 0) {
            entity.remoteId = note.getRemoteId();
        }
        entity.title = note.getTitle();
        entity.modified = note.getModified().getTimeInMillis() / 1000;
        entity.content = note.getContent();
        entity.favorite = note.isFavorite();
        entity.categoryId = getOrCreateCategoryIdByTitle(accountId, note.getCategory());
        entity.eTag = note.getEtag();
        return getNoteDao().addNote(entity);
    }

    private static void validateAccountId(long accountId) {
        if (accountId < 1) {
            throw new IllegalArgumentException("accountId must be greater than 0");
        }
    }

    /**
     * Notify about changed notes.
     */
    protected void notifyWidgets() {
        updateSingleNoteWidgets(context);
        updateNoteListWidgets(context);
    }
}