package com.fabio.eagleeyes.global

import android.util.Patterns
import com.google.android.material.textfield.TextInputLayout

object Validator {

    fun validarCampoVazio(inputLayout: TextInputLayout): Boolean {
        val texto = inputLayout.editText?.text.toString().trim()
        return if (texto.isEmpty()) {
            inputLayout.error = "Este campo é obrigatório"
            false
        } else {
            inputLayout.error = null
            true
        }
    }

    fun validarEmail(inputLayout: TextInputLayout): Boolean {
        val email = inputLayout.editText?.text.toString().trim()
        return when {
            email.isEmpty() -> {
                inputLayout.error = "E-mail é obrigatório"
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                inputLayout.error = "E-mail inválido"
                false
            }
            else -> {
                inputLayout.error = null
                true
            }
        }
    }

    fun validarSenha(inputLayout: TextInputLayout): Boolean {
        val senha = inputLayout.editText?.text.toString().trim()
        return when {
            senha.isEmpty() -> {
                inputLayout.error = "Senha é obrigatória"
                false
            }
            senha.length < 6 -> {
                inputLayout.error = "A senha deve ter pelo menos 6 caracteres"
                false
            }
            else -> {
                inputLayout.error = null
                true
            }
        }
    }

    fun validarCPF(inputLayout: TextInputLayout): Boolean {
        val error = com.fabio.eagleeyes.cadastro.RegistrationValidation.cpfError(inputLayout.editText?.text.toString())
        inputLayout.error = error
        return error == null
    }

    fun validarTelefone(inputLayout: TextInputLayout): Boolean {
        val error = com.fabio.eagleeyes.cadastro.RegistrationValidation.phoneError(inputLayout.editText?.text.toString())
        inputLayout.error = error
        return error == null
    }
}
