package com.recsync.client.camera;

import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.ffmpeg.global.avcodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class JavaCVCameraController {
    private static final Logger logger = LoggerFactory.getLogger(JavaCVCameraController.class);

    private FrameGrabber grabber;
    private FFmpegFrameRecorder recorder;
    private boolean isRecording = false;
    private boolean isRunning = false;

    private final int frameWidth;
    private final int frameHeight;
    private final double frameRate;

    public JavaCVCameraController(int width, int height, double fps) {
        this.frameWidth = width;
        this.frameHeight = height;
        this.frameRate = fps;
    }

    /**
     * 检测所有可用的摄像头
     * @return 可用摄像头的索引列表
     */
    public static List<Integer> getAvailableCameras() {
        List<Integer> cameras = new ArrayList<>();
        int maxCamerasToCheck = 10; // 最多检测10个摄像头

        logger.info("开始检测可用摄像头...");

        for (int i = 0; i < maxCamerasToCheck; i++) {
            FrameGrabber testGrabber = null;
            try {
                testGrabber = new OpenCVFrameGrabber(i);
                testGrabber.setTimeout(5000); // 5秒超时
                testGrabber.start();

                // 尝试抓取一帧来确认摄像头真的可用
                Frame frame = testGrabber.grab();
                if (frame != null && frame.image != null) {
                    cameras.add(i);
                    logger.info("✓ 检测到摄像头 {}", i);
                } else {
                    logger.debug("摄像头 {} 无法获取帧", i);
                }

                // 立即停止并释放资源
                testGrabber.stop();
                testGrabber.release();

                // 等待资源完全释放
                Thread.sleep(200);

            } catch (Exception e) {
                logger.debug("摄像头 {} 不可用: {}", i, e.getMessage());

                // 如果索引0就失败，说明没有摄像头，直接停止检测
                if (i == 0) {
                    logger.warn("索引0摄像头不可用，停止检测");
                    break;
                }

                // 确保资源被释放
                if (testGrabber != null) {
                    try { testGrabber.stop(); } catch (Exception ignored) {}
                    try { testGrabber.release(); } catch (Exception ignored) {}
                }
            }
        }

        // 如果检测失败（没有找到任何摄像头），添加一个默认选项
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

    public Frame grabFrame() throws Exception {
        if (grabber != null && isRunning) {
            return grabber.grab();
        }
        return null;
    }

    public void startRecording(String outputPath) throws Exception {
        recorder = new FFmpegFrameRecorder(outputPath, frameWidth, frameHeight);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("mp4");
        recorder.setFrameRate(frameRate);
        recorder.setVideoBitrate(8_000_000); // 8Mbps
        recorder.setVideoQuality(0); // 最高质量
        recorder.start();

        isRecording = true;
        logger.info("🎬 开始录制: {}", outputPath);
    }

    public void recordFrame(Frame frame) throws Exception {
        if (isRecording && recorder != null && frame != null) {
            recorder.record(frame);
        }
    }

    public void stopRecording() throws Exception {
        if (recorder != null) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            logger.info("⏹️ 停止录制");
        }
    }

    public void stopCamera() throws Exception {
        isRunning = false;
        if (grabber != null) {
            grabber.stop();
            grabber.release();
            logger.info("❌ 相机已停止");
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
