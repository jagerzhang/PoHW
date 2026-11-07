package com.jager.tvquiz;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 把应用私有目录里已校验的 APK 以 content:// 暴露给系统安装器。
 *
 * <p>项目零 Gradle / 零 AndroidX 依赖，不能用 FileProvider，这里用平台 API 实现
 * 最小版本：仅接受 /apk/&lt;文件名&gt;，拒绝目录穿越，只读，不暴露任何查询能力。
 */
public final class UpdateProvider extends ContentProvider {

    public static final String AUTHORITY = "com.jager.tvquiz.update";

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read only");
        File dir = UpdateInstaller.apkDir(getContext());
        String name = uri.getLastPathSegment();
        if (name == null || !name.endsWith(".apk")) {
            throw new FileNotFoundException("bad name");
        }
        File f = new File(dir, name);
        try {
            File parent = f.getParentFile();
            if (parent == null || !dir.getCanonicalPath().equals(parent.getCanonicalPath())) {
                throw new FileNotFoundException("path escape");
            }
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new FileNotFoundException("path check failed");
        }
        if (!f.isFile()) throw new FileNotFoundException("missing apk");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }

    @Override public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}
