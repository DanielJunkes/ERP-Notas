# ERP Notas

ERP multiempresa de estudos para gerenciar empresas, clientes e serviços e emitir **Notas Fiscais de Serviço Eletrônicas (NFS-e)**.

> ⚠️ **A emissão é SIMULADA.** O sistema não se comunica com a Receita Federal nem com a SEFIN Nacional.
> Todo documento gerado é marcado como `SIMULACAO - DOCUMENTO SEM VALIDADE FISCAL`.

## Tecnologias

- Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation)
- JWT via Spring Security OAuth2 Resource Server (HS256), senhas com BCrypt
- PostgreSQL (aplicação) e H2 em memória (testes)
- springdoc-openapi (Swagger UI), Lombok
- JUnit 5, Mockito, MockMvc, Maven (wrapper incluído)

## Requisitos

- JDK 21 ou superior
- PostgreSQL 14 ou superior (instalado localmente ou via Docker)
- Não é preciso instalar o Maven: use `./mvnw` (Linux/macOS/Git Bash) ou `mvnw.cmd` (Windows)

## Configuração do PostgreSQL

A aplicação espera, por padrão, o banco `erp_notas` com usuário `erp` e senha `erp` em `localhost:5432`.

**Opção A, PostgreSQL instalado:** conecte como superusuário (`psql -U postgres` ou pelo pgAdmin) e rode:

```sql
CREATE USER erp WITH PASSWORD 'erp';
CREATE DATABASE erp_notas OWNER erp;
```

**Opção B, Docker:**

```bash
docker compose up -d
```

As tabelas são criadas automaticamente pelo Hibernate na primeira execução (`ddl-auto: update`).

Para usar outro banco ou outras credenciais, defina as variáveis de ambiente:

| Variável | Padrão | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/erp_notas` | URL JDBC |
| `DB_USER` / `DB_PASSWORD` | `erp` / `erp` | credenciais |
| `JWT_SECRET` | valor de estudo | segredo HS256 (mínimo de 32 caracteres); **troque fora do ambiente local** |

## Como executar

```bash
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`, e o Swagger fica em `http://localhost:8080/swagger-ui.html`.

### Front end

Abra `http://localhost:8080/`. É uma tela única em HTML, CSS e JavaScript puro (`src/main/resources/static`), servida pelo próprio Spring Boot: não precisa de Node, build nem CORS. Pela tela é possível:

- entrar e escolher a empresa de trabalho (botão **Acessar** na tela Empresas);
- cadastrar empresas e usuários e liberar empresas para usuários Normal (ADMIN e Gerente);
- gerenciar clientes e serviços da empresa escolhida;
- criar notas, **emitir**, **cancelar** e abrir os **detalhes**: uma tela com todos os dados da nota (prestador, tomador, serviço, valores, tributação e retorno da emissão) e botões para **salvar o XML** da NFS-e e da DPS.

O token fica no `sessionStorage` da aba e some ao fechá-la. A tela esconde os botões que o papel do usuário não permite, mas quem garante as permissões é sempre a API.

Os dados ficam gravados no PostgreSQL e continuam lá entre uma execução e outra.

**Rodar sem PostgreSQL (só para experimentar):** usa o H2 em memória dos testes, e os dados somem quando a aplicação para.

```bash
DB_URL="jdbc:h2:mem:erp;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE" DB_USER=sa DB_PASSWORD= \
SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop ./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true
```

Testes (não precisam de PostgreSQL):

```bash
./mvnw test
```

## Usuários, empresas e autenticação

Na primeira execução a aplicação cria, se ainda não existirem (`config/DadosIniciais`):

| | |
|---|---|
| Usuário ADMIN | e-mail `adm@gmail.com`, senha `adim` |
| Empresa | `Empresa Demonstração LTDA`, CNPJ `11.444.777/0001-61` |

> A senha `adim` é fixa e de estudo: troque-a ou remova a carga inicial antes de usar fora da máquina local.

Não há cadastro público. O fluxo é:

1. O **ADMIN** cria usuários do tipo **Gerente** ou **Normal** (`POST /api/usuarios`). Um gerente só cria usuários Normal.
2. O usuário entra (`POST /api/auth/login`) e recebe um token **ainda sem empresa**.
3. O **Gerente** (ou o ADMIN) cadastra empresas (`POST /api/empresas`) e define quais delas cada usuário Normal pode acessar (`empresaIds` em `PUT /api/usuarios/{id}`).
4. O usuário escolhe a empresa de trabalho em `POST /api/empresas/{id}/acessar`, que confere o acesso e devolve um **novo token com o `empresaId`**.
5. Clientes, serviços e notas usam sempre a empresa do token. Sem empresa escolhida respondem `403`, e um id de outra empresa responde `404`.

Envie `Authorization: Bearer <token>` nas requisições. No Swagger, use o botão **Authorize**. O token vale 120 minutos.

| Tipo | Empresas | Pode |
|---|---|---|
| `ADMIN` | todas | tudo, inclusive criar gerentes e ver todos os usuários |
| `GERENTE` | só as que cadastrou | cadastrar, alterar e desativar as próprias empresas; criar usuários Normal e liberar para eles as próprias empresas; cancelar notas |
| `NORMAL` | só as liberadas para ele | clientes, serviços e notas (criar, emitir e consultar) |

## Exemplos de requisições

```bash
# 1. Login do ADMIN e criação de um gerente
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email": "adm@gmail.com", "senha": "adim"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s -X POST localhost:8080/api/usuarios -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nome": "Gerente", "email": "gerente@empresa.com", "senha": "senha1234", "role": "GERENTE"}'

# 2. Login do gerente, cadastro da empresa e acesso a ela (o novo token traz a empresa)
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email": "gerente@empresa.com", "senha": "senha1234"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s -X POST localhost:8080/api/empresas -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{
  "razaoSocial": "Minha Empresa LTDA", "cnpj": "11.222.333/0001-81", "inscricaoMunicipal": "12345",
  "endereco": {"codigoMunicipio": "4205407", "uf": "SC", "cep": "88000000"}}'
TOKEN=$(curl -s -X POST localhost:8080/api/empresas/2/acessar -H "Authorization: Bearer $TOKEN" \
  | sed 's/.*"token":"\([^"]*\)".*/\1/')

# 3. Cliente e serviço
curl -s -X POST localhost:8080/api/clientes -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nome": "Maria Silva", "documento": "529.982.247-25"}'
curl -s -X POST localhost:8080/api/servicos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"descricao": "Consultoria em TI", "codigoServico": "01.01.01", "valorPadrao": 1000.00}'

# 4. Nota fiscal (PENDENTE): número e valor líquido calculados pelo backend
curl -s -X POST localhost:8080/api/notas-fiscais -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"clienteId": 1, "servicoId": 1, "valorDesconto": 150.00}'

# 5. Emitir, consultar o resultado detalhado e cancelar
curl -s -X POST localhost:8080/api/notas-fiscais/1/emitir   -H "Authorization: Bearer $TOKEN"
curl -s        localhost:8080/api/notas-fiscais/1/emissao   -H "Authorization: Bearer $TOKEN"
curl -s -X POST localhost:8080/api/notas-fiscais/1/cancelar -H "Authorization: Bearer $TOKEN"
```

Os erros seguem o formato padrão `ProblemDetail` (RFC 7807):

| Código | Quando |
|---|---|
| 400 | dados inválidos, com a lista de campos em `erros` |
| 401 | login inválido ou requisição sem token |
| 403 | tipo de usuário sem permissão, ou nenhuma empresa escolhida |
| 404 | registro inexistente ou de outra empresa |
| 409 | CNPJ ou e-mail duplicado, ou cliente com notas |
| 422 | regra de negócio violada |

## Endpoints

| Recurso | Endpoints | Observações |
|---|---|---|
| Auth | `POST /api/auth/login` | público; token sem empresa |
| Empresas | `POST`, `GET`, `GET /{id}`, `POST /{id}/acessar`, `PUT /{id}`, `DELETE /{id}` | `GET` lista as empresas acessíveis; `acessar` devolve o token com a empresa; criar, alterar e desativar: ADMIN e Gerente (dono); `DELETE` **desativa** |
| Usuários | `POST`, `GET`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}` | ADMIN (todos) e Gerente (os que criou); `role` NORMAL ou GERENTE; `empresaIds` = empresas liberadas a um Normal; `DELETE` desativa; ninguém se desativa nem muda o próprio tipo |
| Clientes | `POST`, `GET`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}` | documento opcional (CPF ou CNPJ); não exclui cliente com notas |
| Serviços | `POST`, `GET`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}` | `codigoServico` (lista municipal) e `codigoTributacaoNacional` (cTribNac) precisam estar nas listas (senão `422`); `codigoNbs`, `aliquotaIss` e o trio IBS/CBS são opcionais; `DELETE` desativa |
| Tabelas fiscais | `GET /api/itens-servico?busca=` e `GET /api/itens-servico/{tabela}?busca=` | tabelas fixas (ver abaixo); `{tabela}` = `nacionais`, `nbs`, `indicadores-operacao`, `cst`, `cclasstrib` ou `municipios`; `busca` filtra pelo começo do código (`01.01` ou `0101`) ou por parte da descrição, sem diferenciar acentos |
| Notas | `POST`, `GET`, `GET /{id}`, `POST /{id}/emitir`, `POST /{id}/cancelar`, `GET /{id}/emissao` | cancelar só ADMIN e Gerente; não há edição direta |

## Como funciona a emissão simulada

```
POST /notas-fiscais  ──▶  PENDENTE ──emitir──▶ AUTORIZADA ──cancelar──▶ CANCELADA
                                       └─────▶ REJEITADA ──emitir de novo (após corrigir)──▶ ...
```

1. **Criação:** valida se cliente e serviço são da empresa e se o serviço está ativo. Calcula `valorLiquido = valorServico − valorDesconto` (o desconto não pode passar do valor) e atribui o próximo número da empresa, com a linha da empresa travada para não repetir o número. Também grava os dados da DPS, com padrões e regras do leiaute (ver abaixo).
2. **Emissão:** o `NotaFiscalService` gera o XML da DPS (`DpsXml`) e o envia à interface `EmissorNfse`. Hoje a única implementação é o `SimuladorEmissorNfse`, que:
   - lê o XML com DOCTYPE bloqueado (proteção contra XXE) e confere o leiaute DPS;
   - valida campos obrigatórios, formatos, o `Id` e as regras de negócio do Anexo I que não dependem de cadastros da Receita, citando o código de erro oficial (ex.: `E0004`, `E0580`);
   - **rejeita** se algo falhar, e também quando a descrição contém `[REJEITAR]` (rejeição forçada, útil para testar);
   - **autoriza** nos demais casos, devolvendo a NFS-e no leiaute nacional (ver abaixo), com o número da DPS como número da NFS-e e a chave de acesso de 50 dígitos (gravada em `codigoVerificacao`).
3. A nota grava o status, a mensagem de retorno, o XML da DPS e o XML da NFS-e. Tudo isso pode ser consultado em `GET /api/notas-fiscais/{id}/emissao`, e o XML pode ser salvo pela tela de detalhes.

### NFS-e simulada no leiaute nacional (importação no Domínio)

A NFS-e devolvida pelo simulador segue o leiaute da **NFS-e Nacional v1.01** (Anexo I, linhas 1 a 97): raiz `NFSe`, grupo `infNFSe` e a DPS dentro dele. É o formato do arquivo XML do Portal Nacional que o Domínio importa ("Importação de NFS-e Padrão Nacional").

| Grupo | Como é gerado |
|---|---|
| `Id` / chave | `"NFS"` + município emissor (7) + ambiente gerador `2` + tipo de inscrição `2` + CNPJ (14) + `nNFSe` (13) + AAMM da emissão (4) + código aleatório (9) + DV módulo 11 |
| `xLocEmi`, `xLocPrestacao`, `cLocIncid` | nomes da tabela do IBGE; incidência do ISSQN conforme a Lista Nacional (estabelecimento do prestador, local da prestação ou domicílio do tomador); sem incidência em imunidade e não incidência |
| `emit` | cadastro da empresa, que faz o papel do CNC: razão social, endereço completo (obrigatório), UF, telefone e e-mail |
| `valores` | `vBC` = serviço − desconto; `pAliqAplic` e `vISSQN` só quando o serviço tem alíquota; `vTotalRet` quando o ISS é retido; `vLiq` = serviço − desconto − retenções |
| `IBSCBS` | `vBC` = serviço − desconto − ISSQN; alíquotas de `app.nfse.simulacao.*` (padrão CBS 0,90%, IBS estadual 0,10%, municipal 0%) com a redução do `cClassTrib`; zero quando o CST não exige tributo; `vTotNF` = `vLiq` em 2026 |
| `xOutInf` | `SIMULACAO - DOCUMENTO SEM VALIDADE FISCAL` |

> **Antes de importar em outro sistema:** a NFS-e **não é assinada**, sai com `tpAmb = 2` (homologação) e **não tem validade fiscal**. Importe só numa empresa de teste. O algoritmo do DV da chave não está no Anexo I (usa o da NF-e) e as alíquotas do IBS/CBS são de configuração, não da Calculadora da Receita: confira os dois.

### Leiaute da DPS

A DPS segue o leiaute **DPS v1.01** do Anexo I da NFS-e Nacional (`anexo_i-sefin_adn-dps_nfse-snnfse-v1-01-20260209.xlsx`): namespace `http://www.sped.fazenda.gov.br/nfse`, ordem dos grupos e `Id` = `"DPS"` + município (7) + tipo de inscrição (1) + CNPJ (14) + série (5) + número (15). Ela sai com `tpAmb = 2` (homologação) e um comentário `SIMULACAO - DOCUMENTO SEM VALIDADE FISCAL`, **sem assinatura e sem envio à Sefin**.

| Grupo da DPS | De onde vem |
|---|---|
| `infDPS` (`tpAmb`, `dhEmi`, `verAplic`, `serie`, `nDPS`, `dCompet`, `tpEmit`, `cLocEmi`) | número da nota, data de competência (padrão: hoje) e código IBGE da empresa; `tpEmit = 1` (prestador) e série 1 |
| `prest` (`CNPJ`, `IM`, `fone`, `email`, `regTrib`) | empresa: CNPJ, inscrição municipal e regime tributário (opção pelo Simples, regime de apuração, regime especial). Sem `xNome` nem endereço, como manda a regra E0121 |
| `toma` | cliente: CPF ou CNPJ, nome, endereço (só se completo), telefone e e-mail. Sem CPF/CNPJ, o grupo não vai |
| `serv` (`locPrest`, `cServ`, `obra`, `atvEvento`) | município da prestação (padrão: o da empresa), `cTribNac`, descrição e `cNBS` do serviço; código da obra ou dados do evento quando a Lista Nacional exige |
| `valores` (`vServ`, `vDescIncond`, `tribMun`, `totTrib`) | valor e desconto da nota; tributação e retenção do ISSQN, tipo de imunidade, alíquota do serviço; `totTrib` = `pTotTribSN` para ME/EPP, senão `indTotTrib = 0` |
| `IBSCBS` (`finNFSe`, `cIndOp`, `indDest`, `CST`, `cClassTrib`) | serviço (os três códigos juntos). Obrigatório para não optantes a partir de 2026 e para o Simples a partir de 2027 (nota do Anexo I) |

**Não suportado nesta versão:** exportação de serviço (`comExt`), intermediário (`interm`), substituição de NFS-e (`subst`), deduções (`vDedRed`), tributos federais (`tribFed`), benefício municipal, suspensão de exigibilidade e os grupos opcionais de IBS/CBS (destinatário, imóvel, reembolsos, diferimento).

### Tabelas fiscais

Ficam em `src/main/resources` e são conferidas no cadastro do serviço (`422`) e de novo pelo simulador, com o código de erro do Anexo I.

| Tabela | Arquivo | Origem | Itens |
|---|---|---|---|
| Itens de serviço municipais (LC 116) | `itens-servico.tsv` | idealsoftwares.com.br, tabela 87, sem os revogados | 990 |
| `cTribNac` (Lista Nacional de Serviços) | `codigos-tributacao-nacional.tsv` | aba "MUN.INCID_INFO.SERV." do Anexo I (DPS v1.01); marca os serviços que exigem obra, evento ou endereço do tomador | 337 |
| `cNBS` (E0316) | `codigos-nbs.tsv` | NBS 2.0, Anexo I da Portaria Conjunta RFB/SCS (dez/2018); só os códigos completos de 9 dígitos | 920 |
| `cIndOp` (E0901) | `indicadores-operacao.tsv` | `Tabela-indOper.xlsx`, só os vigentes; marca os 13 que exigem tomador com endereço (E0187/E0234) | 39 |
| `CST` do IBS/CBS | `cst-ibs-cbs.tsv` | Portal da Conformidade Fácil (SVRS), CSTs que têm `cClassTrib` válido para NFS-e | 12 |
| `cClassTrib` (E0958) | `cclasstrib-ibs-cbs.tsv` | Portal da Conformidade Fácil (SVRS), só os válidos para NFS-e e vigentes em 15/11/2026, com o CST de cada um | 73 |

| Municípios (código IBGE) | `municipios-ibge.tsv` | ibge.gov.br/explica/codigos-dos-municipios.php (código, nome e UF) | 5.571 |

Na tela, cada código é um campo com busca, e escolher o `cClassTrib` preenche o CST. O município é escolhido pelo nome: o código IBGE é o que fica salvo, e cidade e UF são preenchidas pela tabela (o back end também as grava a partir do código, ignorando o que vier na requisição, e recusa código inexistente com `422`). As tabelas não se atualizam sozinhas: quando a Receita ou o portal publicarem uma versão nova, gere o arquivo de novo.

### Integração oficial (etapa futura)

Crie outra implementação de `EmissorNfse` (ambiente `PRODUCAO_RESTRITA`), ativada por `app.nfse.ambiente`, seguindo a documentação técnica vigente da NFS-e Nacional: XSD oficial, assinatura digital com certificado e-CNPJ e endpoints oficiais. O `NotaFiscalService` não precisa mudar. Pontos de atenção:

- Separar a chamada HTTP da transação do banco. Hoje a chamada acontece dentro da transação, o que só é aceitável para o simulador local.
- Validar a DPS contra o XSD oficial (não incluído no projeto) e assiná-la com o certificado e-CNPJ.
- Conferir os cadastros que o simulador não confere: CNC do município, situação no Simples Nacional, convênio do município e alíquota parametrizada.

## Decisões de arquitetura

- **Pacotes por módulo** (`empresa`, `usuario`, `cliente`, `servico`, `notafiscal`), cada um com `controller → service → repository`. As regras de negócio ficam nos services e estão documentadas no Javadoc de cada um.
- **Isolamento multiempresa:** os repositories só expõem consultas com `empresaId` (`findByIdAndEmpresaId`), e o id da empresa vem de `UsuarioAutenticado`, que o lê do JWT. A empresa só entra no token depois que `EmpresaService.visivel` confere o acesso.
- **Exclusão lógica** (`ativo = false`) para empresa, usuário e serviço, preservando o histórico fiscal.
- **DTOs como records** com Bean Validation. A conversão fica no `from()` de cada Response e, para as notas, no `NotaFiscalMapper`.
- **Validação de CPF e CNPJ** própria (`Documentos`), que já aceita o **CNPJ alfanumérico**.
- **Erros** com `ResponseStatusException` e `ProblemDetail`, sem uma hierarquia de exceções própria.

## Limitações conhecidas

- Um token emitido continua válido até expirar, mesmo que o usuário seja desativado ou perca o acesso à empresa depois.
- O schema é gerado pelo Hibernate (`ddl-auto: update`). Para produção, use migrações (Flyway).
- Uma nota rejeitada por valores não pode ser editada: crie uma nova.
