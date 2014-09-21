/*
 * Copyright 2014 yuki312 All Right Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Original source code Copyright 2014 Google Inc. All rights reserved.
 *  https://github.com/google/iosched
 */
package yuki312.android.imageryheader;

import android.app.Activity;
import android.app.Fragment;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

public class ImageryHeaderFragment extends Fragment
        implements ObservableScrollView.Callbacks {

    // GapFillアニメ開始位置の調整. 開始位置に"遊び"を持たせる.
    private static final float GAP_FILL_DISTANCE_MULTIPLIER = 1.5f;

    // ヘッダ画像スクロール時のパララックスエフェクト係数
    private static final float HEADER_IMAGE_BACKGROUND_PARALLAX_EFFECT_MULTIPLIER = 0.5f;

    private static final int[] RES_IDS_ACTION_BAR_SIZE = {android.R.attr.actionBarSize};

    private static final float HEADER_IMAGE_ASPECT_RATIO = 1.7777777f;

    private ViewGroup rootView;
    private ObservableScrollView scrollView;

    private View headerImageContainer;
    private ImageView headerImage;

    private View bodyContainer;

    private View headerBarContainer;
    private View headerBarContents;
    private View headerBarBackground;
    private View headerBarShadow;

    private int headerBarTopClearance;
    private int headerImageHeightPixels;
    private int headerBarContentsHeightPixels;

    private boolean showHeaderImage;
    private boolean gapFillShown;

    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener
            = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            recomputeHeaderImageAndScrollingMetrics();
        }
    };

    public ImageryHeaderFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = (ViewGroup) inflater.inflate(R.layout.fragment_imageryheader, container, false);
        scrollView = (ObservableScrollView) rootView.findViewById(R.id.scroll_view);

        headerImageContainer = rootView.findViewById(R.id.header_image_container);
        headerImage = (ImageView) rootView.findViewById(R.id.header_image);
        bodyContainer = rootView.findViewById(R.id.body_container);
        headerBarContainer = rootView.findViewById(R.id.header_bar_container);
        headerBarContents = rootView.findViewById(R.id.header_bar_contents);
        headerBarBackground = rootView.findViewById(R.id.header_bar_background);
        headerBarShadow = rootView.findViewById(R.id.header_bar_shadow);

        setupCustomScrolling(rootView);

        return rootView;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scrollView == null) {
            return;
        }

        ViewTreeObserver vto = scrollView.getViewTreeObserver();
        if (vto.isAlive()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                vto.removeGlobalOnLayoutListener(globalLayoutListener); /* deprecated */
            } else {
                vto.removeOnGlobalLayoutListener(globalLayoutListener);
            }
        }
    }

    @Override
    public void onScrollChanged(int deltaX, int deltaY) {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        // Reposition the header bar -- it's normally anchored to the top of the content,
        // but locks to the top of the screen on scroll
        int scrollY = scrollView.getScrollY();

        float newTop = Math.max(headerImageHeightPixels, scrollY + headerBarTopClearance);
        headerBarContainer.setTranslationY(newTop);
        headerBarBackground.setPivotY(headerBarContentsHeightPixels);

        int gapFillDistance = (int) (headerBarTopClearance * GAP_FILL_DISTANCE_MULTIPLIER);
        boolean showGapFill = !showHeaderImage || (scrollY > (headerImageHeightPixels - gapFillDistance));
        float desiredHeaderScaleY = showGapFill ?
                ((headerBarContentsHeightPixels + gapFillDistance + 1) * 1f / headerBarContentsHeightPixels)
                : 1f;
        if (!showHeaderImage) {
            headerBarBackground.setScaleY(desiredHeaderScaleY);
        } else if (gapFillShown != showGapFill) {
            headerBarBackground.animate()
                    .scaleY(desiredHeaderScaleY)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .setDuration(250)
                    .start();
        }
        gapFillShown = showGapFill;

        // Make a shadow. TODO: Do not need if running on AndroidL
        headerBarShadow.setVisibility(View.VISIBLE);

        if (headerBarTopClearance != 0) {
            // Fill the gap between status bar and header bar with color
            float gapFillProgress = Math.min(Math.max(getProgress(scrollY,
                    headerImageHeightPixels - headerBarTopClearance * 2,
                    headerImageHeightPixels - headerBarTopClearance), 0), 1);
            // TODO: Set elevation properties if running on AndroidL
            headerBarShadow.setAlpha(gapFillProgress);
        }

        // Move background image (parallax effect)
        headerImageContainer.setTranslationY(scrollY * HEADER_IMAGE_BACKGROUND_PARALLAX_EFFECT_MULTIPLIER);
    }

    private void recomputeHeaderImageAndScrollingMetrics() {
        final int actionBarSize = calculateActionBarSize();
        headerBarTopClearance = actionBarSize - headerBarContents.getPaddingTop();
        headerBarContentsHeightPixels = headerBarContents.getHeight();

        headerImageHeightPixels = headerBarTopClearance;
        if (showHeaderImage) {
            headerImageHeightPixels = (int) (headerImage.getWidth() / HEADER_IMAGE_ASPECT_RATIO);
            headerImageHeightPixels = Math.min(headerImageHeightPixels, rootView.getHeight() * 2 / 3);
        }

        ViewGroup.LayoutParams lp;
        lp = headerImageContainer.getLayoutParams();
        if (lp.height != headerImageHeightPixels) {
            lp.height = headerImageHeightPixels;
            headerImageContainer.setLayoutParams(lp);
        }

        lp = headerBarBackground.getLayoutParams();
        if (lp.height != headerBarContentsHeightPixels) {
            lp.height = headerBarContentsHeightPixels;
            headerBarBackground.setLayoutParams(lp);
        }

        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams)
                bodyContainer.getLayoutParams();
        if (mlp.topMargin != headerBarContentsHeightPixels + headerImageHeightPixels) {
            mlp.topMargin = headerBarContentsHeightPixels + headerImageHeightPixels;
            bodyContainer.setLayoutParams(mlp);
        }

        onScrollChanged(0, 0); // trigger scroll handling
    }

    private void setupCustomScrolling(View rootView) {
        scrollView = (ObservableScrollView) rootView.findViewById(R.id.scroll_view);
        scrollView.addCallbacks(this);
        ViewTreeObserver vto = scrollView.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnGlobalLayoutListener(globalLayoutListener);
        }
    }

    @Override
    public void onResume() {
        // Temporary code. TODO: Implements the image drawing code.
        super.onResume();
        loadHeaderImage();
    }

    private void loadHeaderImage() {
        // Temporary code. TODO: Implements the image drawing code.
        showHeaderImage = true;
        headerImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_launcher));
//        boolean show = isShowHeaderImage();
//        if (show) {
//            showHeaderImage = true;
//            ImageLoader.loadImage("http://xxxxx/xx.png", headerImage, new RequestListener<String>() {
//                @Override
//                public void onException(Exception e, String url, Target target) {
//                    showHeaderImage = false;
//                    recomputeHeaderImageAndScrollingMetrics();
//                }
//                @Override
//                public void onImageReady(String url, Target target, boolean b, boolean b2) {
//                    // Trigger image transition
//                    recomputeHeaderImageAndScrollingMetrics();
//                }
//            });
//            recomputeHeaderImageAndScrollingMetrics();
//        } else {
//            showHeaderImage = false;
//            recomputeHeaderImageAndScrollingMetrics();
//        }
    }

    private int calculateActionBarSize() {
        Resources.Theme theme = getActivity().getTheme();
        if (theme == null) {
            return 0;
        }

        TypedArray att = theme.obtainStyledAttributes(RES_IDS_ACTION_BAR_SIZE);
        if (att == null) {
            return 0;
        }

        float size = att.getDimension(0, 0);
        att.recycle();
        return (int) size;
    }

    private float getProgress(int value, int min, int max) {
        if (min == max) {
            throw new IllegalArgumentException("Max (" + max + ") cannot equal min (" + min + ")");
        }

        return (value - min) / (float) (max - min);
    }
}
