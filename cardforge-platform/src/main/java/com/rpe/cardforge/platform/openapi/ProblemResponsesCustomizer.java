package com.rpe.cardforge.platform.openapi;

import com.rpe.cardforge.platform.problem.Problems;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Publica no OpenAPI de cada serviço os esquemas comuns do contrato C6 ({@code ProblemDetail},
 * {@code InvalidField}), as respostas reutilizáveis 400, 401 (com {@code WWW-Authenticate}) e 403
 * em todas as operações, e o catálogo dos tipos de erro com a ação esperada do cliente.
 */
public class ProblemResponsesCustomizer implements OpenApiCustomizer {

  public static final String PROBLEM_SCHEMA = "#/components/schemas/ProblemDetail";

  private static final String PROBLEM_TYPES =
      """

      ### Erros (`application/problem+json`, contrato C6)

      O `type` é `%s<causa>`, estável. Nenhum erro contém CPF, data de nascimento, PAN ou o \
      payload original; todo erro traz `correlationId`.

      | Causa (`type`) | Status | Ação esperada do cliente |
      |---|---|---|
      | `malformed-request` | 400 | Corrigir o JSON, os tipos ou os parâmetros |
      | `invalid-header` | 400 | Corrigir o header |
      | `unauthorized` | 401 | Renovar o token (`WWW-Authenticate` presente) |
      | `forbidden` | 403 | Corrigir os escopos do cliente |
      | `resource-not-found` | 404 | Verificar o ID |
      | `method-not-allowed` | 405 | Não há exclusão física |
      | `validation-failed` | 422 | Corrigir os campos listados em `invalidFields` |
      | `bin-already-registered` | 409 | Usar outro BIN (product) |
      | `bin-immutable` | 422 | Remover `bin` da atualização (product) |
      | `product-canceled-read-only` | 409 | Desistir: produto cancelado não é editável (product) |
      | `invalid-status-transition` | 409 | Desistir: transição não permitida a partir do status atual |
      | `cpf-already-registered` | 409 | Desistir e informar o parceiro; não repetir (cardholder) |
      | `product-not-found` | 422 | Informar que o produto não existe (cardholder) |
      | `product-canceled` | 422 | Informar que o produto foi descontinuado (cardholder) |
      | `dependency-unavailable` | 503 | Repetir depois |
      """
          .formatted(Problems.TYPE_BASE);

  @Override
  public void customise(OpenAPI openApi) {
    Components components = openApi.getComponents();
    if (components == null) {
      components = new Components();
      openApi.setComponents(components);
    }
    components.addSchemas("InvalidField", invalidField());
    components.addSchemas("ProblemDetail", problemDetail());
    components.addResponses(
        "BadRequest",
        problem(
            "malformed-request ou invalid-header: JSON malformado, tipo incompatível ou parâmetro inválido"));
    components.addResponses(
        "Unauthorized",
        problem(
                "unauthorized: token ausente, inválido, expirado ou com emissor ou audiência errados")
            .addHeaderObject(
                "WWW-Authenticate",
                new Header()
                    .description("Bearer, com error e error_description conforme RFC 6750")
                    .schema(new StringSchema())));
    components.addResponses("Forbidden", problem("forbidden: o token não tem o escopo exigido"));

    if (openApi.getPaths() != null) {
      openApi
          .getPaths()
          .values()
          .forEach(
              path ->
                  path.readOperations()
                      .forEach(
                          operation -> {
                            var responses = operation.getResponses();
                            responses.putIfAbsent("400", ref("BadRequest"));
                            responses.putIfAbsent("401", ref("Unauthorized"));
                            responses.putIfAbsent("403", ref("Forbidden"));
                          }));
    }

    Info info = openApi.getInfo() == null ? new Info() : openApi.getInfo();
    String description = info.getDescription() == null ? "" : info.getDescription();
    if (!description.contains("contrato C6")) {
      info.setDescription(description + PROBLEM_TYPES);
    }
    openApi.setInfo(info);
  }

  private static ApiResponse ref(String name) {
    return new ApiResponse().$ref("#/components/responses/" + name);
  }

  private static ApiResponse problem(String description) {
    return new ApiResponse()
        .description(description)
        .content(
            new Content()
                .addMediaType(
                    "application/problem+json",
                    new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA))));
  }

  private static Schema<?> invalidField() {
    return new ObjectSchema()
        .required(List.of("field", "rule"))
        .addProperty("field", new StringSchema().example("cpf"))
        .addProperty("rule", new StringSchema().example("CPF_CHECK_DIGITS"))
        .addProperty("message", new StringSchema());
  }

  private static Schema<?> problemDetail() {
    return new ObjectSchema()
        .required(List.of("type", "title", "status"))
        .addProperty(
            "type",
            new StringSchema().format("uri").example(Problems.TYPE_BASE + "validation-failed"))
        .addProperty("title", new StringSchema())
        .addProperty("status", new IntegerSchema())
        .addProperty(
            "detail",
            new StringSchema()
                .description("Nunca contém CPF, data de nascimento, PAN ou o payload original"))
        .addProperty("instance", new StringSchema())
        .addProperty("correlationId", new StringSchema())
        .addProperty(
            "invalidFields",
            new ArraySchema()
                .items(new Schema<>().$ref("#/components/schemas/InvalidField"))
                .description("Presente em validation-failed; lista todas as violações"));
  }
}
