package com.ojoclaro.android.risk

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RiskDetectorTest {

    private val detector = RiskDetector()

    @Test
    fun normalTextReturnsNoWarnings() {
        val warnings = detector.detectFromVisibleText("Hola, estoy llegando en diez minutos")

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun detectsTransfer() {
        val warnings = detector.detectFromVisibleText("Necesito una transferencia de 500 pesos")

        assertTrue(warnings.any { it.type == RiskType.MONEY_REQUEST })
    }

    @Test
    fun detectsMoneyRequestWithUrgency() {
        val warnings = detector.detectFromVisibleText("Urgente pasame plata ahora")

        assertTrue(warnings.any { it.type == RiskType.MONEY_REQUEST })
        assertTrue(warnings.any { it.type == RiskType.URGENT_MESSAGE })
    }

    @Test
    fun detectsCbu() {
        val warnings = detector.detectFromVisibleText("Pasame tu CBU para transferirte")

        assertTrue(warnings.any { it.type == RiskType.MONEY_REQUEST })
    }

    @Test
    fun detectsCvu() {
        val warnings = detector.detectFromVisibleText("Mandame el CVU de Mercado Pago")

        assertTrue(warnings.any { it.type == RiskType.MONEY_REQUEST })
    }

    @Test
    fun detectsBankAlias() {
        val warnings = detector.detectFromVisibleText("Mi alias bancario es marco.luna.mp")

        assertTrue(warnings.any { it.type == RiskType.MONEY_REQUEST })
    }

    @Test
    fun detectsBank() {
        val warnings = detector.detectFromVisibleText("Banco Galicia saldo disponible")

        assertTrue(warnings.any { it.type == RiskType.BANKING_SCREEN })
    }

    @Test
    fun detectsHomeBanking() {
        val warnings = detector.detectFromVisibleText("Home banking transferencia saldo cuenta")

        assertTrue(warnings.any { it.type == RiskType.BANKING_SCREEN })
    }

    @Test
    fun detectsMercadoPagoAsSensitiveFinancialScreen() {
        val warnings = detector.detectFromVisibleText("Mercado Pago saldo disponible transferir dinero")

        assertTrue(
            warnings.any { it.type == RiskType.BANKING_SCREEN } ||
                warnings.any { it.type == RiskType.MONEY_REQUEST }
        )
    }

    @Test
    fun detectsCodeWithoutAccent() {
        val warnings = detector.detectFromOcrText("Tu codigo de verificacion es 123456")

        assertTrue(warnings.any { it.type == RiskType.VERIFICATION_CODE })
    }

    @Test
    fun detectsCodeWithAccent() {
        val warnings = detector.detectFromOcrText("Tu código de verificación es 123456")

        assertTrue(warnings.any { it.type == RiskType.VERIFICATION_CODE })
    }

    @Test
    fun detectsOtpCode() {
        val warnings = detector.detectFromVisibleText("OTP 445566")

        assertTrue(warnings.any { it.type == RiskType.VERIFICATION_CODE })
    }

    @Test
    fun detectsTwoFactorCode() {
        val warnings = detector.detectFromVisibleText("Código 2FA: 987654")

        assertTrue(warnings.any { it.type == RiskType.VERIFICATION_CODE })
    }

    @Test
    fun detectsPasswordEnglish() {
        val warnings = detector.detectFromVisibleText("Campo password")

        assertTrue(warnings.any { it.type == RiskType.PASSWORD_FIELD })
    }

    @Test
    fun detectsPasswordSpanishWithoutAccent() {
        val warnings = detector.detectFromVisibleText("Ingrese su contrasena")

        assertTrue(warnings.any { it.type == RiskType.PASSWORD_FIELD })
    }

    @Test
    fun detectsPasswordSpanishWithAccent() {
        val warnings = detector.detectFromVisibleText("Ingrese su contraseña")

        assertTrue(warnings.any { it.type == RiskType.PASSWORD_FIELD })
    }

    @Test
    fun detectsPin() {
        val warnings = detector.detectFromVisibleText("Ingrese su PIN")

        assertTrue(warnings.any { it.type == RiskType.PASSWORD_FIELD })
    }

    @Test
    fun detectsUrgency() {
        val warnings = detector.detectFromCommand("Urgente, responde ahora")

        assertTrue(warnings.any { it.type == RiskType.URGENT_MESSAGE })
    }

    @Test
    fun detectsPersonalDataRequest() {
        val warnings = detector.detectFromVisibleText("Pasame tu DNI y dirección")

        assertTrue(warnings.any { it.type == RiskType.PERSONAL_DATA_REQUEST })
    }

    @Test
    fun detectsDocumentRequest() {
        val warnings = detector.detectFromVisibleText("Necesito tu CUIL o CUIT")

        assertTrue(warnings.any { it.type == RiskType.PERSONAL_DATA_REQUEST })
    }

    @Test
    fun detectsAddressRequest() {
        val warnings = detector.detectFromVisibleText("Decime dónde vivís y tu dirección exacta")

        assertTrue(warnings.any { it.type == RiskType.PERSONAL_DATA_REQUEST })
    }

    @Test
    fun detectsMultipleRisksTogether() {
        val warnings = detector.detectFromVisibleText(
            "Urgente, mandame una transferencia y pasame el codigo 123456"
        )

        assertTrue(warnings.any { it.type == RiskType.URGENT_MESSAGE })
        assertTrue(warnings.any { it.type == RiskType.MONEY_REQUEST })
        assertTrue(warnings.any { it.type == RiskType.VERIFICATION_CODE })
    }

    @Test
    fun warningTextsAreNotBlank() {
        val warnings = detector.detectFromVisibleText(
            "Urgente, necesito una transferencia de 500 pesos"
        )

        assertFalse(warnings.isEmpty())
        assertTrue(warnings.all { it.spokenText.isNotBlank() })
    }

    @Test
    fun detectsBankingPackageName() {
        val warnings = detector.detectFromPackageName("com.mercadopago.wallet")

        assertTrue(
            warnings.any { it.type == RiskType.BANKING_SCREEN } ||
                warnings.any { it.type == RiskType.MONEY_REQUEST }
        )
    }

    @Test
    fun detectsKnownBankPackageName() {
        val warnings = detector.detectFromPackageName("ar.com.galicia")

        assertTrue(warnings.any { it.type == RiskType.BANKING_SCREEN })
    }

    @Test
    fun safePackageNameReturnsNoWarnings() {
        val warnings = detector.detectFromPackageName("com.whatsapp")

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun blankInputsReturnNoWarnings() {
        assertTrue(detector.detectFromVisibleText("").isEmpty())
        assertTrue(detector.detectFromOcrText("   ").isEmpty())
        assertTrue(detector.detectFromCommand("").isEmpty())
        assertTrue(detector.detectFromPackageName(null).isEmpty())
    }
}
