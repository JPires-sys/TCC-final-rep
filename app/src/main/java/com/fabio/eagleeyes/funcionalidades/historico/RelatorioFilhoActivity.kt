package com.fabio.eagleeyes.funcionalidades.historico

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.fabio.eagleeyes.databinding.ActivityRelatorioFilhoBinding
import com.fabio.eagleeyes.filho.detalhes.DetalhesFilhoViewModel
import com.fabio.eagleeyes.viewmodel.AnaliseViewModel
import com.fabio.eagleeyes.funcionalidades.historico.HistoricoAdapter
import com.fabio.eagleeyes.global.FirebaseConfig

class RelatorioFilhoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRelatorioFilhoBinding
    private lateinit var viewModelDetalhes: DetalhesFilhoViewModel
    private lateinit var viewModelIA: AnaliseViewModel
    private lateinit var adapter: HistoricoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inflar o binding
        binding = ActivityRelatorioFilhoBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. Recuperar dados do Intent
        val nomeFilho = intent.getStringExtra("FILHO_NOME") ?: "Filho"
        val uidFilho = intent.getStringExtra("FILHO_UID") ?: ""
        val emailFilho = intent.getStringExtra("FILHO_EMAIL") ?: ""

        // 3. Atribuir dados às Views via binding
        binding.txtNomeFilho.text = nomeFilho
        binding.txtEmailFilho.text = emailFilho

        viewModelDetalhes = ViewModelProvider(this).get(DetalhesFilhoViewModel::class.java)
        viewModelIA = ViewModelProvider(this).get(AnaliseViewModel::class.java)

        setupRecyclerView()
        setupObservers()

        val responsavel = FirebaseConfig.getAuth().currentUser?.uid?.let { it != uidFilho } == true
        binding.btnGerarRelatorioIA.visibility = if (responsavel && uidFilho.isNotBlank()) View.VISIBLE else View.GONE
        binding.txtContextoRelatorio.visibility = binding.btnGerarRelatorioIA.visibility
        binding.btnGerarRelatorioIA.setOnClickListener { viewModelIA.gerarRelatorio(uidFilho) }
        viewModelIA.carregando.observe(this) { loading ->
            binding.progressBarHistorico.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnGerarRelatorioIA.isEnabled = !loading
        }
        viewModelIA.respostaIA.observe(this) { response ->
            binding.cardRespostaIA.visibility = View.VISIBLE
            binding.txtRespostaIA.text = response
        }

        if (uidFilho.isNotEmpty()) {
            viewModelDetalhes.carregarHistorico(uidFilho)
        }
    }

    private fun setupRecyclerView() {
        binding.rvHistorico.layoutManager = LinearLayoutManager(this)
    }

    private fun setupObservers() {
        viewModelDetalhes.historico.observe(this) { lista ->
            adapter = HistoricoAdapter(lista)
            binding.rvHistorico.adapter = adapter
        }
    }
}
