package org.thoughtcrime.securesms.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

public abstract class ProgressAsyncTask extends AsyncTask<Void, Void, Void> {

  private final Context context;
  private final String  title;
  private final String  description;

  private ProgressDialog progressDialog;

  public ProgressAsyncTask(Context context, String title, String description) {
    this.context     = context;
    this.title       = title;
    this.description = description;
  }

  @Override
  protected void onPreExecute() {
    this.progressDialog = ProgressDialog.show(context, title, description, true);
  }

  @Override
  protected abstract Void doInBackground(Void... params);

  @Override
  protected void onPostExecute(Void result) {
    if (this.progressDialog != null)
      this.progressDialog.dismiss();
  }

}
