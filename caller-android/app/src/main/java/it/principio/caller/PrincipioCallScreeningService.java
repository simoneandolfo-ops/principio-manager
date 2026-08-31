package it.principio.caller;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrincipioCallScreeningService extends CallScreeningService {
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();

    @Override
    public void onScreenCall(Call.Details callDetails) {
        if (callDetails.getCallDirection() != Call.Details.DIRECTION_INCOMING) return;

        Uri handle = callDetails.getHandle();
        String phone = handle != null ? handle.getSchemeSpecificPart() : "";

        PendingCallStore.Item item = null;
        if (phone != null && !phone.trim().isEmpty()) {
            item = PendingCallStore.enqueue(getApplicationContext(), phone.trim());
        }

        // Come nella prima V1 che sul telefono funzionava: rispondiamo subito ad Android.
        // Nessuna attesa di rete dentro onScreenCall().
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build();
        respondToCall(callDetails, response);

        if (item == null) return;
        final PendingCallStore.Item queued = item;
        NETWORK.execute(() -> {
            try {
                // Stessi timeout della V1 originale: 3.5s connessione / 5s lettura.
                ApiClient.sendIncomingStable(getApplicationContext(), queued.phone, queued.id);
                PendingCallStore.markSent(getApplicationContext(), queued.id, queued.phone);
            } catch (Exception e) {
                PendingCallStore.markError(getApplicationContext(), errorText(e));
                // Solo rete di sicurezza: la chiamata resta già salvata localmente.
                CallerUploadWorker.enqueue(getApplicationContext());
            }
        });
    }

    private static String errorText(Throwable e) {
        if (e == null) return "Errore invio sconosciuto";
        String name = e.getClass().getSimpleName();
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) return name;
        return name + ": " + msg.trim();
    }
}
