package com.example.gettingrichapp.strategy

import com.example.gettingrichapp.model.Action
import com.example.gettingrichapp.model.Action.DOUBLE
import com.example.gettingrichapp.model.Action.HIT
import com.example.gettingrichapp.model.Action.SPLIT
import com.example.gettingrichapp.model.Action.STAND
import com.example.gettingrichapp.model.Action.SURRENDER
import com.example.gettingrichapp.model.CardValue

/**
 * Basic strategy tables for a 6-deck game.
 * Rules: Dealer stands on soft 17 (S17), DAS allowed, late surrender.
 *
 * Dealer upcard index: 2=0, 3=1, 4=2, 5=3, 6=4, 7=5, 8=6, 9=7, 10=8, A=9
 */
object StrategyTables {

    /** Maps a dealer upcard to its column index (0-9). */
    fun dealerIndex(dealerValue: CardValue): Int {
        return when (dealerValue) {
            CardValue.TWO -> 0
            CardValue.THREE -> 1
            CardValue.FOUR -> 2
            CardValue.FIVE -> 3
            CardValue.SIX -> 4
            CardValue.SEVEN -> 5
            CardValue.EIGHT -> 6
            CardValue.NINE -> 7
            CardValue.TEN, CardValue.JACK, CardValue.QUEEN, CardValue.KING -> 8
            CardValue.ACE -> 9
        }
    }

    /**
     * Hard totals table.
     * Row index: hard total - 5 (rows 0..16 for totals 5..21).
     * Totals 4 and below always HIT; 21 always STAND.
     *
     * Columns: dealer 2,3,4,5,6,7,8,9,10,A
     */
    //                                    2     3     4     5     6     7     8     9     10    A
    val HARD_TABLE: Array<Array<Action>> = arrayOf(
        /* 5  */ arrayOf(HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* 6  */ arrayOf(HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* 7  */ arrayOf(HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* 8  */ arrayOf(HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* 9  */ arrayOf(HIT,   DOUBLE,DOUBLE,DOUBLE,DOUBLE,HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* 10 */ arrayOf(DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE,HIT,   HIT  ),
        /* 11 */ arrayOf(DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE),
        /* 12 */ arrayOf(HIT,   HIT,   STAND, STAND, STAND, HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* 13 */ arrayOf(STAND, STAND, STAND, STAND, STAND, HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* 14 */ arrayOf(STAND, STAND, STAND, STAND, STAND, HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* 15 */ arrayOf(STAND, STAND, STAND, STAND, STAND, HIT,   HIT,   HIT,   SURRENDER,HIT),
        /* 16 */ arrayOf(STAND, STAND, STAND, STAND, STAND, HIT,   HIT,   SURRENDER,SURRENDER,SURRENDER),
        /* 17 */ arrayOf(STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND ),
        /* 18 */ arrayOf(STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND ),
        /* 19 */ arrayOf(STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND ),
        /* 20 */ arrayOf(STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND ),
        /* 21 */ arrayOf(STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND )
    )

    /**
     * Soft totals table (hand contains an ace counted as 11).
     * Row index: soft total - 13 (rows 0..8 for soft 13..21).
     * Soft 13 = A+2, soft 14 = A+3, ..., soft 21 = A+10.
     *
     * Columns: dealer 2,3,4,5,6,7,8,9,10,A
     */
    //                                    2     3     4     5     6     7     8     9     10    A
    val SOFT_TABLE: Array<Array<Action>> = arrayOf(
        /* S13 */ arrayOf(HIT,   HIT,   HIT,   DOUBLE,DOUBLE,HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* S14 */ arrayOf(HIT,   HIT,   HIT,   DOUBLE,DOUBLE,HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* S15 */ arrayOf(HIT,   HIT,   DOUBLE,DOUBLE,DOUBLE,HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* S16 */ arrayOf(HIT,   HIT,   DOUBLE,DOUBLE,DOUBLE,HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* S17 */ arrayOf(HIT,   DOUBLE,DOUBLE,DOUBLE,DOUBLE,HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* S18 */ arrayOf(DOUBLE,DOUBLE,DOUBLE,DOUBLE,DOUBLE,STAND, STAND, HIT,   HIT,   HIT  ),
        /* S19 */ arrayOf(STAND, STAND, STAND, STAND, DOUBLE,STAND, STAND, STAND, STAND, STAND ),
        /* S20 */ arrayOf(STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND ),
        /* S21 */ arrayOf(STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND )
    )

    /**
     * Pair splitting table.
     * Row index maps from [pairIndex]:
     *   2-2=0, 3-3=1, 4-4=2, 5-5=3, 6-6=4, 7-7=5, 8-8=6, 9-9=7, 10-10=8, A-A=9
     *
     * Columns: dealer 2,3,4,5,6,7,8,9,10,A
     *
     * Note: When not splitting, the action falls through to hard or soft table.
     * SPLIT means split; HIT/STAND/DOUBLE means don't split (use hard/soft table instead).
     * We encode only SPLIT here; non-SPLIT entries use HIT as a placeholder
     * (the engine falls through to hard/soft table when it sees non-SPLIT).
     */
    //                                    2     3     4     5     6     7     8     9     10    A
    val PAIR_TABLE: Array<Array<Action>> = arrayOf(
        /* 2-2 */ arrayOf(SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, HIT,   HIT,   HIT,   HIT  ),
        /* 3-3 */ arrayOf(SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, HIT,   HIT,   HIT,   HIT  ),
        /* 4-4 */ arrayOf(HIT,   HIT,   HIT,   SPLIT, SPLIT, HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* 5-5 */ arrayOf(HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT,   HIT  ), // never split 5s, treat as hard 10
        /* 6-6 */ arrayOf(SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, HIT,   HIT,   HIT,   HIT,   HIT  ),
        /* 7-7 */ arrayOf(SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, HIT,   HIT,   HIT,   HIT  ),
        /* 8-8 */ arrayOf(SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT ),
        /* 9-9 */ arrayOf(SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, STAND, SPLIT, SPLIT, STAND, STAND ),
        /* T-T */ arrayOf(STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND, STAND ), // never split 10s
        /* A-A */ arrayOf(SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT, SPLIT )
    )

    /** Maps a pair card value to its row index in [PAIR_TABLE]. */
    fun pairIndex(pairValue: CardValue): Int {
        return when (pairValue) {
            CardValue.TWO -> 0
            CardValue.THREE -> 1
            CardValue.FOUR -> 2
            CardValue.FIVE -> 3
            CardValue.SIX -> 4
            CardValue.SEVEN -> 5
            CardValue.EIGHT -> 6
            CardValue.NINE -> 7
            CardValue.TEN, CardValue.JACK, CardValue.QUEEN, CardValue.KING -> 8
            CardValue.ACE -> 9
        }
    }
}
