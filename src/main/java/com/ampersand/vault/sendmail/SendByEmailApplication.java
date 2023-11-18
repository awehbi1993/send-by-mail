package com.ampersand.vault.sendmail;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ampersand.vault.core.exceptions.ApplicationExceptionHandler;
import com.ampersand.vault.core.redis.repos.RedisUserProfileRepository;
import com.ampersand.vault.core.security.access.AclPermissionEvaluator;
import com.ampersand.vault.core.utilities.CorsFilter;
import com.ampersand.vault.core.utilities.SecurityConfiguration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Import(ApplicationExceptionHandler.class)
@EnableJpaRepositories(basePackages = {"com.ampersand.vault.core.permissions.repos"})
@EntityScan(basePackages = {"com.ampersand.vault.core.permissions.entities", "com.ampersand.vault.core.redis.entities"})
@ComponentScan(basePackages = {"com.ampersand.vault.core.redis.repos", "com.ampersand.vault.core.redis.utils"})
@ComponentScan(basePackageClasses = { com.ampersand.vault.sendmail.EmailController.class })
@SpringBootApplication
@EnableJpaAuditing
@EnableRedisRepositories(basePackageClasses = { RedisUserProfileRepository.class })
@Configuration
@EnableTransactionManagement
public class SendByEmailApplication extends CorsFilter {
	private final static boolean disableActionsPermissionsSecurity = true;
	private final boolean disableDataPermissionsSecurity = true;
	
	public static void main(String[] args) {
		SpringApplication.run(SendByEmailApplication.class, args);
	}
	
	@Bean
	public OpenAPI springOpenAPI() {
		return new OpenAPI()
				.components(new Components().addSecuritySchemes("bearer-JWT",
																new SecurityScheme().type(SecurityScheme.Type.HTTP)
																.scheme("bearer").bearerFormat("JWT")
																.in(SecurityScheme.In.HEADER).name("Authorization")))
				.info(new Info().title("Send By E-mail Service").description("Send By E-mail API").version("v2.0.0"))				
				.addSecurityItem(new SecurityRequirement().addList("bearer-JWT", Arrays.asList("read", "write")));
	}
	
	@Bean 
    public AclPermissionEvaluator createAclPermissionEvaluator() {
    	return new AclPermissionEvaluator(disableDataPermissionsSecurity);
    }
	
	@Bean
	public SecurityConfiguration createSecurityConfiguration() {
		return new DmsSecurityConfiguration();
	}
	
	@EnableWebSecurity
	@EnableMethodSecurity
	private static class DmsSecurityConfiguration extends SecurityConfiguration {

		@SuppressWarnings("unchecked")
		@Override
		protected Jwt2AuthoritiesConverter createAuthoritiesConverter() {
			this.setDisableActionsPermissionsSecurity(disableActionsPermissionsSecurity);
			
			return jwt -> {
	            final var realmAccess = (Map<String, Object>) jwt.getClaims().getOrDefault("realm_access", Map.of());
	            final var realmRoles = (Collection<String>) realmAccess.getOrDefault("roles", List.of());

	            final var resourceAccess = (Map<String, Object>) jwt.getClaims().getOrDefault("resource_access", Map.of());
	            // We assume here you have [client-name] clients configured with "client roles" mapper in Keycloak
	            final var clientAccess = (Map<String, Object>) resourceAccess.getOrDefault("crud-application", Map.of());
	            final var clientRoles = (Collection<String>) clientAccess.getOrDefault("roles", List.of());

	            return Stream.concat(realmRoles.stream(),clientRoles.stream()).map(SimpleGrantedAuthority::new).toList();
	        };
		}
		
	}
}
