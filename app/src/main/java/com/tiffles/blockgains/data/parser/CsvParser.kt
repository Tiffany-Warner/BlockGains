package com.tiffles.blockgains.data.parser

import com.tiffles.blockgains.data.model.BuyTransaction
import com.tiffles.blockgains.data.model.SellTransaction
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses CSV files containing buy and sell transactions
 */
object CsvParser {

    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd")
    )

    /**
     * Parse a date string trying multiple formats
     */
    private fun parseDate(dateStr: String): LocalDate {
        val trimmed = dateStr.trim()
        for (formatter in dateFormatters) {
            try {
                return LocalDate.parse(trimmed, formatter)
            } catch (e: Exception) {
                // Try next formatter
            }
        }
        throw IllegalArgumentException("Unable to parse date: $dateStr")
    }

    /**
     * Parse a price string, ignoring currency symbols like '$' and commas
     */
    private fun parsePrice(priceStr: String): BigDecimal {
        val cleanPrice = priceStr.trim().replace("$", "").replace(",", "")
        return BigDecimal(cleanPrice)
    }

    /**
     * Parse buy transactions from CSV
     * Expected columns: "Date bought", "Quantity", "Purchase price per unit"
     */
    fun parseBuyTransactions(inputStream: InputStream): List<BuyTransaction> {
        val transactions = mutableListOf<BuyTransaction>()

        inputStream.bufferedReader().use { reader ->
            val header = reader.readLine()?.lowercase() ?: throw IllegalArgumentException("Empty CSV file")

            // Find column indices
            val columns = header.split(",").map { it.trim() }
            val dateIndex = columns.indexOfFirst { it.contains("date") && it.contains("bought") }
            val quantityIndex = columns.indexOfFirst { it.contains("quantity") }
            val priceIndex = columns.indexOfFirst {
                it.contains("purchase") && it.contains("price") && it.contains("unit")
            }

            if (dateIndex == -1 || quantityIndex == -1 || priceIndex == -1) {
                throw IllegalArgumentException("CSV must contain 'Date bought', 'Quantity', and 'Purchase price per unit' columns")
            }

            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    val values = line.split(",").map { it.trim() }
                    if (values.size > maxOf(dateIndex, quantityIndex, priceIndex)) {
                        try {
                            val transaction = BuyTransaction(
                                dateBought = parseDate(values[dateIndex]),
                                quantity = BigDecimal(values[quantityIndex]),
                                purchasePricePerUnit = parsePrice(values[priceIndex])
                            )
                            transactions.add(transaction)
                        } catch (e: Exception) {
                            throw IllegalArgumentException("Malformed line in buy transactions: $line. Error: ${e.message}")
                        }
                    }
                }
            }
        }

        // Sort by date (FIFO requirement)
        return transactions.sortedBy { it.dateBought }
    }

    /**
     * Parse sell transactions from CSV
     * Expected columns: "Date sold", "Quantity Sold", "Sale price per unit"
     */
    fun parseSellTransactions(inputStream: InputStream): List<SellTransaction> {
        val transactions = mutableListOf<SellTransaction>()

        inputStream.bufferedReader().use { reader ->
            val header = reader.readLine()?.lowercase() ?: throw IllegalArgumentException("Empty CSV file")

            // Find column indices
            val columns = header.split(",").map { it.trim() }
            val dateIndex = columns.indexOfFirst { it.contains("date") && it.contains("sold") }
            val quantityIndex = columns.indexOfFirst {
                it.contains("quantity")
            }
            val priceIndex = columns.indexOfFirst {
                it.contains("sale") && it.contains("price") && it.contains("unit")
            }

            if (dateIndex == -1 || quantityIndex == -1 || priceIndex == -1) {
                throw IllegalArgumentException("CSV must contain 'Date sold', 'Quantity Sold', and 'Sale price per unit' columns")
            }

            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    val values = line.split(",").map { it.trim() }
                    if (values.size > maxOf(dateIndex, quantityIndex, priceIndex)) {
                        try {
                            val transaction = SellTransaction(
                                dateSold = parseDate(values[dateIndex]),
                                quantitySold = BigDecimal(values[quantityIndex]),
                                salePricePerUnit = parsePrice(values[priceIndex])
                            )
                            transactions.add(transaction)
                        } catch (e: Exception) {
                            throw IllegalArgumentException("Malformed line in sell transactions: $line. Error: ${e.message}")
                        }
                    }
                }
            }
        }

        // Sort by date
        return transactions.sortedBy { it.dateSold }
    }
}
