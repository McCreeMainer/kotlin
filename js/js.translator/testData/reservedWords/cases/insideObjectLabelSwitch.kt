package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

object TestObject {
    val t: Int = 0

    fun test() {
        testRenamed("switch", {
            switch@ while (false) {
            }
        })
    }
}

fun box(): String {
    TestObject.test()

    return "OK"
}
