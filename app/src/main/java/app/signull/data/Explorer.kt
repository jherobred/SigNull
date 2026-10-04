package app.signull.data

/** Progress on the blank map. Every discovery earns XP toward the next explorer rank. */
data class ExplorerStats(
    val buildings: Int = 0,
    val floors: Int = 0,
    val rooms: Int = 0,
    val spots: Int = 0,
    val readings: Int = 0,
    val angleScans: Int = 0,
    val deadZones: Int = 0,
    val sweetSpots: Int = 0,
) {
    val xp: Int
        get() = buildings * 25 + floors * 20 + rooms * 15 + spots * 10 +
            (readings - spots).coerceAtLeast(0) * 3 + angleScans * 20 + deadZones * 5

    val rank: ExplorerRank get() = ExplorerRank.forXp(xp)
}

data class ExplorerRank(
    val level: Int,
    val title: String,
    val startXp: Int,
    val nextXp: Int?,
) {
    fun progress(xp: Int): Float =
        if (nextXp == null) 1f else ((xp - startXp).toFloat() / (nextXp - startXp)).coerceIn(0f, 1f)

    companion object {
        private val thresholds = intArrayOf(0, 100, 250, 500, 900, 1500, 2400, 3600)
        private val titles = arrayOf(
            "Newcomer",
            "Signal Scout",
            "Wave Rider",
            "Dead Zone Hunter",
            "Antenna Ace",
            "Spectrum Sage",
            "Bar Baron",
            "Signal Legend",
        )

        fun forXp(xp: Int): ExplorerRank {
            val index = thresholds.indexOfLast { xp >= it }.coerceAtLeast(0)
            return ExplorerRank(
                level = index + 1,
                title = titles[index],
                startXp = thresholds[index],
                nextXp = thresholds.getOrNull(index + 1),
            )
        }
    }
}
