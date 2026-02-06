package com.recsync.core.transfer;

import com.recsync.core.sync.SyncConstants;
import com.recsync.core.transfer.FileTransferProtocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileReceiveServer {
    private static final Logger logger = LoggerFactory.getLogger(FileReceiveServer.class);
    private static final int PORT = SyncConstants.FILE_TRANSFER_PORT;

    private final String archiveDir;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = false;
    private FileReceiveListener listener;

    public interface FileReceiveListener {
        void onFileReceiveStarted(String fileName, String deviceName);
        void onFileReceiveProgress(String fileName, long bytesReceived, long totalBytes);
        void onFileReceiveCompleted(String fileName, String savedPath);
        void onFileReceiveFailed(String fileName, String error);
    }

    public FileReceiveServer(String archiveDir) {
        this.archiveDir = archiveDir;
    }

    public void start(FileReceiveListener listener) throws IOException {
        this.listener = listener;
        this.serverSocket = new ServerSocket(PORT);
        this.threadPool = Executors.newCachedThreadPool();
        this.running = true;

        Files.createDirectories(Paths.get(archiveDir));

        logger.info("✅ 文件接收服务已启动");
        logger.info("   端口: {}", PORT);
        logger.info("   归档目录: {}", archiveDir);

        threadPool.submit(this::acceptConnections);
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("📥 收到上传连接: {}", clientSocket.getInetAddress());
                threadPool.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running) {
                    logger.error("接受连接失败", e);
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            Object obj = in.readObject();
            if (!(obj instanceof UploadRequest)) {
                sendResponse(out, MessageType.ERROR, "无效的请求");
                return;
            }

            UploadRequest request = (UploadRequest) obj;
            logger.info("📋 上传请求: {}", request);

            if (listener != null) {
                listener.onFileReceiveStarted(request.fileName, request.deviceName);
            }

            // 解析文件名并创建分层目录结构
            FileNameInfo fileInfo = parseFileName(request.fileName);
            Path targetDir;
            String simplifiedFileName;

            if (fileInfo != null) {
                // 创建分层目录：{archiveDir}/{测试者ID}/{动作ID}_{回合ID}/
                targetDir = Paths.get(archiveDir,
                                     fileInfo.subjectId,
                                     fileInfo.movementId + "_" + fileInfo.episodeId);
                // 简化文件名：{设备名}.mp4
                simplifiedFileName = fileInfo.deviceName + ".mp4";
            } else {
                // 无法解析，使用旧逻辑（按设备名分类）
                targetDir = Paths.get(archiveDir, sanitizeDeviceName(request.deviceName));
                simplifiedFileName = request.fileName;
            }

            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(simplifiedFileName);

            // 覆盖模式：如果文件存在，先删除
            if (Files.exists(targetFile)) {
                Files.delete(targetFile);
                logger.info("覆盖模式：已删除旧文件 {}", targetFile);
            }

            sendResponse(out, MessageType.UPLOAD_ACCEPTED, "准备接收");

            long bytesReceived = receiveFile(targetFile, request, in, out);

            if (bytesReceived == request.fileSize) {
                String receivedMD5 = calculateMD5(targetFile);
                if (receivedMD5.equalsIgnoreCase(request.fileMD5)) {
                    sendResponse(out, MessageType.VERIFY_SUCCESS, "文件接收完成，校验通过");
                    logger.info("✅ 文件接收成功: {}", targetFile);

                    if (listener != null) {
                        listener.onFileReceiveCompleted(request.fileName, targetFile.toString());
                    }
                } else {
                    Files.deleteIfExists(targetFile);
                    String errorMsg = String.format("MD5校验失败: 期望=%s, 实际=%s",
                            request.fileMD5, receivedMD5);
                    sendResponse(out, MessageType.VERIFY_FAILED, errorMsg);

                    if (listener != null) {
                        listener.onFileReceiveFailed(request.fileName, "MD5校验失败");
                    }
                }
            }

        } catch (Exception e) {
            logger.error("处理客户端连接失败", e);
            if (listener != null) {
                listener.onFileReceiveFailed("未知", e.getMessage());
            }
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                logger.error("关闭Socket失败", e);
            }
        }
    }

    private long receiveFile(Path targetFile, UploadRequest request,
                             ObjectInputStream in, ObjectOutputStream out) throws Exception {
        long bytesReceived = 0;

        try (FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {
            while (bytesReceived < request.fileSize) {
                Object obj = in.readObject();

                if (obj instanceof FileChunk) {
                    FileChunk chunk = (FileChunk) obj;
                    fos.write(chunk.data, 0, chunk.dataLength);
                    bytesReceived += chunk.dataLength;

                    sendResponse(out, MessageType.CHUNK_ACK,
                            String.format("已接收块 %d", chunk.chunkIndex));

                    if (listener != null) {
                        listener.onFileReceiveProgress(request.fileName,
                                bytesReceived,
                                request.fileSize);
                    }

                    if (chunk.chunkIndex % 50 == 0) {
                        double progress = (bytesReceived * 100.0) / request.fileSize;
                        logger.debug("   进度: {:.1f}% ({}/{} bytes)",
                                progress, bytesReceived, request.fileSize);
                    }
                }
            }
        }

        return bytesReceived;
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

        return bytesToHex(md5.digest());
    }

    private void sendResponse(ObjectOutputStream out, MessageType type, String message)
            throws IOException {
        Response response = new Response(type, message);
        out.writeObject(response);
        out.flush();
    }

    private String sanitizeDeviceName(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_]", "_");
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (threadPool != null) {
                threadPool.shutdown();
            }
        } catch (IOException e) {
            logger.error("关闭服务失败", e);
        }
        logger.info("❌ 文件接收服务已停止");
    }

    public String getArchiveDirectory() {
        return archiveDir;
    }

    /**
     * 解析文件名，提取分层信息
     * 文件名格式：{测试者ID}_{动作ID}_{回合ID}_{设备名}.mp4
     * 例如：s01_m01_e1_front.mp4
     */
    private FileNameInfo parseFileName(String fileName) {
        try {
            // 移除扩展名
            String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));

            // 分割文件名
            String[] parts = nameWithoutExt.split("_");

            // 需要4个部分：测试者ID_动作ID_回合ID_设备名
            if (parts.length < 4) {
                logger.warn("文件名格式不符合分层要求，无法解析: {}", fileName);
                return null;
            }

            String subjectId = parts[0];
            String movementId = parts[1];
            String episodeId = parts[2];
            String deviceName = parts[3];

            // 验证各部分格式
            if (!subjectId.matches("s\\d+") || !movementId.matches("m\\d+") ||
                !episodeId.matches("e\\d+")) {
                logger.warn("文件名部分格式不正确: {}", fileName);
                return null;
            }

            logger.info("✅ 解析文件名成功: {} -> {}/{}_{}/{}.mp4",
                       fileName, subjectId, movementId, episodeId, deviceName);

            return new FileNameInfo(subjectId, movementId, episodeId, deviceName);

        } catch (Exception e) {
            logger.error("解析文件名失败: {}", fileName, e);
            return null;
        }
    }

    /**
     * 文件名信息类
     */
    private static class FileNameInfo {
        final String subjectId;
        final String movementId;
        final String episodeId;
        final String deviceName;

        FileNameInfo(String subjectId, String movementId, String episodeId, String deviceName) {
            this.subjectId = subjectId;
            this.movementId = movementId;
            this.episodeId = episodeId;
            this.deviceName = deviceName;
        }
    }
}