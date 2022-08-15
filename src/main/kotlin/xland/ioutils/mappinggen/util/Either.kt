package xland.ioutils.mappinggen.util

sealed class Either<L, R> {
    companion object {
        fun <L, R> left(left : L) : Either<L, R> = Left(left)
        fun <L, R> right(right : R) : Either<L, R> = Right(right)
    }

    open fun isLeft() : Boolean = false
    open val left : L get() = throw NoSuchElementException()

    open fun isRight() : Boolean = false
    open val right : R get() = throw NoSuchElementException()

    inline fun ifLeft(action : L.() -> Unit) : Either<L, R> {
        if (isLeft()) action(left)
        return this
    }
    inline fun ifRight(action : R.() -> Unit) : Either<L, R> {
        if (isRight()) action(right)
        return this
    }

    fun leftOrNull() : L? = if(isLeft()) left else null
    fun rightOrNull() : R? = if(isRight()) right else null

    inline fun <L2> mapLeft(action: (L) -> L2) : Either<L2, R> {
        return if (isLeft()) left(action(left)) else right(right)
    }

    inline fun <R2> mapRight(action: (R) -> R2) : Either<L, R2> {
        return if (isRight()) right(action(right)) else left(left)
    }

    private class Left<L, R>(override val left : L) : Either<L, R>() {
        override fun isLeft(): Boolean = true
    }

    private class Right<L, R>(override val right : R) : Either<L, R>() {
        override fun isRight(): Boolean = true
    }
}

fun <L, R> L?.leftOrNull() : Either<L, R>? =
    if (this == null) null else Either.left(this)
fun <L, R> R?.rightOrNull() : Either<L, R>? =
    if (this == null) null else Either.right(this)
