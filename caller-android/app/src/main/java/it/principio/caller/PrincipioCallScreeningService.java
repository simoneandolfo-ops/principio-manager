package it.principio.caller;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PrincipioCallScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details callDetails) {
        if (callDetails.getCallDirection() != Call.Details.DIRECTION_INCOMING) return;

        Uri handle = callDetails.getHandle();
        String phone = handle != null ? handle.getSchemeSpecificPart() : "";

        if (phone != null && !phone.trim().isEmpty()) {
            final PendingCallStore.Item item = PendingCallStore.enqueue(getApplicationContext(), phone.trim());
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Boolean> future = executor.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    ApiClient.sendIncomingFast(getApplicationContext(), item.phone, item.id);
                    PendingCallStore.markSent(getApplicationContext(), item.id, item.phone);
                    return true;
                }
            });

            try {
                // Aruba può impiegare oltre 1 secondo tra connessione e handshake TLS.
                // Restiamo comunque sotto il limite Android di 5 secondi per CallScreeningService.
                future.get(4200, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                PendingCallStore.markError(getApplicationContext(), "Timeout invio diretto dopo 4.2s");
                CallerUploadWorker.enqueue(getApplicationContext());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                PendingCallStore.markError(getApplicationContext(), errorText(cause));
                CallerUploadWorker.enqueue(getApplicationContext());
            } catch (Exception e) {
                PendingCallStore.markError(getApplicationContext(), errorText(e));
                CallerUploadWorker.enqueue(getApplicationContext());
            } finally {
                executor.shutdownNow();
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

    private static String errorText(Throwable e) {
        if (e == null) return "Errore invio sconosciuto";
        String name = e.getClass().getSimpleName();
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) return name;
        return name + ": " + msg.trim();
    }
}
