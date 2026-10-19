package com.fabio.eagleeyes.pai.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fabio.eagleeyes.usuario.Usuario
import com.fabio.eagleeyes.repositorio.AnaliseRepositorio
import com.fabio.eagleeyes.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class HomePaiViewModel(
    private val repository: HomePaiRepository,
    analiseRepositorio: AnaliseRepositorio = AnaliseRepositorio()
) : HomeViewModel(analiseRepositorio) {

    private val _listaFilhos = MutableLiveData<List<Usuario>>()
    val listaFilhos: LiveData<List<Usuario>> get() = _listaFilhos

    private val _perfilPai = MutableLiveData<Usuario?>()
    val perfilPai: LiveData<Usuario?> get() = _perfilPai

    private var filhosJob: Job? = null

    fun carregarDadosIniciais(uid: String) {
        viewModelScope.launch {
            _carregando.value = true
            try {
                val pai = repository.buscarPai(uid)
                _perfilPai.value = pai
                
                carregarFilhos(uid)
            } catch (e: Exception) {
                _erro.value = "Erro ao carregar perfil: ${e.localizedMessage}"
            } finally {
                _carregando.value = false
            }
        }
    }

    fun carregarFilhos(uidPai: String) {
        filhosJob?.cancel()
        filhosJob = viewModelScope.launch {
            _carregando.value = true
            try {
                repository.observarFilhos(uidPai).collect { filhos ->
                    _listaFilhos.value = filhos
                    _erro.value = ""
                    _carregando.value = false
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _erro.value = "Não foi possível buscar os filhos: ${error.localizedMessage}"
            } finally {
                _carregando.value = false
            }
        }
    }

    private val parentAnalysis = com.fabio.eagleeyes.pai.ParentAnalysisRepository()
    private var analysisJob: Job? = null

    fun analisarComportamentoComIA(pergunta: String) {
        if (analysisJob?.isActive == true || pergunta.isBlank()) return
        analysisJob = viewModelScope.launch {
            _carregando.value = true
            _erro.value = ""
            try {
                _respostaIA.value = parentAnalysis.analyze(pergunta.trim())
            } catch (error: CancellationException) {
                if (error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                _erro.value = "O banco demorou para responder. Tente novamente."
            } catch (error: Exception) {
                _erro.value = "Erro na análise: ${error.message}"
            } finally {
                _carregando.value = false
            }
        }
    }
}
