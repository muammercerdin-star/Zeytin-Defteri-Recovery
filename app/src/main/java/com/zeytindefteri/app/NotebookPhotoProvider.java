package com.zeytindefteri.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * "Defterden oku" için tek dosyalık paylaşım: kamera uygulaması çektiği fotoğrafı uygulamanın önbelleğindeki
 * notebook/photo.jpg dosyasına yazar. Başka hiçbir dosya verilmez; dışa açık değildir (yalnız geçici izinle).
 * Fotoğraf okunduktan sonra silinir.
 */
public final class NotebookPhotoProvider extends ContentProvider {
    static final String NAME = "photo.jpg";

    static String authority(Context c) { return c.getPackageName() + ".notebookphoto"; }

    static Uri uri(Context c) { return Uri.parse("content://" + authority(c) + "/" + NAME); }

    static File file(Context c) {
        File dir = new File(c.getCacheDir(), "notebook");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, NAME);
    }

    static void clear(Context c) {
        File f = file(c);
        if (f.exists()) f.delete();
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!NAME.equals(uri.getLastPathSegment())) throw new FileNotFoundException("yok");
        File f = file(getContext());
        int m = ParcelFileDescriptor.parseMode(mode == null ? "r" : mode);
        if ((m & ParcelFileDescriptor.MODE_WRITE_ONLY) != 0 || (m & ParcelFileDescriptor.MODE_READ_WRITE) != 0) {
            m |= ParcelFileDescriptor.MODE_CREATE;
        }
        return ParcelFileDescriptor.open(f, m);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        File f = file(getContext());
        c.addRow(new Object[]{NAME, f.exists() ? f.length() : 0L});
        return c;
    }

    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
