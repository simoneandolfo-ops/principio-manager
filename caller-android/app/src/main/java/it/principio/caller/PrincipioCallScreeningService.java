package it.principio.caller;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;

public class PrincipioCallScreeningService extends CallScreeningService {
    @Override
    public void onScreenCall(Call.Details callDetails) {
        if (callDetails.getCallDirection() != Call.Details.DIRECTION_INCOMING) return;

        Uri handle = callDetails.getHandle();
        String phone = handle != null ? handle.getSchemeSpecificPart() : "";

        try {
            if (phone != null && !phone.trim().isEmpty()) {
                // Prima salviamo localmente. Se Android chiude il processo subito dopo,
                // il numero resta comunque nella coda persistente.
                PendingCallStore.enqueue(getApplicationContext(), phone.trim());
                CallerUploadWorker.enqueue(getApplicationContext());
            }
        } catch (Exception e) {
            PendingCallStore.markError(getApplicationContext(), e.getMessage());
        } finally {
            // La telefonata deve sempre passare e la risposta al framework resta immediata.
            CallResponse response = new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build();
            respondToCall(callDetails, response);
        }
    }
}
