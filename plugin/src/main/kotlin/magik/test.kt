package magik

import java.io.File

fun main() {
    for (i in 0..1_000_000)
        File("/home/elect/Downloads/demo-flame/2/%07d".format(i)).createNewFile()
}