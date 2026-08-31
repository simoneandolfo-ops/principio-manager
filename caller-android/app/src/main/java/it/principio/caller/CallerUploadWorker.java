package it.principio.caller;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CallerUploadWorker extends Worker {
    private static final String UNIQUE_WORK = "principio-caller-upload-queue";

    public CallerUploadWorker(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (ApiClient.getManagerUrl(c).isEmpty() || ApiClient.getToken(c).isEmpty()) {
            PendingCallStore.markError(c, "Manager non configurato");
            return Result.retry();
        }

        List<PendingCallStore.Item> items = PendingCallStore.all(c);
        for (PendingCallStore.Item item : items) {
            try {
                ApiClient.sendIncoming(c, item.phone, item.id);
                PendingCallStore.markSent(c, item.id, item.phone);
            } catch (Exception e) {
                PendingCallStore.markError(c, e.getMessage());
                return Result.retry();
            }
        }
        return Result.success();
    }

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CallerUploadWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request);
    }
}
