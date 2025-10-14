package com.godLife.project.service.interfaces.AdminInterface;

import com.godLife.project.dto.categories.AuthorityCateDTO;
import com.godLife.project.dto.list.customDTOs.AdminListDTO;
import com.godLife.project.dto.list.customDTOs.AdminUserDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AdminUserService {
    List<AdminUserDTO> getPagedUserList(int page, int size);
    int countAllActiveUsers();

    int banUser(int userIdx);             // 유저 정지
    int existsByUserIdx(int userIdx); // 유저 존재 여부 조회
    int getBanStatus(int userIdx);        // 현재 정지여부 확인 (0 or 1)


    // 권한 관리
    List<AuthorityCateDTO> getAuthorityList();
    List<AdminUserDTO> getUsersByAuthority(int authorityIdx);
    List<AdminListDTO> adminList();
    int updateUserAuthority(int userIdx, int authorityIdx);

}
