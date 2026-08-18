package com.harucut.config.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 엔드포인트가 낼 수 있는 에러 코드를 선언한다.
 *
 * <pre>
 * &#64;ApiErrors({
 *     "NOTICE-001: 없거나 아직 게시되지 않은 공지",
 *     "GEN-002: page &lt; 0 또는 size &lt; 1"
 * })
 * </pre>
 *
 * <p>{@code "코드: 언제 나는지"} 형식이다. 콜론 뒤 설명은 생략할 수 있고, 쓰면 문서에 함께 나온다.
 * 코드에 해당하는 HTTP 상태와 메시지는 {@link ErrorCodeCatalog} 가 실제 {@code ErrorCode} enum 에서
 * 읽어오므로 여기 적을 필요가 없다. <b>없는 코드를 적으면 애플리케이션이 기동에 실패한다.</b>
 *
 * <p><b>왜 enum 이 아니라 문자열인가.</b> 자바 애노테이션의 멤버로 enum 을 받으려면 타입이 하나로
 * 고정되어야 한다. 그런데 한 엔드포인트가 내는 에러는 {@code GlobalErrorCode} 와 {@code SubscriptionErrorCode}
 * 처럼 서로 다른 enum 에 흩어져 있어서 한 배열에 담을 수가 없다. 그래서 코드 문자열을 받고,
 * 대신 기동 시점에 전량 검증해 오타를 컴파일 에러에 준하는 속도로 잡는다.
 *
 * <p>인증이 필요한 경로의 401/403 은 여기 적지 않는다. {@code PublicPaths} 를 기준으로 자동으로 붙는다.
 * {@code @Valid @RequestBody} 가 있는 메서드의 GEN-003 도 자동이다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ApiErrors {

    String[] value();
}
