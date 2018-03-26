package de.robv.android.xposed.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.RootUtil;
import de.robv.android.xposed.installer.util.ThemeUtil;

/**
 * Created by eric on 2018/3/21.
 */

public class CustomActivity extends Activity {

    private String APP_PROCESS_NAME = null;
    private final String BINARIES_FOLDER = AssetUtil.getBinariesFolder();
    private static final String JAR_PATH = XposedApp.BASE_DIR + "bin/XposedBridge.jar";
    private static final String JAR_PATH_NEWVERSION = JAR_PATH + ".newversion";
    private final LinkedList<String> mCompatibilityErrors = new LinkedList<String>();
    private RootUtil mRootUtil = new RootUtil();
    private boolean mHadSegmentationFault = false;

    private static final String PREF_LAST_SEEN_BINARY = "last_seen_binary";
    private int appProcessInstalledVersion;

    private ProgressDialog dlgProgress;

    private ModuleUtil mModuleUtil;

    private static final int INSTALL_MODE_NORMAL = 0;
    private static final int INSTALL_MODE_RECOVERY_AUTO = 1;
    private static final int INSTALL_MODE_RECOVERY_MANUAL = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        System.out.println("CustomActivity ------------------------------ Create ");

        dlgProgress = new ProgressDialog(this);
        dlgProgress.setIndeterminate(true);

        boolean isCompatible = false;
        if (BINARIES_FOLDER == null) {
            // incompatible processor architecture
        } else if (Build.VERSION.SDK_INT == 15) {
            APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk15";
            isCompatible = checkCompatibility();

        } else if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT <= 19) {
            APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk16";
            isCompatible = checkCompatibility();

        } else if (Build.VERSION.SDK_INT > 19) {
            APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk16";
            isCompatible = checkCompatibility();
            if (isCompatible) {
//                txtInstallError.setText(String.format(getString(R.string.not_tested_but_compatible), Build.VERSION.SDK_INT));
//                txtInstallError.setVisibility(View.VISIBLE);
            }
        }

        final boolean success = install();
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (success)
                    ModuleUtil.getInstance().updateModulesList(false);

                // Start tracking the last seen version, irrespective of the installation method and the outcome.
                // 0 or a stale version might be registered, if a recovery installation was requested
                // It will get up to date when the last seen version is updated on a later panel startup
                XposedApp.getPreferences().edit().putInt(PREF_LAST_SEEN_BINARY, appProcessInstalledVersion).commit();
                // Dismiss any warning already being displayed
                //
//              getView().findViewById(R.id.install_reverted_warning).setVisibility(View.GONE);
            }
        });
    }

    private boolean install() {
        final int installMode = getInstallMode();

        if (!startShell())
            return false;

        List<String> messages = new LinkedList<String>();
        boolean showAlert = true;
        try {
            messages.add(getString(R.string.sdcard_location, XposedApp.getInstance().getExternalFilesDir(null)));
            messages.add("");

            messages.add(getString(R.string.file_copying, "Xposed-Disabler-Recovery.zip"));
            if (AssetUtil.writeAssetToSdcardFile("Xposed-Disabler-Recovery.zip", 00644) == null) {
                messages.add("");
                messages.add(getString(R.string.file_extract_failed, "Xposed-Disabler-Recovery.zip"));
                return false;
            }

            File appProcessFile = AssetUtil.writeAssetToFile(APP_PROCESS_NAME, new File(XposedApp.BASE_DIR + "bin/app_process"), 00700);
            if (appProcessFile == null) {
                showAlert(getString(R.string.file_extract_failed, "app_process"));
                return false;
            }

            if (installMode == INSTALL_MODE_NORMAL) {
                // Normal installation
                messages.add(getString(R.string.file_mounting_writable, "/system"));
                if (mRootUtil.executeWithBusybox("mount -o remount,rw /system", messages) != 0) {
                    messages.add(getString(R.string.file_mount_writable_failed, "/system"));
                    messages.add(getString(R.string.file_trying_to_continue));
                }

                if (new File("/system/bin/app_process.orig").exists()) {
                    messages.add(getString(R.string.file_backup_already_exists, "/system/bin/app_process.orig"));
                } else {
                    if (mRootUtil.executeWithBusybox("cp -a /system/bin/app_process /system/bin/app_process.orig", messages) != 0) {
                        messages.add("");
                        messages.add(getString(R.string.file_backup_failed, "/system/bin/app_process"));
                        return false;
                    } else {
                        messages.add(getString(R.string.file_backup_successful, "/system/bin/app_process.orig"));
                    }

                    mRootUtil.executeWithBusybox("sync", messages);
                }

                messages.add(getString(R.string.file_copying, "app_process"));
                if (mRootUtil.executeWithBusybox("cp -a " + appProcessFile.getAbsolutePath() + " /system/bin/app_process", messages) != 0) {
                    messages.add("");
                    messages.add(getString(R.string.file_copy_failed, "app_process", "/system/bin"));
                    return false;
                }
                if (mRootUtil.executeWithBusybox("chmod 755 /system/bin/app_process", messages) != 0) {
                    messages.add("");
                    messages.add(getString(R.string.file_set_perms_failed, "/system/bin/app_process"));
                    return false;
                }
                if (mRootUtil.executeWithBusybox("chown root:shell /system/bin/app_process", messages) != 0) {
                    messages.add("");
                    messages.add(getString(R.string.file_set_owner_failed, "/system/bin/app_process"));
                    return false;
                }

            } else if (installMode == INSTALL_MODE_RECOVERY_AUTO) {
                if (!prepareAutoFlash(messages, "Xposed-Installer-Recovery.zip"))
                    return false;

            } else if (installMode == INSTALL_MODE_RECOVERY_MANUAL) {
                if (!prepareManualFlash(messages, "Xposed-Installer-Recovery.zip"))
                    return false;
            }

            File blocker = new File(XposedApp.BASE_DIR + "conf/disabled");
            if (blocker.exists()) {
                messages.add(getString(R.string.file_removing, blocker.getAbsolutePath()));
                if (mRootUtil.executeWithBusybox("rm " + blocker.getAbsolutePath(), messages) != 0) {
                    messages.add("");
                    messages.add(getString(R.string.file_remove_failed, blocker.getAbsolutePath()));
                    return false;
                }
            }

            messages.add(getString(R.string.file_copying, "XposedBridge.jar"));
            File jarFile = AssetUtil.writeAssetToFile("XposedBridge.jar", new File(JAR_PATH_NEWVERSION), 00644);
            if (jarFile == null) {
                messages.add("");
                messages.add(getString(R.string.file_extract_failed, "XposedBridge.jar"));
                return false;
            }

            mRootUtil.executeWithBusybox("sync", messages);

            showAlert = false;
            messages.add("");
            if (installMode == INSTALL_MODE_NORMAL) {
                mModuleUtil = ModuleUtil.getInstance();

                String packageName = "com.example.eric.myapplication";
                mModuleUtil.setModuleEnabled(packageName, true);
                mModuleUtil.updateModulesList(true);

                offerReboot(new LinkedList<String>());
            }else
                offerRebootToRecovery(messages, "Xposed-Installer-Recovery.zip", installMode);

            return true;

        } finally {
            AssetUtil.removeBusybox();

            if (showAlert)
                showAlert(TextUtils.join("\n", messages).trim());
        }
    }

    private int getInstallMode() {
        int mode = XposedApp.getPreferences().getInt("install_mode", INSTALL_MODE_NORMAL);
        if (mode < INSTALL_MODE_NORMAL || mode > INSTALL_MODE_RECOVERY_MANUAL)
            mode = INSTALL_MODE_NORMAL;
        return mode;
    }

    private boolean startShell() {
        if (mRootUtil.startShell())
            return true;

        showAlert(getString(R.string.root_failed));
        return false;
    }

    private void showAlert(final String result) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showAlert(result);
                }
            });
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showAlert(result);
                }
            });
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(result)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.show();
        TextView txtMessage = (TextView) dialog.findViewById(android.R.id.message);
        txtMessage.setTextSize(14);

        mHadSegmentationFault = result.toLowerCase(Locale.US).contains("segmentation fault");
    }

    private boolean prepareAutoFlash(List<String> messages, String file) {
        if (mRootUtil.execute("ls /cache/recovery", null) != 0) {
            messages.add(getString(R.string.file_creating_directory, "/cache/recovery"));
            if (mRootUtil.executeWithBusybox("mkdir /cache/recovery", messages) != 0) {
                messages.add("");
                messages.add(getString(R.string.file_create_directory_failed, "/cache/recovery"));
                return false;
            }
        }
        messages.add(getString(R.string.file_copying, file));
        File tempFile = AssetUtil.writeAssetToCacheFile(file, 00644);
        if (tempFile == null) {
            messages.add("");
            messages.add(getString(R.string.file_extract_failed, file));
            return false;
        }

        if (mRootUtil.executeWithBusybox("cp -a " + tempFile.getAbsolutePath() + " /cache/recovery/" + file, messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.file_copy_failed, file, "/cache"));
            tempFile.delete();
            return false;
        }

        tempFile.delete();

        messages.add(getString(R.string.file_writing_recovery_command));
        if (mRootUtil.execute("echo \"--update_package=/cache/recovery/" + file + "\n--show_text\" > /cache/recovery/command", messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.file_writing_recovery_command_failed));
            return false;
        }
        return true;
    }

    private boolean prepareManualFlash(List<String> messages, String file) {
        messages.add(getString(R.string.file_copying, file));
        if (AssetUtil.writeAssetToSdcardFile(file, 00644) == null) {
            messages.add("");
            messages.add(getString(R.string.file_extract_failed, file));
            return false;
        }
        return true;
    }

    private void offerReboot(List<String> messages) {
        messages.add(getString(R.string.reboot_confirmation));
        showConfirmDialog(TextUtils.join("\n", messages).trim(),
                new AsyncDialogClickListener(getString(R.string.reboot)) {
                    @Override
                    protected void onAsyncClick(DialogInterface dialog, int which) {
                        reboot(null);
                    }
                }, null);
    }

    private void showConfirmDialog(final String message, final DialogInterface.OnClickListener yesHandler,
                                   final DialogInterface.OnClickListener noHandler) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
//            getActivity().runOnUiThread(new Runnable() {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showConfirmDialog(message, yesHandler, noHandler);
                }
            });
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, yesHandler)
                .setNegativeButton(android.R.string.no, noHandler)
                .create();
        dialog.show();
        TextView txtMessage = (TextView) dialog.findViewById(android.R.id.message);
        txtMessage.setTextSize(14);

        mHadSegmentationFault = message.toLowerCase(Locale.US).contains("segmentation fault");
    }

    private abstract class AsyncDialogClickListener implements DialogInterface.OnClickListener {
        private final CharSequence mProgressDlgText;

        public AsyncDialogClickListener(CharSequence progressDlgText) {
            mProgressDlgText = progressDlgText;
        }

        @Override
        public void onClick(final DialogInterface dialog, final int which) {
            if (mProgressDlgText != null) {
                dlgProgress.setMessage(mProgressDlgText);
                dlgProgress.show();
            }
            new Thread() {
                public void run() {
                    onAsyncClick(dialog, which);
                    dlgProgress.dismiss();
                }
            }.start();
        }

        protected abstract void onAsyncClick(DialogInterface dialog, int which);
    }

    private void offerRebootToRecovery(List<String> messages, final String file, final int installMode) {
        if (installMode == INSTALL_MODE_RECOVERY_AUTO)
            messages.add(getString(R.string.auto_flash_note, file));
        else
            messages.add(getString(R.string.manual_flash_note, file));

        messages.add("");
        messages.add(getString(R.string.reboot_recovery_confirmation));
        showConfirmDialog(TextUtils.join("\n", messages).trim(),
                new AsyncDialogClickListener(getString(R.string.reboot)) {
                    @Override
                    protected void onAsyncClick(DialogInterface dialog, int which) {
                        reboot("recovery");
                    }
                },
                new AsyncDialogClickListener(null) {
                    @Override
                    protected void onAsyncClick(DialogInterface dialog, int which) {
                        if (installMode == INSTALL_MODE_RECOVERY_AUTO) {
                            // clean up to avoid unwanted flashing
                            mRootUtil.executeWithBusybox("rm /cache/recovery/command", null);
                            mRootUtil.executeWithBusybox("rm /cache/recovery/" + file, null);
                            AssetUtil.removeBusybox();
                        }
                    }
                });
    }

    private void reboot(String mode) {
        if (!startShell())
            return;

        List<String> messages = new LinkedList<String>();

        String command = "reboot";
        if (mode != null) {
            command += " " + mode;
            if (mode.equals("recovery"))
                // create a flag used by some kernels to boot into recovery
                mRootUtil.executeWithBusybox("touch /cache/recovery/boot", messages);
        }

        if (mRootUtil.executeWithBusybox(command, messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.reboot_failed));
            showAlert(TextUtils.join("\n", messages).trim());
        }
        AssetUtil.removeBusybox();
    }

    private boolean checkCompatibility() {
        mCompatibilityErrors.clear();
        return checkAppProcessCompatibility();
    }


    private boolean checkAppProcessCompatibility() {
        try {
            if (APP_PROCESS_NAME == null)
                return false;

            File testFile = AssetUtil.writeAssetToCacheFile(APP_PROCESS_NAME, "app_process", 00700);
            if (testFile == null) {
                mCompatibilityErrors.add("could not write app_process to cache");
                return false;
            }

            Process p = Runtime.getRuntime().exec(new String[] { testFile.getAbsolutePath(), "--xposedversion" });

            BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String result = stdout.readLine();
            stdout.close();

            BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String errorLine;
            while ((errorLine = stderr.readLine()) != null) {
                mCompatibilityErrors.add(errorLine);
            }
            stderr.close();

            p.destroy();

            testFile.delete();
            return result != null && result.startsWith("Xposed version: ");
        } catch (IOException e) {
            mCompatibilityErrors.add(e.getMessage());
            return false;
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
