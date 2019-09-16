package cn.szscinfo.j1mp;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import androidx.core.content.FileProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class UpdateManager {

    private Context mContext;

    // 提示语
    private String updateMsg = "有最新的软件包哦，亲快下载吧~";

    // 返回的安装包url
    private String apkUrl = "http://oa.j1mp.cn/app-debug.apk";

    private Dialog noticeDialog;

    private Dialog downloadDialog;
    /* 下载包安装路径 */
    // private static final String savePath = "/data/cache/";

    // private static final String saveFileName = savePath + "j1mp.apk";

    private String savePath = "";
    private String saveFileName = "";

    /* 进度条与通知ui刷新的handler和msg常量 */
    private ProgressBar mProgress;

    private static final int DOWN_UPDATE = 1;

    private static final int DOWN_OVER = 2;

    private int progress;

    private Thread downLoadThread;

    private boolean interceptFlag = false;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DOWN_UPDATE:
                    mProgress.setProgress(progress);
                    break;
                case DOWN_OVER:
                    installApk();
                    break;
                default:
                    break;
            }
        }

        ;
    };

    public UpdateManager(Context context) {
        this.mContext = context;
    }

    //外部接口让主Activity调用
    public void checkUpdateInfo222() {
        String ver1, ver2;
        new Thread(new Runnable(){
            @Override
            public void run() {

                String ver1 = PackageUtils.getVersionName(mContext);

                System.out.println("ver1 = " + ver1);

                String ver2 = getVersion();

                System.out.println("ver2 = " + ver2);

                if (ver1.equals(ver2)) {
                    return;
                }

                showNoticeDialog();
            }
        }).start();

    }

    public void checkUpdateInfo() {

        savePath = "/storage/emulated/0";

        // Environment.getDownloadCacheDirectory()
        saveFileName = savePath + "/j1mp.apk";

        String ver1 = PackageUtils.getVersionName(mContext);

        System.out.println("ver1 = " + ver1);

        String ver2 = getVersion();

        System.out.println("ver2 = " + ver2);

        if (ver1.equals(ver2)) {
            return;
        }

        this.apkUrl = getDownloadUrl();

        showNoticeDialog();
    }

    private String getDownloadUrl() {
        try {
            JSONObject json = json();

            return json.getString("url");
        } catch (JSONException jsone) {
            jsone.printStackTrace();
        }

        return "";
    }

    public void test() {
        // savePath = Environment.getDownloadCacheDirectory().toString();

        savePath = "/storage/emulated/0";

        // Environment.getDownloadCacheDirectory()
        saveFileName = savePath + "/j1mp.apk";


        String ver1 = PackageUtils.getVersionName(mContext);

        System.out.println("ver1 = " + ver1);

        String ver2 = getVersion();

        System.out.println("ver2 = " + ver2);

//        int ret = PackageUtils.compareVersion(ver1, ver2);
//
//        System.out.println("ret = " + ret);

        // showNoticeDialog();
    }

    /**
     * 获取最新版本信息
     *
     * @return String
     * @throws IOException
     * @throws JSONException
     */
    public String getVersion() {
        try {
            JSONObject json = json();

            return json.getString("version");
        } catch (JSONException jsone) {
            jsone.printStackTrace();
        }

        return "";
    }

    public JSONObject json() throws JSONException {

        BufferedReader reader = null;
        String result = null;
        StringBuffer sbf = new StringBuffer();

        try {
            String path = "http://oa.j1mp.cn/update.txt";
            URL url = new URL(path);

            System.out.println(url.toString());

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // conn.connect();
            conn.setConnectTimeout(5000);
            conn.setRequestMethod("GET");

            //

            int code = conn.getResponseCode();
            if (code == 200) {
                InputStream is = conn.getInputStream();

                reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String strRead = null;
                while ((strRead = reader.readLine()) != null) {
                    sbf.append(strRead);
                    sbf.append("\r\n");
                }

                reader.close();
                result = sbf.toString();

                System.out.println(result);

                is.close();
                conn.disconnect();

                //对json数据进行解析
                return new JSONObject(result);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch(IOException io) {
            io.printStackTrace();
        }

        return new JSONObject();
    }

    private void showNoticeDialog() {
        AlertDialog.Builder builder = new Builder(mContext);
        builder.setTitle("软件版本更新");
        builder.setMessage(updateMsg);
        builder.setPositiveButton("下载", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                showDownloadDialog();
            }
        });
        builder.setNegativeButton("以后再说", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        noticeDialog = builder.create();
        noticeDialog.show();
    }

    private void showDownloadDialog() {
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.progress, null);
        mProgress = (ProgressBar) view.findViewById(R.id.progress);

        AlertDialog.Builder builder = new Builder(mContext);
        builder.setTitle("软件版本更新");

        builder.setView(view);
        builder.setNegativeButton("取消", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                interceptFlag = true;
            }
        });

        downloadDialog = builder.create();
        downloadDialog.show();

        downloadApk();
    }

    private Runnable mdownApkRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                URL url = new URL(apkUrl);

                System.out.println(url.toString());

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                int length = conn.getContentLength();
                InputStream is = conn.getInputStream();

                System.out.println(savePath);

                File file = new File(savePath);
                if (!file.exists()) {
                    file.mkdir();
                }

                System.out.println(saveFileName);

                String apkFile = saveFileName;
                File ApkFile = new File(apkFile);
                FileOutputStream fos = new FileOutputStream(ApkFile);

                int count = 0;
                byte buf[] = new byte[1024];

                do {
                    int numread = is.read(buf);
                    count += numread;

                    progress = (int) (((float) count / length) * 100);
                    // 更新进度
                    mHandler.sendEmptyMessage(DOWN_UPDATE);
                    if (numread <= 0) {
                        //下载完成通知安装
                        mHandler.sendEmptyMessage(DOWN_OVER);
                        break;
                    }
                    fos.write(buf, 0, numread);
                } while (!interceptFlag);//点击取消就停止下载.

                fos.close();
                is.close();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    };

    /**
     * 下载apk
     *
     * @param url
     */

    private void downloadApk() {
        downLoadThread = new Thread(mdownApkRunnable);
        downLoadThread.start();
    }

    private void installApk() {
        File apkfile = new File(saveFileName);
        if (!apkfile.exists()) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //安装完成后自动打开

        // 判断版本大于等于7.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Uri apkUri = FileProvider.getUriForFile(mContext, "cn.szscinfo.j1mp.fileprovider", apkfile);
                //Granting Temporary Permissions to a URI
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);//对目标应用临时授权该Uri所代表的文件
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            intent.setDataAndType(Uri.fromFile(apkfile), "application/vnd.android.package-archive");
        }

        mContext.startActivity(intent);
    }
}
