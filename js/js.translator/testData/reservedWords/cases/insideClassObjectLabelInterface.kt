package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

class TestClass {
    companion object {
        val t: Int = 0

        fun test() {
            testRenamed("interface", {
                `interface`@ while (false) {
                }
            })
        }
    }
}

fun box(): String {
    TestClass.test()

    return "OK"
}
