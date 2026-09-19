package com.example.resay.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 Access Token의 사용자 ID가 들어온다.
 *
 * <pre>
 * &#64;GetMapping("/api/v1/recordings")
 * public ApiResponse&lt;...&gt; getRecordings(&#64;CurrentUserId Long userId) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
