package org.example
import io.konad.*
import io.konad.applicative.builders.on
import org.valiktor.ConstraintViolationException
import org.valiktor.functions.isEmail
import org.valiktor.functions.isNotBlank
import org.valiktor.i18n.mapToMessage
import org.valiktor.validate

fun main(args: Array<String>) {
    val contactResult: Result<ContactInfo> = ::ContactInfo.curry()
        .on(PersonalName.of("Foo", "Bar", "J."))
        .on(Email.Unverified.of("foo.bar@gmail.com"))
        .result

    contactResult
        .map { contact ->
            when(contact.email){
                is Email.Unverified -> EmailVerificationService.verify(contact.email)
                is Email.Verified -> contact.email
            }
        }
        .map { verifiedMail: Email.Verified? -> verifiedMail
            ?.run { PasswordResetService.send(verifiedMail) }
            ?: println("Email was not verified") }
        .ifError { errors ->  println(errors.description("\n")) }
}

object PasswordResetService{
    fun send(email: Email.Verified): Unit = TODO("send reset")
}

object EmailVerificationService{
    fun verify(unverifiedEmail: Email.Unverified): Email.Verified? = if(incredibleConditions())Email.Verified(unverifiedEmail) else null

    private fun incredibleConditions() = true
}

data class ContactInfo(
    val name: PersonalName,
    val email: Email)


data class PersonalName(
    val firstname: NotEmptyString,
    val middleInitial: NotEmptyString?,
    val lastname: NotEmptyString){

    companion object {
        fun of(firstname: String, lastname: String, middleInitial: String? = null): Result<PersonalName> =
            ::PersonalName.curry()
                .on(NotEmptyString.of(firstname))
                .on(middleInitial?.run { NotEmptyString.of(middleInitial) } ?: null.ok())
                .on(NotEmptyString.of(lastname))
                .result
    }
}

sealed class Email(val value: String){
    data class Verified(private val email: Unverified): Email(email.value)
    class Unverified private constructor(value: String): Email(value){
        companion object{
            fun of(value: String): Result<Email.Unverified> = valikate {
                validate(Email.Unverified(value)){
                    validate(Email.Unverified::value).isEmail()
                }
            }
        }
    }
}

inline class NotEmptyString
@Deprecated(level = DeprecationLevel.ERROR, message = "use companion method 'of'")
constructor(val value: String){
    companion object{
        @Suppress("DEPRECATION_ERROR")
        fun of(value: String): Result<NotEmptyString> = valikate {
            validate(NotEmptyString(value)){
                validate(NotEmptyString::value).isNotBlank()
            }
        }
    }
}

internal inline fun <reified T> valikate(valikateFn: () -> T): Result<T> = try{
    valikateFn().ok()
}catch (ex: ConstraintViolationException){
    ex.constraintViolations
        .mapToMessage()
        .joinToString("\n") { "\t\"${it.value}\" of ${T::class.simpleName}.${it.property}: ${it.message}" }
        .error()
}
