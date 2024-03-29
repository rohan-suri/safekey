/*
 * Copyright (c) 2013 Menny Even-Danan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.anysoftkeyboard.keyboards.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;
import android.support.v4.view.MotionEventCompat;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import com.anysoftkeyboard.addons.AddOn;
import com.anysoftkeyboard.addons.DefaultAddOn;
import com.anysoftkeyboard.api.KeyCodes;
import com.anysoftkeyboard.base.utils.CompatUtils;
import com.anysoftkeyboard.base.utils.GCUtils;
import com.anysoftkeyboard.base.utils.GCUtils.MemRelatedOperation;
import com.anysoftkeyboard.keyboards.AnyKeyboard;
import com.anysoftkeyboard.keyboards.AnyKeyboard.AnyKey;
import com.anysoftkeyboard.keyboards.GenericKeyboard;
import com.anysoftkeyboard.keyboards.Keyboard;
import com.anysoftkeyboard.keyboards.Keyboard.Key;
import com.anysoftkeyboard.keyboards.KeyboardDimens;
import com.anysoftkeyboard.keyboards.views.preview.KeyPreviewsManager;
import com.anysoftkeyboard.keyboards.views.preview.PreviewPopupTheme;
import com.anysoftkeyboard.theme.KeyboardTheme;
import com.anysoftkeyboard.theme.KeyboardThemeFactory;
import com.anysoftkeyboard.utils.Logger;
import com.menny.android.anysoftkeyboard.AnyApplication;
import com.menny.android.anysoftkeyboard.BuildConfig;
import com.menny.android.anysoftkeyboard.R;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

public class AnyKeyboardViewBase extends View implements
        PointerTracker.UIProxy, OnSharedPreferenceChangeListener {
    // Miscellaneous constants
    public static final int NOT_A_KEY = -1;
    static final String TAG = "ASKKbdViewBase";
    private static final int[] ACTION_KEY_TYPES = new int[]{R.attr.action_done, R.attr.action_search, R.attr.action_go};
    private static final int[] KEY_TYPES = new int[]{R.attr.key_type_function, R.attr.key_type_action};
    private static final long TWO_FINGERS_LINGER_TIME = 30;
    protected final DefaultAddOn mDefaultAddOn;
    /**
     * The canvas for the above mutable keyboard bitmap
     */
    // private Canvas mCanvas;
    protected final Paint mPaint;
    @NonNull
    protected final KeyboardDimensFromTheme mKeyboardDimens = new KeyboardDimensFromTheme();
    // TODO: Let the PointerTracker class manage this pointer queue
    final PointerQueue mPointerQueue = new PointerQueue();
    // Timing constants
    private final int mKeyRepeatInterval;
    /* keys icons */
    private final SparseArray<DrawableBuilder> mKeysIconBuilders = new SparseArray<>(32);
    private final SparseArray<Drawable> mKeysIcons = new SparseArray<>(32);
    @NonNull
    private final PointerTracker.SharedPointerTrackersData mSharedPointerTrackersData = new PointerTracker.SharedPointerTrackersData();
    private final SparseArray<PointerTracker> mPointerTrackers = new SparseArray<>();
    @NonNull
    private final KeyDetector mKeyDetector;
    private int[] mThisWindowOffset;


    protected final PreviewPopupTheme mPreviewPopupTheme = new PreviewPopupTheme();
    /**
     * The dirty region in the keyboard bitmap
     */
    private final Rect mDirtyRect = new Rect();
    private final Rect mKeyBackgroundPadding;
    private final Rect mClipRegion = new Rect(0, 0, 0, 0);
    protected final KeyPressTimingHandler mKeyPressTimingHandler;
    // a single instance is enough, there is no need to recreate every draw
    // operation!
    private final KeyboardDrawOperation mDrawOperation;
    private final Map<TextWidthCacheKey, TextWidthCacheValue> mTextWidthCache = new ArrayMap<>();
    /**
     * Listener for {@link OnKeyboardActionListener}.
     */
    protected OnKeyboardActionListener mKeyboardActionListener;
    /**
     * Notes if the keyboard just changed, so that we could possibly reallocate
     * the mBuffer.
     */
    protected boolean mKeyboardChanged;
    int mSwipeVelocityThreshold;
    int mSwipeXDistanceThreshold;
    int mSwipeYDistanceThreshold;
    int mSwipeSpaceXDistanceThreshold;
    int mScrollXDistanceThreshold;
    int mScrollYDistanceThreshold;
    int mKeyboardActionType = EditorInfo.IME_ACTION_SEND;
    private KeyDrawableStateProvider mDrawableStatesProvider;
    // XML attribute
    private float mKeyTextSize;
    private FontMetrics mTextFM;
    private ColorStateList mKeyTextColor;
    private Typeface mKeyTextStyle = Typeface.DEFAULT;
    private float mLabelTextSize;
    private FontMetrics mLabelFM;
    private float mKeyboardNameTextSize;
    private FontMetrics mKeyboardNameFM;
    private int mKeyboardNameTextColor = Color.WHITE;
    private float mHintTextSize;
    private ColorStateList mHintTextColor;
    private FontMetrics mHintTextFM;
    private int mHintLabelAlign;
    private int mHintLabelVAlign;
    private String mHintOverflowLabel = null;
    private int mShadowColor;
    private int mShadowRadius;
    private int mShadowOffsetX;
    private int mShadowOffsetY;
    private Drawable mKeyBackground;
    protected float mBackgroundDimAmount;
    private float mKeyHysteresisDistance;
    private float mVerticalCorrection;
    protected float mOriginalVerticalCorrection;
    // Main keyboard
    private AnyKeyboard mKeyboard;
    protected String mNextAlphabetKeyboardName;
    protected String mNextSymbolsKeyboardName;
    private String mKeyboardName;

    // Drawing
    private Key[] mKeys;
    private KeyPreviewsManager mKeyPreviewsManager;
    private long mLastTimeHadTwoFingers = 0;

    private Key mInvalidatedKey;
    private boolean mTouchesAreDisabledTillLastFingerIsUp = false;

    public AnyKeyboardViewBase(Context context, AttributeSet attrs) {
        this(context, attrs, R.style.PlainLightAnySoftKeyboard);
    }

    public AnyKeyboardViewBase(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDefaultAddOn = new DefaultAddOn(context, context);
        mKeyPreviewsManager = new KeyPreviewsManager(context, this, mPreviewPopupTheme);

        mKeyPressTimingHandler = new KeyPressTimingHandler(this);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAlpha(255);

        mDrawOperation = new KeyboardDrawOperation(this);

        mKeyBackgroundPadding = new Rect(0, 0, 0, 0);

        resetKeyboardTheme(KeyboardThemeFactory.getCurrentKeyboardTheme(context.getApplicationContext()));
        final Resources res = getResources();

        reloadSwipeThresholdsSettings(res);

        final float slide = res.getDimension(R.dimen.mini_keyboard_slide_allowance);
        mKeyDetector = createKeyDetector(slide);

        mKeyRepeatInterval = 50;

        AnyApplication.getConfig().addChangedListener(this);

        mNextAlphabetKeyboardName = getResources().getString(R.string.change_lang_regular);
        mNextSymbolsKeyboardName = getResources().getString(R.string.change_symbols_regular);
    }

    protected static boolean isSpaceKey(final AnyKey key) {
        return key.getPrimaryCode() == KeyCodes.SPACE;
    }

    public boolean areTouchesDisabled(MotionEvent motionEvent) {
        if (motionEvent != null && mTouchesAreDisabledTillLastFingerIsUp) {
            //calculate new value for mTouchesAreDisabledTillLastFingerIsUp
            //when do we reset the mTouchesAreDisabledTillLastFingerIsUp flag:
            //Only if we have a single pointer
            //and:
            // CANCEL - the single pointer has been cancelled. So no pointers
            // UP - the single pointer has been lifted. So now we have no pointers down.
            // DOWN - this is the first action from the single pointer, so we already were in no-pointers down state.
            final int action = MotionEventCompat.getActionMasked(motionEvent);
            if (MotionEventCompat.getPointerCount(motionEvent) == 1 &&
                    (action == MotionEvent.ACTION_CANCEL ||
                            action == MotionEvent.ACTION_DOWN ||
                            action == MotionEvent.ACTION_UP)) {
                mTouchesAreDisabledTillLastFingerIsUp = false;
                //If the action is UP then we will return the previous value (which is TRUE), since the motion events are disabled until AFTER
                //the UP event, so if this event resets the flag, this event should still be disregarded.
                return action == MotionEvent.ACTION_UP;
            }
        }
        return mTouchesAreDisabledTillLastFingerIsUp;
    }

    public boolean isAtTwoFingersState() {
        //this is a hack, I know.
        //I know that this is a swipe ONLY after the second finger is up, so I already lost the
        //two-fingers count in the motion event.
        return SystemClock.elapsedRealtime() - mLastTimeHadTwoFingers < TWO_FINGERS_LINGER_TIME;
    }

    @CallSuper
    public void disableTouchesTillFingersAreUp() {
        mKeyPressTimingHandler.cancelAllMessages();
        mKeyPreviewsManager.cancelAllPreviews();

        for (int trackerIndex = 0, trackersCount = mPointerTrackers.size(); trackerIndex < trackersCount; trackerIndex++) {
            PointerTracker tracker = mPointerTrackers.valueAt(trackerIndex);
            sendOnXEvent(MotionEvent.ACTION_CANCEL, 0, 0, 0, tracker);
            tracker.setAlreadyProcessed();
        }

        mTouchesAreDisabledTillLastFingerIsUp = true;
    }

    protected void resetKeyboardTheme(@NonNull KeyboardTheme theme) {
        final int keyboardThemeStyleResId = getKeyboardStyleResId(theme);

        final int[] remoteKeyboardThemeStyleable = theme.getResourceMapping().getRemoteStyleableArrayFromLocal(R.styleable.AnyKeyboardViewTheme);
        final int[] remoteKeyboardIconsThemeStyleable = theme.getResourceMapping().getRemoteStyleableArrayFromLocal(R.styleable.AnyKeyboardViewIconsTheme);

        final int[] padding = new int[]{0, 0, 0, 0};

        int keyTypeFunctionAttrId = R.attr.key_type_function;
        int keyActionAttrId = R.attr.key_type_action;
        int keyActionTypeDoneAttrId = R.attr.action_done;
        int keyActionTypeSearchAttrId = R.attr.action_search;
        int keyActionTypeGoAttrId = R.attr.action_go;

        HashSet<Integer> doneLocalAttributeIds = new HashSet<>();
        TypedArray a = theme.getPackageContext().obtainStyledAttributes(keyboardThemeStyleResId, remoteKeyboardThemeStyleable);
        final int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            final int remoteIndex = a.getIndex(i);
            final int localAttrId = R.styleable.AnyKeyboardViewTheme[remoteIndex];
            if (setValueFromTheme(a, padding, localAttrId, remoteIndex)) {
                doneLocalAttributeIds.add(localAttrId);
                if (localAttrId == R.attr.keyBackground) {
                    //keyTypeFunctionAttrId and keyActionAttrId are remote
                    final int[] keyStateAttributes = theme.getResourceMapping().getRemoteStyleableArrayFromLocal(KEY_TYPES);
                    keyTypeFunctionAttrId = keyStateAttributes[0];
                    keyActionAttrId = keyStateAttributes[1];
                }
            }
        }
        a.recycle();
        // taking icons
        int iconSetStyleRes = getKeyboardIconsStyleResId(theme);
        if (iconSetStyleRes != 0) {
            a = theme.getPackageContext().obtainStyledAttributes(iconSetStyleRes, remoteKeyboardIconsThemeStyleable);
            final int iconsCount = a.getIndexCount();
            for (int i = 0; i < iconsCount; i++) {
                final int remoteIndex = a.getIndex(i);
                final int localAttrId = R.styleable.AnyKeyboardViewIconsTheme[remoteIndex];
                if (setKeyIconValueFromTheme(theme, a, localAttrId, remoteIndex)) {
                    doneLocalAttributeIds.add(localAttrId);
                    if (localAttrId == R.attr.iconKeyAction) {
                        //keyActionTypeDoneAttrId and keyActionTypeSearchAttrId and keyActionTypeGoAttrId are remote
                        final int[] keyStateAttributes = theme.getResourceMapping().getRemoteStyleableArrayFromLocal(ACTION_KEY_TYPES);
                        keyActionTypeDoneAttrId = keyStateAttributes[0];
                        keyActionTypeSearchAttrId = keyStateAttributes[1];
                        keyActionTypeGoAttrId = keyStateAttributes[2];
                    }
                }
            }
            a.recycle();
        }
        // filling what's missing
        KeyboardTheme fallbackTheme = KeyboardThemeFactory.getFallbackTheme(getContext().getApplicationContext());
        final int keyboardFallbackThemeStyleResId = getKeyboardStyleResId(fallbackTheme);
        a = fallbackTheme.getPackageContext().obtainStyledAttributes(
                keyboardFallbackThemeStyleResId,
                R.styleable.AnyKeyboardViewTheme);

        final int fallbackCount = a.getIndexCount();
        for (int i = 0; i < fallbackCount; i++) {
            final int index = a.getIndex(i);
            final int attrId = R.styleable.AnyKeyboardViewTheme[index];
            if (doneLocalAttributeIds.contains(attrId))
                continue;
            setValueFromTheme(a, padding, attrId, index);
        }
        a.recycle();
        // taking missing icons
        int fallbackIconSetStyleId = fallbackTheme.getIconsThemeResId();
        a = fallbackTheme.getPackageContext().obtainStyledAttributes(
                fallbackIconSetStyleId,
                R.styleable.AnyKeyboardViewIconsTheme);

        final int fallbackIconsCount = a.getIndexCount();
        for (int i = 0; i < fallbackIconsCount; i++) {
            final int index = a.getIndex(i);
            final int attrId = R.styleable.AnyKeyboardViewIconsTheme[index];
            if (doneLocalAttributeIds.contains(attrId))
                continue;
            setKeyIconValueFromTheme(fallbackTheme, a, attrId, index);
        }
        a.recycle();
        //creating the key-drawable state provider, as we suppose to have the entire data now
        mDrawableStatesProvider = new KeyDrawableStateProvider(keyTypeFunctionAttrId, keyActionAttrId, keyActionTypeDoneAttrId, keyActionTypeSearchAttrId, keyActionTypeGoAttrId);

        // settings.
        // don't forget that there are THREE padding,
        // the theme's and the
        // background image's padding and the
        // View
        Drawable keyboardBackground = super.getBackground();
        if (keyboardBackground != null) {
            Rect backgroundPadding = new Rect();
            keyboardBackground.getPadding(backgroundPadding);
            padding[0] += backgroundPadding.left;
            padding[1] += backgroundPadding.top;
            padding[2] += backgroundPadding.right;
            padding[3] += backgroundPadding.bottom;
        }
        setPadding(padding[0], padding[1], padding[2], padding[3]);

        final Resources res = getResources();
        final int viewWidth = (getWidth() > 0) ? getWidth() : res.getDisplayMetrics().widthPixels;
        mKeyboardDimens.setKeyboardMaxWidth(viewWidth - padding[0] - padding[2]);

        mPaint.setTextSize(mKeyTextSize);

        mKeyBackground.getPadding(mKeyBackgroundPadding);
    }

    protected KeyDetector createKeyDetector(final float slide) {
        return new MiniKeyboardKeyDetector(slide);
    }

    protected boolean setValueFromTheme(TypedArray remoteTypedArray, final int[] padding,
                                        final int localAttrId, final int remoteTypedArrayIndex) {
        try {
            switch (localAttrId) {
                case android.R.attr.background:
                    Drawable keyboardBackground = remoteTypedArray.getDrawable(remoteTypedArrayIndex);
                    if (keyboardBackground == null) return false;
                    CompatUtils.setViewBackgroundDrawable(this, keyboardBackground);
                    break;
                case android.R.attr.paddingLeft:
                    padding[0] = remoteTypedArray.getDimensionPixelSize(remoteTypedArrayIndex, -1);
                    if (padding[0] == -1) return false;
                    break;
                case android.R.attr.paddingTop:
                    padding[1] = remoteTypedArray.getDimensionPixelSize(remoteTypedArrayIndex, -1);
                    if (padding[1] == -1) return false;
                    break;
                case android.R.attr.paddingRight:
                    padding[2] = remoteTypedArray.getDimensionPixelSize(remoteTypedArrayIndex, -1);
                    if (padding[2] == -1) return false;
                    break;
                case android.R.attr.paddingBottom:
                    padding[3] = remoteTypedArray.getDimensionPixelSize(remoteTypedArrayIndex, -1);
                    if (padding[3] == -1) return false;
                    break;
                case R.attr.keyBackground:
                    mKeyBackground = remoteTypedArray.getDrawable(remoteTypedArrayIndex);
                    if (mKeyBackground == null) return false;
                    break;
                case R.attr.keyHysteresisDistance:
                    mKeyHysteresisDistance = remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, -1);
                    if (mKeyHysteresisDistance == -1) return false;
                    break;
                case R.attr.verticalCorrection:
                    mOriginalVerticalCorrection = mVerticalCorrection = remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, -1);
                    if (mOriginalVerticalCorrection == -1) return false;
                    break;
                case R.attr.keyTextSize:
                    mKeyTextSize = remoteTypedArray.getDimensionPixelSize(remoteTypedArrayIndex, -1);
                    if (mKeyTextSize == -1) return false;
                    // you might ask yourself "why did Menny sqrt root the factor?"
                    // I'll tell you; the factor is mostly for the height, not the
                    // font size,
                    // but I also factorize the font size because I want the text to
                    // be a little like
                    // the key size.
                    // the whole factor maybe too much, so I ease that a bit.
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                        mKeyTextSize = (float) (mKeyTextSize * Math.sqrt(AnyApplication.getConfig().getKeysHeightFactorInLandscape()));
                    else
                        mKeyTextSize = (float) (mKeyTextSize * Math.sqrt(AnyApplication.getConfig().getKeysHeightFactorInPortrait()));
                    Logger.d(TAG, "AnySoftKeyboardTheme_keyTextSize " + mKeyTextSize);
                    break;
                case R.attr.keyTextColor:
                    mKeyTextColor = remoteTypedArray.getColorStateList(remoteTypedArrayIndex);
                    if (mKeyTextColor == null) {
                        mKeyTextColor = new ColorStateList(new int[][]{{0}},
                                new int[]{remoteTypedArray.getColor(remoteTypedArrayIndex, 0xFF000000)});
                    }
                    break;
                case R.attr.labelTextSize:
                    mLabelTextSize = remoteTypedArray.getDimensionPixelSize(remoteTypedArrayIndex, -1);
                    if (mLabelTextSize == -1) return false;
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                        mLabelTextSize = mLabelTextSize * AnyApplication.getConfig().getKeysHeightFactorInLandscape();
                    else
                        mLabelTextSize = mLabelTextSize * AnyApplication.getConfig().getKeysHeightFactorInPortrait();
                    break;
                case R.attr.keyboardNameTextSize:
                    mKeyboardNameTextSize = remoteTypedArray.getDimensionPixelSize(remoteTypedArrayIndex, -1);
                    if (mKeyboardNameTextSize == -1) return false;
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                        mKeyboardNameTextSize = mKeyboardNameTextSize * AnyApplication.getConfig().getKeysHeightFactorInLandscape();
                    else
                        mKeyboardNameTextSize = mKeyboardNameTextSize * AnyApplication.getConfig().getKeysHeightFactorInPortrait();
                    break;
                case R.attr.keyboardNameTextColor:
                    mKeyboardNameTextColor = remoteTypedArray.getColor(remoteTypedArrayIndex, Color.WHITE);
                    break;
                case R.attr.shadowColor:
                    mShadowColor = remoteTypedArray.getColor(remoteTypedArrayIndex, 0);
                    break;
                case R.attr.shadowRadius:
                    mShadowRadius = remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, 0);
                    break;
                case R.attr.shadowOffsetX:
                    mShadowOffsetX = remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, 0);
                    break;
                case R.attr.shadowOffsetY:
                    mShadowOffsetY = remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, 0);
                    break;
                case R.attr.backgroundDimAmount:
                    mBackgroundDimAmount = remoteTypedArray.getFloat(remoteTypedArrayIndex, -1f);
                    if (mBackgroundDimAmount == -1f) return false;
                    break;
                case R.attr.keyPreviewBackground:
                    Drawable keyPreviewBackground = remoteTypedArray.getDrawable(remoteTypedArrayIndex);
                    if (keyPreviewBackground == null) return false;
                    mPreviewPopupTheme.setPreviewKeyBackground(keyPreviewBackground);
                    break;
                case R.attr.keyPreviewTextColor:
                    mPreviewPopupTheme.setPreviewKeyTextColor(remoteTypedArray.getColor(remoteTypedArrayIndex, 0xFFF));
                    break;
                case R.attr.keyPreviewTextSize:
                    mPreviewPopupTheme.setPreviewKeyTextSize(remoteTypedArray.getDimensionPixelSize(remoteTypedArrayIndex, 0));
                    break;
                case R.attr.keyPreviewLabelTextSize:
                    mPreviewPopupTheme.setPreviewLabelTextSize(remoteTypedArray.getDimensionPixelSize(remoteTypedArrayIndex, 0));
                    break;
                case R.attr.keyPreviewOffset:
                    mPreviewPopupTheme.setVerticalOffset(remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, 0));
                    break;
                case R.attr.previewAnimationType:
                    int previewAnimationType = remoteTypedArray.getInteger(remoteTypedArrayIndex, -1);
                    if (previewAnimationType == -1) return false;
                    mPreviewPopupTheme.setPreviewAnimationType(previewAnimationType);
                    break;
                case R.attr.keyTextStyle:
                    int textStyle = remoteTypedArray.getInt(remoteTypedArrayIndex, 0);
                    switch (textStyle) {
                        case 0:
                            mKeyTextStyle = Typeface.DEFAULT;
                            break;
                        case 1:
                            mKeyTextStyle = Typeface.DEFAULT_BOLD;
                            break;
                        case 2:
                            mKeyTextStyle = Typeface.defaultFromStyle(Typeface.ITALIC);
                            break;
                        default:
                            mKeyTextStyle = Typeface.defaultFromStyle(textStyle);
                            break;
                    }
                    mPreviewPopupTheme.setKeyStyle(mKeyTextStyle);
                    break;
                case R.attr.keyHorizontalGap:
                    float themeHorizontalKeyGap = remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, -1);
                    if (themeHorizontalKeyGap == -1) return false;
                    mKeyboardDimens.setHorizontalKeyGap(themeHorizontalKeyGap);
                    break;
                case R.attr.keyVerticalGap:
                    float themeVerticalRowGap = remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, -1);
                    if (themeVerticalRowGap == -1) return false;
                    mKeyboardDimens.setVerticalRowGap(themeVerticalRowGap);
                    break;
                case R.attr.keyNormalHeight:
                    int themeNormalKeyHeight = remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, -1);
                    if (themeNormalKeyHeight == -1) return false;
                    mKeyboardDimens.setNormalKeyHeight(themeNormalKeyHeight);
                    break;
                case R.attr.keyLargeHeight:
                    int themeLargeKeyHeight = remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, -1);
                    if (themeLargeKeyHeight == -1) return false;
                    mKeyboardDimens.setLargeKeyHeight(themeLargeKeyHeight);
                    break;
                case R.attr.keySmallHeight:
                    int themeSmallKeyHeight = remoteTypedArray.getDimensionPixelOffset(remoteTypedArrayIndex, -1);
                    if (themeSmallKeyHeight == -1) return false;
                    mKeyboardDimens.setSmallKeyHeight(themeSmallKeyHeight);
                    break;
                case R.attr.hintTextSize:
                    mHintTextSize = remoteTypedArray.getDimensionPixelSize(remoteTypedArrayIndex, -1);
                    if (mHintTextSize == -1) return false;
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                        mHintTextSize = mHintTextSize * AnyApplication.getConfig().getKeysHeightFactorInLandscape();
                    else
                        mHintTextSize = mHintTextSize * AnyApplication.getConfig().getKeysHeightFactorInPortrait();
                    break;
                case R.attr.hintTextColor:
                    mHintTextColor = remoteTypedArray.getColorStateList(remoteTypedArrayIndex);
                    if (mHintTextColor == null) {
                        mHintTextColor = new ColorStateList(new int[][]{{0}}, new int[]{remoteTypedArray.getColor(remoteTypedArrayIndex, 0xFF000000)});
                    }
                    break;
                case R.attr.hintLabelVAlign:
                    mHintLabelVAlign = remoteTypedArray.getInt(remoteTypedArrayIndex, Gravity.BOTTOM);
                    break;
                case R.attr.hintLabelAlign:
                    mHintLabelAlign = remoteTypedArray.getInt(remoteTypedArrayIndex, Gravity.RIGHT);
                    break;
                case R.attr.hintOverflowLabel:
                    mHintOverflowLabel = remoteTypedArray.getString(remoteTypedArrayIndex);
                    break;
            }
            return true;
        } catch (Exception e) {
            // on API changes, so the incompatible themes wont crash me..
            e.printStackTrace();
            return false;
        }
    }

    private boolean setKeyIconValueFromTheme(KeyboardTheme theme, TypedArray remoteTypeArray,
                                             final int localAttrId, final int remoteTypedArrayIndex) {
        final int keyCode;
        try {
            switch (localAttrId) {
                case R.attr.iconKeyShift:
                    keyCode = KeyCodes.SHIFT;
                    break;
                case R.attr.iconKeyControl:
                    keyCode = KeyCodes.CTRL;
                    break;
                case R.attr.iconKeyAction:
                    keyCode = KeyCodes.ENTER;
                    break;
                case R.attr.iconKeyBackspace:
                    keyCode = KeyCodes.DELETE;
                    break;
                case R.attr.iconKeyCancel:
                    keyCode = KeyCodes.CANCEL;
                    break;
                case R.attr.iconKeyGlobe:
                    keyCode = KeyCodes.MODE_ALPHABET;
                    break;
                case R.attr.iconKeySpace:
                    keyCode = KeyCodes.SPACE;
                    break;
                case R.attr.iconKeyTab:
                    keyCode = KeyCodes.TAB;
                    break;
                case R.attr.iconKeyArrowDown:
                    keyCode = KeyCodes.ARROW_DOWN;
                    break;
                case R.attr.iconKeyArrowLeft:
                    keyCode = KeyCodes.ARROW_LEFT;
                    break;
                case R.attr.iconKeyArrowRight:
                    keyCode = KeyCodes.ARROW_RIGHT;
                    break;
                case R.attr.iconKeyArrowUp:
                    keyCode = KeyCodes.ARROW_UP;
                    break;
                case R.attr.iconKeyInputMoveHome:
                    keyCode = KeyCodes.MOVE_HOME;
                    break;
                case R.attr.iconKeyInputMoveEnd:
                    keyCode = KeyCodes.MOVE_END;
                    break;
                case R.attr.iconKeyMic:
                    keyCode = KeyCodes.VOICE_INPUT;
                    break;
                case R.attr.iconKeySettings:
                    keyCode = KeyCodes.SETTINGS;
                    break;
                case R.attr.iconKeyCondenseNormal:
                    keyCode = KeyCodes.MERGE_LAYOUT;
                    break;
                case R.attr.iconKeyCondenseSplit:
                    keyCode = KeyCodes.SPLIT_LAYOUT;
                    break;
                case R.attr.iconKeyCondenseCompactToRight:
                    keyCode = KeyCodes.COMPACT_LAYOUT_TO_RIGHT;
                    break;
                case R.attr.iconKeyCondenseCompactToLeft:
                    keyCode = KeyCodes.COMPACT_LAYOUT_TO_LEFT;
                    break;
                case R.attr.iconKeyClipboardCopy:
                    keyCode = KeyCodes.CLIPBOARD_COPY;
                    break;
                case R.attr.iconKeyClipboardCut:
                    keyCode = KeyCodes.CLIPBOARD_CUT;
                    break;
                case R.attr.iconKeyClipboardPaste:
                    keyCode = KeyCodes.CLIPBOARD_PASTE;
                    break;
                case R.attr.iconKeyClipboardSelect:
                    keyCode = KeyCodes.CLIPBOARD_SELECT_ALL;
                    break;
                case R.attr.iconKeyQuickText:
                    keyCode = KeyCodes.QUICK_TEXT;
                    break;
                default:
                    keyCode = 0;
            }
            if (keyCode == 0) {
                if (BuildConfig.DEBUG)
                    throw new IllegalArgumentException("No valid keycode for attr " + remoteTypeArray.getResourceId(remoteTypedArrayIndex, 0));
                Logger.w(TAG, "No valid keycode for attr %d", remoteTypeArray.getResourceId(remoteTypedArrayIndex, 0));
                return false;
            } else {
                mKeysIconBuilders.put(keyCode, DrawableBuilder.build(theme, remoteTypeArray, remoteTypedArrayIndex));
                Logger.d(TAG, "DrawableBuilders size is %d, newest key code %d for resId %d (at index %d)", mKeysIconBuilders.size(), keyCode, remoteTypeArray.getResourceId(remoteTypedArrayIndex, 0), remoteTypedArrayIndex);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    protected int getKeyboardStyleResId(KeyboardTheme theme) {
        return theme.getPopupThemeResId();
    }

    protected int getKeyboardIconsStyleResId(KeyboardTheme theme) {
        return theme.getPopupIconsThemeResId();
    }

    private void reloadSwipeThresholdsSettings(final Resources res) {
        final float density = res.getDisplayMetrics().density;
        mSwipeVelocityThreshold = (int) (AnyApplication.getConfig()
                .getSwipeVelocityThreshold() * density);
        mSwipeXDistanceThreshold = (int) (AnyApplication.getConfig()
                .getSwipeDistanceThreshold() * density);
        Keyboard kbd = getKeyboard();
        if (kbd != null) {
            mSwipeYDistanceThreshold = (int) (mSwipeXDistanceThreshold *
                    (((float) kbd.getHeight()) / ((float) getWidth())));
        } else {
            mSwipeYDistanceThreshold = 0;
        }
        //In case we don't have a Y distance, we'll use X's
        if (mSwipeYDistanceThreshold == 0) //noinspection SuspiciousNameCombination
            mSwipeYDistanceThreshold = mSwipeXDistanceThreshold;

        mSwipeSpaceXDistanceThreshold = mSwipeXDistanceThreshold / 2;
        mSwipeYDistanceThreshold = mSwipeYDistanceThreshold / 2;

        mScrollXDistanceThreshold = mSwipeXDistanceThreshold / 8;
        mScrollYDistanceThreshold = mSwipeYDistanceThreshold / 8;
    }

    /**
     * Returns the {@link OnKeyboardActionListener} object.
     *
     * @return the listener attached to this keyboard
     */
    protected OnKeyboardActionListener getOnKeyboardActionListener() {
        return mKeyboardActionListener;
    }

    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        for (int trackerIndex = 0, trackersCount = mPointerTrackers.size(); trackerIndex < trackersCount; trackerIndex++) {
            PointerTracker tracker = mPointerTrackers.valueAt(trackerIndex);
            tracker.setOnKeyboardActionListener(listener);
        }
    }

    protected void setKeyboard(AnyKeyboard keyboard, float verticalCorrection) {
        mVerticalCorrection = verticalCorrection;
        mKeysIcons.clear();
        if (mKeyboard != null) {
            dismissAllKeyPreviews();
        }
        // Remove any pending messages, except dismissing preview
        mKeyPressTimingHandler.cancelAllMessages();
        mKeyPreviewsManager.cancelAllPreviews();
        mKeyboard = keyboard;
        mKeyboardName = keyboard != null ? keyboard.getKeyboardName() : null;
        mKeys = mKeyDetector.setKeyboard(keyboard, keyboard.getShiftKey());
        mKeyDetector.setCorrection(-getPaddingLeft(), -getPaddingTop() + verticalCorrection);
        for (int trackerIndex = 0, trackersCount = mPointerTrackers.size(); trackerIndex < trackersCount; trackerIndex++) {
            PointerTracker tracker = mPointerTrackers.valueAt(trackerIndex);
            tracker.setKeyboard(mKeys, mKeyHysteresisDistance);
        }
        // setting the icon/text
        setSpecialKeysIconsAndLabels();

        //the new keyboard might be of a different size
        requestLayout();

        // Hint to reallocate the buffer if the size changed
        mKeyboardChanged = true;
        invalidateAllKeys();
        computeProximityThreshold(keyboard);
    }

    /**
     * Returns the current keyboard being displayed by this view.
     *
     * @return the currently attached keyboard
     */
    public AnyKeyboard getKeyboard() {
        return mKeyboard;
    }

    public final void setKeyboard(AnyKeyboard currentKeyboard, String nextAlphabetKeyboard, String nextSymbolsKeyboard) {
        mNextAlphabetKeyboardName = nextAlphabetKeyboard;
        if (TextUtils.isEmpty(mNextAlphabetKeyboardName))
            mNextAlphabetKeyboardName = getResources().getString(R.string.change_lang_regular);
        mNextSymbolsKeyboardName = nextSymbolsKeyboard;
        if (TextUtils.isEmpty(mNextSymbolsKeyboardName))
            mNextSymbolsKeyboardName = getResources().getString(R.string.change_symbols_regular);
        setKeyboard(currentKeyboard, mOriginalVerticalCorrection);
    }

    public boolean setShifted(boolean shifted) {
        if (mKeyboard != null) {
            if (mKeyboard.setShifted(shifted)) {
                // The whole keyboard probably needs to be redrawn
                invalidateAllKeys();
                return true;
            }
        }
        return false;
    }

    public boolean setShiftLocked(boolean shiftLocked) {
        AnyKeyboard keyboard = getKeyboard();
        if (keyboard != null) {
            if (keyboard.setShiftLocked(shiftLocked)) {
                invalidateAllKeys();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the state of the shift key of the UI, if any.
     *
     * @return true if the shift is in a pressed state, false otherwise. If
     * there is no shift key on the keyboard or there is no keyboard
     * attached, it returns false.
     */
    public boolean isShifted() {
        //if there no keyboard is set, then the shift state is false
        return mKeyboard != null && mKeyboard.isShifted();
    }

    public boolean setControl(boolean control) {
        if (mKeyboard != null) {
            if (mKeyboard.setControl(control)) {
                // The whole keyboard probably needs to be redrawn
                invalidateAllKeys();
                return true;
            }
        }
        return false;
    }

    /**
     * Enables or disables the key feedback popup. This is a popup that shows a
     * magnified version of the depressed key. By default the preview is
     * enabled.
     *
     * @param previewEnabled whether or not to enable the key feedback popup
     */
    protected void setPreviewEnabled(boolean previewEnabled) {
        mKeyPreviewsManager.setEnabled(previewEnabled);
    }

    /**
     * When enabled, calls to {@link OnKeyboardActionListener#onKey} will
     * include key codes for adjacent keys. When disabled, only the primary key
     * code will be reported.
     *
     * @param enabled whether or not the proximity correction is enabled
     */
    public void setProximityCorrectionEnabled(boolean enabled) {
        mKeyDetector.setProximityCorrectionEnabled(enabled);
    }

    private CharSequence adjustLabelToShiftState(AnyKey key) {
        CharSequence label = key.label;
        if (mKeyboard.isShifted()) {
            if (!TextUtils.isEmpty(key.shiftedKeyLabel)) {
                return key.shiftedKeyLabel;
            } else if (label != null && label.length() == 1) {
                label = Character.toString((char) key.getCodeAtIndex(0, mKeyDetector.isKeyShifted(key)));
            }
            //remembering for next time
            if (key.isShiftCodesAlways()) key.shiftedKeyLabel = label;
        }
        return label;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Round up a little
        if (mKeyboard == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
            if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
                width = MeasureSpec.getSize(widthMeasureSpec);
            }
            int height = mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(width, height);
        }
    }

    /**
     * Compute the average distance between adjacent keys (horizontally and
     * vertically) and square it to get the proximity threshold. We use a square
     * here and in computing the touch distance from a key's center to avoid
     * taking a square root.
     */
    private void computeProximityThreshold(Keyboard keyboard) {
        if (keyboard == null)
            return;
        final Key[] keys = mKeys;
        if (keys == null)
            return;
        int length = keys.length;
        int dimensionSum = 0;
        for (Key key : keys) {
            dimensionSum += Math.min(key.width, key.height) + key.gap;
        }
        if (dimensionSum < 0 || length == 0)
            return;
        mKeyDetector.setProximityThreshold((int) (dimensionSum * 1.4f / length));
    }

    @Override
    public void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        mDrawOperation.setCanvas(canvas);

        GCUtils.getInstance().performOperationWithMemRetry(TAG, mDrawOperation, true);
    }

    @CallSuper
    protected void onBufferDraw(Canvas canvas, final Paint paint) {
        if (mKeyboardChanged) {
            invalidateAllKeys();
            mKeyboardChanged = false;
        }

        canvas.getClipBounds(mDirtyRect);

        if (mKeyboard == null)
            return;

        final boolean drawKeyboardNameText = (mKeyboardNameTextSize > 1f)
                && AnyApplication.getConfig().getShowKeyboardNameText();

        final boolean drawHintText = (mHintTextSize > 1) && AnyApplication.getConfig().getShowHintTextOnKeys();

        final boolean useCustomKeyTextColor = false;
        // TODO: final boolean useCustomKeyTextColor =
        // AnyApplication.getConfig().getUseCustomTextColorOnKeys();
        final ColorStateList keyTextColor = useCustomKeyTextColor ?
                new ColorStateList(new int[][]{{0}}, new int[]{0xFF6666FF})
                : mKeyTextColor;

        final boolean useCustomHintColor = drawHintText && false;
        // TODO: final boolean useCustomHintColor = drawHintText &&
        final ColorStateList hintColor = useCustomHintColor ? new ColorStateList(
                new int[][]{{0}}, new int[]{0xFFFF6666})
                : mHintTextColor;

        // allow preferences to override theme settings for hint text position
        final boolean useCustomHintAlign = drawHintText
                && AnyApplication.getConfig().getUseCustomHintAlign();
        final int hintAlign = useCustomHintAlign ? AnyApplication.getConfig()
                .getCustomHintAlign() : mHintLabelAlign;
        final int hintVAlign = useCustomHintAlign ? AnyApplication.getConfig()
                .getCustomHintVAlign() : mHintLabelVAlign;

        final Drawable keyBackground = mKeyBackground;
        final Rect clipRegion = mClipRegion;
        final int kbdPaddingLeft = getPaddingLeft();
        final int kbdPaddingTop = getPaddingTop();
        final Key[] keys = mKeys;
        final Key invalidKey = mInvalidatedKey;

        boolean drawSingleKey = false;
        if (invalidKey != null && canvas.getClipBounds(clipRegion)) {
            // TODO we should use Rect.inset and Rect.contains here.
            // Is clipRegion completely contained within the invalidated key?
            if (invalidKey.x + kbdPaddingLeft - 1 <= clipRegion.left
                    && invalidKey.y + kbdPaddingTop - 1 <= clipRegion.top
                    && invalidKey.x + invalidKey.width + kbdPaddingLeft + 1 >= clipRegion.right
                    && invalidKey.y + invalidKey.height + kbdPaddingTop + 1 >= clipRegion.bottom) {
                drawSingleKey = true;
            }
        }

        for (Key keyBase : keys) {
            final AnyKey key = (AnyKey) keyBase;
            final boolean keyIsSpace = isSpaceKey(key);

            if (drawSingleKey && (invalidKey != key)) {
                continue;
            }
            if (!mDirtyRect.intersects(key.x + kbdPaddingLeft, key.y
                    + kbdPaddingTop, key.x + key.width + kbdPaddingLeft, key.y
                    + key.height + kbdPaddingTop)) {
                continue;
            }
            int[] drawableState = key.getCurrentDrawableState(mDrawableStatesProvider);

            if (keyIsSpace)
                paint.setColor(mKeyboardNameTextColor);
            else
                paint.setColor(keyTextColor.getColorForState(drawableState, 0xFF000000));
            keyBackground.setState(drawableState);

            // Switch the character to uppercase if shift is pressed
            CharSequence label = key.label == null ? null : adjustLabelToShiftState(key);

            final Rect bounds = keyBackground.getBounds();
            if ((key.width != bounds.right) || (key.height != bounds.bottom)) {
                keyBackground.setBounds(0, 0, key.width, key.height);
            }
            canvas.translate(key.x + kbdPaddingLeft, key.y + kbdPaddingTop);
            keyBackground.draw(canvas);

            if (TextUtils.isEmpty(label)) {
                Drawable iconToDraw = getIconToDrawForKey(key, false);
                if (iconToDraw != null/* && shouldDrawIcon */) {
                    //http://developer.android.com/reference/android/graphics/drawable/Drawable.html#getCurrent()
                    //http://stackoverflow.com/a/103600/1324235
                    final boolean is9Patch = iconToDraw.getCurrent() instanceof NinePatchDrawable;

                    // Special handing for the upper-right number hint icons
                    final int drawableWidth;
                    final int drawableHeight;
                    final int drawableX;
                    final int drawableY;

                    drawableWidth = is9Patch ? key.width : iconToDraw.getIntrinsicWidth();
                    drawableHeight = is9Patch ? key.height : iconToDraw.getIntrinsicHeight();
                    drawableX = (key.width + mKeyBackgroundPadding.left
                            - mKeyBackgroundPadding.right - drawableWidth) / 2;
                    drawableY = (key.height + mKeyBackgroundPadding.top
                            - mKeyBackgroundPadding.bottom - drawableHeight) / 2;

                    canvas.translate(drawableX, drawableY);
                    iconToDraw.setBounds(0, 0, drawableWidth, drawableHeight);
                    iconToDraw.draw(canvas);
                    canvas.translate(-drawableX, -drawableY);
                    if (keyIsSpace && drawKeyboardNameText) {
                        // now a little hack, I'll set the label now, so it get
                        // drawn.
                        label = mKeyboardName;
                    }
                } else {
                    // ho... no icon.
                    // I'll try to guess the text
                    label = guessLabelForKey(key.getPrimaryCode());
                }
            }

            if (label != null) {
                // For characters, use large font. For labels like "Done", use
                // small font.
                final FontMetrics fm;
                if (keyIsSpace) {
                    paint.setTextSize(mKeyboardNameTextSize);
                    paint.setTypeface(Typeface.DEFAULT_BOLD);
                    if (mKeyboardNameFM == null)
                        mKeyboardNameFM = paint.getFontMetrics();
                    fm = mKeyboardNameFM;
                } else if (label.length() > 1 && key.getCodesCount() < 2) {
                    setPaintForLabelText(paint);
                    if (mLabelFM == null) mLabelFM = paint.getFontMetrics();
                    fm = mLabelFM;
                } else {
                    setPaintToKeyText(paint);
                    if (mTextFM == null) mTextFM = paint.getFontMetrics();
                    fm = mTextFM;
                }

                if (isLabelOfPictographic(label)) {
                    paint.setTextSize(2f * paint.getTextSize());
                }

                final float labelHeight = -fm.top;
                // Draw a drop shadow for the text
                paint.setShadowLayer(mShadowRadius, mShadowOffsetX,
                        mShadowOffsetY, mShadowColor);

                final float textWidth = adjustTextSizeForLabel(paint, label, key.width);

                // the center of the drawable space, which is value used
                // previously for vertically
                // positioning the key label
                final float centerY = mKeyBackgroundPadding.top
                        + ((key.height - mKeyBackgroundPadding.top - mKeyBackgroundPadding.bottom) / (keyIsSpace ? 3
                        : 2));// the label on the space is a bit higher

                // the X coordinate for the center of the main label text is
                // unaffected by the hints
                final float textX = mKeyBackgroundPadding.left + (key.width - mKeyBackgroundPadding.left - mKeyBackgroundPadding.right) / 2;
                final float textY;
                // Some devices (mostly pre-Honeycomb, have issues with RTL text
                // drawing.
                // Of course, there is no issue with a single character :)
                // so, we'll use the RTL secured drawing (via StaticLayout) for
                // labels.
                if (label.length() > 1
                        && !AnyApplication.getConfig().workaround_alwaysUseDrawText()) {
                    // calculate Y coordinate of top of text based on center
                    // location
                    textY = centerY - ((labelHeight - paint.descent()) / 2);
                    canvas.translate(textX, textY);
                    // RTL fix. But it costs, let do it when in need (more than
                    // 1 character)
                    StaticLayout labelText = new StaticLayout(label,
                            new TextPaint(paint), (int) textWidth,
                            Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    labelText.draw(canvas);
                } else {
                    // to get Y coordinate of baseline from center of text,
                    // first add half the height (to get to
                    // bottom of text), then subtract the part below the
                    // baseline. Note that fm.top is negative.
                    textY = centerY + ((labelHeight - paint.descent()) / 2);
                    canvas.translate(textX, textY);
                    canvas.drawText(label, 0, label.length(), 0, 0, paint);
                }
                canvas.translate(-textX, -textY);
                // (-)

                // Turn off drop shadow
                paint.setShadowLayer(0, 0, 0, 0);
            }

            if (drawHintText) {
                if ((key.popupCharacters != null && key.popupCharacters
                        .length() > 0)
                        || (key.popupResId != 0)
                        || (key.longPressCode != 0)) {
                    Align oldAlign = paint.getTextAlign();

                    String hintText = null;

                    if (key.hintLabel != null && key.hintLabel.length() > 0) {
                        hintText = key.hintLabel.toString();
                        // it is the responsibility of the keyboard layout
                        // designer to ensure that they do
                        // not put too many characters in the hint label...
                    } else if (key.longPressCode != 0) {
                        if (Character.isLetterOrDigit(key.longPressCode))
                            hintText = Character
                                    .toString((char) key.longPressCode);
                    } else if (key.popupCharacters != null) {
                        final String hintString = key.popupCharacters
                                .toString();
                        final int hintLength = hintString.length();
                        if (hintLength <= 3)
                            hintText = hintString;
                    }

                    // if hintText is still null, it means it didn't fit one of
                    // the above
                    // cases, so we should provide the hint using the default
                    if (hintText == null) {
                        if (mHintOverflowLabel != null)
                            hintText = mHintOverflowLabel;
                        else {
                            // theme does not provide a defaultHintLabel
                            // use ˙˙˙ if hints are above, ... if hints are
                            // below
                            // (to avoid being too close to main label/icon)
                            if (hintVAlign == Gravity.TOP)
                                hintText = "˙˙˙";
                            else
                                hintText = "...";
                        }
                    }

                    if (mKeyboard.isShifted())
                        hintText = hintText.toUpperCase(getKeyboard().getLocale());

                    // now draw hint
                    paint.setTypeface(Typeface.DEFAULT);
                    paint.setColor(hintColor.getColorForState(drawableState, 0xFF000000));
                    paint.setTextSize(mHintTextSize);
                    // get the hint text font metrics so that we know the size
                    // of the hint when
                    // we try to position the main label (to try to make sure
                    // they don't overlap)
                    if (mHintTextFM == null) {
                        mHintTextFM = paint.getFontMetrics();
                    }

                    final float hintX;
                    final float hintY;

                    // the (float) 0.5 value is added or subtracted to just give
                    // a little more room
                    // in case the theme designer didn't account for the hint
                    // label location
                    if (hintAlign == Gravity.START) {
                        // left
                        paint.setTextAlign(Align.LEFT);
                        hintX = mKeyBackgroundPadding.left + 0.5f;
                    } else if (hintAlign == Gravity.CENTER) {
                        // center
                        paint.setTextAlign(Align.CENTER);
                        hintX = mKeyBackgroundPadding.left
                                + (key.width - mKeyBackgroundPadding.left - mKeyBackgroundPadding.right) / 2;
                    } else {
                        // right
                        paint.setTextAlign(Align.RIGHT);
                        hintX = key.width - mKeyBackgroundPadding.right - 0.5f;
                    }

                    if (hintVAlign == Gravity.TOP) {
                        // above
                        hintY = mKeyBackgroundPadding.top - mHintTextFM.top + 0.5f;
                    } else {
                        // below
                        hintY = key.height - mKeyBackgroundPadding.bottom - mHintTextFM.bottom - 0.5f;
                    }

                    canvas.drawText(hintText, hintX, hintY, paint);
                    paint.setTextAlign(oldAlign);
                }
            }

            canvas.translate(-key.x - kbdPaddingLeft, -key.y - kbdPaddingTop);
        }
        mInvalidatedKey = null;

        mDirtyRect.setEmpty();
    }

    private static boolean isLabelOfPictographic(CharSequence label) {
        if (label.length() == 0) return false;
        final char hs = label.charAt(0);

        if (0xd800 <= hs && hs <= 0xdbff) {
            return true;
        } else if (Character.isHighSurrogate(hs)) {
            return true;
        }
        return false;
    }

    private float adjustTextSizeForLabel(final Paint paint, final CharSequence label, final int width) {
        TextWidthCacheKey cacheKey = new TextWidthCacheKey(label, width);
        if (mTextWidthCache.containsKey(cacheKey)) {
            return mTextWidthCache.get(cacheKey).setCachedValues(paint);
        }
        float textSize = paint.getTextSize();
        float textWidth = paint.measureText(label, 0, label.length());
        // I'm going to try something if the key is too small for the
        // text:
        // 1) divide the text size by 1.5
        // 2) if still too large, divide by 2.5
        // 3) show no text
        if (textWidth > width) {
            textSize = mKeyTextSize / 1.5f;
            paint.setTextSize(textSize);
            textWidth = paint.measureText(label, 0, label.length());
            if (textWidth > width) {
                textSize = mKeyTextSize / 2.5f;
                paint.setTextSize(textSize);
                textWidth = paint.measureText(label, 0, label.length());
                if (textWidth > width) {
                    textSize = 0f;
                    paint.setTextSize(textSize);
                    textWidth = paint.measureText(label, 0, label.length());
                }
            }
        }

        mTextWidthCache.put(cacheKey, new TextWidthCacheValue(textSize, textWidth));
        return textWidth;
    }

    protected void setPaintForLabelText(Paint paint) {
        paint.setTextSize(mLabelTextSize);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    protected void setPaintToKeyText(final Paint paint) {
        paint.setTextSize(mKeyTextSize);
        paint.setTypeface(mKeyTextStyle);
    }

    public void setKeyboardActionType(final int imeOptions) {
        if ((imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0)
            //IME_FLAG_NO_ENTER_ACTION:
            // Flag of imeOptions: used in conjunction with one of the actions masked by IME_MASK_ACTION.
            // If this flag is not set, IMEs will normally replace the "enter" key with the action supplied.
            // This flag indicates that the action should not be available in-line as a replacement for the "enter" key.
            // Typically this is because the action has such a significant impact or is not recoverable enough
            // that accidentally hitting it should be avoided, such as sending a message.
            // Note that TextView will automatically set this flag for you on multi-line text views.
            mKeyboardActionType = EditorInfo.IME_ACTION_SEND;
        else
            mKeyboardActionType = (imeOptions & EditorInfo.IME_ACTION_SEND);

        // setting the icon/text
        setSpecialKeysIconsAndLabels();
    }

    private void setSpecialKeysIconsAndLabels() {
        Key enterKey = findKeyByPrimaryKeyCode(KeyCodes.ENTER);
        if (enterKey != null) {
            enterKey.icon = null;
            enterKey.iconPreview = null;
            enterKey.label = null;
            ((AnyKey) enterKey).shiftedKeyLabel = null;
            Drawable icon = getIconToDrawForKey(enterKey, false);
            if (icon != null) {
                enterKey.icon = icon;
                enterKey.iconPreview = icon;
            } else {
                CharSequence label = guessLabelForKey(enterKey.getPrimaryCode());
                enterKey.label = label;
                ((AnyKey) enterKey).shiftedKeyLabel = label;
            }
            // making sure something is shown
            if (enterKey.icon == null && TextUtils.isEmpty(enterKey.label)) {
                Logger.i(TAG, "Wow. Unknown ACTION ID " + mKeyboardActionType
                        + ". Will default to ENTER icon.");
                // I saw devices (Galaxy Tab 10") which say the action
                // type is 255...
                // D/ASKKbdViewBase( 3594): setKeyboardActionType
                // imeOptions:33554687 action:255
                // which means it is not a known ACTION
                Drawable enterIcon = getIconForKeyCode(KeyCodes.ENTER);
                enterIcon.setState(mDrawableStatesProvider.DRAWABLE_STATE_ACTION_NORMAL);
                enterKey.icon = enterIcon;
                enterKey.iconPreview = enterIcon;
            }
        }
        //these are dynamic keys
        setSpecialKeyIconOrLabel(KeyCodes.MODE_ALPHABET);
        setSpecialKeyIconOrLabel(KeyCodes.MODE_SYMOBLS);
        setSpecialKeyIconOrLabel(KeyCodes.KEYBOARD_MODE_CHANGE);

        mTextWidthCache.clear();
    }

    private void setSpecialKeyIconOrLabel(int keyCode) {
        Key key = findKeyByPrimaryKeyCode(keyCode);
        if (key != null) {
            if (TextUtils.isEmpty(key.label)) {
                if (key.dynamicEmblem == Keyboard.KEY_EMBLEM_TEXT) {
                    key.label = guessLabelForKey(keyCode);
                } else {
                    key.icon = getIconForKeyCode(keyCode);
                }
            }
        }
    }

    @NonNull
    private CharSequence guessLabelForKey(int keyCode) {
        switch (keyCode) {
            case KeyCodes.ENTER:
                switch (mKeyboardActionType) {
                    case EditorInfo.IME_ACTION_DONE:
                        return getContext().getText(R.string.label_done_key);
                    case EditorInfo.IME_ACTION_GO:
                        return getContext().getText(R.string.label_go_key);
                    case EditorInfo.IME_ACTION_NEXT:
                        return getContext().getText(R.string.label_next_key);
                    case 0x00000007:// API 11: EditorInfo.IME_ACTION_PREVIOUS:
                        return getContext().getText(R.string.label_previous_key);
                    case EditorInfo.IME_ACTION_SEARCH:
                        return getContext().getText(R.string.label_search_key);
                    case EditorInfo.IME_ACTION_SEND:
                        return getContext().getText(R.string.label_send_key);
                    default:
                        return "";
                }
            case KeyCodes.KEYBOARD_MODE_CHANGE:
                if (mKeyboard instanceof GenericKeyboard)
                    return guessLabelForKey(KeyCodes.MODE_ALPHABET);
                else
                    return guessLabelForKey(KeyCodes.MODE_SYMOBLS);
            case KeyCodes.MODE_ALPHABET:
                return mNextAlphabetKeyboardName;
            case KeyCodes.MODE_SYMOBLS:
                return mNextSymbolsKeyboardName;
            case KeyCodes.TAB:
                return getContext().getText(R.string.label_tab_key);
            case KeyCodes.MOVE_HOME:
                return getContext().getText(R.string.label_home_key);
            case KeyCodes.MOVE_END:
                return getContext().getText(R.string.label_end_key);
            case KeyCodes.ARROW_DOWN:
                return "▼";
            case KeyCodes.ARROW_LEFT:
                return "◀";
            case KeyCodes.ARROW_RIGHT:
                return "▶";
            case KeyCodes.ARROW_UP:
                return "▲";
            default:
                return "";
        }
    }

    private Drawable getIconToDrawForKey(Key key, boolean feedback) {
        if (key.dynamicEmblem == Keyboard.KEY_EMBLEM_TEXT)
            return null;

        if (feedback && key.iconPreview != null)
            return key.iconPreview;
        if (key.icon != null)
            return key.icon;

        return getIconForKeyCode(key.getPrimaryCode());
    }

    @Nullable
    public Drawable getDrawableForKeyCode(int keyCode) {
        Drawable icon = mKeysIcons.get(keyCode);

        if (icon == null) {
            // building needed icon
            Logger.d(TAG, "Building icon for key-code %d", keyCode);
            DrawableBuilder builder = mKeysIconBuilders.get(keyCode);
            if (builder == null)
                icon = null;
            else
                icon = builder.buildDrawable();

            if (icon != null) {
                mKeysIcons.put(keyCode, icon);
                Logger.v(TAG, "Current drawable cache size is %d", mKeysIcons.size());
            } else {
                Logger.w(TAG, "Can not find drawable for keyCode %d. Context lost?", keyCode);
            }
        }

        return icon;
    }

    private Drawable getIconForKeyCode(int keyCode) {
        Drawable icon = getDrawableForKeyCode(keyCode);
        // maybe a drawable state is required
        if (icon != null) {
            switch (keyCode) {
                case KeyCodes.ENTER:
                    Logger.d(TAG, "Action key action ID is %d", mKeyboardActionType);
                    switch (mKeyboardActionType) {
                        case EditorInfo.IME_ACTION_DONE:
                            icon.setState(mDrawableStatesProvider.DRAWABLE_STATE_ACTION_DONE);
                            break;
                        case EditorInfo.IME_ACTION_GO:
                            icon.setState(mDrawableStatesProvider.DRAWABLE_STATE_ACTION_GO);
                            break;
                        case EditorInfo.IME_ACTION_SEARCH:
                            icon.setState(mDrawableStatesProvider.DRAWABLE_STATE_ACTION_SEARCH);
                            break;
                        case EditorInfo.IME_ACTION_NONE:
                        case EditorInfo.IME_ACTION_UNSPECIFIED:
                            icon.setState(mDrawableStatesProvider.DRAWABLE_STATE_ACTION_NORMAL);
                            break;
                    }
                    break;
                case KeyCodes.SHIFT:
                    if (mKeyboard.isShiftLocked())
                        icon.setState(mDrawableStatesProvider.DRAWABLE_STATE_MODIFIER_LOCKED);
                    else if (mKeyboard.isShifted())
                        icon.setState(mDrawableStatesProvider.DRAWABLE_STATE_MODIFIER_PRESSED);
                    else
                        icon.setState(mDrawableStatesProvider.DRAWABLE_STATE_MODIFIER_NORMAL);
                    break;
                case KeyCodes.CTRL:
                    if (mKeyboard.isControl())
                        icon.setState(mDrawableStatesProvider.DRAWABLE_STATE_MODIFIER_PRESSED);
                    else
                        icon.setState(mDrawableStatesProvider.DRAWABLE_STATE_MODIFIER_NORMAL);
                    break;
            }
        }
        return icon;
    }

    void dismissAllKeyPreviews() {
        /*for (int trackerIndex = 0, trackersCount = mPointerTrackers.size(); trackerIndex < trackersCount; trackerIndex++) {
            PointerTracker tracker = mPointerTrackers.valueAt(trackerIndex);
            tracker.updateKey(NOT_A_KEY);
        }*/
        mKeyPreviewsManager.cancelAllPreviews();
    }

    @Override
    public void hidePreview(int keyIndex, PointerTracker tracker) {
        final Key key = tracker.getKey(keyIndex);
        if (keyIndex != NOT_A_KEY && key != null) {
            mKeyPreviewsManager.hidePreviewForKey(key);
        }
    }

    @Override
    public void showPreview(int keyIndex, PointerTracker tracker) {
        // We should re-draw popup preview when 1) we need to hide the preview,
        // 2) we will show
        // the space key preview and 3) pointer moves off the space key to other
        // letter key, we
        // should hide the preview of the previous key.
        final boolean hidePreviewOrShowSpaceKeyPreview = (tracker == null);
        // If key changed and preview is on or the key is space (language switch
        // is enabled)
        final Key key = hidePreviewOrShowSpaceKeyPreview ? null : tracker.getKey(keyIndex);
        //this will ensure that in case the key is marked as NO preview, we will just dismiss the previous popup.
        if (keyIndex != NOT_A_KEY && key != null) {
            Drawable iconToDraw = getIconToDrawForKey(key, true);

            // Should not draw hint icon in key preview
            if (iconToDraw != null) {
                mKeyPreviewsManager.showPreviewForKey(key, iconToDraw);
            } else {
                CharSequence label = tracker.getPreviewText(key);
                if (TextUtils.isEmpty(label)) {
                    label = guessLabelForKey(key.getPrimaryCode());
                }

                mKeyPreviewsManager.showPreviewForKey(key, label);
            }
        }
    }

    /**
     * Requests a redraw of the entire keyboard. Calling {@link #invalidate} is
     * not sufficient because the keyboard renders the keys to an off-screen
     * buffer and an invalidate() only draws the cached buffer.
     *
     * @see #invalidateKey(Key)
     */
    public void invalidateAllKeys() {
        mDirtyRect.union(0, 0, getWidth(), getHeight());
        invalidate();
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use
     * this method if only one key is changing it's content. Any changes that
     * affect the position or size of the key may not be honored.
     *
     * @param key key in the attached {@link Keyboard}.
     * @see #invalidateAllKeys
     */
    public void invalidateKey(Key key) {
        if (key == null)
            return;
        mInvalidatedKey = key;
        // TODO we should clean up this and record key's region to use in
        // onBufferDraw.
        mDirtyRect.union(key.x + getPaddingLeft(), key.y + getPaddingTop(),
                key.x + key.width + getPaddingLeft(), key.y + key.height
                        + getPaddingTop());
        // doOnBufferDrawWithMemProtection(mCanvas);
        invalidate(key.x + getPaddingLeft(), key.y + getPaddingTop(), key.x
                + key.width + getPaddingLeft(), key.y + key.height
                + getPaddingTop());
    }

    @NonNull
    public KeyboardDimens getThemedKeyboardDimens() {
        return mKeyboardDimens;
    }

    public float getLabelTextSize() {
        return mLabelTextSize;
    }

    public ColorStateList getKeyTextColor() {
        return mKeyTextColor;
    }

    /**
     * Called when a key is long pressed. By default this will open any popup
     * keyboard associated with this key through the attributes popupLayout and
     * popupCharacters.
     *
     * @param keyboardAddOn the owning keyboard that starts this long-press operation
     * @param key           the key that was long pressed
     * @return true if the long press is handled, false otherwise. Subclasses
     * should call the method on the base class if the subclass doesn't
     * wish to handle the call.
     */
    protected boolean onLongPress(AddOn keyboardAddOn, Key key, boolean isSticky, @NonNull PointerTracker tracker) {
        if (key instanceof AnyKey) {
            AnyKey anyKey = (AnyKey) key;
            if (anyKey.getKeyTags().size() > 0) {
                Object[] tags = anyKey.getKeyTags().toArray();
                for (int tagIndex = 0; tagIndex < tags.length; tagIndex++) {
                    tags[tagIndex] = ":" + tags[tagIndex];
                }
                String joinedTags = TextUtils.join(", ", tags);
                Toast.makeText(getContext().getApplicationContext(), joinedTags, Toast.LENGTH_SHORT).show();
            }
            if (anyKey.longPressCode != 0) {
                getOnKeyboardActionListener().onKey(anyKey.longPressCode, key, 0/*not multi-tap*/, null, true);
                tracker.onCancelEvent();
                return true;
            }
        }

        return false;
    }

    public int[] getLocationInWindow() {
        if (mThisWindowOffset == null) {
            mThisWindowOffset = new int[2];
            getLocationInWindow(mThisWindowOffset);
        }
        return mThisWindowOffset;
    }

    protected PointerTracker getPointerTracker(@NonNull final MotionEvent motionEvent) {
        final int index = MotionEventCompat.getActionIndex(motionEvent);
        final int id = motionEvent.getPointerId(index);
        return getPointerTracker(id);
    }

    protected PointerTracker getPointerTracker(final int id) {
        final Key[] keys = mKeys;
        final OnKeyboardActionListener listener = mKeyboardActionListener;

        if (mPointerTrackers.get(id) == null) {
            final PointerTracker tracker = new PointerTracker(id, mKeyPressTimingHandler, mKeyDetector, this, mSharedPointerTrackersData);
            if (keys != null)
                tracker.setKeyboard(keys, mKeyHysteresisDistance);
            if (listener != null)
                tracker.setOnKeyboardActionListener(listener);
            mPointerTrackers.put(id, tracker);
        }

        return mPointerTrackers.get(id);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent nativeMotionEvent) {
        if (mKeyboard == null)//I mean, if there isn't any keyboard I'm handling, what's the point?
            return false;

        final int action = MotionEventCompat.getActionMasked(nativeMotionEvent);
        final int pointerCount = MotionEventCompat.getPointerCount(nativeMotionEvent);
        if (pointerCount > 1)
            mLastTimeHadTwoFingers = SystemClock.elapsedRealtime();//marking the time. Read isAtTwoFingersState()

        if (mTouchesAreDisabledTillLastFingerIsUp) {
            if (!areTouchesDisabled(nativeMotionEvent)/*this means it was just reset*/) {
                mTouchesAreDisabledTillLastFingerIsUp = false;
                //continue with onTouchEvent flow.
                if (action != MotionEvent.ACTION_DOWN) {
                    //swallowing the event.
                    //in case this is a DOWN event, we do want to pass it
                    return true;
                }
            } else {
                //swallowing touch event until we reset mTouchesAreDisabledTillLastFingerIsUp
                return true;
            }
        }

        final long eventTime = nativeMotionEvent.getEventTime();
        final int index = MotionEventCompat.getActionIndex(nativeMotionEvent);
        final int id = nativeMotionEvent.getPointerId(index);
        final int x = (int) nativeMotionEvent.getX(index);
        final int y = (int) nativeMotionEvent.getY(index);

        if (mKeyPressTimingHandler.isInKeyRepeat()) {
            // It will keep being in the key repeating mode while the key is
            // being pressed.
            if (action == MotionEvent.ACTION_MOVE) {
                return true;
            }
            final PointerTracker tracker = getPointerTracker(id);
            // Key repeating timer will be canceled if 2 or more keys are in
            // action, and current
            // event (UP or DOWN) is non-modifier key.
            if (pointerCount > 1 && !tracker.isModifier()) {
                mKeyPressTimingHandler.cancelKeyRepeatTimer();
            }
            // Up event will pass through.
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < pointerCount; i++) {
                PointerTracker tracker = getPointerTracker(nativeMotionEvent.getPointerId(i));
                tracker.onMoveEvent((int) nativeMotionEvent.getX(i), (int) nativeMotionEvent.getY(i));
            }
        } else {
            PointerTracker tracker = getPointerTracker(id);
            sendOnXEvent(action, eventTime, x, y, tracker);
        }

        return true;
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mThisWindowOffset = null;
    }

    @NonNull
    public final KeyDetector getKeyDetector() {
        return mKeyDetector;
    }

    protected boolean isFirstDownEventInsideSpaceBar() {
        return false;
    }

    private void sendOnXEvent(final int action, final long eventTime,
                              final int x, final int y, PointerTracker tracker) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case 0x00000005:// MotionEvent.ACTION_POINTER_DOWN:
                onDownEvent(tracker, x, y, eventTime);
                break;
            case MotionEvent.ACTION_UP:
            case 0x00000006:// MotionEvent.ACTION_POINTER_UP:
                onUpEvent(tracker, x, y, eventTime);
                break;
            case MotionEvent.ACTION_CANCEL:
                onCancelEvent(tracker, x, y, eventTime);
                break;
        }
    }

    protected void onDownEvent(PointerTracker tracker, int x, int y,
                               long eventTime) {
        if (tracker.isOnModifierKey(x, y)) {
            // Before processing a down event of modifier key, all pointers
            // already being tracked
            // should be released.
            mPointerQueue.releaseAllPointersExcept(tracker, eventTime);
        }
        tracker.onDownEvent(x, y, eventTime);
        mPointerQueue.add(tracker);
    }

    protected void onUpEvent(PointerTracker tracker, int x, int y,
                             long eventTime) {
        if (tracker.isModifier()) {
            // Before processing an up event of modifier key, all pointers
            // already being tracked
            // should be released.
            mPointerQueue.releaseAllPointersExcept(tracker, eventTime);
        } else {
            int index = mPointerQueue.lastIndexOf(tracker);
            if (index >= 0) {
                mPointerQueue.releaseAllPointersOlderThan(tracker, eventTime);
            } else {
                Logger.w(TAG, "onUpEvent: corresponding down event not found for pointer %d", tracker.mPointerId);
                return;
            }
        }
        tracker.onUpEvent(x, y, eventTime);
        mPointerQueue.remove(tracker);
    }

    protected void onCancelEvent(PointerTracker tracker, int x, int y, long eventTime) {
        tracker.onCancelEvent();
        mPointerQueue.remove(tracker);
    }

    @Nullable
    protected Key findKeyByPrimaryKeyCode(int keyCode) {
        if (getKeyboard() == null) {
            return null;
        }

        for (Key key : getKeyboard().getKeys()) {
            if (key.getPrimaryCode() == keyCode) return key;
        }
        return null;
    }

    @CallSuper
    public boolean closing() {
        mKeyPreviewsManager.cancelAllPreviews();
        mKeyPressTimingHandler.cancelAllMessages();

        return true;
    }

    @Override
    public void onStartTemporaryDetach() {
        mKeyPreviewsManager.cancelAllPreviews();
        mKeyPressTimingHandler.cancelAllMessages();
        super.onStartTemporaryDetach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // releasing some memory
        for (int i = 0; i < mKeysIcons.size(); i++) {
            Drawable d = mKeysIcons.valueAt(i);
            CompatUtils.unbindDrawable(d);
        }
        mKeysIcons.clear();
    }

    public void onViewNotRequired() {
        closing();
        AnyApplication.getConfig().removeChangedListener(this);
        // cleaning up memory
        CompatUtils.unbindDrawable(getBackground());
        for (int i = 0; i < mKeysIcons.size(); i++) {
            Drawable d = mKeysIcons.valueAt(i);
            CompatUtils.unbindDrawable(d);
        }
        mKeysIcons.clear();
        mKeysIconBuilders.clear();
        CompatUtils.unbindDrawable(mKeyBackground);
        mKeyPreviewsManager.destroy();

        mKeyboardActionListener = null;
        mKeyboard = null;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Resources res = getResources();

        if (key.equals(res.getString(R.string.settings_key_swipe_distance_threshold))
                || key.equals(res.getString(R.string.settings_key_swipe_velocity_threshold))) {
            reloadSwipeThresholdsSettings(res);
        } else if (key.equals(res.getString(R.string.settings_key_long_press_timeout))
                || key.equals(res.getString(R.string.settings_key_multitap_timeout))) {
            closing();
            mPointerTrackers.clear();
        } else if (key.equals(res.getString(R.string.settings_key_key_press_preview_popup_position))
                || key.equals(res.getString(R.string.settings_key_key_press_shows_preview_popup))
                || key.equals(res.getString(R.string.settings_key_tweak_animations_level))) {
            mKeyPreviewsManager.destroy();
            mKeyPreviewsManager = new KeyPreviewsManager(getContext(), this, mPreviewPopupTheme);
        }
    }

    protected static class KeyPressTimingHandler extends Handler {

        private static final int MSG_REPEAT_KEY = 3;
        private static final int MSG_LONG_PRESS_KEY = 4;

        private final WeakReference<AnyKeyboardViewBase> mKeyboard;
        private boolean mInKeyRepeat;

        public KeyPressTimingHandler(AnyKeyboardViewBase keyboard) {
            mKeyboard = new WeakReference<>(keyboard);
        }

        @Override
        public void handleMessage(Message msg) {
            AnyKeyboardViewBase keyboard = mKeyboard.get();
            if (keyboard == null)
                return;
            final PointerTracker tracker = (PointerTracker) msg.obj;
            switch (msg.what) {
                case MSG_REPEAT_KEY:
                    tracker.repeatKey(msg.arg1);
                    startKeyRepeatTimer(keyboard.mKeyRepeatInterval, msg.arg1, tracker);
                    break;
                case MSG_LONG_PRESS_KEY:
                    Key keyForLongPress = tracker.getKey(msg.arg1);
                    if (keyForLongPress != null) {
                        keyboard.onLongPress(keyboard.getKeyboard().getKeyboardAddOn(), keyForLongPress, false, tracker);
                    }
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }

        public void startKeyRepeatTimer(long delay, int keyIndex, PointerTracker tracker) {
            mInKeyRepeat = true;
            sendMessageDelayed(
                    obtainMessage(MSG_REPEAT_KEY, keyIndex, 0, tracker), delay);
        }

        public void cancelKeyRepeatTimer() {
            mInKeyRepeat = false;
            removeMessages(MSG_REPEAT_KEY);
        }

        public boolean isInKeyRepeat() {
            return mInKeyRepeat;
        }

        public void startLongPressTimer(long delay, int keyIndex, PointerTracker tracker) {
            removeMessages(MSG_LONG_PRESS_KEY);
            sendMessageDelayed(obtainMessage(MSG_LONG_PRESS_KEY, keyIndex, 0, tracker), delay);
        }

        public void cancelLongPressTimer() {
            removeMessages(MSG_LONG_PRESS_KEY);
        }

        public void cancelAllMessages() {
            cancelKeyRepeatTimer();
            cancelLongPressTimer();
        }
    }

    static class PointerQueue {
        private LinkedList<PointerTracker> mQueue = new LinkedList<>();

        public void add(PointerTracker tracker) {
            mQueue.add(tracker);
        }

        public int lastIndexOf(PointerTracker tracker) {
            LinkedList<PointerTracker> queue = mQueue;
            for (int index = queue.size() - 1; index >= 0; index--) {
                PointerTracker t = queue.get(index);
                if (t == tracker)
                    return index;
            }
            return -1;
        }

        public void releaseAllPointersOlderThan(final PointerTracker tracker, final long eventTime) {
            //doing a copy to prevent ConcurrentModificationException
            PointerTracker[] trackers = mQueue.toArray(new PointerTracker[mQueue.size()]);
            for (PointerTracker t : trackers) {
                if (t == tracker) break;
                if (!t.isModifier()) {
                    t.onUpEvent(t.getLastX(), t.getLastY(), eventTime);
                    t.setAlreadyProcessed();
                    mQueue.remove(t);
                }
            }
        }

        public void releaseAllPointers(long eventTime) {
            releaseAllPointersExcept(null, eventTime);
        }

        public void cancelAllPointers() {
            for (PointerTracker t : mQueue) {
                t.onCancelEvent();
            }
            mQueue.clear();
        }

        public void releaseAllPointersExcept(@Nullable PointerTracker tracker, long eventTime) {
            for (PointerTracker t : mQueue) {
                if (t == tracker)
                    continue;
                t.onUpEvent(t.getLastX(), t.getLastY(), eventTime);
                t.setAlreadyProcessed();
            }
            mQueue.clear();
            if (tracker != null) mQueue.add(tracker);
        }

        public void remove(PointerTracker tracker) {
            mQueue.remove(tracker);
        }
    }

    private static class KeyboardDrawOperation implements MemRelatedOperation {

        private final AnyKeyboardViewBase mView;
        private final Paint mPaint;
        private Canvas mCanvas;

        public KeyboardDrawOperation(AnyKeyboardViewBase keyboard) {
            mView = keyboard;
            mPaint = keyboard.mPaint;
        }

        public void setCanvas(Canvas canvas) {
            mCanvas = canvas;
        }

        public void operation() {
            mView.onBufferDraw(mCanvas, mPaint);
        }
    }

    private static class TextWidthCacheValue {
        private final float mTextSize;
        private final float mTextWidth;

        private TextWidthCacheValue(float textSize, float textWidth) {
            mTextSize = textSize;
            mTextWidth = textWidth;
        }

        public float setCachedValues(Paint paint) {
            paint.setTextSize(mTextSize);
            return mTextWidth;
        }
    }

    private static class TextWidthCacheKey {
        private final CharSequence mLabel;
        private final int mKeyWidth;

        private TextWidthCacheKey(CharSequence label, int keyWidth) {
            mLabel = label;
            mKeyWidth = keyWidth;
        }

        @Override
        public int hashCode() {
            return mLabel.hashCode() + mKeyWidth;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof TextWidthCacheKey
                    && ((TextWidthCacheKey) o).mKeyWidth == mKeyWidth
                    && ((TextWidthCacheKey) o).mLabel.equals(mLabel));
        }
    }
}
