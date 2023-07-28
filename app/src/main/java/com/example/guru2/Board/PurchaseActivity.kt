package com.example.guru2.Board

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
//import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.guru2.R

class PurchaseActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.boardpage_purchase)

        val boardpage_iv1 = findViewById<ImageView>(R.id.boardpage_iv1)
        //val framePurchaseBtn = findViewById<Button>(R.id.frame_purchase)

        boardpage_iv1.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
        }

        //frameDeliveryButton.setOnClickListener {
        //showAddPostDialog()
        //}
    }

    private fun showAddPostDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.addpost_dialog)
        dialog.show()
    }
}