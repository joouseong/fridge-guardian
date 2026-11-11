package com.fridgeguardian.user.token;

import com.fridgeguardian.config.AppProperties;
import com.fridgeguardian.user.UserAccount;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    public static final String ISSUER = "fridge-guardian";
    private final JwtEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, AppProperties props, Clock clock) {
        this.encoder = encoder; this.props = props; this.clock = clock;
    }

    public long accessTtlSeconds() { return props.jwt().accessTtl().toSeconds(); }

    /** 토큰에는 이메일 같은 개인정보를 넣지 않고 사용자 ID(sub)와 로그인 방식만 담습니다. */
    public String issueAccessToken(UserAccount user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER)
            .subject(String.valueOf(user.getId())).issuedAt(now).expiresAt(now.plus(props.jwt().accessTtl()))
            .claim("provider", user.getProvider().name()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
