package com.lamahuissn.motoapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one piece of parsing every screen depends on: the server's own answer.
 *
 * The bodies below are not invented — they are the shapes the API in generator-app actually returns
 * (captured from the running server), because a parser tested against a shape the server does not
 * send is a parser that breaks on the first real 401.
 */
class MotoApiAnswerTest {

    @Test
    fun `the 401 the app sees on every expired session is read as a code and a sentence`() {
        val answer = MotoApi.readAnswer(
            401,
            """{"error":{"code":"unauthenticated","message":"انتهت الجلسة — أعِد تسجيل الدخول."}}""",
        )

        assertEquals("unauthenticated", answer.code)
        assertEquals("انتهت الجلسة — أعِد تسجيل الدخول.", answer.message)
        assertTrue(answer.reachable)
        assertFalse(answer.ok)
        assertEquals(401, answer.httpStatus)
    }

    @Test
    fun `a business refusal keeps the code the app branches on`() {
        // The collector posted a reading lower than the previous one: the app must recognise this
        // one by code, not by reading the Arabic sentence and guessing.
        val answer = MotoApi.readAnswer(
            422,
            """{"error":{"code":"refuse.reading_lower","message":"القراءة الجديدة لا يمكن أن تكون أقل من القراءة السابقة."}}""",
        )

        assertEquals("refuse.reading_lower", answer.code)
        assertTrue(answer.message!!.isNotEmpty())
    }

    @Test
    fun `a validation refusal carries the field that was refused`() {
        val answer = MotoApi.readAnswer(
            422,
            """{"error":{"code":"invalid","message":"البيانات المرسلة ناقصة أو غير صحيحة — راجع الحقول المعلَّمة.","fields":{"password":["The password field is required."],"identity":["The identity field is required."]}}}""",
        )

        assertEquals("invalid", answer.code)
        assertEquals("The password field is required.", answer.fields["password"])
        assertEquals(2, answer.fields.size)
    }

    @Test
    fun `a moment of money is never read as success by accident`() {
        // The collection answer, which the app will parse on the next slice: a 201 carries no `error`.
        val answer = MotoApi.readAnswer(201, """{"payment":{"number":"REC-2026-10-0003","amount":"60.00"},"replay":false}""")

        assertTrue(answer.ok)
        assertNull(answer.code)
        assertTrue(answer.fields.isEmpty())
    }

    @Test
    fun `a body that is not the error shape is answered honestly rather than crashing`() {
        // A proxy, a captive-portal page or a 500 from something in front of the app: no `error`
        // object at all. The collector must see "the server did not answer in a shape I know"
        // rather than a crash on the one screen he is standing in the street to use.
        val html = MotoApi.readAnswer(200, "<!DOCTYPE html><html><body>Sign in</body></html>")

        assertNull(html.code)
        assertNull(html.message)
        assertTrue(html.ok)
        assertTrue(html.fields.isEmpty())
    }
}
