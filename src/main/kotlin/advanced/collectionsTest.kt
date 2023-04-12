import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sun.awt.Mutex
import sun.rmi.transport.Channel

interface Storage {
    suspend fun unload(truck: Truck)
    suspend fun load()
    suspend fun getLoadedTruck(): Truck
}
//open class StorageOptimusPrime (): Storage {
//
//}


open class StorageImpl() : Storage {
    val mutexFood_ = Mutex()
    val mutexOther_ = Mutex()
    var sortedGoodsFood_ = mutableListOf<AbstractGoods>() // shared state - общая
    var sortedGoodsOther_ = mutableListOf<AbstractGoods>()
    var outputTruckQueue_ = Channel<Truck>()
    var currentFoodGoodsTrack_ = creatrRandomTruckLoad()
    var currentOtherGoodsTruck_ = creatrRandomTruckLoad()
    override suspend fun unload(truck: Truck) { //truck - это ссылка на уже созданный объект класса Truck где-то в памяти
        log("unload")
        for (good in truck.goods_) {
            delay(timeMillis = good.TIME_TO_LOAD.toLong())
            log(good.toString() + " " + truck.toString())
            if (good.isFood()) {
                mutexFood_.withLock {
                    sortedGoodsFood_.add(good)
                }
            } else {
//                mutexOther_.withLock {
                mutexFood_.withLock {
                    sortedGoodsOther_.add(good)
                }
            }
        }
    }

    companion object {
        fun creatrRandomTruckLoad(): Truck {
            var truckRandom = (0..1).random()
            if (truckRandom == 0) {
                return ParckOfTrucks.SmallTruck(isEmpty = true)
            } else return ParckOfTrucks.MediumTruck(isEmpty = true)
        }
    }

    private suspend fun loadImpl() {
        val tampFoodList = mutableListOf<AbstractGoods>()
        tampFoodList.addAll(sortedGoodsFood_)
        val tampOtherList = mutableListOf<AbstractGoods>()
        tampOtherList.addAll(sortedGoodsOther_)

        if (!sortedGoodsFood_.isEmpty()) { //если список еды НЕ пустой, то грузим еду
            for (it in tampFoodList) {
                if (currentFoodGoodsTrack_.currentLoadCapacity_ <= currentFoodGoodsTrack_.LOAD_CAPACITY_MAX_) {
                    currentFoodGoodsTrack_.currentLoadCapacity_ += it.WHEIT
                    delay(timeMillis = it.TIME_TO_LOAD.toLong())
                    currentFoodGoodsTrack_.goods_.add(it)
                    sortedGoodsFood_.remove(it)

                } else {
                    log("load food")
                    outputTruckQueue_.send(currentFoodGoodsTrack_)
                    currentFoodGoodsTrack_ = creatrRandomTruckLoad()
                    return
                }
            }
        } else if (!sortedGoodsOther_.isEmpty()) { //если не загрузили из списка еды, то грузим из списка вещей
            for (it in tampOtherList) {
                if (currentOtherGoodsTruck_.currentLoadCapacity_ <= currentOtherGoodsTruck_.LOAD_CAPACITY_MAX_) {
                    currentOtherGoodsTruck_.currentLoadCapacity_ += it.WHEIT
                    delay(timeMillis = it.TIME_TO_LOAD.toLong())
                    currentOtherGoodsTruck_.goods_.add(it)
                    sortedGoodsOther_.remove(it)
                } else {
                    log("load other")
                    outputTruckQueue_.send(currentOtherGoodsTruck_)
                    currentOtherGoodsTruck_ = creatrRandomTruckLoad()
                    return
                }
            }
        } else {
            delay(100)
        }
    }

    override suspend fun load() {
        mutexFood_.withLock {
            //нельзя лочить один за одним будет дэдлок, нужна спец функция которая залочит сразу 2 при такой реализации
//            mutexOther_.withLock {
            loadImpl()
//            }
        }
    }

    override suspend fun getLoadedTruck(): Truck {
        return outputTruckQueue_.receive()
    }
}

fun log(message: Any?) {
    println("[${Thread.currentThread().name}] $message")
}

fun main() = runBlocking<Unit> {
    val queueOfTrucks = Channel<Truck>()
    var storage = StorageImpl()


    val job0 = async {
        ParckOfTrucks.trackGenerator.flow.collect() {
            log("first flow ${it.SIZE_TRUCK_}")
            queueOfTrucks.send(it)
        }
    }

    val job1 = async {
        ParckOfTrucks.trackGenerator.flow.collect() {
            log("second flow ${it.SIZE_TRUCK_}")
            queueOfTrucks.send(it)
        }
    }
    val job2 = async {
        ParckOfTrucks.trackGenerator.flow.collect() {
            log("third flow ${it.SIZE_TRUCK_}")
            queueOfTrucks.send(it)
        }
    }

    val PARALLEL_UNLOADERS = 3
    val deferredsUnloads: List<Deferred<Unit>> = (1..PARALLEL_UNLOADERS).map {
        async {
            while (currentCoroutineContext().isActive) {
                var truck = queueOfTrucks.receive() // дай мне из канала самый старый
                storage.unload(truck)   //это один разгрузчик
                log(truck)
            }
        }
    }

    val PARALLEL_LOADS = 2
    val deferredsLoads: List<Deferred<Unit>> = (1..PARALLEL_LOADS).map {
        async {
            while (currentCoroutineContext().isActive) {
                storage.load()
            }
        }
    }

    val job3 = async {
        // читаю траки из сторэджа
        var truck = storage.getLoadedTruck()
        log(truck)
    }

    launch {
        // Спим, а потом выключаем все
        delay(2000)
        job0.cancel()
        job1.cancel()
        job2.cancel()
        deferredsUnloads.forEach() {
            it.cancel()
        }
        deferredsLoads.forEach() {
            it.cancel()
        }
        job3.cancel()
    }
}

open class Truck(val LOAD_CAPACITY_MAX_: Int, val SIZE_TRUCK_: String, val isEmpty: Boolean) {
    var currentLoadCapacity_ = 0

    val goods_ = mutableListOf<AbstractGoods>()

    init {
        if (!isEmpty) {
            while (true) {
                val good = GoodsHelper.getGoods()
                if (currentLoadCapacity_ + good.WHEIT <= LOAD_CAPACITY_MAX_) {
                    currentLoadCapacity_ += good.WHEIT
                    goods_.add(good)
                } else break // завершение
            }
        }
    }

    fun loadFoodGood(good: AbstractGoods): Boolean {
        if (currentLoadCapacity_ + good.WHEIT <= LOAD_CAPACITY_MAX_) {
            currentLoadCapacity_ += good.WHEIT
            goods_.add(good)
            return true
        }
        return false
    }
}

object ParckOfTrucks {
    class BigTruck(isEmpty: Boolean) : Truck(LOAD_CAPACITY_MAX_ = 300, SIZE_TRUCK_ = "Big Truck", isEmpty = isEmpty) {}

    class MediumTruck(isEmpty: Boolean) :
        Truck(LOAD_CAPACITY_MAX_ = 200, SIZE_TRUCK_ = "Medium Truck", isEmpty = isEmpty) {}

    class SmallTruck(isEmpty: Boolean) :
        Truck(LOAD_CAPACITY_MAX_ = 100, SIZE_TRUCK_ = "Small Truck", isEmpty = isEmpty) {}

    suspend fun createTruck(isEmpty: Boolean): Truck {
        var randomGoods = (0..10).random()
        delay(500)
        return when (randomGoods) {
            0 -> BigTruck(isEmpty)
            1 -> MediumTruck(isEmpty)
            else -> SmallTruck(isEmpty)

        }
    }

    object trackGenerator {
        val flow = flow {
            while (currentCoroutineContext().isActive) {
                emit(
                    createTruck(isEmpty = false)
                )
                delay(1000)
            }
        }
    }
}

abstract class AbstractGoods(val WHEIT: Int, val TIME_TO_LOAD: Int, val type: Type) {

    fun isFood(): Boolean {
        return type == Type.FOOD
    }
}

object GoodsHelper {
    //большие товары

    fun createBigTable() = object : AbstractGoods(WHEIT = 50, TIME_TO_LOAD = 400, Type.BIG_GOODS) {}
    fun createBigChair() = object : AbstractGoods(WHEIT = 48, TIME_TO_LOAD = 380, Type.BIG_GOODS) {}
    fun createBigStand() = object : AbstractGoods(WHEIT = 46, TIME_TO_LOAD = 360, Type.BIG_GOODS) {}

    //средние товары
    fun createMiddleBox() = object : AbstractGoods(WHEIT = 40, TIME_TO_LOAD = 340, Type.MEDIUM_GOODS) {}
    fun createMiddleLamp() = object : AbstractGoods(WHEIT = 38, TIME_TO_LOAD = 320, Type.MEDIUM_GOODS) {}
    fun createMiddleComputer() = object : AbstractGoods(WHEIT = 36, TIME_TO_LOAD = 300, Type.MEDIUM_GOODS) {}

    //малогабаритные товары
    fun createSmallBox() = object : AbstractGoods(WHEIT = 32, TIME_TO_LOAD = 280, Type.SMALL_GOODS) {}
    fun createSmallLamp() = object : AbstractGoods(WHEIT = 30, TIME_TO_LOAD = 260, Type.SMALL_GOODS) {}
    fun createSmallLaptop() = object : AbstractGoods(WHEIT = 28, TIME_TO_LOAD = 240, Type.SMALL_GOODS) {}

    //пищевые товары
    fun createPotatoBox() = object : AbstractGoods(WHEIT = 26, TIME_TO_LOAD = 220, Type.FOOD) {}
    fun createBananaBox() = object : AbstractGoods(WHEIT = 24, TIME_TO_LOAD = 200, Type.FOOD) {}
    fun createCucumberBox() = object : AbstractGoods(WHEIT = 22, TIME_TO_LOAD = 180, Type.FOOD) {}

    fun getGoods(): AbstractGoods {
        var randomGoods = (0..10).random()
        return when (randomGoods) {
            0 -> createBigTable()
            1 -> createBigChair()
            2 -> createBigStand()
            3 -> createMiddleBox()
            4 -> createMiddleLamp()
            5 -> createMiddleComputer()
            6 -> createSmallBox()
            7 -> createSmallLamp()
            8 -> createSmallLaptop()
            9 -> createPotatoBox()
            10 -> createBananaBox()
            else -> createCucumberBox()
        }
    }
}

enum class Type {
    BIG_GOODS, MEDIUM_GOODS, SMALL_GOODS, FOOD
}