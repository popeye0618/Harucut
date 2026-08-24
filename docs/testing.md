# 테스트 컨벤션

서비스·컨트롤러 테스트를 작성하거나 수정할 때 이 문서를 따른다.

기존 Kotlin 구현(`~/Documents/harucut_ktl_be/docs/testing.md`)의 규칙을 Java 스택으로 옮긴 것이다.
규칙의 **의도**는 그대로 두고, 도구만 바꿨다.

**스택:** JUnit Jupiter 6.0.3 · Mockito 5.23.0 · AssertJ 3.27.7 · Jackson 3.1.4(`tools.jackson`)

## 도구 대응표

| Kotlin (기존) | Java (이 프로젝트) |
|---|---|
| MockK `mockk<T>()` | Mockito `@Mock` |
| `every { } returns` | `given(...).willReturn(...)` |
| `verify { }` / `verify(exactly = 0)` | `then(mock).should()` / `.shouldHaveNoInteractions()` |
| `just Runs` (void) | **불필요** — Mockito는 void가 기본 no-op |
| `mockk(relaxed = true)` | **불필요** — Mockito는 기본이 relaxed |
| `@MockkBean` (SpringMockK) | `@MockitoBean` |
| MockMvc Kotlin DSL | `MockMvcTester` |

---

## Boot 4에서 위치가 바뀐 것들

온라인 예제는 대부분 Boot 3 기준이라 import가 맞지 않는다. 실제 위치는 아래와 같다.

| 대상 | Boot 4 패키지 |
|------|---------------|
| `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `TestEntityManager` | `org.springframework.boot.jpa.test.autoconfigure` |
| `@JsonTest` | `org.springframework.boot.test.autoconfigure.json` (변경 없음) |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito` |

**`@MockBean`은 Boot 4에서 삭제됐다.** `@MockitoBean`을 쓴다 (Kotlin 쪽 `@MockkBean`의 대응).

---

## 공통 규칙

- `@Nested`로 대상 메서드/엔드포인트별 그룹. 클래스·`@Nested`·`@Test` 마다 **한글 `@DisplayName`**
- **메서드명은 영문.** 무엇을 검증하는지는 이름이 아니라 `@DisplayName`이 말한다
- 검증은 **AssertJ만** (`assertThat` / `assertThatThrownBy`). JUnit `assertEquals`는 쓰지 않는다
- Mockito는 **BDD 스타일로 통일** — `given(...).willReturn(...)`, `then(mock).should()`.
  `when(...)`/`verify(...)`와 섞어 쓰지 않는다
- 도메인 예외는 타입만이 아니라 **에러 코드까지** 확인한다

```java
assertThatThrownBy(() -> service.assertHistoryAccessible(user, requestedAt))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(SubscriptionErrorCode.PLAN_HISTORY_RETENTION_EXCEEDED);
```

---

## 서비스 테스트

스프링 컨텍스트 없이 **순수 클래스**로 작성한다.

- `@ExtendWith(MockitoExtension.class)` + `@Mock` 필드
- **대상 서비스는 `@BeforeEach`에서 생성자로 직접 조립한다. `@InjectMocks`는 쓰지 않는다** —
  리플렉션 주입이 실패해도 조용히 null을 남기고, 한참 뒤 엉뚱한 NPE로 나타난다.
  생성자로 조립하면 의존성이 바뀌는 순간 컴파일이 깨진다
- **`MockitoExtension`의 기본값 `STRICT_STUBS`를 끄지 않는다.** 쓰이지 않는 스텁이 있으면 테스트가 실패한다.
  MockK에는 없던 동작이라 처음엔 성가시지만, 리팩터링 후 죽은 스텁을 잡아준다
- void 메서드에는 스텁을 달지 않는다 (Mockito 기본이 no-op).
  Kotlin의 `just Runs`에 해당하는 코드는 불필요하다
- 엔티티 조립이 반복되면 테스트 클래스 하단에 **private 헬퍼 팩터리**를 둔다

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionPolicyService")
class SubscriptionPolicyServiceTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    private SubscriptionPolicyService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPolicyService(userSubscriptionRepository);
    }

    @Nested
    @DisplayName("assertHistoryAccessible")
    class AssertHistoryAccessible {

        @Test
        @DisplayName("BASIC에서 보관 기간을 초과하면 예외를 던진다")
        void beyondRetention() {
            given(userSubscriptionRepository.findByUserId(1L))
                    .willReturn(Optional.of(subscription(PlanTier.BASIC)));

            assertThatThrownBy(() -> service.assertHistoryAccessible(user(), OLD_TIME))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(SubscriptionErrorCode.PLAN_HISTORY_RETENTION_EXCEEDED);
        }
    }
}
```

---

## 컨트롤러 테스트

`@WebMvcTest` 슬라이스 + **`MockMvcTester`**를 쓴다. `mockMvc.perform(...).andExpect(...)`는 쓰지 않는다 —
검증을 AssertJ로 통일한다는 규칙과 어긋나고(`andExpect`는 Hamcrest 계열),
Kotlin DSL이 주던 가독성을 잃는다. `@WebMvcTest`가 `MockMvcTester`를 자동 구성하므로 주입만 받으면 된다.

- 클래스 선언: `@WebMvcTest(XController.class)` + `@Import(SecurityConfig.class)` + `SecurityBeansMockSupport` 상속
  - `SecurityConfig`가 생성자로 요구하는 빈은 support 클래스가 `@MockitoBean`으로 제공한다.
    각 테스트는 **해당 컨트롤러가 쓰는 서비스만** 추가로 선언한다
- 인증은 `SecurityMockMvcRequestPostProcessors.authentication(...)`을 `.with(...)`로 주입
- 성공 응답은 공통 봉투이므로 `bodyJson().extractingPath("$.data.xxx")`

```java
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@DisplayName("UserController")
class UserControllerTest extends SecurityBeansMockSupport {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("내 정보를 200으로 반환한다")
    void getUserInfo() {
        given(userService.getUserInfo(1L)).willReturn(userInfo());

        assertThat(mockMvc.get().uri("/api/auth/user/info").with(authentication(userToken())))
                .hasStatusOk()
                .bodyJson().extractingPath("$.data.planTier").isEqualTo("PLUS");
    }
}
```

### 실패 케이스

- **401**: authentication 없이 호출 → `.hasStatus(HttpStatus.UNAUTHORIZED)`
- **403**: `@PreAuthorize("hasRole('ADMIN')")` 컨트롤러를 `ROLE_USER` 권한으로 호출
- **400**: 잘못된/누락된 body·param을 보내고, **서비스가 호출되지 않았는지**까지 확인한다
  (`then(userService).shouldHaveNoInteractions()`). 상태 코드만 보면 검증을 통과한 뒤 서비스가 터진 경우와
  구분되지 않는다

---

## 직렬화 테스트

응답 봉투·DTO의 JSON 형태를 검증할 때는 `@JsonTest` 슬라이스를 쓴다.
직접 만든 `ObjectMapper`로 검증하면 애플리케이션 설정(`NON_NULL`, 타임존 등)이 반영되지 않아
**테스트는 통과하는데 실제 응답은 다른** 상황이 생긴다.

---

## 반드시 테스트할 것

성공 경로보다 아래가 우선이다.

- **경계값** — 0, 1, 상한 정확히, 상한+1
- **시간** — `Clock.fixed()`로 고정한다. `Thread.sleep`을 쓰지 않는다
- **에러 응답** — HTTP 상태와 `code`를 **둘 다** 확인한다. 상태만 보면 코드가 바뀌어도 통과한다
- **동시성** — 한도가 있는 기능(쿠폰 사용 등)은 스레드 여럿으로 확인한다
- **N+1** — SQL 로그를 켜고 쿼리 수를 직접 센다

각 Phase에서 무엇을 테스트할지는 [ROADMAP.md](ROADMAP.md)의 Phase별 **테스트** 블록에 적혀 있다.

---

## 실행

```
./gradlew test
```

전체가 `BUILD SUCCESSFUL`이어야 다음 Phase로 넘어간다.
