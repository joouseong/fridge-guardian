package com.fridgegatekeeper.chat;

import com.fridgegatekeeper.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class ChatController {
    private final ChatService chat;

    public ChatController(ChatService chat) { this.chat = chat; }

    @GetMapping("/status")
    public ChatDtos.Status status(Authentication authentication) {
        CurrentUser.id(authentication);
        return new ChatDtos.Status(chat.available(), chat.model());
    }

    @PostMapping("/chat")
    public ChatDtos.Response reply(Authentication authentication, @Valid @RequestBody ChatDtos.Request request) {
        return chat.reply(CurrentUser.id(authentication), request);
    }
}
