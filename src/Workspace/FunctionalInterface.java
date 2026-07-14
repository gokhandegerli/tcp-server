package Workspace;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class FunctionalInterface {


  static void main() {

    System.out.println("Hello World");

    Runnable runnable = FunctionalInterface::doWork;
    runnable.run();
    System.out.println("---------------------------------------");

    int number = 6;
    Predicate<Integer> predicate = n -> n > 5;
    System.out.println(
        "Predicate result: " + number + " is greater than 5? " + predicate.test(number));
    System.out.println("---------------------------------------");

    Function<Integer, Integer> function = n -> n * n;
    Integer apply = function.apply(number);
    System.out.println("Number: " + number + ", Square result: " + apply);
    System.out.println("---------------------------------------");


    Consumer<String> consumer = s -> System.out.println("Consumer accepted: " + s);
    consumer.accept("This is a test");
    System.out.println("---------------------------------------");

    Supplier<Integer> supplier = () -> number * 2;
    System.out.println("Supplier sonuc: " + supplier.get());
    System.out.println("---------------------------------------");


  }

  private static void doWork() {
    System.out.println("Running Sample");
  }


}
