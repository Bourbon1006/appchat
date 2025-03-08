package com.example.appchat.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.appchat.ChatActivity
import com.example.appchat.MainActivity
import com.example.appchat.R
import com.example.appchat.adapter.ContactAdapter
import com.example.appchat.api.ApiClient
import com.example.appchat.model.UserDTO
import com.example.appchat.util.UserPreferences
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ContactsFragment : Fragment() {
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_contacts, container, false)
        
        viewPager = view.findViewById(R.id.viewPager)
        tabLayout = view.findViewById(R.id.tabLayout)
        
        setupViewPager()
        return view
    }

    private fun setupViewPager() {
        val adapter = ContactsPagerAdapter(this)
        viewPager.adapter = adapter
        
        // 连接 TabLayout 和 ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "好友"
                1 -> "群组"
                else -> ""
            }
        }.attach()
    }
}

class ContactsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> FriendsListFragment()
            1 -> GroupListFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}