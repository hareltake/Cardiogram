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

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_TAKE_PHOTO = 1;
    private static final int REQUEST_CUT_PHONTO = 2;
    private static final String TAG = "Cardiogram";
    private static final String CSV = "/storage/emulated/0/Pictures/pixel.csv";
    private static final String IMAG = "/storage/emulated/0/Pictures/JPEG_20151023_155759_-832276725.jpg";
    //黑白框边界的单位长度
    private static final int BORDER = 30;
    //用来显示图片
    private ImageView mImageView = null;
    //拍照键
    private FloatingActionButton mFabCamera = null;
    private FloatingActionButton mFabSave = null;
    private FloatingActionButton mFabPlay = null;
    private ProgressBar mProgressBar = null;

    private Handler mHandler = null;

    private Bitmap mBitmap = null;
    //存储已拍照片或已剪裁照片的路径
    private String mCurrentPhotoPath = null;

    private double mUpBJ;
    private double mUpRadian;
    private double mDownBJ;
    private double mDownRadian;


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

        mHandler = new Handler();

        //初始化控件
        mImageView = (ImageView) findViewById(R.id.image);

        mFabCamera = (FloatingActionButton) findViewById(R.id.fab_camera);
        mFabCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
            }
        });

        this.mFabPlay = ((FloatingActionButton)findViewById(R.id.fab_play));
        this.mFabPlay.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View paramView)
            {
                if (MainActivity.this.mBitmap != null)
                {
                    ExtractTask extractTask = new ExtractTask();
                    extractTask.execute(mBitmap);
                }
            }
        });

        this.mFabSave = ((FloatingActionButton)findViewById(R.id.fab_save));
        this.mFabSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View paramView)
            {
                new LoadImageFromFileTask().execute(IMAG);
            }
        });
        mProgressBar = ((ProgressBar)findViewById(2131492970));
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

    private class ExtractTask extends AsyncTask<Bitmap, Integer, Bitmap>
    {
        protected Bitmap doInBackground(Bitmap[] bitmaps) {
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(CSV);
            } catch (IOException e) {
                e.printStackTrace();
            }
            CSVWriter cw = new CSVWriter(fileWriter);

            Bitmap bitmap = bitmaps[0];
            afterDownBJToolStrip(bitmap);
            afterUpBJToolStrip(bitmap);
            Bitmap grayBitmap = huiduToolStrip(bitmap);
            selfToolStrip(grayBitmap);

            return grayBitmap;
        }

        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            mImageView.setImageBitmap(bitmap);
        }

        protected void onPreExecute() {
            mProgressBar.setVisibility(View.VISIBLE);
        }

        protected void onProgressUpdate(Integer[] integers) {
            mProgressBar.setProgress(integers[0]);
        }

        /**
         * 重新获取下边界
         * @param bitmap
         */
        private void afterDownBJToolStrip(Bitmap bitmap) {
            ArrayList<Point> afterDownPoints = downBJToolStrip(bitmap);
            ArrayList<Point> nihePoints = new ArrayList<Point>();

            mDownBJ = afterDownPoints.get(50).y;
            Log.d(TAG, "mDownBJ: " + mDownBJ);

            double a = 0;
            double b = 0;
            double c = 0;
            double d = 0;

            int width = bitmap.getWidth();

            for (int i = 0; i < width; i++)
            {
                a += afterDownPoints.get(i).x * afterDownPoints.get(i).x;
                b += afterDownPoints.get(i).x;
                c += afterDownPoints.get(i).x * afterDownPoints.get(i).y;
                d += afterDownPoints.get(i).y;
            }
            double deltanihewantop = a * width - b * b;
            double slope = (c * width - b * d) / deltanihewantop;
            double offset = (a * d - c * b) / deltanihewantop;
            double radian = Math.atan(slope);

            for (int i = 0; i < width; i++)
            {
                Point point = new Point(i, slope * i + offset);
                nihePoints.add(point);
            }
        }

        /**
         * 重新获取上边界
         * @param bitmap
         */
        private void afterUpBJToolStrip(Bitmap bitmap) {
            ArrayList<Point> afterUpPoints = upBJToolStrip(bitmap);
            ArrayList<Point> nihePoints = new ArrayList<Point>();

            double a = 0;
            double b = 0;
            double c = 0;
            double d = 0;

            int width = bitmap.getWidth();

            for (int i = 0; i < width; i++)
            {
                a += afterUpPoints.get(i).x * afterUpPoints.get(i).x;
                b += afterUpPoints.get(i).x;
                c += afterUpPoints.get(i).x * afterUpPoints.get(i).y;
                d += afterUpPoints.get(i).y;
            }

            double deltanihewantop = a * width - b * b;
            double slope = (c * width - b * d) / deltanihewantop;
            double offset = (a * d - c * b) / deltanihewantop;
            double radian = Math.atan(slope);

            for (int i = 0; i < width; i++)
            {
                Point point = new Point(i, slope * i + offset);
                nihePoints.add(point);
            }
        }

        /**
         * 获取下边界
         * @param bitmap
         * @return
         */
        private ArrayList<Point> downBJToolStrip(Bitmap bitmap) {
            Mat rgbMat = new Mat();
            Utils.bitmapToMat(bitmap, rgbMat);
            Size size = rgbMat.size();
            ArrayList<Point> downPoints = new ArrayList();
            double[] p1, p2, p3;
            double r1, r2, r3;
            for (int i = 0; i < size.width; i++) {
                for (int j = (int) size.height - 150 - 1; j > 1; j--) {
                    p1 = rgbMat.get(j, i);
                    p2 = rgbMat.get(j - 1, i);
                    p3 = rgbMat.get(j - 2, i);
                    r1 = p1[0];
                    r2 = p2[0];
                    r3 = p3[0];
                    if (r1 < 132 && r2 >= 132 && r3 >= 132) {
                        Point point = new Point(i, j - 1);
                        downPoints.add(point);
                        break;
                    }
                }
                publishProgress((int) (i / size.width * 40));
            }

            int count = downPoints.size();
            double x1 = downPoints.get(0).x;
            double y1 = bitmap.getHeight() - downPoints.get(0).y;
            double x2 = downPoints.get(count - 1).x;
            double y2 = bitmap.getHeight() - downPoints.get(count - 1).y;
            double slope = (y2 - y1) / (x2 - x1);
            mDownRadian = Math.atan(slope);
            Log.d(TAG, "radian: " + mDownRadian);

            return downPoints;
        }

        /**
         *  获取上边界
         * @param bitmap
         * @return
         */
        private ArrayList<Point> upBJToolStrip(Bitmap bitmap) {
            Mat rgbMat = new Mat();
            Utils.bitmapToMat(bitmap, rgbMat);
            Size size = rgbMat.size();
            ArrayList<Point> upPoints = new ArrayList();
            double[] p1, p2, p3;
            double r1, r2, r3;
            for (int i = 0; i < size.width; i++) {
                for (int j = 150; j < size.height; j++) {
                    p1 = rgbMat.get(j, i);
                    p2 = rgbMat.get(j + 1, i);
                    p3 = rgbMat.get(j + 2, i);
                    r1 = p1[0];
                    r2 = p2[0];
                    r3 = p3[0];
                    if (r1 < 132 && r2 >= 132 && r3 >= 132) {
                        Point point = new Point(i, j + 1);
                        upPoints.add(point);
                        break;
                    }
                }
                publishProgress((int) (40 + i / size.width * 40));
            }

            int count = upPoints.size();
            double x1 = upPoints.get(0).x;
            double y1 = bitmap.getHeight() - upPoints.get(0).y;
            double x2 = upPoints.get(count - 1).x;
            double y2 = bitmap.getHeight() - upPoints.get(count - 1).y;
            double slope = (y2 - y1) / (x2 - x1);
            mUpRadian = Math.atan(slope);
            Log.d("Cardiogram", "radian: " + mUpRadian);

            if (mUpRadian < 0) {
                mUpBJ = upPoints.get(count - 1).y;
            } else {
                mUpBJ = upPoints.get(0).y;
            }

            Log.d(TAG, "mUpBJ: " + mUpBJ);

            return upPoints;
        }

        /**
         * 图像灰度化
         * @param bitmap
         * @return
         */
        private Bitmap huiduToolStrip(Bitmap bitmap)
        {
            Mat rgbMat = new Mat();
            Mat grayMat = new Mat();
            Utils.bitmapToMat(bitmap, rgbMat);
            Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY);

            Bitmap grayBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.RGB_565);
            Utils.matToBitmap(grayMat, grayBitmap);

            publishProgress(100);

            return grayBitmap;
        }

        private Bitmap rotateToolStrip(Bitmap bitmap) {
            Bitmap rotateBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
            Matrix matrix = new Matrix();
            matrix.setRotate((float) (mDownRadian * 180 / Math.PI), bitmap.getWidth() / 2, bitmap.getHeight() / 2);

            Canvas canvas = new Canvas(rotateBitmap);
            canvas.drawBitmap(bitmap, matrix, null);

            publishProgress(100);

            return rotateBitmap;
        }

        private void selfToolStrip(Bitmap bitmap)
        {
            for (int i = 0; i < bitmap.getWidth(); i++)
            {
                for (int j = 0; j < 10 + mUpBJ; j++)
                {
                    bitmap.setPixel(i, j, Color.WHITE);
                }
                for (int j = (int) mDownBJ - 20; j < bitmap.getHeight(); j++) {
                    bitmap.setPixel(i, j, Color.WHITE);
                }
            }
        }
    }
}
