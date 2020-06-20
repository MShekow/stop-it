package de.augmentedmind.stopit.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.augmentedmind.stopit.*
import de.augmentedmind.stopit.service.MediaCallbackService

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: BookmarkViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val myToolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(myToolbar)
        viewModel = ViewModelProvider(this).get(BookmarkViewModel::class.java)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        val adapter = BookmarkListAdapter(this)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(DividerItemDecoration(recyclerView.context,
                DividerItemDecoration.VERTICAL))
        viewModel.allBookmarks.observe(this, Observer { bookmarks ->
            bookmarks?.let { adapter.setBookmarks(bookmarks) }
        })
        val intent = Intent(this, MediaCallbackService::class.java)
                .setAction(MediaCallbackService.START_ACTION)
        startService(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.action_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> {
            }
        }
        return true
    }

    companion object {
        const val TAG = "MainActivity"
    }
}