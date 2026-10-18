package net.moesli.scan7.scoring

enum class CardKind {
    NUMBER,
    MODIFIER_PLUS,
    MODIFIER_TIMES2,
    ACTION,
    BACK,
}

/**
 * The 23 detector classes. [label] is the class name exactly as emitted by the model;
 * [value] is the face value for number and plus cards, `null` otherwise.
 */
enum class Card(val label: String, val kind: CardKind, val value: Int? = null) {
    N0("00", CardKind.NUMBER, 0),
    N1("01", CardKind.NUMBER, 1),
    N2("02", CardKind.NUMBER, 2),
    N3("03", CardKind.NUMBER, 3),
    N4("04", CardKind.NUMBER, 4),
    N5("05", CardKind.NUMBER, 5),
    N6("06", CardKind.NUMBER, 6),
    N7("07", CardKind.NUMBER, 7),
    N8("08", CardKind.NUMBER, 8),
    N9("09", CardKind.NUMBER, 9),
    N10("10", CardKind.NUMBER, 10),
    N11("11", CardKind.NUMBER, 11),
    N12("12", CardKind.NUMBER, 12),
    PLUS_2("plus2", CardKind.MODIFIER_PLUS, 2),
    PLUS_4("plus4", CardKind.MODIFIER_PLUS, 4),
    PLUS_6("plus6", CardKind.MODIFIER_PLUS, 6),
    PLUS_8("plus8", CardKind.MODIFIER_PLUS, 8),
    PLUS_10("plus10", CardKind.MODIFIER_PLUS, 10),
    TIMES_2("x2", CardKind.MODIFIER_TIMES2),
    FREEZE("freeze", CardKind.ACTION),
    FLIP_THREE("flip3", CardKind.ACTION),
    SECOND_CHANCE("second_chance", CardKind.ACTION),
    BACK("back", CardKind.BACK);

    companion object {
        private val byLabel = entries.associateBy { it.label }

        fun fromLabel(label: String): Card? = byLabel[label]
    }
}
