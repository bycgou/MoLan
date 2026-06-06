package com.novel.system.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ExtractSummaryRequest {
    @JsonProperty("content")
    private String chapterContent;
    private Integer chapterNum;
}
