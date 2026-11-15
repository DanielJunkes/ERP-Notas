package com.example.erp.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Swagger em /swagger-ui.html. Use o botão "Authorize" com o token de /api/auth/login. */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "ERP Notas", version = "v1",
                description = "ERP multiempresa de estudos. A emissão de NFS-e é SIMULADA e não possui validade fiscal."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
