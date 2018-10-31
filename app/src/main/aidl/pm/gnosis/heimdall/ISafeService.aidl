package pm.gnosis.heimdall;

import pm.gnosis.heimdall.ISafeServiceCallback;

interface ISafeService {
    String getCurrentSafe();
    String sendTransaction(String to, String value, String data);
    void registerCallback(ISafeServiceCallback callback);
    void unregisterCallback(ISafeServiceCallback callback);
}
