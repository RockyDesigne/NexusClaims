package com.titu.nexus_claims.repositories;
import com.titu.nexus_claims.models.ClaimStaffAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ClaimStaffAccountRepository extends JpaRepository<ClaimStaffAccount, String> {
    List<ClaimStaffAccount> findByRoleAndActiveTrueOrderByUsernameAsc(String role);
}
