package pm.gnosis.heimdall;

oneway interface ISafeServiceCallback {
    void transactionSubmitted(String id, String txHash);
    void transactionRejected(String id);
}
