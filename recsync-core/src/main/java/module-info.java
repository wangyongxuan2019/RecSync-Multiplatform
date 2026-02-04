module com.recsync.core {
    requires javax.jmdns;
    requires org.slf4j;

    exports com.recsync.core.sync;
    exports com.recsync.core.transfer;
}