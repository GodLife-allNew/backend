package com.godLife.project.service.interfaces.AdminInterface;

import com.godLife.project.dto.contents.ChallengeDTO;
import com.godLife.project.dto.request.ChallengeSearchParamDTO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ChallAdminService {
  // 최신 챌린지 조회 (페이징 적용)
  List<ChallengeDTO> getLatestAdminChallenges(ChallengeSearchParamDTO param);
  // 최신 챌린지 총 개수 조회
  int countLatestAdminChallenges(ChallengeSearchParamDTO param);

  // 챌린지 생성
  int createChallenge(ChallengeDTO challengeDTO);
  // 챌린지 존재 여부
  boolean existsById(Long challIdx);
  // 챌린지 수정
  int modifyChallenge(ChallengeDTO challengeDTO);
  // 챌린지 삭제
  int deleteChallenge(@Param("challIdx") Long challIdx);

  // 챌린지 공개 / 비공개 상태 변경
  int updateChallengeVisibility(Long challIdx, String visibilityType);
  // 챌린지 이벤트 처리
  int updateChallengeType(Long challIdx, String challengeType);

  // 조기 종료
  void earlyFinishChallenge(Long challIdx);
  // 챌린지 상세 조회
  ChallengeDTO getChallengeDetail(Long challIdx);


}
