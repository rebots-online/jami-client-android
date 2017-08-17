/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package cx.ring.tv.main;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.View;
import android.view.ViewGroup;

import cx.ring.R;
import cx.ring.model.CallContact;

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class CardPresenter extends Presenter {
    private static final String TAG = CardPresenter.class.getSimpleName();

    private static int CARD_WIDTH = 313;
    private static int CARD_HEIGHT = 176;

    static class ViewHolder extends Presenter.ViewHolder {
        private ImageCardView mCardView;
        private Drawable mDefaultCardImage;

        public ViewHolder(View view) {
            super(view);
            mCardView = (ImageCardView) view;
            mDefaultCardImage = getCardView().getResources().getDrawable(R.drawable.ic_contact_picture);
        }

        public ImageCardView getCardView() {
            return mCardView;
        }

        public Drawable getDefaultCardImage() {
            return mDefaultCardImage;
        }

    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();
        ImageCardView cardView = new ImageCardView(context);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setBackgroundColor(context.getResources().getColor(R.color.color_primary_dark));
        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        if (item instanceof CallContact) {
            onBindViewHolderCallContact((ViewHolder) viewHolder, (CallContact)item);
        }
    }

    private void onBindViewHolderCallContact(ViewHolder viewHolder, CallContact contact) {
        viewHolder.mCardView.setTitleText(contact.getDisplayName());
        viewHolder.mCardView.setContentText(contact.getIds().get(0));
        viewHolder.mCardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);
        if (contact.getPhoto() == null) {
            viewHolder.mCardView.setMainImage(viewHolder.getDefaultCardImage());
        }
        else {
            viewHolder.mCardView.setMainImage(
                    new BitmapDrawable(viewHolder.mCardView.getResources(), BitmapFactory.decodeByteArray(contact.getPhoto(), 0, contact.getPhoto().length)));
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        // Nothing to do
    }

    @Override
    public void onViewAttachedToWindow(Presenter.ViewHolder viewHolder) {
        // Nothing to do
    }
}