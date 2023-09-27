package cx.ring.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import cx.ring.R
import cx.ring.databinding.WelcomeJamiLayoutBinding
import cx.ring.utils.BackgroundType
import cx.ring.viewmodel.JamiIdViewModel
import cx.ring.viewmodel.WelcomeJamiViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.jami.utils.Log


@AndroidEntryPoint
class WelcomeJamiFragment : Fragment() {

    private lateinit var binding: WelcomeJamiLayoutBinding
    private val welcomeJamiViewModel: WelcomeJamiViewModel by viewModels({ requireActivity() })
    private val jamiIdViewModel by lazy { ViewModelProvider(this)[JamiIdViewModel::class.java] }

    companion object {
        private val TAG = WelcomeJamiViewModel::class.simpleName!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        WelcomeJamiLayoutBinding.inflate(inflater, container, false).apply {

            if (!welcomeJamiViewModel.uiState.value.isJamiAccount) {
                Log.d(TAG, "Not a Jami account")
                welcomeJamiDescription.visibility = View.GONE
                return@apply
            }

            welcomeJamiViewModel.initJamiIdViewModel(jamiIdViewModel)
            // Create the JamiIdFragment
            childFragmentManager.beginTransaction()
                .replace(R.id.jamiIdFragmentContainerView, JamiIdFragment())
                .commit()

            binding = this
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe the uiState and update the UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                welcomeJamiViewModel.uiState.collect { uiState ->
                    uiState.uiCustomization?.apply {
                        title?.let { binding.welcomeJamiTitle.text = it }
                        description?.let { binding.welcomeJamiDescription.text = it }
                        when (backgroundType) {
                            BackgroundType.COLOR -> {
                                backgroundColor?.let {
                                    binding.welcomeJamiBackground.setImageDrawable(null)
                                    binding.welcomeJamiBackground.setBackgroundColor(it)
                                }
                            }

                            BackgroundType.IMAGE -> {
                                backgroundUrl?.let {
                                    Glide.with(binding.welcomeJamiBackground.context)
                                        .load(it)
                                        //.transition(DrawableTransitionOptions.withCrossFade())
                                        .into(binding.welcomeJamiBackground)
                                }
                                binding.welcomeJamiBackground.setBackgroundColor(0)
                            }

                            else -> {} /* Nothing to do */

                        }
                        logoUrl?.let {
                            Glide.with(binding.welcomeJamiLogo.context)
                                .load(it)
                                //.transition(DrawableTransitionOptions.withCrossFade())
                                .into(binding.welcomeJamiLogo)
                        }
                        logoSize?.let { size ->
                            // Value is a ratio, so multiply actual logo size by it
                            val defaultSize = requireContext().resources
                                .getDimensionPixelSize(R.dimen.welcome_jami_logo_default_size)
                            val newSize = size * defaultSize / 100
                            binding.welcomeJamiLogo.layoutParams?.height = newSize
                        }
                        mainBoxColor?.let {
                            binding.welcomeJamiMainBox.backgroundTintList =
                                ColorStateList.valueOf(it)
                        }
                        areTipsEnabled.let { /* TODO Not yet implemented */ }
                        tipBoxAndIdColor?.let { /* TODO Not yet implemented */ }
                    }
                }
            }
        }
    }
}
