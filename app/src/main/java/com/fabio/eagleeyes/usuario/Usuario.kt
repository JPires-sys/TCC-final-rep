package com.fabio.eagleeyes.usuario

open class Usuario(
    var uid: String = "",
    var nome: String = "",
    var email: String = "",
    var senha: String = "",
    var telefone: String = "",
    var cpf: String = "",
    var genero: String = "",
    var idResponsavel: String? = null, // Vínculo robusto usando o UID do Pai
    var tipo: String = "Usuario" // "Pai", "Filho", "Usuario"
) {
    // Métodos comuns a todos os usuários
    fun validarCampos(): String? {
        return when {
            nome.isEmpty() -> "Nome vazio!"
            email.isEmpty() -> "Email vazio!"
            !email.contains("@") -> "Email inválido!"
            senha.length < 6 -> "Senha muito curta!"
            com.fabio.eagleeyes.cadastro.RegistrationValidation.phoneError(telefone) != null ->
                com.fabio.eagleeyes.cadastro.RegistrationValidation.phoneError(telefone)
            com.fabio.eagleeyes.cadastro.RegistrationValidation.cpfError(cpf) != null ->
                com.fabio.eagleeyes.cadastro.RegistrationValidation.cpfError(cpf)
            else -> null
        }
    }
}
