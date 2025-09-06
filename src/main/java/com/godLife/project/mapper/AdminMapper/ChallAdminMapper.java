package com.godLife.project.mapper.AdminMapper;

import com.godLife.project.dto.contents.ChallengeDTO;
import com.godLife.project.dto.infos.ChallengeJoinDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ChallAdminMapper {
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

  // 챌린지 검색 (제목, 카테고리)
  List<ChallengeDTO> searchChallenges(
          @Param("challTitle") String challTitle,
          @Param("challCategory") String challCategory,
          @Param("challState") String challState,
          @Param("offset") int offset,
          @Param("size") int size,
          @Param("sort") String sort
  );
  // 총 게시글 수 조회 (페이징 계산용)
  int countSearchChallenges(
          @Param("challTitle") String challTitle,
          @Param("challCategory") String challCategory,
          @Param("challState") String challState
  );


  // 챌린지 상세조회
  ChallengeDTO challengeDetail(Long challIdx);

  // 상세조회시 참가자 조회
  List<ChallengeJoinDTO> getChallengeParticipants(Long challIdx);

  // 현재 참여자수 조회
  int countParticipants(@Param("challIdx") Long challIdx);

}
