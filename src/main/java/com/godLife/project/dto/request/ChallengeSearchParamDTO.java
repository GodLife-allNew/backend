package com.godLife.project.dto.request;

import lombok.Data;

@Data
public class ChallengeSearchParamDTO {
    private String challState;
    private Integer challCategoryIdx;
    private String visibilityType;
    private String challengeType;
    private Boolean onlyEnded;  // 종료된 것만 조회 여부
    private Boolean onlyJoined; // 내가 참여중인 챌린지만 조회
    private Long userIdx;       // 로그인 유저 (onlyJoined 사용 시 필요)

    private int page;
    private int size;
    private int offset;
}
