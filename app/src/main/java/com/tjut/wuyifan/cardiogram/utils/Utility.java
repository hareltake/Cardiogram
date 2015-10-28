package com.tjut.wuyifan.cardiogram.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;

import java.util.ArrayList;

/**
 * Created by guogaoyang on 2015/10/26.
 */
public class Utility {
    public static int getThreshValue(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixelNum = new int[256];
        int threshValue = 0;
        int total = 0, color = 0, red = 0;

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                color = bitmap.getPixel(i, j);
                red = Color.red(color);
                pixelNum[red]++;
            }
        }

        int q = 0;
        for (int i = 0; i < 256; i++) {
            total = 0;
            for (int j = -2; j <= 2; j++) {
                q = i + j;
                q = q < 0 ? 0 : q;
                q = q > 255 ? 255 :q;
                total += pixelNum[q];
            }
            pixelNum[i] = (int) (total / 5.0 + 0.5);
        }

        double sum = 0.0, csum = 0.0;
        int n = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * pixelNum[i];
            n += pixelNum[i];
        }

        double fmax = -1.0;
        int n1 = 0, n2 = 0;
        double m1 = 0.0, m2 = 0.0, sb = 0.0;
        double sbpremixn1n2 = 0.0, sbpremixm1m2 = 0.0;
        ArrayList<Point> points = new ArrayList<Point>();
        for (int i = 0; i < 256; i++) {
            n1 += pixelNum[i];
            if (n1 == 0) continue;
            n2 = n - n1;
            if (n2 == 0) break;
            csum += i * pixelNum[i];
            m1 = csum / n1;
            m2 = (sum - csum) / n2;
            sbpremixn1n2 = n1 * n2;
            sbpremixm1m2 = (m1 - m2) * (m1 - m2);
            sb = sbpremixn1n2 * sbpremixm1m2 / 1000000000;
            Point point = new Point(i, (int) sb);
            points.add(point);

            if (sb > fmax) {
                fmax = sb;
                threshValue = i;
            }
        }

        return threshValue;
    }
}
