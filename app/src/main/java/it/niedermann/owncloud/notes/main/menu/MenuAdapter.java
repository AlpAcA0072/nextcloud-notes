package it.niedermann.owncloud.notes.main.menu;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import it.niedermann.owncloud.notes.FormattingHelpActivity;
import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.about.AboutActivity;
import it.niedermann.owncloud.notes.databinding.ItemNavigationBinding;
import it.niedermann.owncloud.notes.persistence.entity.Account;
import it.niedermann.owncloud.notes.preferences.PreferencesActivity;

public class MenuAdapter extends RecyclerView.Adapter<MenuViewHolder> {

    public static final int SERVER_SETTINGS = 2;

    @NonNull
    private final MenuItem[] menuItems;
    @NonNull
    private final Consumer<MenuItem> onClick;

    public MenuAdapter(@NonNull Context context, @NonNull Account account, @NonNull Consumer<MenuItem> onClick) {
        this.menuItems = new MenuItem[]{
                new MenuItem(new Intent(context, FormattingHelpActivity.class), R.string.action_formatting_help, R.drawable.ic_baseline_help_outline_24),
                new MenuItem(generateTrashbinIntent(account), R.string.action_trashbin, R.drawable.ic_delete_grey600_24dp),
                new MenuItem(new Intent(context, PreferencesActivity.class), SERVER_SETTINGS, R.string.action_settings, R.drawable.ic_settings_grey600_24dp),
                new MenuItem(new Intent(context, AboutActivity.class), R.string.simple_about, R.drawable.ic_info_outline_grey600_24dp)
        };
        this.onClick = onClick;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public MenuViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MenuViewHolder(ItemNavigationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MenuViewHolder holder, int position) {
        holder.bind(menuItems[position], onClick);
    }

    public void updateAccount(@NonNull Account account) {
        menuItems[1].setIntent(new Intent(generateTrashbinIntent(account)));
    }

    @Override
    public int getItemCount() {
        return menuItems.length;
    }

    @NonNull
    private static Intent generateTrashbinIntent(@NonNull Account account) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(account.getUrl() + "/index.php/apps/files/?dir=/&view=trashbin"));
    }
}
