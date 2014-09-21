Intro
------------------------------------------------------------------------------
Android Developers Blogで紹介されているioschedアプリの機能を一部実装する. 
Blog: http://android-developers.blogspot.jp/2014/08/material-design-in-2014-google-io-app.html

実現したい機能のイメージは次の通り. スクロールに追従し, 一定のY座標に到達すると画面上部との隙間を埋める(GapFill)ヘッダバーを実装する. 
![Imagery Header]( http://4.bp.blogspot.com/-9jn0pCtadO8/U9_E1mlg8CI/AAAAAAAAAt4/yr9CCSThkpg/s640/change_1.gif)

First Releaseのデザインではスクロール量によってバナーエリアのα値が変化する. Android Developers Blogの著者はこれについて次のコメントを残している. 

> Our concern was that this design bent the physics of material design too far. It’s as if the text was sliding along a piece of paper whose transparency changed throughout the animation.

Material Designの重要なファクタである paper と ink の観点から, バナーエリアに描画されたテキストを残す形で透過アニメーションされる点が, Material Designの物理学から外れていると感じたようだ. 
これを改善したのがUpdate version. Material Designのpaper と inkの物理学をより推進した形だ. 画像ではわかり辛いが, ヘッダバーエリアの下にコンテンツ詳細が潜り込むため, Z軸の存在を考慮してshadow効果も適用されている. 

レイアウト構造は次のようになっている. 
![Layout structore](http://2.bp.blogspot.com/-eR1rw3X0bn4/U9_EvzGY13I/AAAAAAAAAtw/3CqoLPgbtHY/s640/surface2b.gif)

実装を始める前に, 今回参考とさせて頂いたサイト, およびリポジトリを紹介する. 

 - [Android Developers Blog](http://android-developers.blogspot.jp/2014/08/material-design-in-2014-google-io-app.html)
 - [GitHub / Iosched](https://github.com/google/iosched)

ImageryHeaderFragment
------------------------------------------------------------------------------
Material Designではページのヘッダや背景等にイメージを配置し, コンテキストを表現することを推奨している. 今回作成するImageryHeaderFragmentはこれに倣って画像を配置できる機能を継承する(画像を必ず表示する必要はない). FABについては本コンポーネントの責務から外れるため実装されない. また, "L Preview"のAPIは本Fragmentでは使用しない.

### Step by step
実装の手順を紹介する. 

 1. Theme, Colorリソースを準備
 2. ImageryHeaderFragmentに必要なView拡張クラスを実装
 3. Layoutリソースを準備
 4. ImageryHeaderFragmentを実装

### Step 1: Theme
ImageryHeaderFragment, ActionBarとActivityのためのThemeリソースを定義する. 

 - /res/values/colors.xml
```xml
<resources>
    <color name="window_background">#eeeeee</color>

    <!-- API v21移行はandroid:colorPrimaryの値に使用 -->
    <color name="colorPrimary">#a00</color>
</resources>
```

 - /res/values/styles.xml
```xml
<!-- ImageryHeader Theme -->
<style name="Theme.ImageryHeader" parent="android:Theme.Holo.Light.DarkActionBar">
    <item name="android:actionBarStyle">@style/Theme.ImageryHeader.ActionBar</item>
    <item name="android:windowBackground">@color/window_background</item>
    <item name="android:windowContentOverlay">@null</item>
    <item name="android:windowActionBarOverlay">true</item>
</style>

<!-- ImageryHeader Theme Action Bar -->
<style name="Theme.ImageryHeader.ActionBar" parent="android:Widget.Holo.ActionBar">
    <item name="android:displayOptions">showHome</item>
    <item name="android:background">@null</item>
</style>
```

- /AndroidManifest.xml
```xml
<activity
        android:name=".MainActivity"
        android:theme="@style/Theme.ImageryHeader">
```

ActionBarはApp iconとAction itemのために使用するため消すことはしない. かわりに背景を透過し, 不要な要素(shadowやApp title)を非表示にしておく. 

### Step 2: Custom View

#### ObservableScrollView
ImageryHeaderFragmentでは, 各Viewのポジションをスクロール量によって動的に変化させる. 実現するにはScrollViewのスクロールイベントのリスナーが必要であるが, Android純正のScrollViewにはコールバックリスナを受け付ける仕組みがない. これを実現するためにScrollViewを拡張したObservableScrollViewを実装する. 

```java
public class ObservableScrollView extends ScrollView {
    private ArrayList<Callbacks> mCallbacks = new ArrayList<Callbacks>();

    public ObservableScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        for (Callbacks c : mCallbacks) {
            c.onScrollChanged(l - oldl, t - oldt);
        }
    }

    @Override
    public int computeVerticalScrollRange() {
        return super.computeVerticalScrollRange();
    }

    public void addCallbacks(Callbacks listener) {
        if (!mCallbacks.contains(listener)) {
            mCallbacks.add(listener);
        }
    }

    public static interface Callbacks {
        public void onScrollChanged(int deltaX, int deltaY);
    }
}
```

> **Original source code:**
> [GitHub - ObservableScrollView](https://github.com/google/iosched/blob/master/android/src/main/java/com/google/samples/apps/iosched/ui/widget/ObservableScrollView.java)

### Step 3: Layout
リソースとカスタムViewの準備ができたらlayoutリソースを作成する. 画面構成は大きく3つの要素から成る. 

 - Header Image
 - Body
 - Header Bar

3つの要素はレイアウト上, (FragmentLayoutによって)重なるように定義される. 実際には画面のスクロールポジションに従って各ViewのY座標を動的に変えていく. 
Header Barの定義位置を最後にしてあるのはz-indexを手前にするためである. 

 - /res/layout/fragment_imageryheader.xml
```xml
<yuki312.android.imageryheaderfragment.ObservableScrollView
        android:id="@+id/scroll_view"
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true"
        android:overScrollMode="never">

    <FrameLayout
            android:id="@+id/scroll_view_child"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:clipChildren="false">

        <!-- Header Imagery -->
        <FrameLayout
                android:id="@+id/header_image_container"
                android:layout_width="match_parent"
                android:layout_height="wrap_content">

            <ImageView
                    android:id="@+id/header_image"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:scaleType="centerCrop"/>
        </FrameLayout>

        <!-- Body -->
        <LinearLayout
                android:id="@+id/body_container"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@color/window_background"
                android:clipToPadding="false"
                android:orientation="vertical">

            <!-- TODO: Dummy View for scroll. remove this view.-->
            <TextView
                    android:layout_width="match_parent"
                    android:layout_height="777dp"
                    android:text="I`m Dummy"
                    />
        </LinearLayout>

        <!-- Header bar -->
        <FrameLayout
                android:id="@+id/header_bar_container"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:clipChildren="false"
                android:clipToPadding="false">

            <!-- background -->
            <!-- height assigned dynamically, and fill the ActionBar gaps. -->
            <View
                    android:id="@+id/header_bar_background"
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:background="@color/colorPrimary"/>

            <!-- contents -->
            <LinearLayout
                    android:id="@+id/header_bar_contents"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:paddingBottom="16dp"
                    android:paddingTop="16dp">

                <TextView
                        android:id="@+id/header_bar_title"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="Placeholder"/>

            </LinearLayout>

            <!-- shadow -->
            <View
                    android:id="@+id/header_bar_shadow"
                    android:layout_width="match_parent"
                    android:layout_height="6dp"
                    android:layout_gravity="bottom"
                    android:layout_marginBottom="-6dp"
                    android:background="@drawable/bottom_shadow"/>

        </FrameLayout>
    </FrameLayout>
</yuki312.android.imageryheaderfragment.ObservableScrollView>
```

#### @id/header_image_container
ヘッダイメージ画像のコンテナ. ioschedアプリではここにscrim効果を付与するアトリビュートも指定している. scrim効果を実装したい場合は次を追加し, scrim画像を用意する. 

```xml
<FrameLayout
        android:id="@+id/header_image_container"
        ...
        android:foreground="@drawable/photo_banner_scrim">
```

- /res/drawable/photo_banner_scrim
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
            android:angle="270"
            android:centerColor="#0000"
            android:centerY="0.3"
            android:endColor="#0000"
            android:startColor="#3000"/>
</shape>
```

> **Original source code:**
> [GitHub - photo_banner_scrim.xml](https://github.com/google/iosched/blob/master/android/src/main/res/drawable/photo_banner_scrim.xml)


#### @id/header_bar_background
今回のメインViewとなるHeaderBar. 一定量スクロールされるとGapFillアニメーションする. このViewはActionBarが担っていたブランディングカラーのために`colorPrimary`を指定する. 

#### @id/header_bar_shadow
一定量スクロールされるとHeaderBarに影を落とす効果を付与する. これはMaterial Designの物理学に従いBodyよりもHeaderBarのpaper要素が手前(Z depth)にあることを表現している. 

#### android:clipChildren
このアトリビュートは, 子Viewの描画領域が自領域外であっても実行するものである. これはヘッダ
バーの画面上部へのGapFillアニメーションやshadow効果の描画に必要になる. このアトリビュートを使えば親Viewのサイズに影響することなくshadow等の効果が実現できる. 
これはGoogleI/O 2014でも紹介(15:00~)されている(http://youtu.be/lSH9aKXjgt8). ただしhorrible hackの類いである点に留意する.  
Android Lから導入されるlight sourceとshadowのサポートはこれらの問題を解決(構築されたView階層に対してZ軸を考慮した物理的にも正しいshadow効果を描画)する.

### Fragment
準備は整った. 各Viewは`Framelayout`の効果で全て0座標に位置している. 今の状態でアプリ実行すると次のレイアウトになる. 

![Layout define only](https://lh4.googleusercontent.com/iEgJQNFQdWzEdCEz9SVjxqTy9Sgnt9Nnc5zLnfQEYIo=s500 "ImageryHeaderFragment_only_layout_small.png")

ここからはImageryHeaderFragmentのソースコードで各Viewの配置を調整していく. 

```java
public class ImageryHeaderFragment extends Fragment
        implements ObservableScrollView.Callbacks {
	...
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		... findViewByID ...
	    setupCustomScrolling(rootView);
	    return rootView;
	}
	
	private void setupCustomScrolling(View rootView) {
	    scrollView = (ObservableScrollView) rootView.findViewById(R.id.scroll_view);
	    scrollView.addCallbacks(this);
	    ...
	}
```
まずはスクロールイベントを検知するために, ObservableScrollView.Callbacksをimplementsする. Fragment.onCreateView()でObservableScrollViewへコールバック登録. 

```java
    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener
            = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            recomputeHeaderImageAndScrollingMetrics();
        }
    };

    private void setupCustomScrolling(View rootView) {
        ...
        ViewTreeObserver vto = scrollView.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnGlobalLayoutListener(globalLayoutListener);
        }
    }

    private void recomputeHeaderImageAndScrollingMetrics() {
        final int actionBarSize = calculateActionBarSize();
        headerBarTopClearance = actionBarSize - headerBarContents.getPaddingTop();
        headerBarContentsHeightPixels = headerBarContents.getHeight();

        headerImageHeightPixels = headerBarTopClearance;
        if (showHeaderImage) {
            headerImageHeightPixels =
		            (int) (headerImage.getWidth() / HEADER_IMAGE_ASPECT_RATIO);
            headerImageHeightPixels = Math.min(headerImageHeightPixels,
		            rootView.getHeight() * 2 / 3);
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
        if (mlp.topMargin
		         != headerBarContentsHeightPixels + headerImageHeightPixels) {
            mlp.topMargin = headerBarContentsHeightPixels + headerImageHeightPixels;
            bodyContainer.setLayoutParams(mlp);
        }

        onScrollChanged(0, 0); // trigger scroll handling
    }
```

ObservableScrollViewからViewTreeObserverを取得してGlobalLayoutListenerを登録. ObservableScrollViewのレイアウト構築完了を待つ. レイアウト構築が完了したらheaderImageContainer, bodyContainer, headerBarBackgroundのレイアウトを調整する. 

headerBarTopClearance
: recomputeHeaderImageAndScrollingMetrics()では, headerBarTopClearance値を設定する. この値はHeaderBarと画面上部まで(あるいはActionBar部の)隙間間隔を表す. 
コード上ではActionBarの高さから`headerBarContents.getPaddingTop()`つまりHeaderBar領域の上部余白を除いた値を設定することで, HeaderBarのコンテンツ("Placeholder")部とActionBarの隙間を小さくしている. 

次にImageryHeaderFragmentのコアとなる処理を見る. 

```java
    // GapFillアニメ開始位置の調整. 開始位置に"遊び"を持たせる.
    private static final float GAP_FILL_DISTANCE_MULTIPLIER = 1.5f;

    // ヘッダ画像スクロール時のパララックスエフェクト係数
    private static final float HEADER_IMAGE_BACKGROUND_PARALLAX_EFFECT_MULTIPLIER
		    = 0.5f;

    @Override
    public void onScrollChanged(int deltaX, int deltaY) {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        // Reposition the header bar -- it's normally anchored to the
        // top of the content, but locks to the top of the screen on scroll
        int scrollY = scrollView.getScrollY();

        float newTop = Math.max(headerImageHeightPixels,
		        scrollY + headerBarTopClearance);
        headerBarContainer.setTranslationY(newTop);
        headerBarBackground.setPivotY(headerBarContentsHeightPixels);

        int gapFillDistance = 
		        (int) (headerBarTopClearance * GAP_FILL_DISTANCE_MULTIPLIER);
        boolean showGapFill = !showHeaderImage || 
		        (scrollY > (headerImageHeightPixels - gapFillDistance));
        float desiredHeaderScaleY = showGapFill ?
                ((headerBarContentsHeightPixels + gapFillDistance + 1) * 1f 
		                / headerBarContentsHeightPixels)
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
        headerImageContainer.setTranslationY(scrollY *
		        HEADER_IMAGE_BACKGROUND_PARALLAX_EFFECT_MULTIPLIER);
    }
```

#### onScrollChanged
newTop
: HeaderBarの位置を調整する. headerImageHeightPixelsとHeaderBarの隙間(headerBarTopClearance)を考慮したスクロール量(scrollY)とのmaxをとるので, HeaderImageよりもHeaderBarが下に位置することはない. 

GAP_FILL_DISTANCE_MULTIPLIER
: HeaderBarと画面上部の隙間を埋めるGapFillアニメーションの開始位置に係る定数. ただし, GapFillアニメーション後もHeaderBarはheaderBarTopClearance(隙間間隔)の値に到達するまで移動する点に注意. 
画面スクロールのY座標が, headerBarTopClearanceと本係数との乗を超えたときにGapFillアニメーションを開始する. GapFillアニメーションの開始を早めたい場合は本係数を変更する. 

gapFillDistance
: 前述のGAP_FILL_DISTANCE_MULTIPLIERに関係する.

ここではAndroidLが正式リリースされた際の対応をTODOコメントとして残しておく(実際にはAndroidL前後で動作を変えるラッパを用意しておくのが望ましいが, 本件の主旨ではないので割愛. 

### Run & improvement
これで, MaterialDesignの物理学に従った対応ができた. ここまでの実装でアプリを動作させた場合のレイアウトが下記: 

![enter image description here](https://lh5.googleusercontent.com/_9RbV6y7-Okq80Csxw5KbskSPJldoEiJDWWhKcpGkg4=s600 "ImageryHeaderFragment_comp_scrolled_combine.png")

実際に動作させてアニメーションやshadowの具合を確認してみてほしい. 
Material Designは他にもTypography(Roboto, Font size/style), Bold(color, FAB), Layout baselineといった項目があり, これだけではMaterial Designに則ったデザインであるとは到底言えないが, 本稿の目的は達成したのでこれ以上は実装しない. 必要であれば残るMaterial Designの要素を実装していく必要がある. 

以上.


Copyright
------------------------------------------------------------------------------
> Copyright 2014 yuki312 All Right Reserved.
> 
> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
> 
>    http://www.apache.org/licenses/LICENSE-2.0
> 
> Unless required by applicable law or agreed to in writing, software
> distributed under the License is distributed on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
> See the License for the specific language governing permissions and
> limitations under the License.

---

> **License**
Portions of this page are modifications based on work created and shared by the Android Open Source Project and used according to terms described in the Creative Commons 2.5 Attribution License.
