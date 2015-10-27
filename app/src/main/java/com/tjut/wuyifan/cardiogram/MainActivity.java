package com.tjut.wuyifan.cardiogram;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.opencsv.CSVWriter;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_TAKE_PHOTO = 1;
    private static final int REQUEST_CUT_PHONTO = 2;
    private static final String TAG = "Cardiogram";
    //黑白框边界的单位长度
    private static final int BORDER = 30;
    //用来显示图片
    private ImageView mImageView = null;
    //拍照键
    private FloatingActionButton mFabCamera = null;
    private Bitmap mBitmap = null;
    //存储已拍照片或已剪裁照片的路径
    private String mCurrentPhotoPath = null;


    //opencv是一个图像处理的库，异步加载opencv
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //加载布局
        setContentView(R.layout.activity_main);

        //初始化控件
        mImageView = (ImageView) findViewById(R.id.image);

        mFabCamera = (FloatingActionButton) findViewById(R.id.fab_camera);
        mFabCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, mLoaderCallback);
    }

    //调用本地的相机app，开始拍照，所拍照片存在mCurrentPhotoPath路径下
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
            }
            if (photoFile != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(photoFile));
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    //调用本地的截图app，开始截图，所截照片存在mCurrentPhotoPath路径下
    private void dispatchCutPictureIntent() {
        Intent cutPictureIntent = new Intent("com.android.camera.action.CROP");
        File photoFile = null;
        cutPictureIntent.setDataAndType(Uri.fromFile(new File(mCurrentPhotoPath)), "image/*");
        cutPictureIntent.putExtra("crop", "true");
        cutPictureIntent.putExtra("return-data", false);
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
        }
        if(photoFile != null) {
            cutPictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
        }
        startActivityForResult(cutPictureIntent, REQUEST_CUT_PHONTO);
    }

    //根据时间生成图片名
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }


    //调用本地相机或截图app结束后的回调事件，拍照完后开始截图，截图完后开始加边框并显示
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_TAKE_PHOTO:
                    dispatchCutPictureIntent();
                    break;
                case REQUEST_CUT_PHONTO:
                    LoadImageFromFileTask task = new LoadImageFromFileTask();
                    task.execute(mCurrentPhotoPath);
                    Log.d(TAG, mCurrentPhotoPath);
                    break;
            }
        }
    }

    //异步加载剪裁后的图片，即mCurrentPhotoPath路径下的图片
    private class LoadImageFromFileTask extends AsyncTask<String, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(String... params) {
            File file = new File(params[0]);

            if (file.exists()){
                Bitmap bitmap =  BitmapFactory.decodeFile(file.getAbsolutePath());
                //白框、黑框高度和宽度
                int whiteHeight = 2 * BORDER;
                int blackHeight = 3 * BORDER;
                int whiteWidth = bitmap.getWidth();
                int blackWidth = bitmap.getWidth();

                Bitmap barBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight() + 2 * (whiteHeight + blackHeight), Bitmap.Config.RGB_565);

                //画边框
                Canvas canvas = new Canvas(barBitmap);
                Paint paint = new Paint();
                paint.setColor(Color.BLACK);
                canvas.drawRect(0, 0, blackWidth, blackHeight, paint);
                canvas.drawRect(0, barBitmap.getHeight() - blackHeight, blackWidth, barBitmap.getHeight(), paint);
                paint.setColor(Color.WHITE);
                canvas.drawRect(0, blackHeight, whiteWidth, blackHeight + whiteHeight, paint);
                canvas.drawRect(0, blackHeight + whiteHeight + bitmap.getHeight(), whiteWidth, barBitmap.getHeight() - blackHeight, paint);
                canvas.drawBitmap(bitmap, 0, whiteHeight + blackHeight, null);

                return barBitmap;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);

            if(bitmap != null) {
                mBitmap = bitmap;
                //显示加边框后的图片
                mImageView.setImageBitmap(bitmap);
            }
        }
    }
}
