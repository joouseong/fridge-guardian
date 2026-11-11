package com.fridgeguardian.user.preference;

import com.fridgeguardian.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/preferences")
public class PreferenceController {
    private final PreferenceService preferences;

    public PreferenceController(PreferenceService preferences) { this.preferences = preferences; }

    @GetMapping
    public PreferenceDtos.Response get(Authentication authentication) {
        return preferences.get(CurrentUser.id(authentication));
    }

    @PutMapping
    public PreferenceDtos.Response update(Authentication authentication, @Valid @RequestBody PreferenceDtos.Request request) {
        return preferences.update(CurrentUser.id(authentication), request);
    }
}
