package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.dto.ai.AiPredictionRequestEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiPredictionConsumerTest {

    @Mock
    private PredictionResultService predictionResultService;

    @InjectMocks
    private AiPredictionConsumer aiPredictionConsumer;

    @Nested
    @DisplayName("processAiPrediction")
    class ProcessAiPrediction {

        @Test
        @DisplayName("큐 이벤트를 받으면 mock AI 응답을 만들어 PredictionResultService에 전달한다") //가장 기본적인 정상 흐름
        void processAiPredictionSuccess() { //올바른 객체를 만들어서 후속 서비스에 위임하는지
            // given
            AiPredictionRequestEvent event = new AiPredictionRequestEvent(1L, 10L, "raw-data/user-1/file.csv");
            ArgumentCaptor<AiPredictionResponse> responseCaptor = ArgumentCaptor.forClass(AiPredictionResponse.class);

            // when
            aiPredictionConsumer.processAiPrediction(event);

            // then
            verify(predictionResultService).processResult(
                    org.mockito.Mockito.eq(event.getRawDataFileId()),
                    org.mockito.Mockito.eq(event.getUserId()),
                    responseCaptor.capture()
            );

            AiPredictionResponse response = responseCaptor.getValue();
            assertThat(response.getCluster_id()).isEqualTo(1);
            assertThat(response.isRisk_cluster()).isTrue();
            assertThat(response.getConfidence()).isEqualTo(98.5);
        }

        @Test
        @DisplayName("스레드가 인터럽트된 상태면 InterruptedException을 삼키고 결과 저장을 호출하지 않는다")
        void processAiPredictionSkipsWhenThreadInterrupted() {
            // given
            AiPredictionRequestEvent event = new AiPredictionRequestEvent(1L, 10L, "raw-data/user-1/file.csv");
            Thread.currentThread().interrupt();

            try {
                // when
                aiPredictionConsumer.processAiPrediction(event);

                // then
                verify(predictionResultService, never()).processResult(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong(), org.mockito.Mockito.any());
            } finally {
                // 테스트 격리를 위해 인터럽트 상태 복원 방지
                Thread.interrupted();
            }
        }

        @Test
        @DisplayName("결과 저장 중 예외가 나면 RuntimeException으로 감싸서 던진다")
        void processAiPredictionWrapsExceptionWhenPredictionResultServiceFails() {
            // given
            AiPredictionRequestEvent event = new AiPredictionRequestEvent(1L, 10L, "raw-data/user-1/file.csv");
            doThrow(new IllegalStateException("db save failed"))
                    .when(predictionResultService)
                    .processResult(org.mockito.Mockito.eq(10L), org.mockito.Mockito.eq(1L), org.mockito.Mockito.any(AiPredictionResponse.class));

            // when & then
            assertThatThrownBy(() -> aiPredictionConsumer.processAiPrediction(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("AI 분석 실패")
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }
}
