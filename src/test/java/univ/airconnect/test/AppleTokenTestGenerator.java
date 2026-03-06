package univ.airconnect.test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;

public class AppleTokenTestGenerator {

    public static void main(String[] args) {

        String secret = "testtesttesttesttesttesttesttest";

        String token = Jwts.builder()
                .setIssuer("https://appleid.apple.com")
                .setAudience("com.airconnect.app")
                .setSubject("testAppleUser")
                .claim("email", "test@test.com")
                .setExpiration(new Date(System.currentTimeMillis() + 1000000))
                .signWith(
                        io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes()),
                        SignatureAlgorithm.HS256
                )
                .compact();

        System.out.println(token);
    }
}