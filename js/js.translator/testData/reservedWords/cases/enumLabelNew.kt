package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

enum class Foo {
    BAR;

    val t: Int = 0

    fun test() {
        testRenamed("new", {
            new@ while (false) {
            }
        })
    }
}

fun box(): String {
    Foo.BAR.test()

    return "OK"
}
