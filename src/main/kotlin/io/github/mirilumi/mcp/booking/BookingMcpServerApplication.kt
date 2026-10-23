package io.github.mirilumi.mcp.booking

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BookingMcpServerApplication

fun main(args: Array<String>) {
    runApplication<BookingMcpServerApplication>(*args)
}
