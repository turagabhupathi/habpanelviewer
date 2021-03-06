package de.vier_bier.habpanelviewer.settings;

import android.app.Activity;
import android.app.LoaderManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.List;

import javax.net.ssl.SSLException;

import de.vier_bier.habpanelviewer.AdminReceiver;
import de.vier_bier.habpanelviewer.BuildConfig;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for preferences.
 */
public class SettingsFragment extends PreferenceFragment {
    private DevicePolicyManager mDPM;

    private static final String[] ITEMS_PREFS = new String[]{
            "pref_motion_item", "pref_proximity_item", "pref_volume_item", "pref_connected_item",
            "pref_pressure_item", "pref_brightness_item", "pref_temperature_item", "pref_command_item",
            "pref_battery_item", "pref_battery_charging_item", "pref_battery_level_item",
            "pref_screen_item", "pref_usage_item"};

    private ItemsAsyncTaskLoader mLoader;
    private final LoaderManager.LoaderCallbacks<List<String>> mLoaderCallbacks = new LoaderManager.LoaderCallbacks<List<String>>() {
        @Override
        public Loader<List<String>> onCreateLoader(int i, Bundle bundle) {
            return mLoader;
        }

        @Override
        public void onLoadFinished(Loader<List<String>> loader, List<String> strings) {
            for (String key : ITEMS_PREFS) {
                final EditText editText = ((EditTextPreference) findPreference(key)).getEditText();

                if (editText instanceof AutoCompleteTextView) {
                    AutoCompleteTextView t = (AutoCompleteTextView) editText;
                    t.setAdapter(new ArrayAdapter(getActivity(), android.R.layout.simple_dropdown_item_1line, strings));
                }
            }
        }

        @Override
        public void onLoaderReset(Loader<List<String>> loader) {
            for (String key : ITEMS_PREFS) {
                final EditText editText = ((EditTextPreference) findPreference(key)).getEditText();

                if (editText instanceof AutoCompleteTextView) {
                    AutoCompleteTextView t = (AutoCompleteTextView) editText;
                    t.setAdapter(null);
                }
            }
        }
    };

    private boolean cameraEnabled = false;
    private boolean motionEnabled = false;
    private boolean proximityEnabled = false;
    private boolean pressureEnabled = false;
    private boolean brightnessEnabled = false;
    private boolean temperatureEnabled = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getArguments();
        if (bundle != null) {
            cameraEnabled = bundle.getBoolean("camera_enabled");
            motionEnabled = bundle.getBoolean("motion_enabled");
            proximityEnabled = bundle.getBoolean("proximity_enabled");
            pressureEnabled = bundle.getBoolean("pressure_enabled");
            brightnessEnabled = bundle.getBoolean("brightness_enabled");
            temperatureEnabled = bundle.getBoolean("temperature_enabled");
        }

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        mDPM = (DevicePolicyManager) getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
        mLoader = new ItemsAsyncTaskLoader(getActivity());

        // disable preferences if functionality is not available
        if (!cameraEnabled) {
            findPreference("pref_camera").setEnabled(false);
            findPreference("pref_camera").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_camera)));
        }
        if (!motionEnabled) {
            findPreference("pref_motion").setEnabled(false);
            findPreference("pref_motion").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_motion)));
        }
        if (!proximityEnabled) {
            findPreference("pref_proximity").setEnabled(false);
            findPreference("pref_proximity").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_proximity)));
        }
        if (!pressureEnabled) {
            findPreference("pref_pressure").setEnabled(false);
            findPreference("pref_pressure").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_pressure)));
        }
        if (!brightnessEnabled) {
            findPreference("pref_brightness").setEnabled(false);
            findPreference("pref_brightness").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_brightness)));
        }
        if (!temperatureEnabled) {
            findPreference("pref_temperature").setEnabled(false);
            findPreference("pref_temperature").setSummary(getString(R.string.notAvailableOnDevice, getString(R.string.pref_temperature)));
        }

        onActivityResult(42, RESULT_OK, null);

        // add validation to the device admin
        CheckBoxPreference adminPreference = (CheckBoxPreference) findPreference("pref_device_admin");
        adminPreference.setOnPreferenceChangeListener(new AdminValidatingListener());

        // add validation to the openHAB url
        EditTextPreference urlPreference = (EditTextPreference) findPreference("pref_server_url");
        urlPreference.setOnPreferenceChangeListener(new URLValidatingListener());
        mLoader.setServerUrl(urlPreference.getText());

        EditTextPreference connectedIntervalPreference =
                (EditTextPreference) findPreference("pref_connected_interval");
        connectedIntervalPreference.setOnPreferenceChangeListener(new NumberValidatingListener(0, 6000));

        // add validation to the items
        for (String key : ITEMS_PREFS) {
            final EditText editText = ((EditTextPreference) findPreference(key)).getEditText();
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(final Editable editable) {
                    final String itemName = editable.toString();

                    getActivity().runOnUiThread(() -> {
                        if (mLoader.isValid(itemName)) {
                            editText.setTextColor(Color.GREEN);
                        } else {
                            editText.setTextColor(Color.RED);
                        }
                    });
                }

                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }
            });

            // initial color
            editText.setText(editText.getText());
        }

        getLoaderManager().initLoader(1234, null, mLoaderCallbacks);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 42 && resultCode == RESULT_OK) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            boolean isActive = mDPM.isAdminActive(AdminReceiver.COMP);

            if (prefs.getBoolean("pref_device_admin", false) != isActive) {
                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putBoolean("pref_device_admin", isActive);
                editor1.putString("pref_app_version", BuildConfig.VERSION_NAME);
                editor1.apply();

                CheckBoxPreference adminPreference = (CheckBoxPreference) findPreference("pref_device_admin");
                adminPreference.setChecked(isActive);
            }
        }
    }

    private void removeAsAdmin() {
        mDPM.removeActiveAdmin(AdminReceiver.COMP);

        CheckBoxPreference adminPreference = (CheckBoxPreference) findPreference("pref_device_admin");
        adminPreference.setChecked(false);
    }

    private void installAsAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.COMP);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.deviceAdminDescription));
        startActivityForResult(intent, 42);
    }

    private static final class ValidateHabPanelTask extends AsyncTask<String, Void, Void> {
        private WeakReference<Activity> activity;
        private final CharSequence preferenceName;

        ValidateHabPanelTask(Activity act, CharSequence prefName) {
            activity = new WeakReference<>(act);
            preferenceName = prefName;
        }

        @Override
        protected Void doInBackground(String... urls) {
            String dialogTitle = null;
            String dialogText = null;
            try {
                String serverURL = urls[0] + "/rest/services";
                HttpURLConnection urlConnection = ConnectionUtil.createUrlConnection(serverURL);
                urlConnection.connect();

                if (urlConnection.getResponseCode() != 200) {
                    dialogText = getResString(R.string.notValidOpenHabUrl);
                } else {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader((urlConnection.getInputStream())))) {
                        String line;
                        boolean habPanelFound = false;
                        while (!habPanelFound && (line = br.readLine()) != null) {
                            habPanelFound = line.contains("\"org.openhab.habpanel\"");
                        }
                        if (!habPanelFound) {
                            dialogText = getResString(R.string.habPanelNotAvailable);
                        }
                    }
                }

                urlConnection.disconnect();
            } catch (MalformedURLException e) {
                dialogText = urls[0] + getResString(R.string.notValidUrl);
            } catch (SSLException e) {
                dialogTitle = getResString(R.string.certInvalid);
                dialogText = getResString(R.string.couldNotConnect) + " " + urls[0] + ".\n" +
                        getResString(R.string.acceptCertWhenOurOfSettings);
            } catch (IOException | GeneralSecurityException e) {
                dialogText = getResString(R.string.couldNotConnect) + " " + urls[0];
            }

            Activity a = activity.get();
            if (dialogText != null && a != null && !a.isFinishing()) {
                if (dialogTitle == null) {
                    dialogTitle = preferenceName + " " + getResString(R.string.invalid);
                }

                UiUtil.showDialog(a, dialogTitle, dialogText);
            }

            return null;
        }

        private String getResString(int resId) {
            Activity a = activity.get();
            if (a != null) {
                return a.getResources().getString(resId);
            }

            return "";
        }
    }

    private class URLValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            String text = (String) o;
            mLoader.setServerUrl(text);

            if (text == null || text.isEmpty()) {
                return true;
            }
            AsyncTask<String, Void, Void> validator =
                    new ValidateHabPanelTask(SettingsFragment.this.getActivity(), preference.getTitle());
            validator.execute(text);

            return true;
        }

    }

    private class AdminValidatingListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            boolean value = (Boolean) o;

            if (value && !mDPM.isAdminActive(AdminReceiver.COMP)) {
                installAsAdmin();
            } else if (!value && mDPM.isAdminActive(AdminReceiver.COMP)) {
                removeAsAdmin();
            }

            return false;
        }
    }

    private class NumberValidatingListener implements Preference.OnPreferenceChangeListener {
        private final int minVal;
        private final int maxVal;

        NumberValidatingListener(int minVal, int maxVal) {
            this.minVal = minVal;
            this.maxVal = maxVal;
        }

        @Override
        public boolean onPreferenceChange(final Preference preference, Object o) {
            boolean invalid;
            try {
                int val = Integer.parseInt((String) o);

                invalid = val < minVal || val > maxVal;
            } catch (NumberFormatException e) {
                invalid = true;
            }

            if (invalid && getActivity() != null && !getActivity().isFinishing()) {
                UiUtil.showDialog(getActivity(), preference.getTitle() + " "
                                + SettingsFragment.this.getResources().getString(R.string.invalid),
                        getString(R.string.noValidIntInRange, minVal, maxVal));
                return false;
            }

            return true;
        }
    }

}
