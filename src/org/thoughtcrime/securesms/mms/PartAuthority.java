package org.thoughtcrime.securesms.mms;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class PartAuthority {

  private static final String PART_URI_STRING   = "content://org.thoughtcrime.securesms/part";
  private static final String THUMB_URI_STRING  = "content://org.thoughtcrime.securesms/thumb";

  public  static final Uri    PART_CONTENT_URI  = Uri.parse(PART_URI_STRING);
  public  static final Uri    THUMB_CONTENT_URI = Uri.parse(THUMB_URI_STRING);

  private static final int PART_ROW  = 1;
  private static final int THUMB_ROW = 2;

  private static final UriMatcher uriMatcher;

  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI("org.thoughtcrime.securesms", "part/#", PART_ROW);
    uriMatcher.addURI("org.thoughtcrime.securesms", "thumb/#", THUMB_ROW);
  }

  public static InputStream getPartStream(Context context, MasterSecret masterSecret, Uri uri)
      throws FileNotFoundException
  {
    PartDatabase partDatabase = DatabaseFactory.getPartDatabase(context);
    long         partId       = ContentUris.parseId(uri);
    int          match        = uriMatcher.match(uri);

    switch (match) {
      case PART_ROW:  return partDatabase.getPartStream(masterSecret, partId);
      case THUMB_ROW: return partDatabase.getThumbnailStream(masterSecret, partId);
      default:        return context.getContentResolver().openInputStream(uri);
    }
  }

  public static boolean isLocal(Uri uri) {
    int match = uriMatcher.match(uri);
    switch (match) {
      case PART_ROW:
      case THUMB_ROW:
        return true;
      default:
        return false;
    }
  }

}
