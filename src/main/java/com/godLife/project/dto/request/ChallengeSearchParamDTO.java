package com.godLife.project.dto.request;

import lombok.Data;

@Data
public class ChallengeSearchParamDTO {
    private String challState;
    private Integer challCategoryIdx;
    private String visibilityType;
    private String challengeType;
    private Boolean onlyEnded;  // 종료된 것만 조회 여부

    private int page;
    private int size;
    private int offset;
}
