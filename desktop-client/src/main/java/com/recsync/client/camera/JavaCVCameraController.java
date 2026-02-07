package com.recsync.client.camera;

import org.bytedeco.javacv.*;
import org.bytedeco.ffmpeg.global.avcodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * 软录制模式相机控制器
 *
 * 工作原理：
 * 1. 相机始终运行，持续采集视频帧
 * 2. 每帧附加同步时钟时间戳
 * 3. 收到触发时间后，从第一个时间戳 >= 触发时间的帧开始写入
 * 4. 避免录制器冷启动带来的延迟和不确定性
 */
public class JavaCVCameraController {
    private static final Logger logger = LoggerFactory.getLogger(JavaCVCameraController.class);

    private FrameGrabber grabber;
    private FFmpegFrameRecorder recorder;
    private volatile boolean isRunning = false;

    private final int frameWidth;
    private final int frameHeight;
    private final double frameRate;

    // 软录制状态
    private volatile RecordingState recordingState = RecordingState.IDLE;
    private volatile long triggerTimeNs = 0;        // 触发时间（本地同步时钟）
    private volatile String pendingOutputPath;       // 待写入的文件路径
    private volatile long recordingStartTimeNs = 0;  // 实际开始录制的时间戳
    private volatile long frameCount = 0;            // 已录制帧数

    // 同步时钟提供者（用于获取对齐后的时间戳）
    private LongSupplier syncClockSupplier = System::nanoTime;  // 默认使用本地时钟

    /**
     * 录制状态
     */
    public enum RecordingState {
        IDLE,           // 空闲，仅预览
        WAITING,        // 等待触发时间
        RECORDING,      // 正在录制
        STOPPING        // 正在停止
    }

    /**
     * 带时间戳的帧
     */
    public static class TimestampedFrame {
        public final Frame frame;
        public final long timestampNs;  // 同步时钟时间戳

        public TimestampedFrame(Frame frame, long timestampNs) {
            this.frame = frame;
            this.timestampNs = timestampNs;
        }
    }

    public JavaCVCameraController(int width, int height, double fps) {
        this.frameWidth = width;
        this.frameHeight = height;
        this.frameRate = fps;
    }

    /**
     * 设置同步时钟提供者
     * @param clockSupplier 返回当前同步时钟时间（纳秒）的函数
     */
    public void setSyncClockSupplier(LongSupplier clockSupplier) {
        this.syncClockSupplier = clockSupplier;
    }

    /**
     * 获取当前同步时钟时间
     */
    public long getSyncTimeNs() {
        return syncClockSupplier.getAsLong();
    }

    /**
     * 检测所有可用的摄像头
     */
    public static List<Integer> getAvailableCameras() {
        List<Integer> cameras = new ArrayList<>();
        int maxCamerasToCheck = 10;

        logger.info("开始检测可用摄像头...");

        for (int i = 0; i < maxCamerasToCheck; i++) {
            FrameGrabber testGrabber = null;
            try {
                testGrabber = new OpenCVFrameGrabber(i);
                testGrabber.setTimeout(5000);
                testGrabber.start();

                Frame frame = testGrabber.grab();
                if (frame != null && frame.image != null) {
                    cameras.add(i);
                    logger.info("✓ 检测到摄像头 {}", i);
                }

                testGrabber.stop();
                testGrabber.release();
                Thread.sleep(200);

            } catch (Exception e) {
                logger.debug("摄像头 {} 不可用: {}", i, e.getMessage());
                if (i == 0) break;
                if (testGrabber != null) {
                    try { testGrabber.stop(); } catch (Exception ignored) {}
                    try { testGrabber.release(); } catch (Exception ignored) {}
                }
            }
        }

        if (cameras.isEmpty()) {
            cameras.add(0);
            logger.warn("未检测到可用摄像头，添加默认选项 0");
        }

        logger.info("摄像头检测完成，找到 {} 个摄像头", cameras.size());
        return cameras;
    }

    public void startCamera(int cameraIndex) throws Exception {
        grabber = new OpenCVFrameGrabber(cameraIndex);
        grabber.setImageWidth(frameWidth);
        grabber.setImageHeight(frameHeight);
        grabber.setFrameRate(frameRate);
        grabber.start();

        isRunning = true;
        logger.info("✅ 相机已启动: {}x{} @ {}fps", frameWidth, frameHeight, frameRate);
    }

    /**
     * 抓取一帧并附加同步时钟时间戳
     */
    public TimestampedFrame grabTimestampedFrame() throws Exception {
        if (grabber != null && isRunning) {
            Frame frame = grabber.grab();
            if (frame != null) {
                long timestamp = getSyncTimeNs();
                return new TimestampedFrame(frame, timestamp);
            }
        }
        return null;
    }

    /**
     * 抓取帧（兼容旧接口）
     */
    public Frame grabFrame() throws Exception {
        if (grabber != null && isRunning) {
            return grabber.grab();
        }
        return null;
    }

    /**
     * 软录制模式：设置触发时间，准备开始录制
     *
     * @param outputPath 输出文件路径
     * @param triggerTimeNs 触发时间（本地同步时钟，纳秒）
     */
    public void prepareRecording(String outputPath, long triggerTimeNs) throws Exception {
        if (recordingState != RecordingState.IDLE) {
            logger.warn("无法准备录制：当前状态为 {}", recordingState);
            return;
        }

        this.pendingOutputPath = outputPath;
        this.triggerTimeNs = triggerTimeNs;
        this.frameCount = 0;

        // 预先初始化录制器（但不开始写入）
        recorder = new FFmpegFrameRecorder(outputPath, frameWidth, frameHeight);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("mp4");
        recorder.setFrameRate(frameRate);
        recorder.setVideoBitrate(8_000_000);
        recorder.setVideoQuality(0);
        recorder.start();

        recordingState = RecordingState.WAITING;

        long waitTimeMs = (triggerTimeNs - getSyncTimeNs()) / 1_000_000;
        logger.info("🎬 软录制准备就绪: 等待触发 ({}ms后), 输出: {}", waitTimeMs, outputPath);
    }

    /**
     * 处理一帧（在预览循环中调用）
     * 根据当前状态决定是否写入该帧
     *
     * @param tsFrame 带时间戳的帧
     * @return true 如果帧被写入录制文件
     */
    public boolean processFrame(TimestampedFrame tsFrame) throws Exception {
        if (tsFrame == null || tsFrame.frame == null) {
            return false;
        }

        switch (recordingState) {
            case WAITING:
                // 检查是否到达触发时间
                if (tsFrame.timestampNs >= triggerTimeNs) {
                    // 从这一帧开始录制
                    recordingState = RecordingState.RECORDING;
                    recordingStartTimeNs = tsFrame.timestampNs;

                    long delayMs = (tsFrame.timestampNs - triggerTimeNs) / 1_000_000;
                    logger.info("✅ 软录制触发: 帧时间戳={}, 触发延迟={}ms",
                            tsFrame.timestampNs, delayMs);

                    // 写入第一帧
                    recorder.record(tsFrame.frame);
                    frameCount++;
                    return true;
                }
                break;

            case RECORDING:
                // 正常录制
                recorder.record(tsFrame.frame);
                frameCount++;
                return true;

            case STOPPING:
            case IDLE:
            default:
                // 不录制
                break;
        }

        return false;
    }

    /**
     * 硬录制模式：立即开始录制（兼容旧接口）
     */
    public void startRecording(String outputPath) throws Exception {
        if (recordingState != RecordingState.IDLE) {
            logger.warn("无法开始录制：当前状态为 {}", recordingState);
            return;
        }

        recorder = new FFmpegFrameRecorder(outputPath, frameWidth, frameHeight);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("mp4");
        recorder.setFrameRate(frameRate);
        recorder.setVideoBitrate(8_000_000);
        recorder.setVideoQuality(0);
        recorder.start();

        recordingState = RecordingState.RECORDING;
        recordingStartTimeNs = getSyncTimeNs();
        frameCount = 0;
        logger.info("🎬 硬录制开始: {}", outputPath);
    }

    /**
     * 录制一帧（兼容旧接口）
     */
    public void recordFrame(Frame frame) throws Exception {
        if (recordingState == RecordingState.RECORDING && recorder != null && frame != null) {
            recorder.record(frame);
            frameCount++;
        }
    }

    /**
     * 停止录制
     */
    public void stopRecording() throws Exception {
        if (recorder != null && recordingState != RecordingState.IDLE) {
            recordingState = RecordingState.STOPPING;

            long durationMs = (getSyncTimeNs() - recordingStartTimeNs) / 1_000_000;

            recorder.stop();
            recorder.release();
            recorder = null;

            logger.info("⏹️ 录制完成: 帧数={}, 时长={}ms", frameCount, durationMs);

            recordingState = RecordingState.IDLE;
            triggerTimeNs = 0;
            pendingOutputPath = null;
        }
    }

    public void stopCamera() throws Exception {
        isRunning = false;

        // 如果正在录制，先停止录制
        if (recordingState != RecordingState.IDLE) {
            stopRecording();
        }

        if (grabber != null) {
            grabber.stop();
            grabber.release();
            logger.info("❌ 相机已停止");
        }
    }

    public boolean isRecording() {
        return recordingState == RecordingState.RECORDING;
    }

    public boolean isWaitingTrigger() {
        return recordingState == RecordingState.WAITING;
    }

    /**
     * 检查是否处于任何活跃录制状态（等待触发或正在录制）
     * 用于防止在软录制模式下重复开始录制或切换摄像头
     */
    public boolean isRecordingActive() {
        return recordingState == RecordingState.WAITING || recordingState == RecordingState.RECORDING;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public RecordingState getRecordingState() {
        return recordingState;
    }

    public long getFrameCount() {
        return frameCount;
    }

    public long getTriggerTimeNs() {
        return triggerTimeNs;
    }
}
