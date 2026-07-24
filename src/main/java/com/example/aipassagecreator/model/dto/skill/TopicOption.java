package com.example.aipassagecreator.model.dto.skill;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TopicOption {
    private String title;
    private String type;
    private String workload;
    private List<String> outline = new ArrayList<>();
    private List<String> pros = new ArrayList<>();
    private List<String> cons = new ArrayList<>();
}
