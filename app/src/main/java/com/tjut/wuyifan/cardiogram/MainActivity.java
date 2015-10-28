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
import android.widget.TextView;

import com.opencsv.CSVWriter;
import com.tjut.wuyifan.cardiogram.utils.Utility;

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
    private static final String IMAG = "/storage/emulated/0/Pictures/JPEG_20151028_125903_-188392486.jpg";
    //黑白框边界的单位长度
    private static final int BORDER = 30;
    //用来显示图片
    private ImageView mImageView = null;
    //拍照键
    private FloatingActionButton mFabCamera = null;
    private FloatingActionButton mFabSave = null;
    private FloatingActionButton mFabPlay = null;
    private ProgressBar mProgressBar = null;
    private TextView textView = null;

    private Handler mHandler = null;

    private Bitmap mBitmap = null;
    //存储已拍照片或已剪裁照片的路径
    private String mCurrentPhotoPath = null;

    //存储最后生成的坐标
    ArrayList<Point> generatedPoints = null;
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

        mProgressBar = ((ProgressBar)findViewById(R.id.progress));

        textView = (TextView) findViewById(R.id.textView);

        generatedPoints = new ArrayList<Point>();
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
            Bitmap bitmap = bitmaps[0];
            publishProgress(-1, 0);
            downBJToolStrip(bitmap);
            publishProgress(-1, 1);
            upBJToolStrip(bitmap);
            publishProgress(-1, 2);
            Bitmap rotateBitmap = rotateToolStrip(bitmap);
            publishProgress(-1, 3);
            afterDownBJToolStrip(rotateBitmap);
            publishProgress(-1, 4);
            afterUpBJToolStrip(rotateBitmap);
            publishProgress(-1, 5);
            final Bitmap grayBitmap = huiduToolStrip(rotateBitmap);
            publishProgress(-1, 6);
            selfToolStrip(grayBitmap);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mImageView.setImageBitmap(grayBitmap);
                }
            });
            publishProgress(-1, 7);
            generateCoordinates(grayBitmap);
            publishProgress(-1, 8);
            redrawToolStrip();

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
            if (integers[0] != -1) {
                mProgressBar.setProgress(integers[0]);
            } else {
                switch (integers[1]) {
                    case 0:
                        textView.setText("获取下边界");
                        break;
                    case 1:
                        textView.setText("获取上边界");
                        break;
                    case 2:
                        textView.setText("旋转矫正");
                        break;
                    case 3:
                        textView.setText("重新获取下边界");
                        break;
                    case 4:
                        textView.setText("重新获取上边界");
                        break;
                    case 5:
                        textView.setText("灰度化");
                        break;
                    case 6:
                        textView.setText("二值化");
                        break;
                    case 7:
                        textView.setText("存储坐标");
                        break;
                    case 8:
                        textView.setText("重画心电图");
                        break;
                    default:
                        break;
                }
            }
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

            for (int i = 2; i < width - 2; i++)
            {
                a += afterDownPoints.get(i - 2).x * afterDownPoints.get(i - 2).x;
                b += afterDownPoints.get(i - 2).x;
                c += afterDownPoints.get(i - 2).x * afterDownPoints.get(i - 2).y;
                d += afterDownPoints.get(i - 2).y;
            }
            double deltanihewantop = a * width - b * b;
            double slope = (c * width - b * d) / deltanihewantop;
            double offset = (a * d - c * b) / deltanihewantop;
            double radian = Math.atan(slope);

            for (int i = 2; i < width - 2; i++)
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

            for (int i = 2; i < width - 2; i++)
            {
                a += afterUpPoints.get(i - 2).x * afterUpPoints.get(i - 2).x;
                b += afterUpPoints.get(i - 2).x;
                c += afterUpPoints.get(i - 2).x * afterUpPoints.get(i - 2).y;
                d += afterUpPoints.get(i - 2).y;
            }

            double deltanihewantop = a * width - b * b;
            double slope = (c * width - b * d) / deltanihewantop;
            double offset = (a * d - c * b) / deltanihewantop;
            double radian = Math.atan(slope);

            for (int i = 2; i < width - 2; i++)
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
            for (int i = 2; i < size.width - 2; i++) {
                for (int j = (int) size.height - 150 - 1 - 3; j > 1; j--) {
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
                publishProgress((int) (i / size.width * 30));
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
            for (int i = 2; i < size.width - 2; i++) {
                for (int j = 150 + 2; j < size.height - 2; j++) {
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
                publishProgress((int) (30 + i / size.width * 30));
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
//            int color, color1, color2;
//            int r0, r1, r2, r3, r4, r5, r6, r7, r8;
//            for (int i = 1; i < bitmap.getWidth() - 3; i++) {
//                for (int j = 1; j < bitmap.getHeight() - 3; j++) {
//                    color = bitmap.getPixel(i, j);
//                    r0 = Color.red(color);
//                    color = bitmap.getPixel(i + 1, j);
//                    r1 = Color.red(color);
//                    color = bitmap.getPixel(i - 1, j);
//                    r2 = Color.red(color);
//                    color = bitmap.getPixel(i - 1, j + 1);
//                    r3 = Color.red(color);
//                    color = bitmap.getPixel(i - 1, j - 1);
//                    r4 = Color.red(color);
//                    color = bitmap.getPixel(i + 1, j - 1);
//                    r5 = Color.red(color);
//                    color = bitmap.getPixel(i + 1, j + 1);
//                    r6 = Color.red(color);
//                    color = bitmap.getPixel(i, j + 1);
//                    r7 = Color.red(color);
//                    color = bitmap.getPixel(i, j - 1);
//                    r8 = Color.red(color);
//
//                    if ((r2 == 255) && (r3 == 255) && (r4 == 255) && (r5 == 255) && (r6 == 255) && (r7 == 255) && (r1 == 255)) {
//                        bitmap.setPixel(i, j, Color.argb(0xff, 0xff, 0xff, 0xff));
//                    } else {
//                        bitmap.setPixel(i, j, Color.argb(0xff, r0, r0, r0));
//                    }
//                }
//            }
            Mat rgbMat = new Mat();
            Mat grayMat = new Mat();
            Mat binaryMat = new Mat();
            Utils.bitmapToMat(bitmap, rgbMat);
            Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY);
            Imgproc.adaptiveThreshold(grayMat, binaryMat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 5, 2);

            Bitmap grayBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.RGB_565);
            Utils.matToBitmap(grayMat, grayBitmap);

            publishProgress(65);

            return bitmap;
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
            for (int i = 0; i < bitmap.getWidth() - 2; i++)
            {
                for (int j = 0; j < mUpBJ + 10; j++) {
                    bitmap.setPixel(i, j, Color.WHITE);
                }
                for (int j = (int) mDownBJ - 20; j < bitmap.getHeight(); j++) {
                    bitmap.setPixel(i, j, Color.WHITE);
                }
            }

            int threshValue = Utility.getThreshValue(bitmap);

            publishProgress(75);

            int first = 0, flag2 = 1;
            int color = 0, red = 0;
            int shaobing = 0, shaobingx = 0, shaobingy = 0;

            for (int i = 1; i < bitmap.getWidth(); i++) {
                if (flag2 == 0) break;
                for (int j = (int) mDownBJ - 20; j > (int) mUpBJ + 10; j--) {
                    color = bitmap.getPixel(i, j);
                    red = Color.red(color);

                    if (red < threshValue + 10) {
                        shaobingx = i;
                        shaobingy = j;
                        bitmap.setPixel(i, j, Color.BLACK);
                        flag2 = 0;
                        break;
                    } else {
                        bitmap.setPixel(i, j, Color.WHITE);
                    }
                }
            }

            publishProgress(80);

            int flag3 = 0;
            int miny = 2000;
            int realy = 0;
            int t = 0;
            for (int i = shaobingx + 1; i < bitmap.getWidth(); i++) {
                flag3 = 0;
                miny = 2000;
                realy = 0;

                for (int j = (int) mUpBJ + 10; j < (int) mDownBJ - 20; j++) {
                    color = bitmap.getPixel(i, j);
                    red = Color.red(color);

                    if (red < threshValue + 10) {
                        t = Math.abs(j - shaobingy);
                        if (t < miny & t != 0) {
                            miny = t;
                            realy = j;
                            flag3 = 1;
                        }
                        bitmap.setPixel(i, j, Color.WHITE);
                    } else {
                        bitmap.setPixel(i ,j, Color.WHITE);
                    }
                }

                if (flag3 == 1) {
                    bitmap.setPixel(i, realy, Color.BLACK);
                    shaobingy = realy;
                    shaobing = realy;
                } else {
                    shaobing = 0;
                }
                for (int j = shaobing + 1; j < mDownBJ - 20; j++) {
                    bitmap.setPixel(i, j, Color.WHITE);
                }
            }

            publishProgress(85);
        }

        private void generateCoordinates(Bitmap bitmap) {
            int oldy = 2;
            int flag = 1;
            int color = 0;
            int red = 0;
            int targetLength = 0;

            for (int i = 2; i < bitmap.getWidth() - 2; i++) {
                for (int j = 2; j < bitmap.getHeight() - 2; j++) {
                    color = bitmap.getPixel(i, j);
                    red = Color.red(color);
                    if (red == 0) {
                        Point point = new Point(i, j - (int) mUpBJ);
                        generatedPoints.add(point);
                        targetLength = i;
                        break;
                    }
                }
            }

            publishProgress(90);

            int count = generatedPoints.size();
            double time = count * 0.0035;
            //将坐标写入csv文件
            try {
                FileWriter fileWriter = new FileWriter(CSV);
                CSVWriter cw = new CSVWriter(fileWriter);
                ArrayList<String[]> coordinates = new ArrayList<String[]>();
                double height = mDownBJ - mUpBJ;
                String[] coordinate = new String[2];
                for (int i = 0; i < count; i++) {
                    String x = String.valueOf(generatedPoints.get(i).x);
                    String y = String.valueOf(height - generatedPoints.get(i).y);
//                String[] coordinate = new String[]{x, y};
//                coordinates.add(coordinate);
                    coordinate[0] = x;
                    coordinate[1] = y;
                    cw.writeNext(coordinate);
                }
                cw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            publishProgress(100);
        }

        private void redrawToolStrip() {
            
        }
    }
}
