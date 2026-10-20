package com.my.project.application.feature.dto;

import com.my.project.llm.bo.ZoneTrendImage;
import com.my.project.llm.bo.ZoneTrendSnapshot;
import java.util.List;

public record ZoneTrendAnalysisCommand(ZoneTrendSnapshot snapshot, List<ZoneTrendImage> images) { }
