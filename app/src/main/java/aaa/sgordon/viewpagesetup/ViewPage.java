package aaa.sgordon.viewpagesetup;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.fragment.app.Fragment;

import aaa.sgordon.viewpagesetup.components.DragHelper;
import aaa.sgordon.viewpagesetup.components.ScaleHelper;
import aaa.sgordon.viewpagesetup.databinding.ViewPageBinding;

public class ViewPage extends Fragment {
	ViewPageBinding binding;

	DragHelper dragHelper;
	ScaleHelper scaleHelper;


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = ViewPageBinding.inflate(inflater, container, false);

		ViewStub mediaStub = binding.mediaStub;
		mediaStub.setLayoutResource(R.layout.image_layout);
		mediaStub.inflate();

		ViewStub bottomSliderStub = binding.bottomSliderStub;
		bottomSliderStub.setLayoutResource(R.layout.bottom_slider_layout);
		bottomSliderStub.inflate();

		binding.viewB.findViewById(R.id.test_button).setOnClickListener(v -> {
			Toast.makeText(requireContext(), "Test Button Clicked", Toast.LENGTH_SHORT).show();
		});

		dragHelper = new DragHelper(binding.motionLayout, binding.viewA, binding.viewB);
		scaleHelper = new ScaleHelper(binding.dimBackground, binding.viewA, () -> {
			//Dismiss the fragment
		});

		return binding.getRoot();
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		MotionLayout motionLayout = binding.motionLayout;

		motionLayout.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				motionLayout.getViewTreeObserver().removeOnPreDrawListener(this);

				ImageView media = view.findViewById(R.id.media);
				float mediaHeight = dragHelper.getMediaHeight(media);
				dragHelper.onMediaReady(mediaHeight);
				return true;
			}
		});

		motionLayout.setOnTouchListener((v, event) -> {
			v.performClick();

			if(!scaleHelper.isActive())
				dragHelper.onMotionEvent(event);
			if(!dragHelper.isActive())
				scaleHelper.onMotionEvent(event);

			return true;
		});
	}
}
