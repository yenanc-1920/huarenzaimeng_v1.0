package com.huarenzaimeng.api.buyerauth;

import jakarta.servlet.*; import jakarta.servlet.http.*;
import org.springframework.context.annotation.Profile; import org.springframework.core.annotation.Order; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component @Profile("release-mysql") @Order(20)
public final class BuyerSessionFilter extends OncePerRequestFilter {
    public static final String BUYER="hz.trusted.buyer"; private final BuyerAuthService auth; BuyerSessionFilter(BuyerAuthService auth){this.auth=auth;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){
        String path=r.getRequestURI().substring(r.getContextPath().length());
        return !(path.startsWith("/buyer-api/v1/")||path.startsWith("/buyer-auth/v1/recovery-cases")||path.equals("/api/v1/quotes")||path.equals("/api/v1/orders")
                ||path.startsWith("/api/v1/orders/")||path.equals("/api/v1/recovery-cases")
                ||path.startsWith("/api/v1/recovery-cases/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse p,FilterChain c)throws ServletException,IOException{
        String h=r.getHeader("Authorization"); String token=h!=null&&h.startsWith("Bearer ")?h.substring(7):null;
        var buyer=auth.authenticate(token); if(buyer.isEmpty()){p.setStatus(401);p.setContentType("application/json");p.getWriter().write("{\"status\":\"UNAUTHENTICATED\"}");return;}
        r.setAttribute(BUYER,new BuyerSessionPrincipal(buyer.get().subjectRef(),buyer.get().sessionRef()));c.doFilter(r,p);
    }
}
