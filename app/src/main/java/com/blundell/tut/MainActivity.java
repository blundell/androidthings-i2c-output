package com.blundell.tut;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HT16K33 segment display
 * <p>
 * ENCODED_DIGITS from https://github.com/adafruit/Adafruit_LED_Backpack
 */
public class MainActivity extends Activity {
    private static final Map<Character, Short> ENCODED_DIGITS = new HashMap<>();

    static {
        ENCODED_DIGITS.put('0', (short) 0b00001100_00111111);
        ENCODED_DIGITS.put('1', (short) 0b00000000_00000110);
        ENCODED_DIGITS.put('2', (short) 0b00000000_11011011);
        ENCODED_DIGITS.put('3', (short) 0b00000000_10001111);
        ENCODED_DIGITS.put('4', (short) 0b00000000_11100110);
        ENCODED_DIGITS.put('5', (short) 0b00100000_01101001);
        ENCODED_DIGITS.put('6', (short) 0b00000000_11111101);
        ENCODED_DIGITS.put('7', (short) 0b00000000_00000111);
        ENCODED_DIGITS.put('8', (short) 0b00000000_11111111);
        ENCODED_DIGITS.put('9', (short) 0b00000000_11101111);
    }

    private static final String I2C_ADDRESS = "I2C1";
    private static final int HT16k33_SEGMENT_DISPLAY_SLAVE = 0x70;
    private static final int COUNTDOWN_FROM = 1000;

    private I2cDevice bus;
    private Handler handler;
    private int count = COUNTDOWN_FROM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PeripheralManagerService service = new PeripheralManagerService();
        try {
            bus = service.openI2cDevice(I2C_ADDRESS, HT16k33_SEGMENT_DISPLAY_SLAVE);
        } catch (IOException e) {
            throw new IllegalStateException(I2C_ADDRESS + " bus slave "
                                                + HT16k33_SEGMENT_DISPLAY_SLAVE + " connection cannot be opened.", e);
        }

        HandlerThread handlerThread = new HandlerThread("PeripheralThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    protected void onStart() {
        super.onStart();

        try {
            bus.write(new byte[]{(byte) (0x20 | 0b00000001)}, 1);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot turn on peripheral (exit standby)", e);
        }

        try {
            bus.write(new byte[]{(byte) (0x80 | 0b00000001)}, 1);
//            bus.write(new byte[]{(byte) (0x80 | 0b00000111)}, 1); // blinking
        } catch (IOException e) {
            throw new IllegalStateException("Cannot turn on the LED display", e);
        }

        handler.post(writeDisplay);
    }

    private final Runnable writeDisplay = new Runnable() {
        @Override
        public void run() {
            if (count < 0) {
                return;
            }

            Log.d("TUT", "display " + count);
            char[] digits = convertToChars(count);

            try {
                bus.writeRegWord(0x0, ENCODED_DIGITS.get(digits[0]));
                bus.writeRegWord(0x2, ENCODED_DIGITS.get(digits[1]));
                bus.writeRegWord(0x4, ENCODED_DIGITS.get(digits[2]));
                bus.writeRegWord(0x6, ENCODED_DIGITS.get(digits[3]));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot write " + count + " to peripheral.", e);
            }

            count--;
            handler.postDelayed(this, TimeUnit.MILLISECONDS.toMillis(100));
        }

        private char[] convertToChars(int count) {
            return padWithZeros(count).toCharArray();
        }

        private String padWithZeros(int count) {
            String countAsString = String.valueOf(count);
            int length = countAsString.length();
            if (length == 1) {
                return "000" + countAsString;
            }
            if (length == 2) {
                return "00" + countAsString;
            }
            if (length == 3) {
                return "0" + countAsString;
            }
            if (length == 4) {
                return countAsString;
            }
            throw new IllegalStateException(count + " cannot be padded. We only coded to handle 4 digits.");
        }
    };

    @Override
    protected void onStop() {
        handler.removeCallbacks(writeDisplay);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        try {
            bus.close();
        } catch (IOException e) {
            Log.e("TUT", I2C_ADDRESS + " bus slave "
                + HT16k33_SEGMENT_DISPLAY_SLAVE + "connection cannot be closed, you may experience errors on next launch.", e);
        }
        super.onDestroy();
    }
}
