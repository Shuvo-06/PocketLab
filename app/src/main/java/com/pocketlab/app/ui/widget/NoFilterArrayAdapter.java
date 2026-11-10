package com.pocketlab.app.ui.widget;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for a read-only dropdown (an AutoCompleteTextView with inputType="none"). A normal
 * ArrayAdapter filters its list by the text in the box, so after one item was picked or the text was
 * restored, the dropdown showed only that one item and the position of a click no longer matched the
 * original list. This adapter always shows every item.
 */
public final class NoFilterArrayAdapter extends ArrayAdapter<String> {

    private final List<String> all;

    public NoFilterArrayAdapter(@NonNull Context context, @NonNull List<String> items) {
        super(context, android.R.layout.simple_dropdown_item_1line, items);
        this.all = new ArrayList<>(items);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults r = new FilterResults();
                r.values = all;
                r.count = all.size();
                return r;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };
    }
}
