package com.godLife.project.mapper.AdminMapper;

import com.godLife.project.dto.contents.ChallengeDTO;
import com.godLife.project.dto.infos.ChallengeJoinDTO;
import com.godLife.project.dto.request.ChallengeSearchParamDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ChallAdminMapper {

  // 통합 챌린지 조회 (필터 + 페이징)
  List<ChallengeDTO> getLatestAdminChallenges(ChallengeSearchParamDTO param);
  // 통합 챌린지 개수 조회
  int countLatestAdminChallenges(ChallengeSearchParamDTO param);

  // 챌린지 생성
  void createChallenge(ChallengeDTO challengeDTO);
  // 챌린지 존재 여부 확인
  int existsById(@Param("challIdx") Long challIdx);
  // 챌린지 수정
  int modifyChallenge(ChallengeDTO challengeDTO);
  // 챌린지 삭제
  int deleteChallenge(@Param("challIdx") Long challIdx);
  // 자식 테이블(조인,인증 테이블) 삭제
  void deleteVerifyByChallIdx(@Param("challIdx") Long challIdx);
  void deleteChallJoinByChallIdx(@Param("challIdx") Long challIdx);
  // 조기 종료 처리
  int earlyFinishChallenge(Long challIdx);
  // 유저 참여형 챌린지 최초 참여시 시작/종료시간, 상태 업데이트
  void updateChallengeStartTime(Map<String, Object> params);

  // 챌린지 공개/비공개 처리
  int updateChallengeVisibility(@Param("challIdx") Long challIdx,
                                 @Param("visibilityType") String visibilityType);
  // 챌린지 이벤트 처리
  int updateChallengeType(@Param("challIdx") Long challIdx, @Param("challengeType") String challengeType);


  // 챌린지 상세조회
  ChallengeDTO challengeDetail(Long challIdx);

  // 상세조회시 참가자 조회
  List<ChallengeJoinDTO> getChallengeParticipants(Long challIdx);

  // 현재 참여자수 조회
  int countParticipants(@Param("challIdx") Long challIdx);

}
