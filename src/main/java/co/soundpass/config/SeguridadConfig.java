package co.soundpass.config;

import co.soundpass.seguridad.JwtFiltro;
import co.soundpass.seguridad.RespuestaErrorSeguridad;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Quién puede llamar a qué (mismo criterio que x-acceso en docs/openapi.yaml):
 * lecturas del catálogo y registro/login son públicos, las escrituras del catálogo, los tipos de entrada y el
 * check-in son de ADMIN, y todo lo demás exige estar autenticado. Se decide aquí, en la cadena de filtros, para que
 * un usuario sin permiso reciba 401/403 antes de que se valide el cuerpo de la solicitud. Los controladores de
 * ADMIN conservan además @PreAuthorize como segunda barrera.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    private static final List<HttpMethod> ESCRITURAS =
            List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, JwtFiltro jwtFiltro, RespuestaErrorSeguridad errores)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(errores).accessDeniedHandler(errores))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    a.requestMatchers(HttpMethod.POST, "/api/v1/auth/registro", "/api/v1/auth/login").permitAll();
                    a.requestMatchers(HttpMethod.GET, "/api/v1/eventos/*/ventas", "/api/v1/logs/**").hasRole("ADMIN");
                    a.requestMatchers(HttpMethod.POST, "/api/v1/eventos/*/cola/turnos").authenticated();
                    a.requestMatchers(HttpMethod.GET, "/api/v1/eventos/*/cola/turnos/mio").authenticated();
                    a.requestMatchers(HttpMethod.DELETE, "/api/v1/eventos/*/cola/turnos/mio").authenticated();
                    a.requestMatchers(HttpMethod.GET,
                            "/api/v1/artistas/**", "/api/v1/escenarios/**", "/api/v1/eventos/**").permitAll();
                    a.requestMatchers(HttpMethod.POST, "/api/v1/eventos/*/resenas").authenticated();
                    for (HttpMethod escritura : ESCRITURAS) {
                        a.requestMatchers(escritura,
                                "/api/v1/artistas/**", "/api/v1/escenarios/**", "/api/v1/eventos/**",
                                "/api/v1/tipos-entrada/**", "/api/v1/checkins/**").hasRole("ADMIN");
                    }
                    a.anyRequest().authenticated();
                })
                .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
