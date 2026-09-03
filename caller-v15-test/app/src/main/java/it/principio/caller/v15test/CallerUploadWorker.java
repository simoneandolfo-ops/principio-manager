package it.principio.caller.v15test;

import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.List;

public final class CallerUploadWorker extends Worker {
  public CallerUploadWorker(Context appContext, WorkerParameters params) { super(appContext, params); }
  @Override public Result doWork() {
    Context c = getApplicationContext();
    if (SessionStore.invalid(c)) return Result.success();
    while (true) {
      List<PendingCallStore.Item> q = PendingCallStore.all(c);
      if (q.isEmpty()) return Result.success();
      try {
        ApiClient.sendIncoming(c, q.get(0).phone);
        PendingCallStore.removeFirst(c);
      } catch (ApiClient.ApiException e) {
        if (e.authError) return Result.success();
        return Result.retry();
      } catch (Exception e) {
        return Result.retry();
      }
    }
  }
}
