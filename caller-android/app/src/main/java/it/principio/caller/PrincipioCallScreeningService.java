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

        if (phone != null && !phone.trim().isEmpty()) {
            PendingCallStore.Item item = PendingCallStore.enqueue(getApplicationContext(), phone.trim());
            try {
                // Tentativo principale mentre Android ha sicuramente svegliato il CallScreeningService.
                // Timeout molto corto: restiamo ampiamente entro il limite di risposta della chiamata.
                ApiClient.sendIncomingFast(getApplicationContext(), item.phone, item.id);
                PendingCallStore.markSent(getApplicationContext(), item.id, item.phone);
            } catch (Exception e) {
                PendingCallStore.markError(getApplicationContext(), e.getMessage());
                // Rete di sicurezza: la coda persistente resta e WorkManager ritenterà.
                CallerUploadWorker.enqueue(getApplicationContext());
            }
        }

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
