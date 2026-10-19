package com.fabio.eagleeyes

import com.fabio.eagleeyes.cadastro.RegistrationValidation as Validation
import org.junit.Assert.*
import org.junit.Test

class RegistrationValidationTest {
    @Test fun validatesBothCpfCheckDigitsAndNormalizesFormatting() {
        assertEquals("52998224725", Validation.cpfDigits("529.982.247-25"))
        assertNull(Validation.cpfError("11144477735"))
        for (invalid in listOf("52998224724", "52998224715", "529982247", "529982247250", "abc52998224725", "")) {
            assertNotNull(invalid, Validation.cpfError(invalid))
        }
    }
    @Test fun rejectsAllRepeatedCpfDigits() {
        for (digit in '0'..'9') assertNotNull(Validation.cpfError(digit.toString().repeat(11)))
    }
    @Test fun acceptsNationalFixedAndMobileNumbersWithOptionalBrazilCode() {
        for ((input, expected) in listOf(
            "(11) 3456-7890" to "1134567890",
            "(11) 91234-5678" to "11912345678",
            "+55 (11) 91234-5678" to "11912345678",
            "551134567890" to "1134567890",
            "55912345678" to "55912345678"
        )) assertEquals(expected, Validation.validatedPhone(input))
    }
    @Test fun rejectsWrongPhoneLengthLettersAndUnsupportedCountryCode() {
        for (input in listOf("", "123456789", "119123456789", "55119123456789", "abc11912345678", "+1 11912345678", "01912345678")) {
            assertNotNull(input, Validation.phoneError(input))
        }
    }
}
