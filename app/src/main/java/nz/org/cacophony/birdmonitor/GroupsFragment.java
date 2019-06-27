package nz.org.cacophony.birdmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONObject;

import static nz.org.cacophony.birdmonitor.IdlingResourceForEspressoTesting.getGroupsIdlingResource;

public class GroupsFragment extends Fragment {
    private static final String TAG = "GroupsFragment";

    private EditText etNewGroupInput;
    private Button btnCreateGroup;
    private ListView lvGroups;
    private ArrayAdapter<String> adapter;
    private TextView tvMessages;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_groups, container, false);
        setUserVisibleHint(false);

        etNewGroupInput = view.findViewById(R.id.etNewGroupInput);
        btnCreateGroup = view.findViewById(R.id.btnCreateGroup);
        lvGroups = view.findViewById(R.id.lvGroups);
        tvMessages = view.findViewById(R.id.tvMessages);

        adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, Util.getGroupsStoredOnPhone(getActivity()));
        lvGroups.setAdapter(adapter);
        resetGroups();

        btnCreateGroup.setOnClickListener(v -> addGroup());

        lvGroups.setOnItemClickListener((a, v, index, l) -> {
            String group = lvGroups.getItemAtPosition(index).toString();
            ((SetupWizardActivity) getActivity()).setGroup(group);
            ((SetupWizardActivity) getActivity()).nextPageView();
        });

        return view;
    }

    private void resetGroups() {
        //https://stackoverflow.com/questions/14503006/android-listview-not-refreshing-after-notifydatasetchanged
        adapter.clear();
        adapter.addAll(Util.getGroupsStoredOnPhone(getActivity()));
        adapter.sort(String::compareTo);
        adapter.notifyDataSetChanged();
    }

    private void addGroup() {
        try {
            // this line adds the data of your EditText and puts in your array
            final String newGroup = etNewGroupInput.getText().toString();

            // Check group name is at least 4 characters long
            if (newGroup.length() < 4) {
                ((SetupWizardActivity) getActivity()).displayOKDialogMessage("Oops", "Please enter a group name of at least 4 characters.");
                return;
            }

            // Check if this group already exists
            if (Util.getGroupsStoredOnPhone(getActivity()).contains(newGroup)) {
                ((SetupWizardActivity) getActivity()).displayOKDialogMessage("Oops", "Sorry, can NOT add that group as it already exists.");
                return;
            }
            ((SetupWizardActivity) getActivity()).setGroup(newGroup);
            tvMessages.setText("Adding group to server");
            Util.addGroupToServer(getActivity(), newGroup, () -> {
                // Only add the group to the UI on success
                getActivity().runOnUiThread(() -> {
                    resetGroups();
                    etNewGroupInput.setText("");
                });
            });

        } catch (Exception ex) {
            Log.e(TAG, ex.getLocalizedMessage(), ex);
        }
    }

    @Override
    public void setUserVisibleHint(final boolean visible) {
        super.setUserVisibleHint(visible);
        if (getActivity() == null) {
            return;
        }
        if (visible) {
            tvMessages.setText("");
            resetGroups();

            IntentFilter iff = new IntentFilter("SERVER_GROUPS");
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(onNotice, iff);

        } else {
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(onNotice);
        }
    }


    private final BroadcastReceiver onNotice = new BroadcastReceiver() {
        //https://stackoverflow.com/questions/8802157/how-to-use-localbroadcastmanager

        @Override
        public void onReceive(Context context, Intent intent) {

            try {

                if (getView() == null) {
                    return;
                }
                String jsonStringMessage = intent.getStringExtra("jsonStringMessage");

                if (jsonStringMessage != null) {

                    JSONObject joMessage = new JSONObject(jsonStringMessage);
                    String messageType = joMessage.getString("messageType");
                    String messageToDisplay = joMessage.getString("messageToDisplay");


                    if (messageType.equalsIgnoreCase("SUCCESSFULLY_ADDED_GROUP")) {
                        // update the list of groups from server
                        tvMessages.setText(messageToDisplay);
                        ((EditText) getView().findViewById(R.id.etNewGroupInput)).setText("");

                        ((SetupWizardActivity) getActivity()).nextPageView();

                        // Refresh groups list (added as automated testing sometimes tried to get group before they were showing

                        tvMessages.setText("");
                        resetGroups();

                        getGroupsIdlingResource.decrement();

                    } else if (messageType.equalsIgnoreCase("FAILED_TO_ADD_GROUP")) {
                        ((SetupWizardActivity) getActivity()).displayOKDialogMessage("Error", messageToDisplay);
                        ((SetupWizardActivity) getActivity()).setGroup(null);

                        resetGroups();
                        getGroupsIdlingResource.decrement();
                    } else if (messageType.equalsIgnoreCase("SUCCESSFULLY_RETRIEVED_GROUPS")) {

                        resetGroups();
                        getGroupsIdlingResource.decrement();

                    } else if (messageType.equalsIgnoreCase("FAILED_TO_RETRIEVE_GROUPS")) {
                        ((SetupWizardActivity) getActivity()).displayOKDialogMessage("Error", messageToDisplay);
                        getGroupsIdlingResource.decrement();
                    }

                }


            } catch (Exception ex) {

                Log.e(TAG, ex.getLocalizedMessage(), ex);
                try {
                    getGroupsIdlingResource.decrement();
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage(), e);
                }
            }
        }
    };

}