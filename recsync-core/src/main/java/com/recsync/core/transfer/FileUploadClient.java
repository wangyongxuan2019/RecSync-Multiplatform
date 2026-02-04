package com.recsync.core.transfer;

import com.recsync.core.sync.SyncConstants;
import com.recsync.core.transfer.FileTransferProtocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class FileUploadClient {
    private static final Logger logger = LoggerFactory.getLogger(FileUploadClient.class);

    private final String leaderIP;
    private final int leaderPort;
    private final String deviceName;
    private UploadProgressListener progressListener;

    public interface UploadProgressListener {
        void onUploadStarted(String fileName);
        void onUploadProgress(long bytesUploaded, long totalBytes, double percentage);
        void onUploadCompleted(String fileName);
        void onUploadFailed(String fileName, String error);
    }

    public FileUploadClient(String leaderIP, String deviceName) {
        this.leaderIP = leaderIP;
        this.leaderPort = SyncConstants.FILE_TRANSFER_PORT;
        this.deviceName = deviceName;
    }

    public void setProgressListener(UploadProgressListener listener) {
        this.progressListener = listener;
    }

    public boolean uploadFile(Path filePath) {
        File file = filePath.toFile();
        if (!file.exists()) {
            notifyError(file.getName(), "文件不存在");
            return false;
        }

        try (Socket socket = new Socket(leaderIP, leaderPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            logger.info("📤 开始上传: {}", file.getName());

            String md5 = calculateMD5(filePath);

            UploadRequest request = new UploadRequest(
                    file.getName(),
                    file.length(),
                    md5,
                    deviceName
            );

            logger.debug("发送上传请求: {}", request);
            out.writeObject(request);
            out.flush();

            Response response = (Response) in.readObject();
            if (response.type != MessageType.UPLOAD_ACCEPTED) {
                notifyError(file.getName(), "上传被拒绝: " + response.message);
                return false;
            }

            logger.info("✅ Leader已接受上传");
            notifyStarted(file.getName());

            boolean success = uploadFileData(file, out, in);

            if (success) {
                response = (Response) in.readObject();
                if (response.type == MessageType.VERIFY_SUCCESS) {
                    logger.info("✅ 上传成功，校验通过");
                    notifyCompleted(file.getName());

                    deleteLocalFiles(filePath);
                    return true;
                } else {
                    notifyError(file.getName(), "校验失败: " + response.message);
                    return false;
                }
            }

            return false;

        } catch (Exception e) {
            logger.error("上传失败", e);
            notifyError(file.getName(), e.getMessage());
            return false;
        }
    }

    private boolean uploadFileData(File file, ObjectOutputStream out, ObjectInputStream in)
            throws Exception {
        long bytesUploaded = 0;
        int chunkIndex = 0;
        byte[] buffer = new byte[SyncConstants.FILE_CHUNK_SIZE];

        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                // 创建数据副本，避免buffer被重用导致数据错误
                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                FileChunk chunk = new FileChunk(chunkIndex++, chunkData, bytesRead);

                out.writeObject(chunk);
                out.flush();

                Response response = (Response) in.readObject();
                if (response.type != MessageType.CHUNK_ACK) {
                    notifyError(file.getName(), "块确认失败");
                    return false;
                }

                bytesUploaded += bytesRead;

                double percentage = (bytesUploaded * 100.0) / file.length();
                notifyProgress(bytesUploaded, file.length(), percentage);

                if (chunkIndex % 50 == 0) {
                    logger.debug("   上传进度: {:.1f}% ({}/{} bytes)",
                            percentage, bytesUploaded, file.length());
                }
            }
        }

        logger.info("📦 文件发送完成，等待校验...");
        return true;
    }

    private void deleteLocalFiles(Path filePath) throws IOException {
        Files.delete(filePath);
        logger.info("🗑️  本地文件已删除: {}", filePath);

        String csvFileName = filePath.getFileName().toString().replace(".mp4", ".csv");
        Path csvPath = filePath.getParent().resolve(csvFileName);
        if (Files.exists(csvPath)) {
            Files.delete(csvPath);
            logger.info("🗑️  CSV文件已删除: {}", csvPath);
        }
    }

    private String calculateMD5(Path filePath) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md5.update(buffer, 0, bytesRead);
            }
        }

        byte[] digest = md5.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void notifyStarted(String fileName) {
        if (progressListener != null) {
            progressListener.onUploadStarted(fileName);
        }
    }

    private void notifyProgress(long uploaded, long total, double percentage) {
        if (progressListener != null) {
            progressListener.onUploadProgress(uploaded, total, percentage);
        }
    }

    private void notifyCompleted(String fileName) {
        if (progressListener != null) {
            progressListener.onUploadCompleted(fileName);
        }
    }

    private void notifyError(String fileName, String error) {
        if (progressListener != null) {
            progressListener.onUploadFailed(fileName, error);
        }
    }
}