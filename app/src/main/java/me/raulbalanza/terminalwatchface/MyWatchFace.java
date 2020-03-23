/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.raulbalanza.terminalwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (R.id.message_update == message.what) {
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        mUpdateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
                    }
                }
            }
        };

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private boolean mRegisteredTimeZoneReceiver = false;

        private static final float STROKE_WIDTH = 3f;

        private Calendar mCalendar;

        private Paint mBackgroundPaint;
        private Paint mHandPaint;

        private boolean mAmbient;
        private Bitmap background;
        private int prevFrame;
        private int [] frames;
        private float [] maxBinarySize;
        private float [] maxConsoleSize;

        private Typeface font;
        private Typeface font2;

        private String time1;
        private String date1;

        private int mWidth;
        private int mHeight;
        private int internalWidth;
        private int internalHeight;
        private int internalX;
        private int internalY;
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this).build());

            int [] fr = {R.drawable.frame_00, R.drawable.frame_01,R.drawable.frame_02, R.drawable.frame_03, R.drawable.frame_04, R.drawable.frame_05, R.drawable.frame_06,
                    R.drawable.frame_07, R.drawable.frame_08, R.drawable.frame_09, R.drawable.frame_10, R.drawable.frame_11, R.drawable.frame_12, R.drawable.frame_13,
                    R.drawable.frame_14, R.drawable.frame_15, R.drawable.frame_16, R.drawable.frame_17, R.drawable.frame_18, R.drawable.frame_19, R.drawable.frame_20,
                    R.drawable.frame_21, R.drawable.frame_22, R.drawable.frame_23, R.drawable.frame_24, R.drawable.frame_25, R.drawable.frame_26, R.drawable.frame_27,
                    R.drawable.frame_28, R.drawable.frame_29, R.drawable.frame_30, R.drawable.frame_31, R.drawable.frame_32, R.drawable.frame_33, R.drawable.frame_34,
                    R.drawable.frame_35, R.drawable.frame_36, R.drawable.frame_37, R.drawable.frame_38};
            frames = fr;

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.WHITE);
            mBackgroundPaint.setStrokeWidth(STROKE_WIDTH);
            mBackgroundPaint.setAntiAlias(true);
            mBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);

            AssetManager am = getApplicationContext().getAssets();
            font = Typeface.createFromAsset(am,
                    String.format(Locale.US, "fonts/%s", "lucidaconsole.ttf"));

            font2 = Typeface.createFromAsset(am,
                    String.format(Locale.US, "fonts/%s", "joystixmonospace.ttf"));

            mHandPaint = new Paint();
            mHandPaint.setColor(Color.WHITE);
            mHandPaint.setStrokeWidth(STROKE_WIDTH);
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mHandPaint.setTypeface(font);

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
            /*
             * Calculate the lengths of the watch hands and store them in member variables.
             */

            final float internalSqX = (float) (0.5 * Math.sqrt(2) * mWidth);
            final float internalSqY = (float) (0.5 * Math.sqrt(2) * mHeight);
            internalX = (int) ((mWidth-internalSqX)/2.0);
            internalY = (int) ((mHeight-internalSqY)/2.0);

            internalWidth = (int) internalSqX;
            internalHeight = (int) internalSqY;

            time1 = "root@watch:~$ date +%T";
            date1 = "root@watch:~$ date +%x";

            String max = (time1.length() > date1.length() ? time1 : date1);
            maxConsoleSize = maxTextSize(max, 50f, font);

            maxBinarySize = maxTextSize("000000", 100f, font2);
            prevFrame = 0;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // Draw matrix animation
            background = BitmapFactory.decodeResource(getResources(), frames[nextFrame(39)]);
            mScale = ((float) mWidth) / (float) background.getWidth();

            background = Bitmap.createScaledBitmap(background,
                    (int) (background.getWidth() * mScale),
                    (int) (background.getHeight() * mScale), true);

            canvas.drawBitmap(background, 0, 0, mBackgroundPaint);

            // Get current time and date
            final int hours = mCalendar.get(Calendar.HOUR_OF_DAY);
            final int minutes = mCalendar.get(Calendar.MINUTE);
            final int seconds = mCalendar.get(Calendar.SECOND);

            String time2 = parseTwoDigit(hours) + ":" + parseTwoDigit(minutes) + ":" + parseTwoDigit(seconds);

            final int day = mCalendar.get(Calendar.DAY_OF_MONTH);
            final int month = mCalendar.get(Calendar.MONTH)+1;
            final int year = mCalendar.get(Calendar.YEAR);

            String date2 = parseTwoDigit(day) + "/" + parseTwoDigit(month) + "/" + year;

            String binaryHour = Integer.toBinaryString(hours);
            String binaryMinute = Integer.toBinaryString(minutes);
            String binarySecond = Integer.toBinaryString(seconds);

            String [] screen = {time1, time2, date1, date2};

            mHandPaint.setTextSize(maxConsoleSize[0]); // Assuming that no clock will allow for a font size greater than 50.0

            final float last = printText(canvas, screen, maxConsoleSize[1], mHandPaint,10f);

            String [] screen2 = {toSixBits(binaryHour), toSixBits(binaryMinute), toSixBits(binarySecond)};

            // Hora central
            Rect r = new Rect();
            mBackgroundPaint.getTextBounds("000000", 0, 6, r);
            mBackgroundPaint.setTextSize(maxBinarySize[0]);
            mBackgroundPaint.setTypeface(font2);

            canvas.drawText(screen2[0], mCenterX-(r.width()/2), internalY+last+4+maxBinarySize[1], mBackgroundPaint);
            canvas.drawText(screen2[1], mCenterX-(r.width()/2), internalY+last+2*(maxBinarySize[1]+4), mBackgroundPaint);
            canvas.drawText(screen2[2], mCenterX-(r.width()/2), internalY+last+3*(maxBinarySize[1]+4), mBackgroundPaint);

        }

        private int nextFrame(int max){

            prevFrame++;
            if (prevFrame == max){ prevFrame = 0; }
            return prevFrame;

        }

        private float printText(Canvas c, String [] lines, float jump, Paint paint, float base){

            for (int i=0; i<lines.length; i++){

                c.drawText(lines[i], internalX+10, internalY+base, paint);
                base+=(jump+3);

            }

            return base;

        }

        private float [] maxTextSize(String text, float textSize, Typeface font){

            float [] res = new float[2];
            Rect r = new Rect();
            Paint p = new Paint();
            p.setStrokeWidth(STROKE_WIDTH);
            p.setAntiAlias(true);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setTextSize(textSize);
            p.setTypeface(font);
            p.getTextBounds(text, 0, text.length(), r);
            int width = r.right;
            // System.out.println("The width is " + width);
            if (width < (internalWidth-10/*-textSize*/)){
                res[0] = textSize-0.5f;
                res[1] = r.height();
                return res;
            } else return maxTextSize(text, textSize-0.5f, font);

        }

        private String parseTwoDigit(int time){

            if (time >= 10) return time + "";
            else return "0" + time;

        }

        private String toSixBits(String s){
            int missing = 6-s.length();

            while (missing > 0){
                s = "0" + s;
                missing--;
            }

            return s;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /*
             * Whether the timer should be running depends on whether we're visible
             * (as well as whether we're in ambient mode),
             * so we may need to start or stop the timer.
             */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(R.id.message_update);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}
