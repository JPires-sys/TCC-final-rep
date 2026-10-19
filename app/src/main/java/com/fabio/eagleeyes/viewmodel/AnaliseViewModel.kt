package com.fabio.eagleeyes.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabio.eagleeyes.pai.ParentAnalysisRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

class AnaliseViewModel : ViewModel() {
    private val analysis = ParentAnalysisRepository()
    private val _respostaIA = MutableLiveData<String>()
    val respostaIA: LiveData<String> get() = _respostaIA
    private val _carregando = MutableLiveData(false)
    val carregando: LiveData<Boolean> get() = _carregando

    fun gerarRelatorio(uidFilho: String) {
        if (_carregando.value == true || uidFilho.isBlank()) return
        _carregando.value = true
        viewModelScope.launch {
            try {
                _respostaIA.value = analysis.analyze(
                    "Gere um relatório do jovem selecionado, considerando o tempo de tela e as conversas salvas no Chatbox. " +
                        "Apresente observações sustentadas pelos registros, limitações e sugestões de diálogo para o responsável.", uidFilho)
            } catch (error: CancellationException) {
                if (error !is TimeoutCancellationException) throw error
                _respostaIA.value = "O banco demorou para responder. Tente novamente."
            } catch (error: Exception) {
                _respostaIA.value = "Não foi possível gerar o relatório: ${error.message}"
            } finally {
                _carregando.value = false
            }
        }
    }
}
