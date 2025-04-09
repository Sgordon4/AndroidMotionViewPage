package aaa.sgordon.viewpagesetup.oldrefs;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;

import aaa.sgordon.viewpagesetup.R;
import aaa.sgordon.viewpagesetup.databinding.FragmentDragBinding;

public class DragFragment extends Fragment {
	FragmentDragBinding binding;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		scroller = new OverScroller(requireContext());
		choreographer = Choreographer.getInstance();
		flingCallback = new Choreographer.FrameCallback() {
			@Override
			public void doFrame(long frameTimeNanos) {

				if (scroller.computeScrollOffset()) {
					dragState = DragState.OPEN;

					//Convert current position to progress
					int currY = scroller.getCurrY();
					float newProgress = currY / transitionDistance;

					binding.motionLayout.setProgress(newProgress);
					choreographer.postFrameCallback(this);
				}
			}
		};
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragmentDragBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}





	private float screenHeight;
	private float mediaHeight;
	private float transitionDistance;

	private float thresholdOpen;
	private float thresholdChange;
	private float progressAtOpen;

	private enum DragState { CLOSED, OPEN }
	private DragState dragState = DragState.CLOSED;

	VelocityTracker velocityTracker;
	private float startY;
	private float startProgress;


	private OverScroller scroller;
	private Choreographer choreographer;
	private Choreographer.FrameCallback flingCallback;

	private ValueAnimator snapAnimator;


	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		MotionLayout motionLayout = binding.motionLayout;
		View viewA = binding.viewA;
		View viewB = binding.viewB;

		ImageView media = binding.media;


		visualizeThresholds(view);


		motionLayout.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				motionLayout.getViewTreeObserver().removeOnPreDrawListener(this);

				screenHeight = motionLayout.getHeight();

				//Get the actual height of the media in our media view
				int width = media.getDrawable().getIntrinsicWidth();
				int height = media.getDrawable().getIntrinsicHeight();
				double scale = ((double)media.getWidth())/width;
				mediaHeight = Math.toIntExact(Math.round(scale * height));

				//If the media is too small (likely 0 due to error), pretend media is fullscreen
				if(mediaHeight < 4)
					mediaHeight = screenHeight;


				//Threshold Open is 1/3 from top of screen
				//Threshold Change is 3/4 down actual image
				thresholdOpen = screenHeight * (1/3f);
				thresholdChange = screenHeight/2 + mediaHeight/4;



				//We want to place the top of viewB at the bottom of the visible media
				//To do this, shift the translation in the motionConstraints
				float mediaBottomToScreenBottom = mediaHeight/2 - screenHeight/2;

				transitionDistance = viewB.getHeight() + mediaBottomToScreenBottom;

				ConstraintSet startConstraintSet = motionLayout.getConstraintSet(R.id.start);
				ConstraintSet endConstraintSet = motionLayout.getConstraintSet(R.id.end);

				float startTranslationY_B = startConstraintSet.getConstraint(R.id.view_b).transform.translationY;
				float endTranslationY_A = endConstraintSet.getConstraint(R.id.view_a).transform.translationY;

				startConstraintSet.setTranslationY(R.id.view_b, startTranslationY_B + mediaBottomToScreenBottom);
				endConstraintSet.setTranslationY(R.id.view_a, endTranslationY_A - mediaBottomToScreenBottom);
				//endConstraintSet.setTranslationY(R.id.viewB, endTranslationY_B + mediaBottomToScreenBottom - mediaBottomToScreenBottom);

				motionLayout.updateState(R.id.start, startConstraintSet);
				motionLayout.updateState(R.id.end, endConstraintSet);



				float mediaBottom = screenHeight/2 + mediaHeight/2;
				progressAtOpen = (mediaBottom - thresholdOpen) / transitionDistance;


				return true;
			}
		});



		motionLayout.setOnTouchListener((v, event) -> {
			v.performClick();

			float mediaBottom = screenHeight/2 + mediaHeight/2;
			float currMediaBottom = mediaBottom + viewA.getTranslationY();


			//Drag the views based on touch
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					startY = event.getY();
					startProgress = motionLayout.getProgress();

				case MotionEvent.ACTION_MOVE:
					float dy = startY - event.getY();
					float dPercent = (dy) / transitionDistance;

					float newProgress = startProgress + dPercent;
					newProgress = Math.max(0f, Math.min(1f, newProgress));
					motionLayout.setProgress(newProgress);

				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					//Do nothing
			}





			//Fling logic
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					//Stop any current fling
					choreographer.removeFrameCallback(flingCallback);
					scroller.abortAnimation();

					//Stop any current snap
					if (snapAnimator != null && snapAnimator.isRunning())
						snapAnimator.cancel();

					if (velocityTracker == null)
						velocityTracker = VelocityTracker.obtain();
					else
						velocityTracker.clear();
					velocityTracker.addMovement(event);

					break;

				case MotionEvent.ACTION_MOVE:
					velocityTracker.addMovement(event);
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					velocityTracker.addMovement(event);

					velocityTracker.computeCurrentVelocity(1000);
					float velocityY = velocityTracker.getYVelocity();

					boolean isFling = Math.abs(velocityY) > ViewConfiguration.get(requireContext()).getScaledMinimumFlingVelocity();
					if(!isFling) break;


					//Bottom above threshold Open, perform a normal fling
					if(currMediaBottom < thresholdOpen) {
						//Distance between thresholdOpen and the top of viewB and/or mediaBottom
						float thresholdOpenToViewBTop = mediaBottom - thresholdOpen;

						scroller.fling(
								0,
								(int) -viewA.getTranslationY(),
								0,
								(int) -velocityY,
								0,
								0,
								(int) thresholdOpenToViewBTop,
								(int) transitionDistance
						);

						choreographer.postFrameCallback(flingCallback);
						return true;
					}
					//Bottom below threshold Open
					else if (currMediaBottom > thresholdOpen) {
						boolean flingUp = velocityY < 0;

						if(flingUp) {
							//Snap to Open
							animateSnapTo(progressAtOpen);
							dragState = DragState.OPEN;
						}
						else {
							//Snap to Closed
							animateSnapTo(0);
							dragState = DragState.CLOSED;
						}

						return true;
					}
			}



			//Snap between open/closed based on drag ending position
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					//If a snap is already in progress, cancel it
					if (snapAnimator != null && snapAnimator.isRunning())
						snapAnimator.cancel();
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:

					//Bottom below threshold Change
					if (currMediaBottom >= thresholdChange) {
						//Snap to Closed
						animateSnapTo(0);
						dragState = DragState.CLOSED;
					}
					//Bottom above threshold Change, but below threshold Open
					else if (currMediaBottom < thresholdChange && currMediaBottom > thresholdOpen) {
						//Snap to Open
						animateSnapTo(progressAtOpen);
						dragState = DragState.OPEN;
					}
					//Bottom above threshold Open
					else {//if(mediaBottom <= thresholdOpen) {
						dragState = DragState.OPEN;
						//Do nothing
					}
			}

			return true;
		});
	}


	private void visualizeThresholds(View view) {
		View thresholdOpenLine = binding.thresholdOpen;
		View thresholdChangeLine = binding.thresholdChange;

		view.post(() -> {
			FrameLayout.LayoutParams paramsA = (FrameLayout.LayoutParams) thresholdOpenLine.getLayoutParams();
			paramsA.topMargin = (int) thresholdOpen;
			thresholdOpenLine.setLayoutParams(paramsA);
			thresholdOpenLine.setVisibility(View.VISIBLE);

			FrameLayout.LayoutParams paramsB = (FrameLayout.LayoutParams) thresholdChangeLine.getLayoutParams();
			paramsB.topMargin = (int) thresholdChange;
			thresholdChangeLine.setLayoutParams(paramsB);
			thresholdChangeLine.setVisibility(View.VISIBLE);
		});
	}

	private void animateSnapTo(float targetProgress) {
		if (snapAnimator != null && snapAnimator.isRunning())
			snapAnimator.cancel();

		float currentProgress = binding.motionLayout.getProgress();
		snapAnimator = ValueAnimator.ofFloat(currentProgress, targetProgress);
		snapAnimator.setDuration(150);
		snapAnimator.setInterpolator(new DecelerateInterpolator());
		snapAnimator.addUpdateListener(anim -> {
			float p = (float) anim.getAnimatedValue();
			binding.motionLayout.setProgress(p);
		});
		snapAnimator.start();
	}

	public void snapToOpen() {
		animateSnapTo(progressAtOpen);
		dragState = DragState.OPEN;
	}
}



