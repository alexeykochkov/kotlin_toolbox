

// Memory
// Heap/Stack

class ReferenceCounter {
public:
    ReferenceCounter () {
        counter_ = 1;  // один держатель ссылки есть
    }
    int* counter_ = new int();

    void increment() {
        ++counter_;
    }

    void decrement() {
        --counter_;

        if (counter_ == 0) {
//            delete this // удалить себя
        }
    }

    void copy() {
        increment();
    }

    void decturction() {
        decrement();
    }
};

class Warrior : public ReferenceCounter {  // Не явно наследуется
    int* real_stuff = new int(); // REAL heap allocation

    int health_points_;  // allocated on heap

public:
    void attack() {

    }
};

auto fun() {
    auto warrior = Warrior();  // heap - всего один в примере, но на немного много считающих ссылок
    // warrior - allocated on Stack!

    // vars allocated on stack deleted after stack folding

    // warrior - будет удален
    return warrior/*.copy()*/;  // copy  // counter == 2
    //warrior.decturction();
    // counter = 1
}  // происходить сворачивание стека, и все переменные выделенные на стеке удаляют (в котлине - отдаеются сборщику мусора)

// ref1 -> Obj1
// ref2 -> Obj1

auto fun_with_args(Warrior warrior) {  // Warrior.copy called
    // count = 2
    warrior.attack();

    return warrior; // Warrior.copy called
}

int main() {
//    auto warrior = new Warrior();  // heap
    // warrior - allocated on Stack!

    // vars allocated on stack deleted after stack folding

    auto warrior1 = fun();  // counter = 1
    auto warrior2 = warrior1; // warrior2.countr = 2 и у warrior1.countre = 2

    auto warrior4 = fun_with_args(warrior1);  // warrior1.copy() -> count = 2
}  // warrior1.desturction() => count == 0 - warrior1 - DEAD




















