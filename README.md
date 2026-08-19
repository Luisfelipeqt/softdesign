# softdesign

API para gerenciamento de pautas e sessões de votação de uma cooperativa.

## Como executar

Suba o banco de dados (PostgreSQL) via Docker Compose:

```bash
docker-compose up -d
```

Depois rode a aplicação:

```bash
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`. Documentação Swagger em `http://localhost:8080/swagger-ui.html`.

## Rotas

| Método | Rota                          | Descrição                                   |
|--------|-------------------------------|----------------------------------------------|
| POST   | `/api/v1/pautas`              | Cadastra uma nova pauta                       |
| POST   | `/api/v1/pautas/{id}/sessao`  | Abre a sessão de votação da pauta (padrão de 1 minuto, configurável no corpo da requisição) |
| POST   | `/api/v1/pautas/{id}/votos`   | Registra o voto de um associado (CPF) na pauta |
| GET    | `/api/v1/pautas/{id}/resultado` | Contabiliza os votos e retorna o resultado  |

## Serviço externo de validação de CPF

Antes de aceitar um voto, a API consulta `https://user-info.herokuapp.com/users/{cpf}` para saber se o associado está apto a votar.

**No momento esse serviço está fora do ar** (a Heroku descontinuou apps gratuitos e este parece ter sido removido). Enquanto isso, qualquer tentativa de voto vai receber `404` dessa chamada e a API vai responder `400` com a mensagem `"CPF invalido."` — não é um bug, é reflexo do serviço externo estar indisponível.

A URL desse serviço é configurável via `app.user-info.base-url` (ou variável de ambiente `APP_USER_INFO_BASE_URL`), então basta apontar para uma instância própria ou um mock caso seja necessário testar o fluxo completo de votação.
