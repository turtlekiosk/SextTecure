/** 
 * Copyright (C) 2011 Whisper Systems
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;

import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;

import ws.com.google.android.mms.pdu.PduPart;

public abstract class Slide {

  private   static final int MAX_CACHE_SIZE = 20;
  protected static final Map<Uri, SoftReference<Drawable>> thumbnailCache =
      Collections.synchronizedMap(new LRUCache<Uri, SoftReference<Drawable>>(MAX_CACHE_SIZE));

  protected final PduPart      part;
  protected final Context      context;
  protected       MasterSecret masterSecret;
	
  public Slide(Context context, PduPart part) {
    this.part    = part;
    this.context = context;
  }
	
  public Slide(Context context, MasterSecret masterSecret, PduPart part) {
    this(context, part);
    this.masterSecret = masterSecret;
  }
	
  public InputStream getPartDataInputStream() throws FileNotFoundException {
		return PartAuthority.getPartStream(context, masterSecret, part.getDataUri());
  }

  protected byte[] getPartData() {
    try {
      if (part.getData() != null)
        return part.getData();

      return Util.readFully(PartAuthority.getPartStream(context, masterSecret, part.getDataUri()));
    } catch (IOException e) {
      Log.w("Slide", e);
      return new byte[0];
    }
  }
	
  public String getContentType() {
    return new String(part.getContentType());
  }
	
  public Uri getUri() {
    return part.getDataUri();
  }
	
  public Drawable getThumbnail(int maxWidth, int maxHeight) {
    throw new AssertionError("getThumbnail() called on non-thumbnail producing slide!");
  }

  public void setThumbnailOn(ImageView imageView) {
    final long setBegin = System.currentTimeMillis();
    Drawable thumbnail = getCachedThumbnail();

    if (thumbnail != null) {
      Log.w("ImageSlide", "Setting cached thumbnail...");
      setThumbnailOn(imageView, thumbnail, true);
      return;
    }

    final ColorDrawable            temporaryDrawable = new ColorDrawable(Color.TRANSPARENT);
    final WeakReference<ImageView> weakImageView     = new WeakReference<ImageView>(imageView);
    final Handler                  handler           = new Handler();
    final int                      maxWidth          = imageView.getWidth();
    final int                      maxHeight         = imageView.getHeight();

    imageView.setImageDrawable(temporaryDrawable);

    if (maxWidth == 0 || maxHeight == 0)
      return;

    MmsDatabase.slideResolver.execute(new Runnable() {
      @Override
      public void run() {
        final long      startThumb  = System.currentTimeMillis();
        final Drawable  bitmap      = getThumbnail(maxWidth, maxHeight);
        final ImageView destination = weakImageView.get();

        if (destination != null && destination.getDrawable() == temporaryDrawable) {
          handler.post(new Runnable() {
            @Override
            public void run() {
              setThumbnailOn(destination, bitmap, false);
              long endThumb = System.currentTimeMillis();
              Log.w("Slide", "Thumbnail generation: " + (endThumb - startThumb) + " millis.");
              Log.w("Slide", "Full process: " + (endThumb - setBegin) + " millis.");
            }
          });
        }
      }
    });

//    imageView.setImageDrawable(getThumbnail(imageView.getWidth(), imageView.getHeight()));
  }

  public boolean hasImage() {
    return false;
  }
	
  public boolean hasVideo() {
    return false;
  }
	
  public boolean hasAudio() {
    return false;
  }
	
  public Bitmap getImage() {
    throw new AssertionError("getImage() called on non-image slide!");
  }
	
  public boolean hasText() {
    return false;
  }
	
  public String getText() {
    throw new AssertionError("getText() called on non-text slide!");
  }
	
  public PduPart getPart() {
    return part;
  }

  private void setThumbnailOn(ImageView imageView, Drawable thumbnail, boolean fromMemory) {
    if (fromMemory) {
      imageView.setImageDrawable(thumbnail);
    } else if (thumbnail instanceof AnimationDrawable) {
      imageView.setImageDrawable(thumbnail);
      ((AnimationDrawable)imageView.getDrawable()).start();
    } else {
      TransitionDrawable fadingResult = new TransitionDrawable(new Drawable[]{new ColorDrawable(Color.TRANSPARENT), thumbnail});
      imageView.setImageDrawable(fadingResult);
      fadingResult.startTransition(300);
    }
  }

  protected Drawable getCachedThumbnail() {
    synchronized (thumbnailCache) {
      SoftReference<Drawable> bitmapReference = thumbnailCache.get(part.getDataUri());
      Log.w("Slide", "Got soft reference: " + bitmapReference);

      if (bitmapReference != null) {
        Drawable bitmap = bitmapReference.get();
        Log.w("Slide", "Got cached bitmap: " + bitmap);
        if (bitmap != null) return bitmap;
        else                thumbnailCache.remove(part.getDataUri());
      }
    }

    return null;
  }

}
