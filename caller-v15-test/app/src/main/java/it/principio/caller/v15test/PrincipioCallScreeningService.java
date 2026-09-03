package it.principio.caller.v15test;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public final class PrincipioCallScreeningService extends CallScreeningService {
  @Override public void onScreenCall(Call.Details details) {
    CallResponse response = new CallResponse.Builder()
        .setDisallowCall(false).setRejectCall(false).setSilenceCall(false)
        .setSkipCallLog(false).setSkipNotification(false).build();
    respondToCall(details,response);
    if(details.getCallDirection()!=Call.Details.DIRECTION_INCOMING) return;
    Uri h=details.getHandle();
    String phone=h==null?"":h.getSchemeSpecificPart();
    if(phone==null||phone.trim().isEmpty()) return;
    PendingCallStore.add(getApplicationContext(),phone.trim());
    Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    OneTimeWorkRequest req=new OneTimeWorkRequest.Builder(CallerUploadWorker.class).setConstraints(constraints).build();
    WorkManager.getInstance(getApplicationContext()).enqueue(req);
  }
}
