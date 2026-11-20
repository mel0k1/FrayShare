package com.frayshare.app.core.transfer

data class ItemPlan(val index: Int, val offset: Long)

object Plan {

    fun compute(sizes: List<Long>, stored: List<Long>): List<ItemPlan> {
        require(sizes.size == stored.size) { "plan size mismatch" }
        return sizes.mapIndexed { index, size ->
            val have = stored[index]
            val offset = when {
                have == size -> size
                have in 1 until size -> have
                else -> 0L
            }
            ItemPlan(index, offset)
        }
    }
}
