package com.fridgeguardian.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import static com.fridgeguardian.support.ApiTestSupport.asUser;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 닉네임·비밀번호 변경(PUT /api/auth/me)을 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountUpdateIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    private UserAccount local;
    private UserAccount social;

    @BeforeEach void createUsers() {
        local = users.saveAndFlush(new UserAccount("acct-local@example.com", passwords.encode("oldpass1"), "기존"));
        social = users.saveAndFlush(UserAccount.social(AuthProvider.GOOGLE, "acct-update-google-1", null, "소셜", null));
    }

    private ResultActions update(UserAccount user, String body) throws Exception {
        return mvc.perform(put("/api/auth/me").with(asUser(user)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test void requiresLogin() throws Exception {
        mvc.perform(put("/api/auth/me").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"a\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void changesNicknameOnly() throws Exception {
        update(local, "{\"nickname\":\" 새이름 \"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("새이름")).andExpect(jsonPath("$.hasPassword").value(true));
        assertTrue(passwords.matches("oldpass1", users.findById(local.getId()).orElseThrow().getPasswordHash()));
    }

    @Test void changesPasswordAfterVerifyingCurrent() throws Exception {
        update(local, "{\"nickname\":\"기존\",\"currentPassword\":\"wrong\",\"newPassword\":\"newpass1\",\"newPasswordConfirm\":\"newpass1\"}")
            .andExpect(status().isBadRequest());
        update(local, "{\"nickname\":\"기존\",\"currentPassword\":\"oldpass1\",\"newPassword\":\"newpass1\",\"newPasswordConfirm\":\"other\"}")
            .andExpect(status().isBadRequest());
        update(local, "{\"nickname\":\"기존\",\"currentPassword\":\"oldpass1\",\"newPassword\":\"123\",\"newPasswordConfirm\":\"123\"}")
            .andExpect(status().isBadRequest());
        update(local, "{\"nickname\":\"기존\",\"currentPassword\":\"oldpass1\",\"newPassword\":\"newpass1\",\"newPasswordConfirm\":\"newpass1\"}")
            .andExpect(status().isOk());
        assertTrue(passwords.matches("newpass1", users.findById(local.getId()).orElseThrow().getPasswordHash()));
    }

    @Test void socialOnlyAccountCannotSetPassword() throws Exception {
        update(social, "{\"nickname\":\"소셜\",\"currentPassword\":\"x\",\"newPassword\":\"newpass1\",\"newPasswordConfirm\":\"newpass1\"}")
            .andExpect(status().isBadRequest());
        update(social, "{\"nickname\":\"바꿈\"}").andExpect(status().isOk()).andExpect(jsonPath("$.hasPassword").value(false));
    }
}
