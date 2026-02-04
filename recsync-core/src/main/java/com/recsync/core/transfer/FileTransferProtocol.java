package com.recsync.core.transfer;

import java.io.Serializable;

public class FileTransferProtocol {

    public enum MessageType {
        UPLOAD_REQUEST,
        UPLOAD_ACCEPTED,
        UPLOAD_REJECTED,
        FILE_CHUNK,
        CHUNK_ACK,
        UPLOAD_COMPLETE,
        VERIFY_SUCCESS,
        VERIFY_FAILED,
        ERROR
    }

    public static class UploadRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        public String fileName;
        public long fileSize;
        public String fileMD5;
        public String deviceName;
        public long timestamp;

        public UploadRequest(String fileName, long fileSize, String md5, String device) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileMD5 = md5;
            this.deviceName = device;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("UploadRequest[%s, %.2fMB, device=%s]",
                    fileName, fileSize / 1024.0 / 1024.0, deviceName);
        }
    }

    public static class FileChunk implements Serializable {
        private static final long serialVersionUID = 1L;

        public int chunkIndex;
        public byte[] data;
        public int dataLength;

        public FileChunk(int index, byte[] data, int length) {
            this.chunkIndex = index;
            this.data = data;
            this.dataLength = length;
        }
    }

    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        public MessageType type;
        public String message;
        public Object data;

        public Response(MessageType type, String message) {
            this.type = type;
            this.message = message;
        }

        public Response(MessageType type, String message, Object data) {
            this.type = type;
            this.message = message;
            this.data = data;
        }
    }
}
