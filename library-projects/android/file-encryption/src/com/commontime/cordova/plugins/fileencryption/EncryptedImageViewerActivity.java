/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */

package com.commontime.cordova.plugins.fileencryption;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;

public class EncryptedImageViewerActivity extends Activity
{
    private static final String LOG_TAG = "EncryptedImageViewer";
    private static final String UNABLE_TO_LOAD_IMAGE = "Unable to load image.";

    Bitmap bmp = null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        ActionBar actionBar = getActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        actionBar.setStackedBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        actionBar.setDisplayHomeAsUpEnabled(true);

        setContentView(createInitialView());

        final String path = getIntent().getStringExtra("path");
        final FileEncryption fe = new FileEncryption();
        fe.decrypt(path, getCacheDir().getAbsolutePath(), new FileEncryption.DecryptCallback() {
            @Override
            public void onSuccess(String path) {
                if(path != null) {
                    new GetAndShowBitmapAsyncOperation(path).execute();
                } else {
                    setContentView(createImageLoadErrorView());
                }
            }
            @Override
            public void onFailure() {
                finish();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        if (bmp != null)
            bmp .recycle();
        super.onDestroy();
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private View createInitialView()
    {
        RelativeLayout rl = new RelativeLayout(getApplicationContext());

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );

        rl.setLayoutParams(lp);

        ProgressBar pb = new ProgressBar(getApplicationContext());

        RelativeLayout.LayoutParams lp_tv = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );

        lp_tv.addRule(RelativeLayout.CENTER_IN_PARENT);

        pb.setLayoutParams(lp_tv);

        rl.addView(pb);

        return rl;
    }

    private View createImageLoadErrorView()
    {
        RelativeLayout rl = new RelativeLayout(getApplicationContext());

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );

        rl.setLayoutParams(lp);

        TextView tv = new TextView(getApplicationContext());

        RelativeLayout.LayoutParams lp_tv = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );

        lp_tv.addRule(RelativeLayout.CENTER_IN_PARENT);

        tv.setLayoutParams(lp_tv);

        tv.setText(UNABLE_TO_LOAD_IMAGE);

        tv.setPadding(15,15,15,15);

        tv.setTextColor(Color.WHITE);

        tv.setTextSize(15);

        rl.addView(tv);

        return rl;
    }

    private class GetAndShowBitmapAsyncOperation extends AsyncTask<String, Void, Bitmap>
    {
        String path;

        public GetAndShowBitmapAsyncOperation(String path) {
            this.path = path;
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);
                int inSampleSize = calculateInSampleSize(options, 1500, 1500);
                BitmapFactory.Options newOptions = new BitmapFactory.Options();
                newOptions.inJustDecodeBounds = false;
                newOptions.inSampleSize = inSampleSize;
                newOptions.inMutable = true;
                bmp = BitmapFactory.decodeFile(path, newOptions);
            } catch (Exception e) {
            }
            return bmp;
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            if (bmp != null) {
                ZoomFunctionality img = new ZoomFunctionality(EncryptedImageViewerActivity.this);
                img.setImageBitmap(bmp);
                img.setMaxZoom(4f);
                setContentView(img);
            } else {
                setContentView(createImageLoadErrorView());
            }
            File fdelete = new File(path);
            if (fdelete.exists()) {
                if (fdelete.delete()) {
                    Log.d(LOG_TAG, "file Deleted : " + path);
                } else {
                    Log.d(LOG_TAG, "file not Deleted : " + path);
                }
            }
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }
}