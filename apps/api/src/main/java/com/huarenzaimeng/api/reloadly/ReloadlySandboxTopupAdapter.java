package com.huarenzaimeng.api.reloadly;

/**
 * Sandbox adapter boundary. The candidate deliberately contains no credential loader,
 * HTTP client, URI, or Spring bean; a later explicitly authorized transport may implement it.
 */
final class ReloadlySandboxTopupAdapter implements ReloadlySandboxTopupPort {
    private final Transport transport;

    ReloadlySandboxTopupAdapter(Transport transport) { this.transport = transport; }

    @Override public CreateResult create(String requestRef) {
        if (requestRef == null || !requestRef.matches("RLD-A-[A-Z0-9-]{8,40}"))
            return new Rejected("REQUEST_REF_INVALID");
        CreateResult result = transport.create(new CreateCommand(
                requestRef, 1100, "AE", "0503971821", "CA", "11231231231"));
        return result == null ? new Unknown("PROVIDER_RESULT_UNKNOWN_NO_RETRY") : result;
    }

    @Override public StatusResult status(long providerTransactionId) {
        if (providerTransactionId <= 0) return new StatusUnknown("PROVIDER_REFERENCE_INVALID");
        StatusResult result = transport.status(providerTransactionId);
        return result == null ? new StatusUnknown("STATUS_RESULT_UNKNOWN_NO_RETRY") : result;
    }

    interface Transport {
        CreateResult create(CreateCommand command);
        StatusResult status(long providerTransactionId);
    }

    record CreateCommand(String requestRef, int operatorId, String recipientCountry,
                         String recipientNumber, String senderCountry, String senderNumber) {}
}

