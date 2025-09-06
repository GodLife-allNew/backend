package com.godLife.project.service.interfaces.AdminInterface;

import com.godLife.project.dto.contents.ChallengeDTO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface ChallAdminService {
  // 챌린지 생성
  int createChallenge(ChallengeDTO challengeDTO);
  // 챌린지 존재 여부
  boolean existsById(Long challIdx);
  // 챌린지 수정
  int modifyChallenge(ChallengeDTO challengeDTO);
  // 챌린지 삭제
  int deleteChallenge(@Param("challIdx") Long challIdx);
  // 조기 종료
  void earlyFinishChallenge(Long challIdx);


    // --------------------- 조회 -----------------------
    // 챌린지 검색 및 조회(페이징 적용)
    Map<String, Object> searchChallenges(
            @Param("challTitle") String challTitle,
            @Param("challCategory") String challCategory,
            @Param("challState") String challState,   // 상태 필터 추가
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("sort") String sort
    );

  ChallengeDTO getChallengeDetail(Long challIdx);          // 챌린지 상세 조회

}
