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
      String phone = q.get(0).phone;
      try {
        ApiClient.sendIncoming(c, phone);
        Diagnostics.sent(c, phone);
        PendingCallStore.removeFirst(c);
      } catch (ApiClient.ApiException e) {
        Diagnostics.error(c, e.getClass().getSimpleName()+": "+e.getMessage());
        if (e.authError) return Result.success();
        return Result.retry();
      } catch (Exception e) {
        Diagnostics.error(c, e.getClass().getSimpleName()+": "+e.getMessage());
        return Result.retry();
      }
    }
  }
}
