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

package com.anysoftkeyboard.devicespecific;

import android.annotation.TargetApi;
import android.content.Context;

import com.anysoftkeyboard.IndirectlyInstantiated;

@TargetApi(19)
@IndirectlyInstantiated
public class AskV19GestureDetector extends AskV8GestureDetector {
    public AskV19GestureDetector(Context context, AskOnGestureListener listener) {
        super(context, listener);
        //this behavior is not good for ASK. See https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/332
        mScaleGestureDetector.setQuickScaleEnabled(false);
    }
}
