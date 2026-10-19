package com.fabio.eagleeyes.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.fabio.eagleeyes.SessionManager
import com.fabio.eagleeyes.databinding.ActivityLoginBinding
import com.fabio.eagleeyes.cadastro.CadastroOpcoesActivity
import com.fabio.eagleeyes.filho.home.HomeFilhoActivity
import com.fabio.eagleeyes.global.FirebaseConfig
import com.fabio.eagleeyes.global.Validator
import com.fabio.eagleeyes.pai.home.HomePaiActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        sessionManager = SessionManager(this)

        // Verifica sessão ativa antes de mostrar o login
        if (verificarSessaoAtiva()) return

        setContentView(binding.root)
        setupArchitecture()
        setupObservers()
        setupCliques()

        // Preenche e-mail salvo, se houver
        binding.edtEmail.setText(sessionManager.recuperarEmail())
    }

    private fun setupArchitecture() {
        val dao = LoginDAO()
        val repository = LoginRepository(dao)
        val factory = LoginViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[LoginViewModel::class.java]
    }

    private fun setupCliques() {
        binding.btnEntrar.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            val senha = binding.edtSenha.text.toString().trim()

            if (validarLogin()) {
                viewModel.logar(email, senha)
            }
        }

        binding.btnCadastrar.setOnClickListener {
            startActivity(Intent(this, CadastroOpcoesActivity::class.java))
        }
    }

    private fun validarLogin(): Boolean {
        val emailValido = Validator.validarEmail(binding.layoutEmail)
        val senhaValida = Validator.validarSenha(binding.layoutSenha)
        return emailValido && senhaValida
    }

    private fun setupObservers() {
        viewModel.carregando.observe(this) { estaCarregando ->
            binding.progressBar.visibility = if (estaCarregando) View.VISIBLE else View.GONE
            binding.btnEntrar.isEnabled = !estaCarregando
            binding.layoutEmail.isEnabled = !estaCarregando
            binding.layoutSenha.isEnabled = !estaCarregando
        }

        viewModel.sucesso.observe(this) { tipo ->
            tipo?.let {
                val email = binding.edtEmail.text.toString().trim().lowercase()
                sessionManager.salvarDadosLogin(email, it)
                navegarParaHome(it, email)
            }
        }

        viewModel.erro.observe(this) { mensagem ->
            mensagem?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun verificarSessaoAtiva(): Boolean {
        val currentUser = FirebaseConfig.getAuth().currentUser
        if (currentUser != null) {
            val tipo = sessionManager.recuperarTipoUsuario()
            val email = sessionManager.recuperarEmail()
            if (tipo != null && email != null) {
                navegarParaHome(tipo, email)
                return true
            }
        }
        return false
    }

    private fun navegarParaHome(tipo: String, email: String) {
        val destino = when (tipo.lowercase()) {
            "pai" -> HomePaiActivity::class.java
            "filho" -> HomeFilhoActivity::class.java
            else -> null
        }

        destino?.let {
            val intent = Intent(this, it).apply {
                putExtra("USER_EMAIL", email)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            if (::viewModel.isInitialized) viewModel.limparEstado()
            finish()
        }
    }
}
