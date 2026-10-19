package com.fabio.eagleeyes.cadastro

object RegistrationValidation {
    fun cpfError(value: String): String? {
        val digits = value.filter { it in '0'..'9' }
        if (value.isBlank()) return "CPF é obrigatório"
        if (!value.matches(Regex("[0-9.\\-\\s]+")) || digits.length != 11) return "CPF deve ter 11 dígitos"
        if (digits.toSet().size == 1) return "CPF inválido. Confira os dígitos"
        for (length in 9..10) {
            val sum = (0 until length).sumOf { digits[it].digitToInt() * (length + 1 - it) }
            val remainder = sum % 11
            val check = if (remainder < 2) 0 else 11 - remainder
            if (digits[length].digitToInt() != check) return "CPF inválido. Confira os dígitos"
        }
        return null
    }

    fun phoneError(value: String): String? {
        if (value.isBlank()) return "Telefone é obrigatório"
        if (!value.trim().matches(Regex("\\+?[0-9()\\-\\s]+"))) return "Telefone inválido. Use apenas números, DDD e, opcionalmente, +55"
        val raw = value.filter { it in '0'..'9' }
        val international = value.trim().startsWith('+') || raw.length in 12..13
        if (international && (!raw.startsWith("55") || raw.length !in 12..13)) return "Use o código +55 e o telefone com DDD"
        val digits = phoneDigits(value)
        return if (digits.length !in 10..11 || digits.firstOrNull() == '0')
            "Informe o telefone com DDD: 10 dígitos para fixo ou 11 para celular" else null
    }

    fun cpfDigits(value: String): String {
        require(cpfError(value) == null) { cpfError(value).orEmpty() }
        return value.filter { it in '0'..'9' }
    }

    fun phoneDigits(value: String): String {
        val digits = value.filter { it in '0'..'9' }
        return if (digits.length in 12..13 && digits.startsWith("55")) digits.drop(2) else digits
    }

    fun validatedPhone(value: String): String {
        require(phoneError(value) == null) { phoneError(value).orEmpty() }
        return phoneDigits(value)
    }
}
