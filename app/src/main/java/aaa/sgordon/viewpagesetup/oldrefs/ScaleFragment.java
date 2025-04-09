package aaa.sgordon.viewpagesetup.oldrefs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.fragment.app.Fragment;

import aaa.sgordon.viewpagesetup.databinding.FragmentScaleBinding;

public class ScaleFragment extends Fragment {
	FragmentScaleBinding binding;
	MotionLayout motionLayout;
	View dimBackground;
	View mediaView;

	float initialX, initialY;
	float downX, downY;
	boolean isDragging = false;
	VelocityTracker velocityTracker;
	float scaleDistanceThreshold = 150f; // Max distance before scale stops decreasing
	float snapBackRadius = 100f; // Distance to snap back

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = FragmentScaleBinding.inflate(inflater, container, false);

		motionLayout = binding.motion;
		dimBackground = binding.dimBackground;
		mediaView = binding.media;

		// Run after layout to get center point
		mediaView.post(() -> {
			initialX = mediaView.getX();
			initialY = mediaView.getY();
		});

		mediaView.setOnTouchListener((v, event) -> {
			v.performClick();

			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					downX = event.getRawX();
					downY = event.getRawY();
					velocityTracker = VelocityTracker.obtain();
					velocityTracker.addMovement(event);
					isDragging = false;
					return true;

				case MotionEvent.ACTION_MOVE:
					velocityTracker.addMovement(event);

					float moveX = event.getRawX();
					float moveY = event.getRawY();

					float deltaX = moveX - downX;
					float deltaY = moveY - downY;

					if (!isDragging) {
						// Check for downward intent
						if (deltaY > 20 && Math.abs(deltaY) > Math.abs(deltaX)) {
							isDragging = true;
						} else {
							return false; // Don't interfere with other gestures
						}
					}


					float newTranslationX = mediaView.getTranslationX() + (moveX - downX);
					float newTranslationY = mediaView.getTranslationY() + (moveY - downY);

					mediaView.setTranslationX(newTranslationX);
					mediaView.setTranslationY(newTranslationY);

					// Distance from center
					float dx = newTranslationX;
					float dy = newTranslationY;
					float distance = (float) Math.hypot(dx, dy);

					// Calculate scale
					float scale = 1f - Math.min(0.5f, distance / scaleDistanceThreshold * 0.5f); // Min scale 0.5
					mediaView.setScaleX(scale);
					mediaView.setScaleY(scale);


					//Set the background scrim alpha
					//float alpha = 1f - Math.min(0.7f, distance / scaleDistanceThreshold * 0.7f); //Min alpha 0.3
					float alpha = 1f - distance / scaleDistanceThreshold;
					dimBackground.setAlpha(alpha);


					downX = moveX;
					downY = moveY;
					return true;

				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					velocityTracker.addMovement(event);
					velocityTracker.computeCurrentVelocity(1000);

					float vx = velocityTracker.getXVelocity();
					float vy = velocityTracker.getYVelocity();
					velocityTracker.recycle();
					velocityTracker = null;

					float finalDx = mediaView.getTranslationX();
					float finalDy = mediaView.getTranslationY();
					float totalDistance = (float) Math.hypot(finalDx, finalDy);

					if (totalDistance < snapBackRadius) {
						// Snap back
						mediaView.animate()
								.translationX(0)
								.translationY(0)
								.scaleX(1f)
								.scaleY(1f)
								.setDuration(300)
								.start();

						dimBackground.animate().alpha(1f).setDuration(300).start();
					} else {
						// Optional: fling off or stay
						// For now: keep position and scale
					}

					isDragging = false;
					return true;
			}
			return false;
		});


		return binding.getRoot();
	}
}
