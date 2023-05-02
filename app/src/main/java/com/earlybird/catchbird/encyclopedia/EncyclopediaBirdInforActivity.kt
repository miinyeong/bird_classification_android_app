package com.earlybird.catchbird.encyclopedia

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.earlybird.catchbird.*
import com.earlybird.catchbird.community.LoginActivity
import com.earlybird.catchbird.community.SignupActivity
import com.earlybird.catchbird.data.BirdInfoData
import com.earlybird.catchbird.data.CaptureTime
import com.earlybird.catchbird.databinding.ActivityEncyclopediaBirdInforBinding
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage


class EncyclopediaBirdInforActivity : AppCompatActivity(),ConfirmDialogInterface {
    private val binding: ActivityEncyclopediaBirdInforBinding by lazy {
        ActivityEncyclopediaBirdInforBinding.inflate(layoutInflater)
    }
    private val databaseName:String = "birdName"
    private var database: SQLiteDatabase? = null
    private val birdImage = "bird_image"
    private val birdInfo = "bird_info"
    private var birdKor = ""

    private var latitude: String? = null
    private var longitude: String? = null

    private var user : FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (database == null){
            createDatabase()
        }

        birdKor = intent.getStringExtra("birdKor").toString()
        searchBirdInfo(birdKor)

        val bird_info = BirdInfoData.image_m    // 새 기본 이미지
        BirdInfoData.image_f  // 새(암컷) 이미지 추가 필요

        binding.encyclopediaBtnBirdInforOk.setOnClickListener {
            finish()
        }
        binding.encyclopediaBirdLocation.setOnClickListener {
            val dialog = CustomDialog(this)
            dialog.isCancelable = true
            dialog.show(this.supportFragmentManager,"ConfirmDialog")
        }
        binding.encyclopediaBirdInforNameText.text = BirdInfoData.specie // 새 이름
        binding.encyclopediaBirdInforText2.text= BirdInfoData.info      // 새 정보
        binding.encyclopediaBirdInforImage2.clipToOutline = true
        Glide.with(this).load(bird_info).into(binding.encyclopediaBirdInforImage2)

        // ModelResultFragment에서 넘어온 경우
        intent.getStringExtra("cameraUri")?.run {
            binding.imageUpload.visibility = View.VISIBLE
            latitude = intent.getStringExtra("latitude")
            longitude = intent.getStringExtra("longitude")

            binding.imageUpload.setOnClickListener {
                uploadImage(this)
            }
        }
    }

    override fun onYesButtonClick(num: Int, theme: Int) {
        TODO("Not yet implemented")
    }

    private fun uploadImage(imageUri:String) {
        user = Firebase.auth.currentUser
        if (user==null) {
            AlertDialog.Builder(this)
                .setTitle("로그인 필요")
                .setMessage("커뮤니티를 이용하시려면 로그인이 필요합니다!")
                .setPositiveButton("로그인") { dialog, which ->
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                }
                .setNegativeButton("취소") { dialog, which -> }
                .setNeutralButton("회원가입") { dialog, which ->
                    val intent = Intent(this, SignupActivity::class.java)
                    startActivity(intent)
                }
                .setCancelable(false) // 뒤로가기 사용불가
                .create()
                .show()
        } else {
            val imageName = imageUri.split('/').last()
            val imageRef = FirebaseStorage.getInstance().reference.child("userBirdImages/${user!!.uid}/$birdKor/$imageName")

            imageRef.putFile(imageUri.toUri()).continueWithTask {
                return@continueWithTask imageRef.downloadUrl
            }.addOnFailureListener {
                Toast.makeText(this, "업로드 실패\n다시 시도해주세요", Toast.LENGTH_SHORT).show()
            }.addOnSuccessListener {uri->
                uploadLoaction(imageName, uri)   // 위치 정보 업로드
            }
        }
    }

    private fun uploadLoaction(imageName:String, uri: Uri) {
        val db = Firebase.firestore
        val data = hashMapOf(
            "uid" to user?.uid,
            "bird" to birdKor,
            "date" to CaptureTime.date,
            "time" to CaptureTime.time,
            "latitude" to latitude,
            "longitude" to longitude,
            "imageUri" to uri
        )

        if (user != null) {
            db.collection("birdImageData").document(user!!.uid)
                .collection("imageInfo").document(imageName)
                .set(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "업로드가 완료되었습니다", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "업로드 실패\n다시 시도해주세요", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun createDatabase() {
        database = openOrCreateDatabase(databaseName, MODE_PRIVATE, null)
    }

    private fun searchBirdInfo(specie: String) {
        val sql = "select specie, image_m, image_f, info from $birdImage, $birdInfo " +
                "where $birdImage.specie_k = $birdInfo.specie and $birdInfo.specie = '$specie'"
        val cursor = database?.rawQuery(sql, null)
        if (cursor != null) {
            cursor.moveToNext()
            BirdInfoData.specie = cursor.getString(0)
            BirdInfoData.image_m = cursor.getString(1).toUri()
            BirdInfoData.image_f = cursor.getString(2).toUri()
            BirdInfoData.info = cursor.getString(3)
            cursor.close()
        }
    }
}