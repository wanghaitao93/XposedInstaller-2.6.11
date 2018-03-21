package de.robv.android.xposed.installer;

import android.app.Activity;
import android.os.Bundle;

/**
 * Created by eric on 2018/3/21.
 */

public class CustomActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        System.out.println("CustomActivity ------------------------------ Create ");
        int cnt = 1;
        while(true)
        {
            System.out.println(cnt++);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        System.out.println("CustomActivity ------------------------------ Start ");

    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        System.out.println("CustomActivity ------------------------------ Stop ");

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.out.println("CustomActivity ------------------------------ Destory ");
    }
}
