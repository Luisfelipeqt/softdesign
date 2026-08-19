package technical.softdesign.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import technical.softdesign.api.common.dtos.Resultado;
import technical.softdesign.api.common.dtos.UserInfoResponse;
import technical.softdesign.api.common.dtos.VotoRequest;
import technical.softdesign.core.entities.PautaEntity;
import technical.softdesign.core.entities.SessaoVotacaoEntity;
import technical.softdesign.core.entities.VotoEntity;
import technical.softdesign.core.entities.Voto;
import technical.softdesign.core.exceptions.NegocioException;
import technical.softdesign.core.exceptions.RecursoNaoEncontradoException;
import technical.softdesign.core.repositories.PautaRepository;
import technical.softdesign.core.repositories.SessaoVotacaoRepository;
import technical.softdesign.core.repositories.VotoRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static technical.softdesign.api.common.dtos.UserInfoResponse.Status.ABLE_TO_VOTE;
import static technical.softdesign.core.entities.Voto.SIM;

@ExtendWith(MockitoExtension.class)
class PautaServiceTest {

    private static final String CPF = "12345678909";

    @Mock
    private PautaRepository pautaRepository;

    @Mock
    private SessaoVotacaoRepository sessaoVotacaoRepository;

    @Mock
    private VotoRepository votoRepository;

    @Mock
    private UserInfoService userInfoService;

    private PautaService pautaService;

    private PautaEntity pauta;

    private UUID pautaId;

    @BeforeEach
    void setUp() {
        pautaService = new PautaService(pautaRepository, sessaoVotacaoRepository, votoRepository, userInfoService);
        ReflectionTestUtils.setField(pautaService, "duracaoPadraoMinutos", 1);

        pautaId = UUID.randomUUID();
        pauta = new PautaEntity();
        pauta.setId(pautaId);
        pauta.setTituloPauta("Titulo");
    }

    @Test
    void deveAbrirSessaoComDuracaoPadraoQuandoNaoInformada() {
        when(pautaRepository.findById(pautaId)).thenReturn(Optional.of(pauta));
        when(sessaoVotacaoRepository.existsByPautaId(pautaId)).thenReturn(false);
        when(sessaoVotacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = pautaService.abrirSessao(pautaId, null);

        assertThat(response.dataFechamento()).isAfter(response.dataAbertura());
    }

    @Test
    void naoDeveAbrirSessaoQuandoPautaJaPossuiSessao() {
        when(pautaRepository.findById(pautaId)).thenReturn(Optional.of(pauta));
        when(sessaoVotacaoRepository.existsByPautaId(pautaId)).thenReturn(true);

        assertThatThrownBy(() -> pautaService.abrirSessao(pautaId, null))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void naoDeveAbrirSessaoQuandoPautaNaoExiste() {
        when(pautaRepository.findById(pautaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pautaService.abrirSessao(pautaId, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveRegistrarVotoQuandoAssociadoAptoENaoVotouAinda() {
        var sessao = sessaoAberta();
        when(sessaoVotacaoRepository.findByPautaId(pautaId)).thenReturn(Optional.of(sessao));
        when(votoRepository.existsBySessaoVotacaoIdAndAssociadoId(sessao.getId(), CPF)).thenReturn(false);
        when(userInfoService.findByCpf(CPF)).thenReturn(new UserInfoResponse(ABLE_TO_VOTE));

        pautaService.votar(pautaId, new VotoRequest(CPF, SIM));

        verify(votoRepository).save(any(VotoEntity.class));
    }

    @Test
    void naoDeveRegistrarVotoQuandoAssociadoJaVotou() {
        var sessao = sessaoAberta();
        when(sessaoVotacaoRepository.findByPautaId(pautaId)).thenReturn(Optional.of(sessao));
        when(votoRepository.existsBySessaoVotacaoIdAndAssociadoId(sessao.getId(), CPF)).thenReturn(true);

        assertThatThrownBy(() -> pautaService.votar(pautaId, new VotoRequest(CPF, SIM)))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void naoDeveRegistrarVotoQuandoSessaoEncerrada() {
        var sessao = new SessaoVotacaoEntity();
        sessao.setId(UUID.randomUUID());
        sessao.setPauta(pauta);
        sessao.setDataAbertura(LocalDateTime.now().minusMinutes(5));
        sessao.setDataFechamento(LocalDateTime.now().minusMinutes(1));
        when(sessaoVotacaoRepository.findByPautaId(pautaId)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> pautaService.votar(pautaId, new VotoRequest(CPF, SIM)))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void naoDeveRegistrarVotoQuandoAssociadoNaoAptoAVotar() {
        var sessao = sessaoAberta();
        when(sessaoVotacaoRepository.findByPautaId(pautaId)).thenReturn(Optional.of(sessao));
        when(votoRepository.existsBySessaoVotacaoIdAndAssociadoId(sessao.getId(), CPF)).thenReturn(false);
        when(userInfoService.findByCpf(CPF)).thenReturn(new UserInfoResponse(UserInfoResponse.Status.UNABLE_TO_VOTE));

        assertThatThrownBy(() -> pautaService.votar(pautaId, new VotoRequest(CPF, SIM)))
                .isInstanceOf(NegocioException.class);

        verify(votoRepository, never()).save(any());
    }

    @Test
    void deveTratarCpfInvalidoComoNegocioException() {
        var sessao = sessaoAberta();
        when(sessaoVotacaoRepository.findByPautaId(pautaId)).thenReturn(Optional.of(sessao));
        when(votoRepository.existsBySessaoVotacaoIdAndAssociadoId(sessao.getId(), CPF)).thenReturn(false);
        when(userInfoService.findByCpf(CPF)).thenThrow(HttpClientErrorException.NotFound.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> pautaService.votar(pautaId, new VotoRequest(CPF, SIM)))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void deveCalcularResultadoAprovado() {
        var sessao = sessaoAberta();
        when(pautaRepository.findById(pautaId)).thenReturn(Optional.of(pauta));
        when(sessaoVotacaoRepository.findByPautaId(pautaId)).thenReturn(Optional.of(sessao));
        when(votoRepository.countBySessaoVotacaoIdAndVoto(sessao.getId(), SIM)).thenReturn(3L);
        when(votoRepository.countBySessaoVotacaoIdAndVoto(sessao.getId(), Voto.NAO)).thenReturn(1L);

        var resultado = pautaService.resultado(pautaId);

        assertThat(resultado.resultado()).isEqualTo(Resultado.APROVADA);
        assertThat(resultado.votosSim()).isEqualTo(3L);
        assertThat(resultado.votosNao()).isEqualTo(1L);
    }

    private SessaoVotacaoEntity sessaoAberta() {
        var sessao = new SessaoVotacaoEntity();
        sessao.setId(UUID.randomUUID());
        sessao.setPauta(pauta);
        sessao.setDataAbertura(LocalDateTime.now());
        sessao.setDataFechamento(LocalDateTime.now().plusMinutes(1));
        return sessao;
    }
}