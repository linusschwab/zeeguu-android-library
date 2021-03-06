package ch.unibe.zeeguulibrary.MyWords;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.TextView;

import java.util.ArrayList;

import ch.unibe.R;
import ch.unibe.zeeguulibrary.Core.ZeeguuConnectionManager;

/**
 * Fragment that is responsible to create the view for the MyWords list
 */
public class MyWordsFragment extends Fragment {
    private ZeeguuFragmentMyWordsCallbacks callback;
    private ZeeguuConnectionManager connectionManager;

    //Listview variables
    private MyWordsExpandableAdapter adapter;
    private ExpandableListView myWordsListView;
    private SwipeRefreshLayout swipeLayout;

    private boolean listviewExpanded;
    private boolean listviewRefreshing;

    private ActionMode mode;
    private MenuItem menuItemExpandCollapse;
    private MenuItem menuItemRefresh;

    private TextView emptyText;

    public interface ZeeguuFragmentMyWordsCallbacks {
        ZeeguuConnectionManager getZeeguuConnectionManager();

        void displayMessage(String message);

        void openUrlInBrowser(String URL);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mywords, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        listviewRefreshing = false;

        //create listview for myWordsListView and customize it
        emptyText = (TextView) view.findViewById(R.id.mywords_empty);
        myWordsListView = (ExpandableListView) view.findViewById(R.id.mywords_listview);

        //open actionbar menu for deleting the items when longclick
        myWordsListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (id != 0) {
                    AppCompatActivity activity = (AppCompatActivity) getActivity();
                    mode = activity.startSupportActionMode(new ActionBarCallBack(id, view));
                    return true;
                }
                return false;
            }
        });

        //when clicked somewhere else, close the longclick dialog
        myWordsListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View view, int groupPosition, int childPosition, long id) {
                if (id != 0) {
                    if (mode != null)
                        mode.finish();
                    return true;
                } else {
                    MyWordsInfoHeader header = (MyWordsInfoHeader) adapter.getChild(groupPosition, childPosition);

                    if (URLUtil.isValidUrl(header.getUrl())) {
                        header.setClicked();
                        callback.openUrlInBrowser(header.getUrl());
                        return true;
                    }
                    return false;
                }
            }
        });

        swipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.mywords_listview_swipe_refresh_layout);
        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshMyWords();
            }
        });

        //activate individual menu for this fragments
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        callback = (ZeeguuFragmentMyWordsCallbacks) getActivity();
        connectionManager = callback.getZeeguuConnectionManager();

        ArrayList<MyWordsHeader> list = connectionManager.getAccount().getMyWords();
        adapter = new MyWordsExpandableAdapter(getActivity(), list);
        myWordsListView.setAdapter(adapter);

        myWordsListView.setEmptyView(emptyText);
        if (adapter.isEmpty())
            setEmptyViewText();

        expandMyWordsList();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Make sure that the interface is implemented in the container activity
        try {
            callback = (ZeeguuFragmentMyWordsCallbacks) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement ZeeguuFragmentTextCallbacks");
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_mywords, menu);
        super.onCreateOptionsMenu(menu, inflater);

        menuItemExpandCollapse = menu.findItem(R.id.listview_expand_collapse);
        menuItemRefresh = menu.findItem(R.id.listview_refresh);
        updateMenuItems();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.listview_refresh) {
            refreshMyWords();
            return true;

        } else if (item.getItemId() == R.id.listview_expand_collapse) {
            if (!connectionManager.getAccount().isUserLoggedIn())
                callback.displayMessage(getString(R.string.error_login_first));
            else if (listviewExpanded)
                collapseMyWordsList();
            else
                expandMyWordsList();
            return true;
        }

        return false;
    }

    //// Public functions ////
    @Override
    public void onResume() {
        super.onResume();

        if (adapter != null) {
            adapter.notifyDataSetChanged();

            if (adapter.isEmpty())
                setEmptyViewText();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mode != null) {
            mode.finish();
        }
    }

    public void notifyDataSetChanged(boolean myWordsChanged) {
        //update wordlist if changes happend
        if (myWordsChanged && adapter != null) {
            adapter.notifyDataSetChanged();
            expandMyWordsList();
        }
        //turn of refreshing parameters
        listviewRefreshing = false;

        if (isAdded()) {
            swipeLayout.setRefreshing(false);
            updateMenuItems();
            setEmptyViewText();
        }
    }

    //// private classes ////

    private void expandMyWordsList() {
        listviewExpanded = true;
        for (int i = 0; i < adapter.getGroupCount(); i++)
            myWordsListView.expandGroup(i);

        updateMenuItems();
    }

    private void collapseMyWordsList() {
        listviewExpanded = false;
        for (int i = 0; i < adapter.getGroupCount(); i++)
            myWordsListView.collapseGroup(i);

        updateMenuItems();
    }

    private void updateMenuItems() {
        if (menuItemExpandCollapse != null && menuItemRefresh != null) {
            boolean showListMenus = connectionManager.getAccount().isUserInSession();

            menuItemRefresh.setVisible(showListMenus);
            menuItemExpandCollapse.setVisible(showListMenus);

            if (listviewExpanded)
                menuItemExpandCollapse.setTitle(R.string.mywords_collapse)
                        .setIcon(R.drawable.ic_action_mywords_expand);
            else
                menuItemExpandCollapse.setTitle(R.string.mywords_expand)
                        .setIcon(R.drawable.ic_action_mywords_collapse);
        }
    }

    private void refreshMyWords() {
        if (!connectionManager.getAccount().isUserLoggedIn()) {
            callback.displayMessage(getString(R.string.error_login_first));
        } else if (listviewRefreshing) {
            callback.displayMessage(getString(R.string.error_refreshing_already_running));
        } else {
            listviewRefreshing = true;
            if (!connectionManager.getMyWordsFromServer())
                listviewRefreshing = false; //if request not sent, set refresh variable false
        }
    }

    private void setEmptyViewText() {
        if (connectionManager.getAccount().isUserInSession())
            emptyText.setText(getString(R.string.mywords_empty));
        else if (connectionManager.isNetworkAvailable())
            emptyText.setText(getString(R.string.login_zeeguu_sign_in_message));
        else
            emptyText.setText(getString(R.string.error_no_internet_connection));
    }

    private class ActionBarCallBack implements ActionMode.Callback {
        private long id;
        private View lastSelectedView;

        public ActionBarCallBack(long id, View view) {
            this.id = id;
            lastSelectedView = view;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.item_delete) {
                if (connectionManager.getAccount().deleteWord(id) != null) {
                    connectionManager.removeBookmarkFromServer(id);
                    callback.displayMessage(getString(R.string.successful_bookmark_deleted));
                } else {
                    callback.displayMessage(getString(R.string.error_bookmark_delete));
                }
                mode.finish();

            }
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            lastSelectedView.setSelected(true);
            mode.getMenuInflater().inflate(R.menu.menu_mywords_actionmode, menu);
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (lastSelectedView != null) {
                lastSelectedView.setSelected(false);
                lastSelectedView = null;
            }
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }
    }
}