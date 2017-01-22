package com.anysoftkeyboard.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.graphics.Palette;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.anysoftkeyboard.AnySoftKeyboard;
import com.anysoftkeyboard.keyboards.AnyKeyboard;
import com.anysoftkeyboard.keyboards.KeyboardFactory;
import com.anysoftkeyboard.keyboards.views.DemoAnyKeyboardView;
import com.anysoftkeyboard.ui.settings.setup.SetUpKeyboardWizardFragment;
import com.anysoftkeyboard.ui.settings.setup.SetupSupport;
import com.anysoftkeyboard.ui.tutorials.ChangeLogFragment;
import com.anysoftkeyboard.utils.Logger;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.menny.android.anysoftkeyboard.BuildConfig;
import com.menny.android.anysoftkeyboard.R;

import net.evendanan.chauffeur.lib.FragmentChauffeurActivity;
import net.evendanan.chauffeur.lib.experiences.TransitionExperiences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainFragment extends Fragment {

    private static final String TAG = "MainFragment";
    private AnimationDrawable mNotConfiguredAnimation = null;
    private AsyncTask<Bitmap, Void, Palette.Swatch> mPaletteTask;
    private DemoAnyKeyboardView mDemoAnyKeyboardView;
    public boolean RECORD_KEYS = false;
    private SharedPreferences prefs;
    public static final String RECORDING_STATUS = "recording_status";
    public static final int TEXTING_ONLY = 1;
    public static final int TEXTING_AND_DRIVING = 2;
    public static final int NONE = 0;
    private Button submit_button;
    private Button start_texting_only;
    private Button start_texting_and_driving;
    private RadioGroup technique_selection;
    private List texting_only_data;
    private List texting_driving_data;

    public static void setupLink(View root, int showMoreLinkId, ClickableSpan clickableSpan, boolean reorderLinkToLastChild) {
        TextView clickHere = (TextView) root.findViewById(showMoreLinkId);
        if (reorderLinkToLastChild) {
            ViewGroup rootContainer = (ViewGroup) root;
            rootContainer.removeView(clickHere);
            rootContainer.addView(clickHere);
        }

        SpannableStringBuilder sb = new SpannableStringBuilder(clickHere.getText());
        sb.clearSpans();//removing any previously (from instance-state) set click spans.
        sb.setSpan(clickableSpan, 0, clickHere.getText().length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        clickHere.setMovementMethod(LinkMovementMethod.getInstance());
        clickHere.setText(sb);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.main_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
        if (prefs.getInt(MainFragment.RECORDING_STATUS, -1) == -1)
            prefs.edit().putInt(MainFragment.RECORDING_STATUS, 0).commit();
        submit_button = (Button) view.findViewById(R.id.button_submit);
        start_texting_and_driving = (Button) view.findViewById(R.id.start_text_drive);
        start_texting_only = (Button) view.findViewById(R.id.start_text_only);
        technique_selection = (RadioGroup) view.findViewById(R.id.myRadioGroup);
        start_texting_and_driving.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (prefs.getInt(MainFragment.RECORDING_STATUS, -1) == MainFragment.NONE)
                    prefs.edit().putInt(MainFragment.RECORDING_STATUS, MainFragment.TEXTING_AND_DRIVING).commit();
                else {
                    prefs.edit().putInt(MainFragment.RECORDING_STATUS, MainFragment.NONE).commit();
                    texting_driving_data = AnySoftKeyboard.mRecord;
                    AnySoftKeyboard.mRecord = new LinkedList();//clear old data
                }
                updateButtons();
            }
        });
        start_texting_only.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (prefs.getInt(MainFragment.RECORDING_STATUS, -1) == MainFragment.NONE)
                    prefs.edit().putInt(MainFragment.RECORDING_STATUS, MainFragment.TEXTING_ONLY).commit();
                else {
                    prefs.edit().putInt(MainFragment.RECORDING_STATUS, MainFragment.NONE).commit();
                    texting_only_data = AnySoftKeyboard.mRecord;
                    AnySoftKeyboard.mRecord = new LinkedList();//clear old data
                }
                updateButtons();
            }
        });
        submit_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (texting_driving_data == null || texting_only_data == null) {
                    Toast.makeText(getActivity(), "Error please finish recording!", Toast.LENGTH_SHORT);
                    return;
                }
                Map allData = new HashMap<>();

                int technique_used = technique_selection.getCheckedRadioButtonId();
                if(technique_used == R.id.mode_1)
                    allData.put("technique", 0);
                else if(technique_used == R.id.mode_2)
                    allData.put("technique", 1);
                else
                    allData.put("technique", 2);

                allData.put("texting_only", texting_only_data);
                allData.put("texting_driving", texting_driving_data);

                // Write a message to the database
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                DatabaseReference myRef = database.getReference("" + System.currentTimeMillis());
                myRef.setValue(allData);

                texting_only_data = null;
                texting_driving_data = null;
            }
        });
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

    }

    @Override
    public void onStart() {
        super.onStart();
        MainSettingsActivity.setActivityTitle(this, getString(R.string.how_to_pointer_title));
        updateButtons();
    }

    private void updateButtons() {
        Logger.i(TAG, "Buttons updated!!, status = %d", prefs.getInt(MainFragment.RECORDING_STATUS, -1));
        Logger.i(TAG, "mRecord length = %d,", AnySoftKeyboard.mRecord.size());
        int status = prefs.getInt(MainFragment.RECORDING_STATUS, -1);
        if (status == MainFragment.NONE)
        {
            start_texting_only.setText("Record Texting Only");
            start_texting_and_driving.setText("Record Text and Drive");
        }
        else if (status == MainFragment.TEXTING_AND_DRIVING)
        {
            start_texting_only.setText("Record Texting Only");
            start_texting_and_driving.setText("Stop Recording Text and Drive ...");
        }
        else {
            start_texting_only.setText("Stop Recording Texting Only ...");
            start_texting_and_driving.setText("Record Text and Drive");
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        //there is a case where the fragment is created BUT the view is not (yet),
        //but then the fragment is destroyed (without ever reaching a state where view should be created).
        //so, protecting against that.
        if (mDemoAnyKeyboardView != null) mDemoAnyKeyboardView.onViewNotRequired();
        super.onDestroy();
    }
}