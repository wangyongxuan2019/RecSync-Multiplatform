package com.recsync.leader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * 测试者信息
 */
public class SubjectInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;           // 姓名
    private int age;               // 年龄
    private String gender;         // 性别 (男/女/其他)
    private double weight;         // 体重 (kg)
    private double height;         // 身高 (cm)
    private double bmi;            // BMI (自动计算)
    private String recordTime;     // 记录时间

    public SubjectInfo() {
        this.name = "";
        this.age = 0;
        this.gender = "男";
        this.weight = 0.0;
        this.height = 0.0;
        this.bmi = 0.0;
        this.recordTime = getCurrentTime();
    }

    public SubjectInfo(String name, int age, String gender, double weight, double height) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.weight = weight;
        this.height = height;
        this.bmi = calculateBMI(weight, height);
        this.recordTime = getCurrentTime();
    }

    /**
     * 计算BMI = 体重(kg) / [身高(m)]²
     */
    public static double calculateBMI(double weightKg, double heightCm) {
        if (heightCm <= 0 || weightKg <= 0) {
            return 0.0;
        }
        double heightM = heightCm / 100.0;
        return weightKg / (heightM * heightM);
    }

    /**
     * 获取BMI分类
     */
    public String getBMICategory() {
        if (bmi < 18.5) {
            return "偏瘦";
        } else if (bmi < 24.0) {
            return "正常";
        } else if (bmi < 28.0) {
            return "偏胖";
        } else {
            return "肥胖";
        }
    }

    /**
     * 保存到Properties文件
     */
    public void saveToFile(Path filePath) throws IOException {
        Properties props = new Properties();
        props.setProperty("name", name);
        props.setProperty("age", String.valueOf(age));
        props.setProperty("gender", gender);
        props.setProperty("weight", String.valueOf(weight));
        props.setProperty("height", String.valueOf(height));
        props.setProperty("bmi", String.format("%.2f", bmi));
        props.setProperty("bmi_category", getBMICategory());
        props.setProperty("record_time", recordTime);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            props.store(fos, "Subject Information - " + name);
        }
    }

    /**
     * 从Properties文件加载
     */
    public static SubjectInfo loadFromFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            return new SubjectInfo();
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            props.load(fis);
        }

        SubjectInfo info = new SubjectInfo();
        info.name = props.getProperty("name", "");
        info.age = Integer.parseInt(props.getProperty("age", "0"));
        info.gender = props.getProperty("gender", "男");
        info.weight = Double.parseDouble(props.getProperty("weight", "0.0"));
        info.height = Double.parseDouble(props.getProperty("height", "0.0"));
        info.bmi = Double.parseDouble(props.getProperty("bmi", "0.0"));
        info.recordTime = props.getProperty("record_time", getCurrentTime());

        return info;
    }

    /**
     * 导出为CSV格式（用于数据分析）
     */
    public String toCSV() {
        return String.format("%s,%d,%s,%.2f,%.2f,%.2f,%s,%s",
            name, age, gender, weight, height, bmi, getBMICategory(), recordTime);
    }

    /**
     * CSV表头
     */
    public static String getCSVHeader() {
        return "姓名,年龄,性别,体重(kg),身高(cm),BMI,BMI分类,记录时间";
    }

    private static String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Override
    public String toString() {
        return String.format("测试者[%s, %d岁, %s, %.1fkg, %.1fcm, BMI=%.2f(%s)]",
            name, age, gender, weight, height, bmi, getBMICategory());
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) {
        this.weight = weight;
        this.bmi = calculateBMI(weight, height);
    }

    public double getHeight() { return height; }
    public void setHeight(double height) {
        this.height = height;
        this.bmi = calculateBMI(weight, height);
    }

    public double getBmi() { return bmi; }

    public String getRecordTime() { return recordTime; }
    public void setRecordTime(String recordTime) { this.recordTime = recordTime; }
}
