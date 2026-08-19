package technical.softdesign.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import technical.softdesign.api.common.dtos.UserInfoResponse;

@Service
public class UserInfoService {

    private final RestClient restClient;

    public UserInfoService(RestClient.Builder builder,
                            @Value("${app.user-info.base-url}") String baseUrl) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public UserInfoResponse findByCpf(String cpf) {
        return restClient.get()
                .uri("/users/{cpf}", cpf)
                .retrieve()
                .body(UserInfoResponse.class);
    }
}
