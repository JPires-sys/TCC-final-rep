package com.fabio.eagleeyes.cadastro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.fabio.eagleeyes.R
import com.fabio.eagleeyes.databinding.LayoutCamposComunsRegistroBinding
import com.fabio.eagleeyes.filho.home.HomeFilhoActivity
import com.fabio.eagleeyes.global.Validator
import com.fabio.eagleeyes.home.HomeUsuario
import com.fabio.eagleeyes.pai.home.HomePaiActivity
import com.fabio.eagleeyes.usuario.UsuarioRepositorio

/**
 * Base Activity para Cadastros.
 */
abstract class CadastroActivity<T : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: T
    protected lateinit var viewModel: CadastroViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = inflateBinding()
        setContentView(binding.root)

        val repositorio = UsuarioRepositorio()
        val factory = CadastroViewModelFactory(repositorio)
        viewModel = ViewModelProvider(this, factory)[CadastroViewModel::class.java]

        setupCommonViews()
        setupObservers()
        setupSpecificLogic()
    }

    abstract fun inflateBinding(): T

    abstract fun setupSpecificLogic()

    private fun setupCommonViews() {
        findViewById<View>(R.id.btnVoltar)?.setOnClickListener {
            finish()
        }
    }

    protected fun validarCamposBase(campos: LayoutCamposComunsRegistroBinding): Boolean {
        return Validator.validarCampoVazio(campos.layoutNome) &&
                Validator.validarEmail(campos.layoutEmail) &&
                Validator.validarSenha(campos.layoutSenha) &&
                Validator.validarCPF(campos.layoutCPF) &&
                Validator.validarTelefone(campos.layoutTelefone)
    }

    protected open fun setupObservers() {
        viewModel.carregando.observe(this) { estaCarregando ->
            val progressBar = findViewById<View>(R.id.progressBar)
            val btnCadastrar = findViewById<View>(R.id.btnCadastrar)
            
            progressBar?.visibility = if (estaCarregando) View.VISIBLE else View.GONE
            btnCadastrar?.isEnabled = !estaCarregando
        }

        viewModel.sucesso.observe(this) { tipo ->
            tipo?.let {
                Toast.makeText(this, "Cadastro realizado com sucesso!", Toast.LENGTH_SHORT).show()
                viewModel.limparSucesso()
                redirecionarParaHome(it)
            }
        }

        viewModel.erro.observe(this) { mensagem ->
            mensagem?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun redirecionarParaHome(tipo: String) {
        val intent = when (tipo) {
            "Pai" -> Intent(this, HomePaiActivity::class.java)
            "Filho" -> Intent(this, HomeFilhoActivity::class.java)
            else -> Intent(this, HomeUsuario::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    protected fun getGeneroSelecionado(rgId: Int): String {
        return when (rgId) {
            R.id.rbMasculino -> "Masculino"
            R.id.rbFeminino -> "Feminino"
            else -> "Outro"
        }
    }
}
