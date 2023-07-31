package com.example.guru2.Board

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.guru2.data.PostDataModel
import com.example.guru2.data.PostType
import com.example.guru2.data.Replies
import com.example.guru2.databinding.ActivityWritePostBinding
import com.example.guru2.util.FileUtils
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import java.io.ByteArrayOutputStream
import java.util.function.Consumer

class WritePostActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityWritePostBinding
    private var postId = ""
    private var replies: ArrayList<Replies> = ArrayList<Replies>()
    private var imageUriList = ArrayList<String>()
    private val bitmapList = ArrayList<Bitmap>()
    private val maxSize = 2
    private val db = FirebaseFirestore.getInstance()
    private lateinit var type : PostType

    private var isClear = false

    private val COLLECTION_PATH = "FoodPosts"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityWritePostBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        type = intent.getSerializableExtra("type") as PostType
        initVariable()
        setPostItem()
        onViewClick()
    }

    // 33 미만 버전 콜백
    var mGetImage = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.data != null) {
            val data = result.data
            if (data!!.clipData != null) {
                val clipData = data.clipData
                if (clipData!!.itemCount > 2) {
                    Toast.makeText(
                        this@WritePostActivity,
                        "이미지는 최대 2장까지 선택 가능합니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        val bitmap = FileUtils.uriToBitmap(this@WritePostActivity, uri)
                        bitmapList.add(bitmap)
                        Log.i("##INFO", "(): bitmap.size = " + bitmapList.size)
                        if (mBinding.imOneWrite.drawable != null) {
                            mBinding.imTwoWrite.setImageBitmap(bitmap)
                        } else {
                            mBinding.imOneWrite.setImageBitmap(bitmap)
                        }
                    }
                }
            }

        }
    }

    //region ---- getImages Section  ---
    // 33 이상 버전 콜백
    var pickMultipleMedia = registerForActivityResult<PickVisualMediaRequest, List<Uri>>(
        ActivityResultContracts.PickMultipleVisualMedia(maxSize)
    ) { uris: List<Uri> ->
        Log.i("##INFO", "uris - (): ");
        // photo picker.
        if (!uris.isEmpty()) {
            for (uri in uris) {
                Log.d("######", "uri : $uri")
                // 대용량 업그레이드 시 권한 길게 유지
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flag)
                val bitmap: Bitmap =
                    FileUtils.uriToBitmap(this, uri)
                if (mBinding.imOneWrite.drawable != null) {
                    mBinding.imTwoWrite.setImageBitmap(bitmap)
                } else {
                    mBinding.imOneWrite.setImageBitmap(bitmap)
                }
                bitmapList.add(bitmap)
            }
        } else {
            Log.d("#######", "uri 없음 !!")
        }
    }
    private val isPhotoPickerAvailable: Boolean
        private get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun launchPhotoPicker() {
        if (isPhotoPickerAvailable) {
            pickMultipleMedia.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ImageOnly)
                    .build()
            )
        } else {
            val intent = Intent()
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.action = Intent.ACTION_PICK
            mGetImage.launch(intent)
        }
    }

    //endregion
    private fun initVariable() {
        Log.i("##INFO", "PostType.Delivery.name = ${PostType.Delivery}");
        mBinding.tvCategoryWrite.text =
            if (intent.getSerializableExtra("type") == PostType.Delivery) "배달/주문" else "공동구매"
        type = intent.getSerializableExtra("type") as PostType
    }


    private fun setPostItem() {
        val getPostData: PostDataModel? = intent.getSerializableExtra("postInfo") as PostDataModel?

        // 넘어온 데이터가 있을 경우
        if (getPostData != null) {
            mBinding.edTitleWrite.setText(getPostData.title)
            mBinding.edContentWrite.setText(getPostData.content)
            mBinding.edPasswordWrite.setText(getPostData.password)
            postId = getPostData.id.toString()
            replies = getPostData.replies!!
            if (getPostData.pictures?.size == 0) return
            if (getPostData.pictures!!.size === 2) {
                val uri = Uri.parse(getPostData.pictures!![1])
                Glide.with(this).load(uri).into(mBinding.imTwoWrite)
            }

            Glide.with(this).load(getPostData.pictures?.get(0)).into(mBinding.imOneWrite)
            imageUriList = getPostData.pictures!!
        }
    }

    private fun onViewClick() {
        mBinding.btCreateWrite.setOnClickListener { v ->
            mBinding.prLoadingPost.setVisibility(View.VISIBLE)
            //user 입력란에 공백이 있는지에 대한 확인
            val title: String = mBinding.edTitleWrite.getText().toString()
            val content: String = mBinding.edContentWrite.getText().toString()
            val password: String = mBinding.edPasswordWrite.getText().toString()
            if (title.isEmpty() && password.isEmpty()) {
                Toast.makeText(this, "빈 부분이 있습니다", Toast.LENGTH_SHORT).show()
                mBinding.prLoadingPost.setVisibility(View.GONE)
            } else if (!bitmapList.isEmpty()) {
                bitmapList.forEach(Consumer { image: Bitmap ->
                    Log.i("##INFO", "onViewClick(): bitmapList is not empty")
                    getImageUri(image)
                })
            } else if (!imageUriList.isEmpty()) {
                addPost()
            } else {
                addPost()
            }
        }
        mBinding.imBackWrite.setOnClickListener { v -> finish() }
        mBinding.ibtGetPhotoWrite.setOnClickListener { v ->
            if (mBinding.imOneWrite.getDrawable() != null && mBinding.imTwoWrite.drawable != null) {
                Toast.makeText(this, "이미지는 최대 1장까지 선택 가능합니다.", Toast.LENGTH_SHORT)
                    .show()
            } else {
                launchPhotoPicker()
            }
        }
        mBinding.imOneCancelWrite.setOnClickListener { v ->
            mBinding.imOneWrite.setImageResource(0)
            if (!imageUriList.isEmpty()) {
                Log.i("##INFO", "onViewClick(): delete Image to imageUriList")
                imageUriList.removeAt(0)
            }
            if (!bitmapList.isEmpty()) {
                Log.i("##INFO", "onViewClick(): delete Image to bitmapList")
                bitmapList.removeAt(0)
            }
        }

        mBinding.imTwoCancelWrite.setOnClickListener { v ->
            mBinding.imTwoWrite.setImageResource(0)
            if (imageUriList.isNotEmpty()) {
                if (imageUriList.size == 2) {
                    imageUriList.removeAt(1)
                } else {
                    imageUriList.removeAt(0)
                }
            }
            if (bitmapList.isNotEmpty()) {
                if (bitmapList.size == 2) {
                    bitmapList.removeAt(1)
                } else {
                    bitmapList.removeAt(0)
                }
            }
        }
        if (bitmapList.isNotEmpty()) {
            if (bitmapList.size == 2) {
                bitmapList.removeAt(1)
            } else {
                bitmapList.removeAt(0)
            }
        }
    }


    private fun getImageUri(imageBitmap: Bitmap) {
        Log.i("##INFO", "getImageUri(): bitmap = $imageBitmap")
        val storage: FirebaseStorage = FirebaseStorage.getInstance()
        val storageRef: StorageReference = storage.getReference()
        val randomNum = (Math.random() * 100000).toInt()
        val mountainsRef: StorageReference =
            storageRef.child(mBinding.edTitleWrite.text.toString() + randomNum.toString() + ".jpg")
        val baos = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()
        val uploadTask: UploadTask = mountainsRef.putBytes(data)
        uploadTask.addOnFailureListener(OnFailureListener { exception ->
            Log.i(
                "##INFO",
                "onFailure(): exception = " + exception.message
            )
        }).addOnSuccessListener(
            OnSuccessListener<Any?> {
                Log.i("##INFO", "onSuccess(): success save images")
                mountainsRef.getDownloadUrl().addOnSuccessListener(OnSuccessListener<Uri> { uri ->
                    Log.i("##INFO", "onSuccess(): getImageUri = $uri")
                    if (imageUriList.size < 2) {
                        imageUriList.add(uri.toString())
                        Log.i("##INFO", "onSuccess(): bimLIst.size = " + bitmapList.size)
                    }
                    addPost()
                })
            })
    }

    private fun addPost() {
        if (isClear) return
        isClear = true
        val title: String = mBinding.edTitleWrite.getText().toString()
        val content: String = mBinding.edContentWrite.getText().toString()
        val password: String = mBinding.edPasswordWrite.getText().toString()
        val res: Boolean = setPost(
            PostDataModel(
                type,
                postId,
                title,
                content,
                password,
                ArrayList(),
                imageUriList
            ), postId
        )
        if (res) {
            Toast.makeText(this, "게시글 작성에 성공하였습니다.", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "게시글 작성에 실패하였습니다.", Toast.LENGTH_SHORT).show()
        }
    }


    fun setPost(postInfo: PostDataModel?, postId: String): Boolean {
        if (postInfo == null) return false
        if (postInfo.title.isEmpty() || postInfo.content.isEmpty() || postInfo.password.isEmpty()) return false

        if (!postId.isEmpty()) {
            //case -> 게시글 수정
            db.collection(COLLECTION_PATH).document(postId).update("Posts", postInfo)
                .addOnSuccessListener { }
                .addOnFailureListener { e: Exception ->
                }
        } else {
            //case -> 게시글 첫 작성
            val posts: MutableMap<String, PostDataModel> = HashMap<String, PostDataModel>()
            posts["Posts"] = postInfo
            db.collection(COLLECTION_PATH).add(posts)
                .addOnSuccessListener { documentReference: DocumentReference? ->
                    postInfo.id = documentReference!!.id

                    db.collection(COLLECTION_PATH).document(documentReference.id).update("Posts", postInfo)
                        .addOnSuccessListener {

                        }
                        .addOnFailureListener { e: Exception ->
                        }
                }
                .addOnFailureListener { e: Exception ->
                    Log.e("##ERROR", "error = ${e.message}");
                }
        }
        return true
    }
}
