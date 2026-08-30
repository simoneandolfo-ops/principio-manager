package it.principio.caller;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrincipioCallScreeningService extends CallScreeningService {
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    public void onScreenCall(Call.Details callDetails) {
        if (callDetails.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
            CallResponse response = new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build();
            respondToCall(callDetails, response);

            Uri handle = callDetails.getHandle();
            final String phone = handle != null ? handle.getSchemeSpecificPart() : "";
            if (phone != null && !phone.trim().isEmpty()) {
                io.execute(() -> {
                    try { ApiClient.sendIncoming(getApplicationContext(), phone.trim()); }
                    catch (Exception ignored) { }
                });
            }
        }
    }

    @Override
    public void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }
}
