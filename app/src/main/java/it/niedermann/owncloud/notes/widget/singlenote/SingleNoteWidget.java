package it.niedermann.owncloud.notes.widget.singlenote;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.edit.BaseNoteFragment;
import it.niedermann.owncloud.notes.edit.EditNoteActivity;
import it.niedermann.owncloud.notes.persistence.NotesRepository;
import it.niedermann.owncloud.notes.persistence.entity.SingleNoteWidgetData;

public class SingleNoteWidget extends AppWidgetProvider {

    private static final String TAG = SingleNoteWidget.class.getSimpleName();

    static void updateAppWidget(Context context, AppWidgetManager awm, int[] appWidgetIds) {
        final Intent templateIntent = new Intent(context, EditNoteActivity.class);
        final NotesRepository repo = NotesRepository.getInstance(context);

        for (int appWidgetId : appWidgetIds) {
            final SingleNoteWidgetData data = repo.getSingleNoteWidgetData(appWidgetId);
            if (data != null) {
                templateIntent.putExtra(BaseNoteFragment.PARAM_ACCOUNT_ID, data.getAccountId());

                final PendingIntent templatePendingIntent = PendingIntent.getActivity(context, appWidgetId, templateIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

                final Intent serviceIntent = new Intent(context, SingleNoteWidgetService.class);
                serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));

                final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_single_note);
                views.setPendingIntentTemplate(R.id.single_note_widget_lv, templatePendingIntent);
                views.setRemoteAdapter(R.id.single_note_widget_lv, serviceIntent);
                views.setEmptyView(R.id.single_note_widget_lv, R.id.widget_single_note_placeholder_tv);

                awm.notifyAppWidgetViewDataChanged(appWidgetId, R.id.single_note_widget_lv);
                awm.updateAppWidget(appWidgetId, views);
            } else {
                Log.i(TAG, "onUpdate has been triggered before the user finished configuring the widget");
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        updateAppWidget(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        AppWidgetManager awm = AppWidgetManager.getInstance(context);

        updateAppWidget(context, AppWidgetManager.getInstance(context),
                (awm.getAppWidgetIds(new ComponentName(context, SingleNoteWidget.class))));
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        final NotesRepository repo = NotesRepository.getInstance(context);

        for (int appWidgetId : appWidgetIds) {
            new Thread(() -> repo.removeSingleNoteWidget(appWidgetId)).start();
        }
        super.onDeleted(context, appWidgetIds);
    }

    /**
     * Update single note widget, if the note data was changed.
     */
    public static void updateSingleNoteWidgets(Context context) {
        context.sendBroadcast(new Intent(context, SingleNoteWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE));
    }
}
