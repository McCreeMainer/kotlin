// SKIP_TXT

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (NEGATIVE)
 *
 * SPEC VERSION: 0.1-296
 * PLACE: expressions, when-expression -> paragraph 6 -> sentence 1
 * NUMBER: 1
 * DESCRIPTION: 'When' with bound value and type test condition (without companion object in classes), but without type checking operator.
 * HELPERS: classes
 */

// TESTCASE NUMBER: 1
fun case_1(value_1: Any): String {
    when (value_1) {
        EmptyClass -> return ""
    }

    return ""
}

// TESTCASE NUMBER: 2
fun case_2(value_1: Any): String {
    when (value_1) {
        Any -> return ""
    }

    return ""
}

// TESTCASE NUMBER: 3
fun case_3(value_1: Any): String {
    when (value_1) {
        Nothing -> return ""
    }

    return ""
}
