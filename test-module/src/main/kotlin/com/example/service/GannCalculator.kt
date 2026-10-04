package com.example.service

import com.example.model.GannLevels
import kotlin.math.sqrt
import kotlin.math.round

object GannCalculator {
    fun calculate(pdc: Double): GannLevels {
        val sqCal = sqrt(pdc)
        val bsl = (sqCal - 0.0625) * (sqCal - 0.0625)
        val bt2 = (sqCal + 0.5) * (sqCal + 0.5)
        val t1 = (sqCal + 1.0) * (sqCal + 1.0)
        val t2 = (sqCal + 1.5) * (sqCal + 1.5)
        val t3 = (sqCal + 2.0) * (sqCal + 2.0)
        val t4 = (sqCal + 2.5) * (sqCal + 2.5)
        val t5 = (sqCal + 3.0) * (sqCal + 3.0)
        val t6 = (sqCal + 3.5) * (sqCal + 3.5)
        val t7 = (sqCal + 4.0) * (sqCal + 4.0)
        val t8 = (sqCal + 4.5) * (sqCal + 4.5)
        val t9 = (sqCal + 5.0) * (sqCal + 5.0)
        val t10 = (sqCal + 5.5) * (sqCal + 5.5)

        return GannLevels(
            previousClose = pdc,
            stopLoss = bsl,
            buyPrice = bt2,
            target1 = t1,
            target2 = t2,
            target3 = t3,
            target4 = t4,
            target5 = t5,
            target6 = t6,
            target7 = t7,
            target8 = t8,
            target9 = t9,
            target10 = t10
        )
    }

    fun printTable(title: String, gann: GannLevels) {
        println("\n--- GANN BUY LEVELS up to TP10 [ $title ] ---")
        println("Previous Day Close (PDC) : ${gann.previousClose}")
        println("Stop Loss (SL)           : ${round(gann.stopLoss * 100) / 100}")
        println("Buy At or Above (Entry)  : ${round(gann.buyPrice * 100) / 100}")
        println("Target 1 (TP1)           : ${round(gann.target1 * 100) / 100}")
        println("Target 2 (TP2)           : ${round(gann.target2 * 100) / 100}")
        println("Target 3 (TP3)           : ${round(gann.target3 * 100) / 100}")
        println("Target 4 (TP4)           : ${round(gann.target4 * 100) / 100}")
        println("Target 5 (TP5)           : ${round(gann.target5 * 100) / 100}")
        println("Target 6 (TP6)           : ${round(gann.target6 * 100) / 100}")
        println("Target 7 (TP7)           : ${round(gann.target7 * 100) / 100}")
        println("Target 8 (TP8)           : ${round(gann.target8 * 100) / 100}")
        println("Target 9 (TP9)           : ${round(gann.target9 * 100) / 100}")
        println("Target 10 (TP10)         : ${round(gann.target10 * 100) / 100}")
        println("--------------------------------------------------")
    }
}
