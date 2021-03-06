package me.shaohui.shareutil;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.AccessToken;
import com.sina.weibo.sdk.api.share.IWeiboShareAPI;
import com.sina.weibo.sdk.api.share.WeiboShareSDK;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.WXAPIFactory;

import java.util.List;

import me.shaohui.shareutil.login.LoginListener;
import me.shaohui.shareutil.login.LoginPlatform;
import me.shaohui.shareutil.login.LoginResult;
import me.shaohui.shareutil.share.ShareImageObject;
import me.shaohui.shareutil.share.ShareListener;
import me.shaohui.shareutil.share.SharePlatform;
import me.shaohui.shareutil.share.instance.DefaultShareInstance;
import me.shaohui.shareutil.share.instance.FaceBookShareInstance;
import me.shaohui.shareutil.share.instance.QQShareInstance;
import me.shaohui.shareutil.share.instance.ShareInstance;
import me.shaohui.shareutil.share.instance.WeiboShareInstance;
import me.shaohui.shareutil.share.instance.WxShareInstance;

/**
 * Created by shaohui on 2016/11/18.
 */

public class ShareUtil {
    /**
     * 测试case
     * <p>
     * 1. 本地图片 vs 网络图片
     * 2. 图片大小限制
     * 3. 文字长度限制
     */

    public static final int TYPE = 798;
    //DelayedTime
    private static final int DELAYED_TIME = 500;
    public static ShareListener mShareListener;

    private static ShareInstance mShareInstance;

    final static int TYPE_IMAGE = 1;
    final static int TYPE_TEXT = 2;
    final static int TYPE_MEDIA = 3;

    private static int mType;
    private static int mPlatform;
    private static String mText;
    private static ShareImageObject mShareImageObject;
    private static String mTitle;
    private static String mSummary;
    private static String mTargetUrl;
    private static Context mContext;

    static void action(Activity activity) {
        mShareInstance = getShareInstance(mPlatform, activity);

        if (!mShareInstance.isInstall(activity)) {
            activity.finish();
            mShareListener.doShareFailure(new Exception("not install"));
            return;
        }

        switch (mType) {
            case TYPE_TEXT:
                mShareInstance.shareText(mPlatform, mText, activity, mShareListener);
                break;
            case TYPE_IMAGE:
                mShareInstance.shareImage(mPlatform, mShareImageObject, activity, mShareListener);
                break;
            case TYPE_MEDIA:
                mShareInstance.shareMedia(mPlatform, mTitle, mTargetUrl, mSummary,
                        mShareImageObject, activity, mShareListener);
                break;
        }

        // 默认系统分享没有回调，所以需要手动处理掉分享的activity
        if (mPlatform == SharePlatform.DEFAULT) {
            activity.finish();
        }
    }

    public static void shareText(Context context, @SharePlatform.Platform int platform, String text,
                                 ShareListener listener) {
        mType = TYPE_TEXT;
        mText = text;
        mPlatform = platform;
        mShareListener = listener;

        context.startActivity(_ShareActivity.newInstance(context, TYPE, platform));
    }

    public static void shareImage(Context context, @SharePlatform.Platform final int platform,
                                  final String urlOrPath, ShareListener listener) {
        mType = TYPE_IMAGE;
        mPlatform = platform;
        mShareImageObject = new ShareImageObject(urlOrPath);
        mShareListener = listener;

        context.startActivity(_ShareActivity.newInstance(context, TYPE, platform));
    }

    public static void shareImage(Context context, @SharePlatform.Platform final int platform,
                                  final Bitmap bitmap, ShareListener listener) {
        mType = TYPE_IMAGE;
        mPlatform = platform;
        mShareImageObject = new ShareImageObject(bitmap);
        mShareListener = listener;

        context.startActivity(_ShareActivity.newInstance(context, TYPE, platform));
    }

    public static void shareMedia(Context context, @SharePlatform.Platform int platform,
                                  String title, String summary, String targetUrl, Bitmap thumb, ShareListener listener) {
        mType = TYPE_MEDIA;
        mPlatform = platform;
        mShareImageObject = new ShareImageObject(thumb);
        mSummary = summary;
        mTargetUrl = targetUrl;
        mTitle = title;
        mShareListener = listener;

        context.startActivity(_ShareActivity.newInstance(context, TYPE, platform));
    }

    public static void shareMedia(Context context, @SharePlatform.Platform int platform,
                                  String title, String summary, String targetUrl, String thumbUrlOrPath,
                                  ShareListener listener) {
        mType = TYPE_MEDIA;
        mPlatform = platform;
        mShareImageObject = new ShareImageObject(thumbUrlOrPath);
        mSummary = summary;
        mTargetUrl = targetUrl;
        mTitle = title;
        mShareListener = listener;
        mContext = context;
        if (platform == SharePlatform.FACEBOOK && AccessToken.getCurrentAccessToken() == null) {
            LoginUtil.login(context, LoginPlatform.FACEBOOK, new FaceBookLoginListener());
            return;
        }

        context.startActivity(_ShareActivity.newInstance(context, TYPE, platform));
    }

    public static void handleResult(Intent data) {
        // 微博分享会同时回调onActivityResult和onNewIntent， 而且前者返回的intent为null
        if (mShareInstance != null && data != null) {
            ShareLog.i("catch result");
            mShareInstance.handleResult(data);
        } else {
            if (mShareInstance == null) {
                ShareLog.e("share instance is null");
            }
            if (data == null) {
                ShareLog.e("data is null");
            }
        }
    }

    public static void handleResult(final int requestCode,
                                    final int resultCode, final Intent data) {
        if (mShareInstance instanceof FaceBookShareInstance) {
            final FaceBookShareInstance fsInstance = (FaceBookShareInstance) mShareInstance;
            fsInstance.handleResult(requestCode, resultCode, data);
        }
    }

    private static ShareInstance getShareInstance(@SharePlatform.Platform int platform,
                                                  Context context) {
        switch (platform) {
            case SharePlatform.WX:
            case SharePlatform.WX_TIMELINE:
                return new WxShareInstance(context, ShareManager.CONFIG.getWxId());
            case SharePlatform.QQ:
            case SharePlatform.QZONE:
                return new QQShareInstance(context, ShareManager.CONFIG.getQqId());
            case SharePlatform.WEIBO:
                return new WeiboShareInstance(context, ShareManager.CONFIG.getWeiboId());
            case SharePlatform.FACEBOOK:
                return new FaceBookShareInstance();
            case SharePlatform.DEFAULT:
            default:
                return new DefaultShareInstance();
        }
    }

    public static void recycle() {
        mTitle = null;
        mSummary = null;
        mShareListener = null;

        // bitmap recycle
        if (mShareImageObject != null
                && mShareImageObject.getBitmap() != null
                && !mShareImageObject.getBitmap().isRecycled()) {
            mShareImageObject.getBitmap().recycle();
        }
        mShareImageObject = null;

        if (mShareInstance != null) {
            mShareInstance.recycle();
        }
        mShareInstance = null;
    }

    /**
     * 检查客户端是否安装
     */

    public static boolean isQQInstalled(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return false;
        }

        List<PackageInfo> packageInfos = pm.getInstalledPackages(0);
        for (PackageInfo info : packageInfos) {
            if (TextUtils.equals(info.packageName.toLowerCase(), "com.tencent.mobileqq")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWeiBoInstalled(@NonNull Context context) {
        IWeiboShareAPI shareAPI =
                WeiboShareSDK.createWeiboAPI(context, ShareManager.CONFIG.getWeiboId());
        return shareAPI.isWeiboAppInstalled();
    }

    public static boolean isWeiXinInstalled(Context context) {
        IWXAPI api = WXAPIFactory.createWXAPI(context, ShareManager.CONFIG.getWxId(), true);
        return api.isWXAppInstalled();
    }

    public static boolean isFaceBookInstalled(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return false;
        }

        List<PackageInfo> packageInfos = pm.getInstalledPackages(0);
        for (PackageInfo info : packageInfos) {
            if (TextUtils.equals(info.packageName.toLowerCase(), "com.facebook.katana")) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     */
    private static class FaceBookLoginListener extends LoginListener {

        @Override
        public void loginSuccess(LoginResult result) {
            if (mContext != null) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mContext.startActivity(_ShareActivity.newInstance(mContext, TYPE, mPlatform));
                    }
                }, DELAYED_TIME);

            }
        }

        @Override
        public void loginFailure(Exception e) {

        }

        @Override
        public void loginCancel() {

        }
    }


}
