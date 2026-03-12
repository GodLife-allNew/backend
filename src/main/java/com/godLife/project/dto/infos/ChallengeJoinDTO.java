package com.godLife.project.dto.infos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ChallengeJoinDTO {
    @Schema(description = "챌린지 참여 고유 인덱스", example = "1")
    private int challJoinIdx;

    @Schema(description = "참여 챌린지 인덱스", example = "1")
    private Long challIdx;

    @Schema(description = "참여 유저 인덱스", example = "1")
    private int userIdx;

    @Schema(description = "활동 목표시간", example = "2시간")
    private int activityTime;

    @Schema(description = "유저 닉네임", example = "유저1")
    private String userNick;
}
