package com.godLife.project.controller.AdminController;

import com.godLife.project.dto.contents.ChallengeDTO;
import com.godLife.project.dto.request.ChallengeSearchParamDTO;
import com.godLife.project.handler.GlobalExceptionHandler;
import com.godLife.project.service.interfaces.AdminInterface.ChallAdminService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/challenges")
public class ChallAdminController {
  @Autowired
  private GlobalExceptionHandler handler;

  @Autowired
  private final ChallAdminService challAdminService;

  public ChallAdminController(ChallAdminService challAdminService) {
    this.challAdminService = challAdminService;
  }

  // -------------------- 최신 챌린지 조회 ----------------
  @GetMapping("/latest")
  public ResponseEntity<?> getLatestAdminChallenges(
          @RequestParam(required = false) String challState,
          @RequestParam(required = false) Integer challCategoryIdx,
          @RequestParam(required = false) String visibilityType,
          @RequestParam(required = false) String challengeType,
          @RequestParam(required = false, defaultValue = "false") Boolean onlyActive,
          @RequestParam(required = false, defaultValue = "false") Boolean onlyEnded,
          @RequestParam(defaultValue = "1") int page,
          @RequestParam(defaultValue = "10") int size
  ) {
    ChallengeSearchParamDTO param = new ChallengeSearchParamDTO();
    param.setChallState(challState);
    param.setChallCategoryIdx(challCategoryIdx);
    param.setVisibilityType(visibilityType);
    param.setChallengeType(challengeType);
    param.setOnlyActive(onlyActive);
    param.setOnlyEnded(onlyEnded);
    param.setPage(page);
    param.setSize(size);

    List<ChallengeDTO> challenges = challAdminService.getLatestAdminChallenges(param);
    int totalChallenges = challAdminService.countLatestAdminChallenges(param);
    int totalPages = (int) Math.ceil((double) totalChallenges / size);

    Map<String, Object> response = new HashMap<>();
    response.put("status", 200);
    response.put("message", "챌린지 조회 성공");
    response.put("challenges", challenges);
    response.put("totalPages", totalPages);
    response.put("currentPage", page);
    response.put("pageSize", size);

    return ResponseEntity.ok(response);
  }

  // 챌린지 생성 API
  @PostMapping("/create")
  public ResponseEntity<Map<String, Object>> createChallenge(@Valid @RequestBody ChallengeDTO challengeDTO,
                                                             BindingResult result) {
    if (result.hasErrors()) {
      return ResponseEntity.badRequest().body(handler.getValidationErrors(result));
    }

    try {
      int insertResult = challAdminService.createChallenge(challengeDTO);

      // 응답 메세지 세팅
      String msg = switch (insertResult) {
        case 201 -> "챌린지 작성 완료";
        case 500 -> "서버 내부적으로 오류가 발생하여 챌린지를 저장하지 못했습니다.";
        default -> "알 수 없는 오류가 발생했습니다.";
      };

      return ResponseEntity.status(handler.getHttpStatus(insertResult)).body(handler.createResponse(insertResult, msg));

    } catch (IllegalArgumentException e) {
      log.error("잘못된 요청: {}", e.getMessage());
      return ResponseEntity.badRequest().body(handler.createResponse(400, e.getMessage()));
    } catch (Exception e) {
      log.error("서버 오류 발생: ", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(handler.createResponse(500, "예기치 못한 오류가 발생했습니다."));
    }
  }

  // ----------- 챌린지 수정 --------------
  @PatchMapping("/modify")
  public ResponseEntity<Map<String, Object>> modifyChallenge(
          @Valid @RequestBody ChallengeDTO challengeDTO,
          BindingResult result
  ) {
    log.info(" 챌린지 수정 요청: {}", challengeDTO); // 요청 정보 로깅

    // 유효성 검사 실패 시 에러 반환
    if (result.hasErrors()) {
      log.warn("유효성 검사 실패: {}", result.getFieldErrors());
      return ResponseEntity.badRequest().body(handler.getValidationErrors(result));
    }

    // 챌린지 존재 여부 확인
    if (!challAdminService.existsById(challengeDTO.getChallIdx())) {
      log.warn("챌린지 존재하지 않음: challIdx={}", challengeDTO.getChallIdx());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(handler.createResponse(404, "요청하신 챌린지가 존재하지 않습니다."));
    }

    try {
      // 수정 로직 실행
      int modifyResult = challAdminService.modifyChallenge(challengeDTO);
      log.info("챌린지 수정 결과: challIdx={}, modifyResult={}", challengeDTO.getChallIdx(), modifyResult);

      Map<String, Object> response = new LinkedHashMap<>();
      response.put("status", modifyResult);

      switch (modifyResult) {
        case 1, 200 -> { // 1과 200을 같은 처리로 묶음
          response.put("message", "챌린지 수정 완료");
          return ResponseEntity.ok(response);
        }
        case 403 -> {
          log.warn("수정 권한 없음: challIdx={}", challengeDTO.getChallIdx());
          response.put("message", "작성자가 아닙니다. 재로그인 해주세요.");
        }
        case 404 -> {
          log.warn("수정 대상 챌린지 없음: challIdx={}", challengeDTO.getChallIdx());
          response.put("message", "요청하신 챌린지가 존재하지 않습니다.");
        }
        case 500 -> {
          log.error("서버 내부 오류 발생: challIdx={}", challengeDTO.getChallIdx());
          response.put("message", "서버 내부적으로 오류가 발생하여 챌린지를 수정하지 못했습니다.");
        }
        default -> {
          log.error("예상치 못한 오류 발생: challIdx={}, modifyResult={}", challengeDTO.getChallIdx(), modifyResult);
          response.put("message", "알 수 없는 오류가 발생했습니다. 관리자에게 문의하세요.");
        }
      }

      return ResponseEntity.status(handler.getHttpStatus(modifyResult)).body(response);
    } catch (Exception e) {
      log.error("예외 발생: challIdx={}, error={}", challengeDTO.getChallIdx(), e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(Map.of("status", 500, "message", "서버 내부 오류 발생"));
    }
  }

  //              ------------- 삭제 --------------
  @PatchMapping("/delete")
  public ResponseEntity<Map<String, Object>> deleteChallenge(
          @Valid @RequestBody ChallengeDTO challengeDTO,
          BindingResult result) {
    // 유효성 검사 실패 시 에러 반환
    if (result.hasErrors()) {
      return ResponseEntity.badRequest().body(handler.getValidationErrors(result));
    }

    // 챌린지 존재 여부 확인
    Long challIdx = challengeDTO.getChallIdx();
    if (!challAdminService.existsById(challIdx)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(handler.createResponse(404, "요청하신 챌린지가 존재하지 않습니다."));
    }

    // 삭제 서비스 실행
    int deleteResult = challAdminService.deleteChallenge(challIdx); // 관리자 권한 체크 후 삭제

    // 응답 메시지 설정
    String msg = switch (deleteResult) {
      case 200 -> "챌린지 삭제 완료";
      case 403 -> "관리자 권한이 없습니다.";
      case 404 -> "요청하신 챌린지가 존재하지 않습니다.";
      case 500 -> "서버 내부 오류로 챌린지를 삭제하지 못했습니다.";
      default -> "알 수 없는 오류가 발생했습니다.";
    };

    return ResponseEntity.status(handler.getHttpStatus(deleteResult))
            .body(handler.createResponse(deleteResult, msg));
  }

  // -------------  챌린지 상세 조회  -----------------
  @GetMapping("/detail/{challIdx}")
  public ChallengeDTO getChallengeDetail(@PathVariable Long challIdx) {
    // 서비스에서 챌린지 상세 정보 조회 및 업데이트
    return challAdminService.getChallengeDetail(challIdx);
  }



  // ----------- 챌린지 공개 / 비공개 상태 변경
  @PostMapping("/visibility/{challIdx}")
  public ResponseEntity<Map<String, Object>> updateVisibility(
          @PathVariable Long challIdx,   // URL에서 가져옴
          @RequestParam String visibilityType) {  // 쿼리 파라미터로 가져옴

    // 챌린지 존재 여부 확인
    if (!challAdminService.existsById(challIdx)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(handler.createResponse(404, "요청하신 챌린지가 존재하지 않습니다."));
    }

    // 공개/비공개 서비스 실행
    int updateResult = challAdminService.updateChallengeVisibility(challIdx, visibilityType);

    String msg = switch (updateResult) {
      case 200 -> "챌린지 공개 상태 변경 완료";
      case 403 -> "관리자 권한이 없습니다.";
      case 404 -> "요청하신 챌린지가 존재하지 않습니다.";
      case 500 -> "서버 내부 오류로 상태를 변경하지 못했습니다.";
      default -> "알 수 없는 오류가 발생했습니다.";
    };

    return ResponseEntity.status(handler.getHttpStatus(updateResult))
            .body(handler.createResponse(updateResult, msg));
  }


  // -------------- 챌린지 이벤트 처리 ----------------
  @PostMapping("type/{challIdx}")
  public ResponseEntity<Map<String, Object>> updateChallengeType(
          @PathVariable Long challIdx,
          @RequestParam String challengeType){

    // 챌린지 존재여부 확인
    if(!challAdminService.existsById(challIdx)){
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(handler.createResponse(404, "요청하신 챌린지가 존재하지 않습니다."));
    }

    // 이벤트 서비스 실행
    int updateResult = challAdminService.updateChallengeType(challIdx, challengeType);

    String msg = switch (updateResult) {
      case 200 -> "챌린지 타입이 변경되었습니다.";
      case 403 -> "관리자 권한이 없습니다.";
      case 404 -> "요청하신 챌린지가 존재하지 않습니다.";
      case 500 -> "서버 내부 오류로 타입을 변경하지 못했습니다.";
      default -> "알 수 없는 오류가 발생했습니다.";
    };

    return ResponseEntity.status(handler.getHttpStatus(updateResult))
            .body(handler.createResponse(updateResult, msg));
  }





  //  -----------  챌린지 조기 종료  --------------
  @PutMapping("/earlyFinish/{challIdx}")
  public ResponseEntity<String> earlyFinish(@PathVariable Long challIdx) {
    try {
      challAdminService.earlyFinishChallenge(challIdx);
      return ResponseEntity.ok("챌린지가 성공적으로 종료되었습니다.");
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

}