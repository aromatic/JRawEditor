package it.aromagnoli.raw.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ImageAdjustmentHistory {
    private String imagePath;
    private LocalDateTime timestamp = LocalDateTime.now();
    private List<ExposureSettings> exposureSteps = new ArrayList<>();

    public ImageAdjustmentHistory() {}

    public ImageAdjustmentHistory(String imagePath) {
        this.imagePath = imagePath;
    }

    public void addStep(ExposureSettings step) {
        this.exposureSteps.add(step);
    }

    // Getter e Setter per Jackson/Gson
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public List<ExposureSettings> getExposureSteps() { return exposureSteps; }
}
